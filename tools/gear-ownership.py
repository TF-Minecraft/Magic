#!/usr/bin/env python3
"""Inspect Magic ownership intents; undo only unchanged gear records offline."""
import argparse
import base64
import copy
from decimal import Decimal
import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import stat
import tempfile
import uuid

import yaml


class GearLoader(yaml.SafeLoader):
    def construct_mapping(self, node, deep=False):
        self.flatten_mapping(node)
        result = {}
        for key_node, value_node in node.value:
            key = self.construct_object(key_node, deep=deep)
            if key in result:
                raise ValueError("duplicate or ambiguous YAML key")
            result[key] = self.construct_object(value_node, deep=deep)
        return result


def raw_yaml(data):
    value = yaml.load(data, Loader=GearLoader)
    if not isinstance(value, dict):
        raise ValueError("expected YAML map")
    def check(node, ancestors):
        if isinstance(node, (dict, list)):
            if id(node) in ancestors or len(ancestors) >= 100:
                raise ValueError("recursive or excessively nested YAML")
            for child in node.values() if isinstance(node, dict) else node:
                check(child, ancestors | {id(node)})
    check(value, set())
    return value


def raw_json(data):
    def pairs(entries):
        result = {}
        for key, value in entries:
            if key in result:
                raise ValueError("duplicate JSON key")
            result[key] = value
        return result
    def unsupported(value):
        raise ValueError("unsupported JSON number: " + value)
    value = json.loads(data, object_pairs_hook=pairs, parse_float=Decimal, parse_constant=unsupported)
    if not isinstance(value, dict):
        raise ValueError("expected JSON object")
    return value


def same(left, right):
    if type(left) is not type(right):
        return False
    if isinstance(left, dict):
        left_keys = {(type(key), key): key for key in left}
        right_keys = {(type(key), key): key for key in right}
        return left_keys.keys() == right_keys.keys() and all(
            same(left[left_keys[key]], right[right_keys[key]]) for key in left_keys)
    if isinstance(left, list):
        return len(left) == len(right) and all(same(a, b) for a, b in zip(left, right))
    return left == right


def digest(data):
    return hashlib.sha256(data).hexdigest()


def identity(value):
    if not isinstance(value, str) or str(uuid.UUID(value)) != value:
        raise ValueError("invalid UUID identity")
    return value


def integer(value, expected=None):
    if type(value) is not int or value < 0 or value > 9223372036854775807 or expected is not None and value != expected:
        raise ValueError("unsupported integral format/revision")
    return value


def reference(account, character):
    return f"CharacterRef[ownerId={account}, characterId={character}]"


def owner(fields, prefix):
    return identity(fields[prefix + "-account"]), identity(fields[prefix + "-character"])


def station(row):
    if not isinstance(row, dict) or not isinstance(row.get("world"), str) or not row["world"].strip() or "," in row["world"]:
        raise ValueError("invalid station location")
    coordinates = []
    for key in ("x", "y", "z"):
        value = row.get(key)
        if type(value) is not int or not -2147483648 <= value <= 2147483647:
            raise ValueError("invalid station coordinate")
        coordinates.append(value)
    return (row["world"], *coordinates)


def authority(row):
    value = row.get("ownership")
    if not isinstance(value, dict) or integer(value.get("format"), 1) != 1 or value.get("kind") != "CHARACTER":
        raise ValueError("unresolved station authority")
    return identity(value["account"]), identity(value["character"])


def return_row(row, expected_id, expected_owner):
    integer(row.get("format"), 1)
    if identity(row["id"]) != expected_id or owner(row, "owner") != expected_owner or row.get("state") not in ("pending", "delivering", "held", "delivered"):
        raise ValueError("return identity/state/full owner mismatch")
    if not isinstance(row.get("items"), list) or not all(isinstance(item, str) for item in row["items"]):
        raise ValueError("invalid return item bytes")
    for item in row["items"]:
        base64.b64decode(item, validate=True)


