import base64
import copy
import importlib.util
import json
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch
import uuid

import yaml

spec = importlib.util.spec_from_file_location("gear_ownership", Path(__file__).with_name("gear-ownership.py"))
gear = importlib.util.module_from_spec(spec)
spec.loader.exec_module(gear)


class RecoveryTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name) / "data"
        self.root.mkdir()
        self.backup = Path(self.temp.name) / "recovery"
        self.account, self.character, self.recipient, self.heir, self.death, self.key = [str(uuid.uuid4()) for _ in range(6)]
        self.before = {"world": "world", "x": 10, "y": 64, "z": 20, "owner": self.account,
                       "display": str(uuid.uuid4()), "item": {"legacy": "kept", "future": None}, "rested": True,
                       "charged": {"part": 2}, "future": {"null": None, "binary": b"\x00\xff", "types": [True, 1, 1.0]},
                       "occupancy-id": self.key, "ownership": {"format": 1, "kind": "CHARACTER", "account": self.account, "character": self.character},
                       "effect-state": "active", "item-bytes": base64.b64encode(b"Paper item bytes").decode()}
        self.after = copy.deepcopy(self.before)
        self.after["ownership"].update({"account": self.recipient, "character": self.heir})
        self.entry = {"type": "station", "property": f"station:world,10,64,20@{self.key}", "id": self.key,
                      "source": "gear-stations.yml", "target": "gear-stations.yml",
                      "selector": base64.b64encode(yaml.safe_dump({"key": 0}).encode()).decode(),
                      "death": self.death, "from-account": self.account, "from-character": self.character,
                      "to-account": self.recipient, "to-character": self.heir, "revision": 1,
                      "decision": self.decision(), "phase": "moved"}
        self.snapshots(self.entry, yaml.safe_dump(self.before).encode(), yaml.safe_dump(self.after).encode())
        self.journal = self.root / "gear-ownership/estates" / (self.death + ".json")
        self.saved = {"format": 1, "kind": "estate", "death": self.death, "from-account": self.account, "from-character": self.character, "entries": [self.entry]}
        self.other = {"world": None, "opaque": {"future": None}}
        self.document = {"future-root": {"null": None}, "stations": {0: self.after, "0": self.other, "unloaded": None}}
        self.station_file = self.root / "gear-stations.yml"
        self.write(self.station_file, yaml.safe_dump(self.document, sort_keys=False).encode())
        self.write_json(self.journal, self.saved)
        self.receipt(self.entry)
        self.write(self.root / "profiles.yml", b"focus: do not change\n")

    def decision(self):
        return f"EstateDecision[deathId={self.death}, testator=CharacterRef[ownerId={self.account}, characterId={self.character}], diedAt=1970-01-01T00:00:00Z, property=Character[ref=CharacterRef[ownerId={self.recipient}, characterId={self.heir}]], guildLocks=Frozen[], revision=1]"

    @staticmethod
    def write(path, data):
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_bytes(data)

    def write_json(self, path, value):
        self.write(path, json.dumps(value).encode())

    def snapshots(self, entry, before, after):
        for name, value in (("before", before), ("after", after)):
            entry[name] = base64.b64encode(value).decode()
            entry[name + "-hash"] = gear.digest(value)
        self.write(self.root / "gear-ownership/backups" / (entry["before-hash"] + ".bin"), before)

    def receipt(self, entry):
        path = self.root / "gear-ownership/receipts" / self.death / (gear.digest(entry["property"].encode()) + ".json")
        self.write_json(path, dict(entry, phase="moved", format=1, kind="receipt"))
        return path

    def inspect(self, apply=False):
        return gear.inspect(self.root, "estates", self.death, apply, self.backup)

    def completion(self, revision=1, moved=None, retained=None, decision=None):
        record = {"format": 1, "kind": "completion", "death": self.death,
                  "from-account": self.account, "from-character": self.character,
                  "revision": revision, "decision": decision or self.decision(),
                  "moved": [self.entry["property"]] if moved is None else moved,
                  "retained": [] if retained is None else retained}
        if ", property=Character[" in record["decision"]:
            record.update({"recipient-kind": "CHARACTER", "to-account": self.recipient, "to-character": self.heir})
        else:
            record["recipient-kind"] = "WAITING" if ", property=Waiting[]," in record["decision"] else "FROZEN"
        record["counts"] = {"gear": len(record["moved"])}
        self.saved.setdefault("completions", {})[str(revision)] = record
        path = self.root / "gear-ownership/completions" / self.death / (str(revision) + ".json")
        self.write_json(path, record)
        self.write_json(self.journal, self.saved)
        return path

    def test_completion_and_empty_historical_retention_bind_independent_inventory_before_restore(self):
        current = self.completion()
        frozen = self.decision().replace(f"Character[ref=CharacterRef[ownerId={self.recipient}, characterId={self.heir}]]", "Frozen[]").replace("revision=1]", "revision=0]")
        earlier = self.completion(0, [], [self.entry["property"]], frozen)
        self.assertNotIn("error", self.inspect()["journals"][0])
        original = self.station_file.read_bytes()
        self.assertNotIn("error", self.inspect(True)["journals"][0])
        for path in (current, earlier):
            self.assertFalse(path.exists())
            self.assertTrue((self.root / "gear-ownership/rolled-back/completions" / self.death / path.name).is_file())
        self.assertEqual(original, (self.backup / "data/gear-stations.yml").read_bytes())

    def test_hash_consistent_completion_corruption_blocks_whole_apply_before_backup(self):
        path = self.completion()
        original = self.station_file.read_bytes()
        valid = copy.deepcopy(self.saved["completions"]["1"])
        changes = [("revision", 1.0), ("from-account", self.recipient), ("decision", "wrong"),
                   ("recipient-kind", "WAITING"), ("to-character", self.character),
                   ("counts", {"gear": 0}), ("moved", []),
                   ("retained", [self.entry["property"]]), ("moved", [self.entry["property"]] * 2)]
        for field, value in changes:
            bad = dict(valid, **{field: value})
            self.saved["completions"]["1"] = bad
            self.write_json(path, bad)
            self.write_json(self.journal, self.saved)
            self.assertIn("error", self.inspect(True)["journals"][0], field)
            self.assertEqual(original, self.station_file.read_bytes())
            self.assertFalse(self.backup.exists())
        self.saved["completions"]["1"] = valid
        self.write_json(path, valid)
        self.write_json(self.journal, self.saved)
        path.unlink()
        self.assertIn("error", self.inspect(True)["journals"][0])
        self.write_json(path, valid)
        self.write_json(path.with_name("01.json"), valid)
        self.assertIn("error", self.inspect(True)["journals"][0])
        self.assertFalse(self.backup.exists())

    def test_duplicate_return_operations_hidden_in_other_character_folders_block_before_restore(self):
        key = str(uuid.uuid4())
        for character in (self.character, self.heir):
            self.write_json(self.root / "gear-returns" / character / (key + ".json"),
                            {"format": 1, "id": key, "owner-account": self.account,
                             "owner-character": character, "state": "delivered", "items": []})
        original = self.station_file.read_bytes()
        self.assertIn("error", self.inspect(True)["journals"][0])
        self.assertEqual(original, self.station_file.read_bytes())
        self.assertFalse(self.backup.exists())

    def test_station_rollback_preserves_integer_and_string_keys_other_rows_nulls_binary_and_profiles(self):
        current = self.station_file.read_bytes()
        result = self.inspect(True)
        self.assertNotIn("error", result["journals"][0])
        restored = gear.raw_yaml(self.station_file.read_bytes())
        self.assertTrue(gear.same(self.before, restored["stations"][0]))
        self.assertTrue(gear.same(self.other, restored["stations"]["0"]))
        self.assertIsNone(restored["stations"]["unloaded"])
        self.assertEqual({"null": None}, restored["future-root"])
        self.assertEqual(current, (self.backup / "data/gear-stations.yml").read_bytes())
        self.assertEqual(b"focus: do not change\n", (self.root / "profiles.yml").read_bytes())
        self.assertFalse((self.backup / "data/profiles.yml").exists())
        self.assertFalse(self.journal.exists())

    def test_current_progress_and_restored_completed_source_block_without_effects(self):
        for value in (dict(self.after, rested=False), self.before):
            document = copy.deepcopy(self.document)
            document["stations"][0] = value
            self.write(self.station_file, yaml.safe_dump(document).encode())
            original = self.station_file.read_bytes()
            result = self.inspect(True)
            self.assertIn("error", result["journals"][0])
            self.assertEqual(original, self.station_file.read_bytes())
            self.assertTrue(self.journal.exists())
            self.assertFalse(self.backup.exists())

    def test_recomputed_payload_hashes_cannot_change_unknown_types_items_charge_or_ownership(self):
        for field, value in [("item", {}), ("charged", {}), ("future", {"null": "lost"}), ("occupancy-id", str(uuid.uuid4())), ("rested", False)]:
            entry = copy.deepcopy(self.entry)
            after = copy.deepcopy(self.after)
            after[field] = value
            self.snapshots(entry, gear.payload(entry, "before"), yaml.safe_dump(after).encode())
            self.write_json(self.journal, dict(self.saved, entries=[entry]))
            self.receipt(entry)
            self.assertIn("error", self.inspect()["journals"][0], field)
        after = copy.deepcopy(self.after)
        after["future"]["types"] = [1, True, 1]
        entry = copy.deepcopy(self.entry)
        self.snapshots(entry, gear.payload(entry, "before"), yaml.safe_dump(after).encode())
        self.write_json(self.journal, dict(self.saved, entries=[entry]))
        self.assertIn("error", self.inspect()["journals"][0])

    def test_hash_consistent_unknown_yaml_key_type_changes_block_apply_without_effects(self):
        for previous, changed in ((False, 0), (True, 1), (1, 1.0)):
            with self.subTest(previous=type(previous), changed=type(changed)):
                before = copy.deepcopy(self.before)
                after = copy.deepcopy(self.after)
                before["future"]["typed-keys"] = {previous: "kept"}
                after["future"]["typed-keys"] = {changed: "kept"}
                entry = copy.deepcopy(self.entry)
                self.snapshots(entry, yaml.safe_dump(before).encode(), yaml.safe_dump(after).encode())
                self.write_json(self.journal, dict(self.saved, entries=[entry]))
                self.receipt(entry)
                document = copy.deepcopy(self.document)
                document["stations"][0] = after
                self.write(self.station_file, yaml.safe_dump(document, sort_keys=False).encode())
                original = {path.relative_to(self.root): path.read_bytes() for path in self.root.rglob("*") if path.is_file()}
                self.assertIn("error", self.inspect(True)["journals"][0])
                self.assertEqual(original, {path.relative_to(self.root): path.read_bytes() for path in self.root.rglob("*") if path.is_file()})
                self.assertFalse(self.backup.exists())

    def test_schema_fullrefs_decision_paths_and_typed_selector_are_checked_for_all_receipts(self):
        for field, value in [("revision", 1.5), ("revision", True), ("decision", "wrong"), ("death", str(uuid.uuid4())),
                             ("from-account", str(uuid.uuid4())), ("to-account", str(uuid.uuid4())), ("target", "../outside"),
                             ("selector", base64.b64encode(b"key: '0'\n").decode())]:
            entry = dict(self.entry, **{field: value})
            self.write_json(self.journal, dict(self.saved, entries=[entry]))
            self.receipt(entry)
            before = self.station_file.read_bytes()
            self.assertIn("error", self.inspect(True)["journals"][0], field)
            self.assertEqual(before, self.station_file.read_bytes())

    def test_invalid_later_entry_and_missing_extra_or_changed_receipts_never_partially_restore(self):
        cases = [dict(self.saved, entries=[self.entry, {"type": "station", "id": str(uuid.uuid4())}]),
                 dict(self.saved, entries=[self.entry, self.entry])]
        for saved in cases:
            self.write_json(self.journal, saved)
            current = self.station_file.read_bytes()
            self.assertIn("error", self.inspect(True)["journals"][0])
            self.assertEqual(current, self.station_file.read_bytes())
        self.write_json(self.journal, self.saved)
        self.receipt(self.entry).unlink()
        self.assertIn("error", self.inspect()["journals"][0])
        self.receipt(self.entry)
        extra = dict(self.entry, property="wrong")
        self.receipt(extra)
        self.assertIn("error", self.inspect()["journals"][0])

    def add_return(self, state="pending", phase="moved"):
        key = str(uuid.uuid4())
        before = {"format": 1, "id": key, "owner-account": self.account, "owner-character": self.character,
                  "state": state, "items": [base64.b64encode(b"real item bytes").decode()], "future": {"kept": None}}
        after = dict(before, **{"owner-account": self.recipient, "owner-character": self.heir})
        entry = {key: value for key, value in self.entry.items() if key not in ("selector", "before", "after", "before-hash", "after-hash")}
        entry.update({"type": "return", "property": "return:" + key, "id": key, "source": f"gear-returns/{self.character}/{key}.json", "target": f"gear-returns/{self.heir}/{key}.json", "phase": phase})
        self.snapshots(entry, json.dumps(before).encode(), json.dumps(after).encode())
        self.write(self.root / entry["target"], gear.payload(entry, "after"))
        if phase == "moved":
            self.receipt(entry)
        self.saved["entries"].append(entry)
        self.write_json(self.journal, self.saved)
        return entry

    def test_mixed_station_and_return_rollback_restores_exact_queue_bytes_with_backup_first(self):
        entry = self.add_return()
        result = self.inspect(True)
        self.assertNotIn("error", result["journals"][0])
        self.assertEqual(gear.payload(entry, "before"), (self.root / entry["source"]).read_bytes())
        self.assertFalse((self.root / entry["target"]).exists())
        self.assertEqual(gear.payload(entry, "after"), (self.backup / "data" / entry["target"]).read_bytes())

    def test_ambiguous_return_effects_or_progress_hold_entire_mixed_journal(self):
        entry = self.add_return(state="held")
        station = self.station_file.read_bytes()
        target = (self.root / entry["target"]).read_bytes()
        self.assertIn("error", self.inspect(True)["journals"][0])
        self.assertEqual(station, self.station_file.read_bytes())
        self.assertEqual(target, (self.root / entry["target"]).read_bytes())
        self.assertFalse(self.backup.exists())

    def test_fixed_legacy_migration_roundtrip_and_unapplied_plan_do_not_guess_new_owner(self):
        self.journal.unlink()
        self.receipt(self.entry).unlink()
        before = {key: value for key, value in self.before.items() if key not in ("occupancy-id", "ownership", "effect-state")}
        after = copy.deepcopy(before)
        after.update({"occupancy-id": self.key, "ownership": {"format": 1, "kind": "CHARACTER", "account": self.account, "character": self.heir}, "effect-state": "active"})
        entry = {key: value for key, value in self.entry.items() if key not in ("death", "from-character", "revision", "decision")}
        entry["to-account"] = self.account
        self.snapshots(entry, yaml.safe_dump(before).encode(), yaml.safe_dump(after).encode())
        run = str(uuid.uuid4())
        path = self.root / "gear-ownership/migration" / (run + ".json")
        saved = {"format": 1, "kind": "migration", "run": run, "entries": [entry]}
        self.write_json(path, saved)
        document = copy.deepcopy(self.document)
        document["stations"][0] = after
        self.write(self.station_file, yaml.safe_dump(document).encode())
        result = gear.inspect(self.root, "migration", run, True, self.backup)
        self.assertNotIn("error", result["journals"][0])
        self.assertTrue(gear.same(before, gear.raw_yaml(self.station_file.read_bytes())["stations"][0]))
        path.parent.mkdir(parents=True, exist_ok=True)
        saved["entries"][0]["phase"] = "planned"
        run = str(uuid.uuid4())
        saved["run"] = run
        path = path.with_name(run + ".json")
        self.write_json(path, saved)
        original = self.station_file.read_bytes()
        preview = gear.inspect(self.root, "migration", run)
        self.assertIn("unapplied", preview["journals"][0]["records"][0]["result"])
        self.assertEqual(original, self.station_file.read_bytes())
        result = gear.inspect(self.root, "migration", run, True, Path(self.temp.name) / "second-recovery")
        self.assertNotIn("error", result["journals"][0])
        self.assertEqual(original, self.station_file.read_bytes())

    def test_duplicate_formats_inventory_and_backup_failure_preserve_every_current_byte(self):
        self.assertRaises(ValueError, gear.raw_json, '{"format":1,"format":1}')
        self.assertRaises(ValueError, gear.raw_yaml, "a: {key: null, key: 1}")
        self.assertRaises(ValueError, gear.raw_yaml, "a: &loop [*loop]")
        self.assertRaises(ValueError, gear.inspect, self.root, "estates", None, True, self.backup)
        self.assertRaises(ValueError, gear.backup_gear, self.root, self.root / "unsafe")
        current = self.station_file.read_bytes()
        with patch.object(gear, "durable", side_effect=OSError("backup disk full")):
            result = self.inspect(True)
        self.assertIn("error", result["journals"][0])
        self.assertEqual(current, self.station_file.read_bytes())
        self.assertTrue(self.journal.exists())
        (self.root / "gear-returns").symlink_to(Path(self.temp.name))
        self.assertRaises(ValueError, self.inspect)

    def test_whole_station_image_is_rechecked_after_backup_and_preserves_latest_unrelated_data(self):
        backup = gear.backup_gear
        def latest(root, destination):
            document = gear.raw_yaml(self.station_file.read_bytes())
            document["future-root"]["latest"] = "preserved"
            self.write(self.station_file, yaml.safe_dump(document, sort_keys=False).encode())
            return backup(root, destination)
        with patch.object(gear, "backup_gear", side_effect=latest):
            result = self.inspect(True)
        self.assertNotIn("error", result["journals"][0])
        self.assertEqual("preserved", gear.raw_yaml(self.station_file.read_bytes())["future-root"]["latest"])
        self.assertEqual("preserved", gear.raw_yaml((self.backup / "data/gear-stations.yml").read_bytes())["future-root"]["latest"])

    def test_native_world_and_java_integer_selector_constraints_are_mirrored(self):
        for world in ("", " ", "comma,world"):
            self.assertRaises(ValueError, gear.station, dict(self.before, world=world))
        for key in (2147483648, -2147483649, True):
            entry = dict(self.entry, selector=base64.b64encode(yaml.safe_dump({"key": key}).encode()).decode())
            self.write_json(self.journal, dict(self.saved, entries=[entry]))
            before = self.station_file.read_bytes()
            self.assertIn("error", self.inspect(True)["journals"][0])
            self.assertEqual(before, self.station_file.read_bytes())
            self.assertFalse(self.backup.exists())


if __name__ == "__main__":
    unittest.main()