def safe(root, relative):
    if not isinstance(relative, str):
        raise ValueError("missing property path")
    path = (root / relative).resolve()
    if path == root or root not in path.parents:
        raise ValueError("property path escapes gear data")
    return path


def payload(entry, part):
    value = base64.b64decode(entry[part], validate=True)
    if digest(value) != entry[part + "-hash"]:
        raise ValueError("property payload hash mismatch")
    return value


def captured(journal, entry):
    death = identity(journal["death"])
    source, recipient = owner(entry, "from"), owner(entry, "to")
    revision = integer(entry["revision"])
    decision = entry["decision"]
    if entry.get("death") != death or source != owner(journal, "from") or source == recipient:
        raise ValueError("estate full reference/death mismatch")
    if not isinstance(decision, str) or not decision.startswith(f"EstateDecision[deathId={death}, testator={reference(*source)}, diedAt=") or f", property=Character[ref={reference(*recipient)}], guildLocks=" not in decision or not decision.endswith(f", revision={revision}]"):
        raise ValueError("captured full estate decision mismatch")
    return source, recipient


def property_id(value):
    if not isinstance(value, str):
        raise ValueError("invalid completion property ID")
    if value.startswith("return:"):
        identity(value[7:])
    elif value.startswith("station:"):
        location, occupancy = value[8:].rsplit("@", 1)
        world, x, y, z = location.split(",")
        row = {"world": world, "x": int(x), "y": int(y), "z": int(z)}
        station(row)
        if [str(row[key]) for key in ("x", "y", "z")] != [x, y, z]:
            raise ValueError("noncanonical completion station coordinate")
        identity(occupancy)
    else:
        raise ValueError("unsupported completion property ID")
    return value


def completion_ids(value):
    if not isinstance(value, list):
        raise ValueError("missing completion property inventory")
    ids = [property_id(item) for item in value]
    if len(set(ids)) != len(ids) or ids != sorted(ids, key=lambda item: item.encode("utf-16-be")):
        raise ValueError("duplicate or unsorted completion property IDs")
    return ids


def completion_plan(root, journal, plans):
    copies = journal.get("completions", {})
    if not isinstance(copies, dict):
        raise ValueError("invalid completion inventory")
    folder = root / "gear-ownership/completions" / journal["death"]
    expected_files, result = set(), []
    source = owner(journal, "from")
    for key, record in copies.items():
        if not isinstance(key, str) or not re.fullmatch(r"0|[1-9][0-9]*", key) or not isinstance(record, dict):
            raise ValueError("invalid completion revision record")
        revision = integer(record.get("revision"), int(key))
        integer(record.get("format"), 1)
        if record.get("kind") != "completion" or record.get("death") != journal["death"] or owner(record, "from") != source:
            raise ValueError("completion full source/header differs")
        decision = record.get("decision")
        if not isinstance(decision, str) or not decision.startswith(f"EstateDecision[deathId={journal['death']}, testator={reference(*source)}, diedAt=") or not decision.endswith(f", revision={revision}]"):
            raise ValueError("completion full captured decision differs")
        recipient = re.search(r", property=(Character\[ref=CharacterRef\[ownerId=([^,]+), characterId=([^\]]+)\]\]|Waiting\[\]|Frozen\[\]), guildLocks=", decision)
        if recipient is None:
            raise ValueError("unsupported completion property recipient")
        if recipient.group(2) is not None:
            target = identity(recipient.group(2)), identity(recipient.group(3))
            if record.get("recipient-kind") != "CHARACTER" or owner(record, "to") != target:
                raise ValueError("completion independent recipient differs")
        elif record.get("recipient-kind") != recipient.group(1).removesuffix("[]").upper() or "to-account" in record or "to-character" in record:
            raise ValueError("completion noncharacter recipient differs")
        moved, retained = completion_ids(record.get("moved")), completion_ids(record.get("retained"))
        counts = record.get("counts")
        if not isinstance(counts, dict) or set(counts) != {"gear"} or integer(counts["gear"]) != len(moved) or set(moved) & set(retained):
            raise ValueError("completion counts/retained inventory differs")
        expected = {plan["entry"]["property"] for plan in plans if "receipt" in plan and integer(plan["entry"]["revision"]) <= revision}
        if set(moved) != expected:
            raise ValueError("completion cumulative receipt IDs differ")
        for plan in plans:
            entry = plan["entry"]
            if integer(entry["revision"]) == revision and entry["decision"] != decision:
                raise ValueError("completion/entry same revision decision differs")
        path = folder / (key + ".json")
        if not same(raw_json(path.read_bytes()), record):
            raise ValueError("independent immutable completion differs")
        expected_files.add(path)
        result.append(path)
    if set(folder.glob("*.json")) != expected_files:
        raise ValueError("missing/extra immutable completion inventory")
    return result


def entry_plan(root, journal, entry):
    key = identity(entry["id"])
    if entry.get("type") not in ("station", "return") or entry.get("phase") not in ("planned", "published", "moved"):
        raise ValueError("unsupported property kind/phase")
    before_bytes, after_bytes = payload(entry, "before"), payload(entry, "after")
    archive = root / "gear-ownership/backups" / (entry["before-hash"] + ".bin")
    if archive.read_bytes() != before_bytes:
        raise ValueError("immutable original archive differs")
    recipient = owner(entry, "to")
    if journal["kind"] == "estate":
        source_owner, recipient = captured(journal, entry)
    else:
        if entry["type"] != "station" or identity(entry["from-account"]) != recipient[0]:
            raise ValueError("invalid fixed legacy recipient")
        source_owner = None
    source, target = safe(root, entry["source"]), safe(root, entry["target"])
    if entry["type"] == "station":
        if entry["source"] != "gear-stations.yml" or entry["target"] != "gear-stations.yml":
            raise ValueError("station path mismatch")
        before, after = raw_yaml(before_bytes), raw_yaml(after_bytes)
        location = station(before)
        if station(after) != location or identity(after["occupancy-id"]) != key or authority(after) != recipient:
            raise ValueError("station location/occupancy/full authority mismatch")
        expected_property = "station:" + ",".join(str(value) for value in location) + "@" + key
        if entry.get("property") != expected_property:
            raise ValueError("station property ID mismatch")
        selector = raw_yaml(base64.b64decode(entry["selector"], validate=True))
        if set(selector) != {"key"} or type(selector["key"]) not in (str, int):
            raise ValueError("invalid typed station selector")
        if type(selector["key"]) is int and not -2147483648 <= selector["key"] <= 2147483647:
            raise ValueError("station selector is outside Java Integer range")
        expected = copy.deepcopy(before)
        if journal["kind"] == "migration":
            if identity(before["owner"]) != entry["from-account"] or any(field in before for field in ("occupancy-id", "ownership", "effect-state")):
                raise ValueError("legacy source ownership/reserved fields mismatch")
            expected.update({"occupancy-id": key, "ownership": {"format": 1, "kind": "CHARACTER", "account": recipient[0], "character": recipient[1]}, "effect-state": "active"})
        else:
            if authority(before) != source_owner or identity(before["occupancy-id"]) != key:
                raise ValueError("station source full authority mismatch")
            expected["ownership"]["account"], expected["ownership"]["character"] = recipient
        if not same(expected, after):
            raise ValueError("station plan changes content beyond ownership")
        return {"entry": entry, "source": source, "target": target, "before": before, "after": after, "before-bytes": before_bytes, "after-bytes": after_bytes, "selector": selector["key"]}
    if "selector" in entry or entry.get("property") != "return:" + key:
        raise ValueError("return selector/property mismatch")
    expected_source = f"gear-returns/{source_owner[1]}/{key}.json"
    expected_target = f"gear-returns/{recipient[1]}/{key}.json"
    if entry["source"] != expected_source or entry["target"] != expected_target or source == target:
        raise ValueError("return paths differ from full fixed refs")
    before, after = raw_json(before_bytes), raw_json(after_bytes)
    return_row(before, key, source_owner)
    return_row(after, key, recipient)
    expected = copy.deepcopy(before)
    expected["owner-account"], expected["owner-character"] = recipient
    if not same(expected, after):
        raise ValueError("return plan changes content beyond ownership")
    return {"entry": entry, "source": source, "target": target, "before": before, "after": after, "before-bytes": before_bytes, "after-bytes": after_bytes}


def journal_plan(root, path, kind):
    journal = raw_json(path.read_bytes())
    integer(journal.get("format"), 1)
    if journal.get("kind") != ("estate" if kind == "estates" else "migration"):
        raise ValueError("journal kind mismatch")
    if identity(journal["death" if kind == "estates" else "run"]) != path.stem or not isinstance(journal.get("entries"), list):
        raise ValueError("journal path/entry inventory mismatch")
    plans, properties, selectors = [], set(), set()
    for entry in journal["entries"]:
        plan = entry_plan(root, journal, entry)
        if entry["property"] in properties or entry["type"] == "station" and (type(plan["selector"]), plan["selector"]) in selectors:
            raise ValueError("duplicate property ID/row selector")
        properties.add(entry["property"])
        if entry["type"] == "station":
            selectors.add((type(plan["selector"]), plan["selector"]))
        plans.append(plan)
    if kind == "estates":
        folder = root / "gear-ownership/receipts" / journal["death"]
        expected_files = set()
        for plan in plans:
            entry = plan["entry"]
            receipt = folder / (digest(entry["property"].encode()) + ".json")
            if entry["phase"] == "moved" or receipt.exists():
                expected = dict(entry, phase="moved", format=1, kind="receipt")
                if not same(raw_json(receipt.read_bytes()), expected):
                    raise ValueError("independent immutable receipt differs")
                expected_files.add(receipt)
                plan["receipt"] = receipt
        if set(folder.glob("*.json")) != expected_files:
            raise ValueError("missing/extra immutable receipt inventory")
        completions = completion_plan(root, journal, plans)
    else:
        completions = []
    return journal, plans, completions


def current_plan(root, plans):
    document, stations = None, None
    if any(plan["entry"]["type"] == "station" for plan in plans):
        document = raw_yaml((root / "gear-stations.yml").read_bytes())
        stations = document.get("stations")
        if not isinstance(stations, dict):
            raise ValueError("station inventory is not a map")
        locations, ids = set(), set()
        for row in stations.values():
            if not isinstance(row, dict):
                continue
            try:
                location = station(row)
            except (ValueError, KeyError):
                continue
            if row.get("effect-state") != "released":
                if location in locations:
                    raise ValueError("duplicate station location")
                locations.add(location)
            if row.get("occupancy-id") is not None:
                key = identity(row["occupancy-id"])
                if key in ids:
                    raise ValueError("duplicate occupancy ID")
                ids.add(key)
    for plan in plans:
        entry = plan["entry"]
        if entry["type"] == "station":
            keys = [key for key in stations if type(key) is type(plan["selector"]) and key == plan["selector"]]
            if len(keys) != 1:
                raise ValueError("typed station selector is missing")
            value = stations[keys[0]]
            before, after = same(value, plan["before"]), same(value, plan["after"])
            if not before and not after:
                raise ValueError("station changed or progressed after planning")
            if before and (entry["phase"] != "planned" or "receipt" in plan):
                raise ValueError("completed/published station restored at its source")
            if after and value.get("effect-state") != "active":
                raise ValueError("station effect evidence requires manual recovery")
            if before and entry.get("decision") is not None and value.get("effect-state") != "active":
                raise ValueError("station effect evidence requires manual recovery")
            plan["noop"] = before
        else:
            source, target = plan["source"], plan["target"]
            old = source.read_bytes() if source.is_file() else None
            new = target.read_bytes() if target.is_file() else None
            if old is not None and old != plan["before-bytes"] or new is not None and new != plan["after-bytes"]:
                raise ValueError("return source/destination changed")
            if entry["phase"] == "moved" or "receipt" in plan:
                if old is not None:
                    raise ValueError("completed return restored at its source")
            if old is None and new is None or old is None and entry["phase"] == "planned":
                raise ValueError("return copies/effect evidence missing")
            if entry["phase"] != "planned" and new is None:
                raise ValueError("published return destination disappeared")
            if plan["before"].get("state") != "pending" or plan["after"].get("state") != "pending":
                raise ValueError("return effect evidence requires manual recovery")
            plan["noop"] = old is not None and new is None and entry["phase"] == "planned"
    return document


def staging(path):
    if path.name.startswith("magic-") and path.name.endswith(".tmp"):
        if stat.S_ISREG(path.lstat().st_mode):
            return True
        raise ValueError("nonregular gear staging path requires manual recovery")
    return False


def return_inventory(root, plans):
    inventory = {}
    for folder in (root / "gear-returns").glob("*"):
        if staging(folder):
            continue
        if not folder.is_dir():
            raise ValueError("invalid return directory inventory")
        for path in folder.glob("*"):
            if staging(path):
                continue
            row = raw_json(path.read_bytes())
            key, full_owner = identity(row.get("id")), owner(row, "owner")
            return_row(row, key, full_owner)
            if path.relative_to(root).as_posix() != f"gear-returns/{full_owner[1]}/{key}.json":
                raise ValueError("return full owner/path differs")
            inventory.setdefault(key, []).append(path)
    for key, copies in inventory.items():
        if len(copies) == 1:
            continue
        captured = [plan for plan in plans if plan["entry"]["type"] == "return" and plan["entry"]["id"] == key]
        if len(copies) != 2 or len(captured) != 1:
            raise ValueError("duplicate return operation ID")
        plan = captured[0]
        if plan["entry"]["phase"] == "moved" or "receipt" in plan or set(copies) != {plan["source"], plan["target"]} or plan["source"].read_bytes() != plan["before-bytes"] or plan["target"].read_bytes() != plan["after-bytes"]:
            raise ValueError("duplicate return ID is not an exact unfinished captured publication")


def force(folder):
    for parent in (folder, *folder.parents):
        descriptor = os.open(parent, os.O_RDONLY)
        try:
            os.fsync(descriptor)
        finally:
            os.close(descriptor)


def durable(path, data):
    path.parent.mkdir(parents=True, exist_ok=True)
    force(path.parent)
    descriptor, temporary = tempfile.mkstemp(prefix="gear-ownership-", dir=path.parent)
    try:
        with os.fdopen(descriptor, "wb") as stream:
            stream.write(data)
            stream.flush()
            os.fsync(stream.fileno())
        os.replace(temporary, path)
        force(path.parent)
    finally:
        if os.path.exists(temporary):
            os.unlink(temporary)


def gear_files(root):
    files = []
    for name in ("gear-stations.yml", "gear-returns", "gear-ownership"):
        path = root / name
        for item in ([path] if path.exists() else []) + (list(path.rglob("*")) if path.is_dir() else []):
            if item.is_symlink():
                raise ValueError("gear symlink requires manual recovery")
            if item.is_file():
                files.append(item)
    return sorted(files)


def backup_gear(root, backup):
    backup = Path(backup).resolve()
    if backup == root or root in backup.parents:
        raise ValueError("recovery backup must be outside Magic data")
    files = gear_files(root)
    original = {str(path.relative_to(root)): digest(path.read_bytes()) for path in files}
    backup.mkdir(parents=True)
    for path in files:
        output = backup / "data" / path.relative_to(root)
        durable(output, path.read_bytes())
    copied = {name: digest((backup / "data" / name).read_bytes()) for name in original}
    current = {str(path.relative_to(root)): digest(path.read_bytes()) for path in gear_files(root)}
    if copied != original or current != original:
        raise ValueError("gear recovery backup verification failed")
    durable(backup / "SHA256.json", json.dumps(original, indent=2).encode())
    return original


def archive(path, destination):
    destination.parent.mkdir(parents=True, exist_ok=True)
    force(destination.parent)
    os.link(path, destination)
    force(destination.parent)
    path.unlink()
    force(path.parent)


def inspect(root, kind="estates", selected=None, apply=False, backup=None):
    root = Path(root).resolve()
    if not root.is_dir() or kind not in ("migration", "estates"):
        raise ValueError("invalid gear data directory/kind")
    gear_files(root)  # Reject links before reading any ownership data.
    folder = root / "gear-ownership" / kind
    if selected is not None:
        files = [folder / (identity(selected) + ".json")]
    else:
        files = sorted(folder.glob("*.json"))
    if apply and (selected is None or backup is None):
        raise ValueError("apply requires one --journal and a new --backup")
    report = {"journals": []}
    for path in files:
        row = {"journal": str(path), "records": []}
        report["journals"].append(row)
        try:
            journal, plans, completions = journal_plan(root, path, kind)
            return_inventory(root, plans)
            document = current_plan(root, plans)
            moved_archive = root / "gear-ownership/rolled-back" / kind / path.name
            if moved_archive.exists():
                raise ValueError("rollback journal archive already exists")
            for completion in completions:
                if (root / "gear-ownership/rolled-back/completions" / completion.parent.name / completion.name).exists():
                    raise ValueError("rollback completion archive already exists")
            for plan in plans:
                if "receipt" in plan and (root / "gear-ownership/rolled-back/receipts" / plan["receipt"].parent.name / plan["receipt"].name).exists():
                    raise ValueError("rollback receipt archive already exists")
                row["records"].append({"property": plan["entry"]["property"], "result": "unapplied plan; no property effects" if plan["noop"] else "safe unchanged ownership rollback"})
            if apply:
                original = backup_gear(root, backup)
                if original != {str(item.relative_to(root)): digest(item.read_bytes()) for item in gear_files(root)}:
                    raise ValueError("gear changed after recovery preflight")
                document = current_plan(root, plans)
                return_inventory(root, plans)
                # One checked whole-file publication preserves every unrelated station row.
                if document is not None and any(plan["entry"]["type"] == "station" and not plan["noop"] for plan in plans):
                    for plan in plans:
                        if plan["entry"]["type"] == "station" and not plan["noop"]:
                            document["stations"][plan["selector"]] = copy.deepcopy(plan["before"])
                    durable(root / "gear-stations.yml", yaml.safe_dump(document, sort_keys=False).encode())
                for plan in plans:
                    if plan["entry"]["type"] == "return" and not plan["noop"]:
                        current_plan(root, [plan])
                        if not plan["source"].exists():
                            durable(plan["source"], plan["before-bytes"])
                        if plan["target"].exists():
                            plan["target"].unlink()
                            force(plan["target"].parent)
                    if "receipt" in plan:
                        archive(plan["receipt"], root / "gear-ownership/rolled-back/receipts" / plan["receipt"].parent.name / plan["receipt"].name)
                for completion in completions:
                    archive(completion, root / "gear-ownership/rolled-back/completions" / completion.parent.name / completion.name)
                archive(path, moved_archive)
                row["result"] = "restored checked original records; evidence archived"
            else:
                row["result"] = "whole journal preflight passed"
        except (OSError, ValueError, KeyError, TypeError, AttributeError, yaml.YAMLError) as error:
            row["error"] = str(error)
            if apply:
                row["apply_error"] = "preserve recovery backup and keep Paper stopped for manual review"
    return report


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--data", type=Path, required=True, help="plugins/Magic/data; Paper must be stopped")
    parser.add_argument("--kind", choices=("migration", "estates"), default="estates")
    parser.add_argument("--journal", help="one migration run or estate death UUID; required with apply")
    parser.add_argument("--apply", action="store_true")
    parser.add_argument("--backup", type=Path, help="new external gear recovery directory")
    args = parser.parse_args()
    print(json.dumps(inspect(args.data, args.kind, args.journal, args.apply, args.backup), indent=2))


if __name__ == "__main__":
    main()
