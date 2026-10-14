import base64
import copy
import contextlib
import importlib.util
import io
import json
import os
from pathlib import Path
import tempfile
import time
import stat
import sys
from types import SimpleNamespace
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

    def test_regular_magic_staging_at_both_return_levels_is_retained_and_backed_up(self):
        entry = self.add_return()
        staging = [self.root / "gear-returns/magic-outer.tmp",
                   self.root / "gear-returns" / self.heir / "magic-inner.tmp"]
        for path in staging:
            self.write(path, b"\xffpartial staging buffer\x00")
        self.assertNotIn("error", self.inspect()["journals"][0])
        self.assertNotIn("error", self.inspect(True)["journals"][0])
        for path in staging:
            self.assertEqual(b"\xffpartial staging buffer\x00", path.read_bytes())
            self.assertEqual(path.read_bytes(), (self.backup / "data" / path.relative_to(self.root)).read_bytes())
        self.assertEqual(gear.payload(entry, "before"), (self.root / entry["source"]).read_bytes())

    def test_nonregular_magic_staging_and_unknown_files_hold_before_any_backup_or_restore(self):
        for inner in (False, True):
            for kind in ("directory", "file-link", "dangling-link", "unknown", "magic-json", "other-tmp"):
                with self.subTest(inner=inner, kind=kind):
                    self.setUp()
                    self.add_return()
                    folder = self.root / "gear-returns" / self.heir if inner else self.root / "gear-returns"
                    linked = self.root / "unrelated.bin"
                    self.write(linked, b"unrelated bytes")
                    name = {"unknown": "unknown.bin", "magic-json": "magic-buffer.json", "other-tmp": "other.tmp"}.get(kind, "magic-held.tmp")
                    path = folder / name
                    if kind == "directory": path.mkdir()
                    elif kind.endswith("link"): path.symlink_to(linked if kind == "file-link" else self.root / "missing")
                    else: path.write_bytes(b"unrecognized authoritative bytes")
                    original = {file.relative_to(self.root): file.read_bytes() for file in self.root.rglob("*") if file.is_file() and not file.is_symlink()}
                    if kind.endswith("link"):
                        self.assertRaisesRegex(ValueError, "symlink", self.inspect, True)
                    else:
                        result = self.inspect(True)
                        self.assertIn("error", result["journals"][0])
                    self.assertFalse(self.backup.exists())
                    self.assertEqual(original, {file.relative_to(self.root): file.read_bytes() for file in self.root.rglob("*") if file.is_file() and not file.is_symlink()})
                    if kind == "directory": path.rmdir()
                    else: path.unlink()

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


class LegacyEmptyProducerPolicyTests(unittest.TestCase):
    def test_shipped_allowlist_is_exact_reviewed_preownership_producer(self):
        unpatched_spec = importlib.util.spec_from_file_location("shipped_gear_ownership", Path(__file__).with_name("gear-ownership.py"))
        shipped = importlib.util.module_from_spec(unpatched_spec)
        unpatched_spec.loader.exec_module(shipped)
        self.assertEqual({"d864c30aa6c01453e38bc2539d0ccea889b846aa47e9050982026e894a2e8247": "Magic 0.5.2"}, shipped.LEGACY_EMPTY_PRODUCERS)
        self.assertNotIn("fb40f229b4b79fd1a6cd10c196d9a9a7d6dc40db4e97d4a7d6806b1f645002af", shipped.LEGACY_EMPTY_PRODUCERS)


class LegacyEmptyImportTests(unittest.TestCase):
    # Exact named output of the retained actual 0.5.2 save probe; no external JAR needed by this suite.
    OLD_0_5_2_EMPTY_SAVE = b""

    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.base = Path(self.temp.name)
        self.root = self.base / "plugins/Magic/data"
        self.root.mkdir(parents=True)
        (self.root / "characters").mkdir()
        self.station = self.root / "gear-stations.yml"
        self.station.write_bytes(self.OLD_0_5_2_EMPTY_SAVE)
        self.station.chmod(0o640)
        self.profile = self.root / "characters/unchanged.yml"
        self.profile.write_bytes(b"focus: keep unchanged\n")
        self.jar = self.base / "legacy.jar"
        self.jar.write_bytes(b"isolated test producer; actual release proved separately")
        # Isolate file-hash validation without licensing/external artifacts in normal CI.
        producer = patch.dict(gear.LEGACY_EMPTY_PRODUCERS, {gear.digest(self.jar.read_bytes()): "Magic 0.5.2"}, clear=True)
        producer.start()
        self.addCleanup(producer.stop)
        self.backup = self.base / "external/recovery"
        self.attest = self.base / "lineage.txt"

    def snapshot(self):
        rows = {}
        for path in sorted(self.root.rglob("*")):
            info = os.lstat(path)
            data = path.read_bytes() if stat.S_ISREG(info.st_mode) else os.readlink(path) if stat.S_ISLNK(info.st_mode) else None
            stamp = (info.st_mtime_ns, info.st_ctime_ns) if stat.S_ISREG(info.st_mode) else None
            rows[str(path.relative_to(self.root))] = (info.st_dev, info.st_ino, info.st_mode, info.st_uid, info.st_gid, info.st_nlink, stamp, data)
        return rows

    def preview(self, **kwargs):
        return gear.legacy_empty_import(self.root, self.jar, **kwargs)

    def prepared(self):
        baseline = self.preview()["baseline"]
        self.attest.write_text("Synthetic stopped checkpoint and old-only lineage evidence\nBaseline: " + baseline + "\n")
        return dict(apply=True, expect_baseline=baseline, attest=self.attest, backup=self.backup)

    def apply(self, **overrides):
        args = self.prepared()
        args.update(overrides)
        return self.preview(**args)

    def refused(self, call, reason=None):
        before = self.snapshot()
        report = call()
        self.assertEqual("REFUSED", report["verdict"], report)
        if reason:
            self.assertIn(reason, report["error"])
        self.assertEqual(before, self.snapshot(), "refusal must preserve source bytes and inodes")
        return report

    def cli(self, *args):
        output, error = io.StringIO(), io.StringIO()
        code = 0
        with patch.object(sys, "argv", ["gear-ownership.py", "--data", str(self.root), *map(str, args)]), contextlib.redirect_stdout(output), contextlib.redirect_stderr(error):
            try:
                gear.main()
            except SystemExit as stopped:
                code = stopped.code
        self.assertNotIn("Traceback", output.getvalue() + error.getvalue())
        return code, json.loads(output.getvalue()) if output.getvalue() else None

    def test_actual_old_empty_fixture_preview_is_inert_and_binds_metadata(self):
        before = self.snapshot()
        report = self.preview()
        self.assertEqual("ELIGIBLE_REQUIRES_LINEAGE_ATTESTATION", report["verdict"])
        self.assertEqual(gear.digest(b""), report["record"]["station"]["sha256"])
        self.assertEqual({"dev", "ino", "size", "sha256", "mode", "uid", "gid", "mtime-ns", "ctime-ns", "nlink"}, set(report["record"]["station"]))
        self.assertEqual(["gear-ownership", "gear-returns"], report["record"]["history-absent"])
        self.assertEqual("8a4c1c4742d8bc8be8c84c2f91a2b5337a275b0caa0acf9f4ac88c7628fb5619", report["record"]["target-sha256"])
        self.assertEqual(before, self.snapshot())
        self.assertFalse(self.backup.exists())

    def test_apply_publishes_only_canonical_empty_and_durable_evidence(self):
        args = self.prepared()
        before = self.snapshot()
        with patch.object(gear.os, "open", wraps=os.open) as opens:
            report = self.preview(**args)
        self.assertEqual("IMPORTED", report["verdict"], report)
        self.assertEqual(b"stations: {}\n", self.station.read_bytes())
        self.assertEqual(0o640, stat.S_IMODE(self.station.stat().st_mode))
        self.assertEqual(before["characters/unchanged.yml"], self.snapshot()["characters/unchanged.yml"])
        self.assertEqual({"characters", "gear-stations.yml"}, set(p.name for p in self.root.iterdir()))
        self.assertEqual(b"", (self.backup / "data/gear-stations.yml").read_bytes())
        self.assertEqual({"gear-stations.yml": gear.digest(b"")}, json.loads((self.backup / "SHA256.json").read_text()))
        self.assertEqual(self.attest.read_bytes(), (self.backup / "LINEAGE-ATTESTATION").read_bytes())
        plan = json.loads((self.backup / "IMPORT-PLAN.json").read_text())
        self.assertEqual(args["expect_baseline"], plan["baseline"])
        self.assertEqual(gear.digest(self.attest.read_bytes()), plan["attestation-sha256"])
        self.assertEqual(gear.digest(Path(gear.__file__).read_bytes()), plan["tool-sha256"])
        self.assertTrue(plan["created-at-utc"].endswith("+00:00"))
        self.assertEqual(report, json.loads((self.backup / "IMPORT-RESULT.json").read_text()))
        flags = next(call.args[1] for call in opens.call_args_list if Path(call.args[0]).name.startswith(gear.LEGACY_EMPTY_STAGING))
        self.assertEqual(os.O_CREAT | os.O_EXCL | os.O_NOFOLLOW, flags & (os.O_CREAT | os.O_EXCL | os.O_NOFOLLOW))

    def test_already_canonical_replay_creates_no_backup_or_new_publication(self):
        self.assertEqual("IMPORTED", self.apply()["verdict"])
        before = self.snapshot()
        second = self.base / "second-recovery"
        report = self.preview(apply=True, expect_baseline="stale", backup=second)
        self.assertEqual("ALREADY_CANONICAL", report["verdict"])
        self.assertEqual(before, self.snapshot())
        self.assertFalse(second.exists())
        for legacy_args in [(), ("--kind", "migration"), ("--kind", "estates")]:
            code, report = self.cli(*legacy_args)
            self.assertEqual(0, code)
            self.assertEqual({"journals": []}, report)

    @staticmethod
    def ownership_view(info, **changed):
        fields = {name: getattr(info, name) for name in dir(info) if name.startswith("st_")}
        fields.update(changed)
        return SimpleNamespace(**fields)

    def test_matching_ownership_never_calls_chown_including_mirrored_views(self):
        owner = self.station.stat()
        with patch.object(gear.os, "fchown", side_effect=PermissionError("matching bindfs ownership must not be changed")) as chown:
            report = self.apply()
        self.assertEqual("IMPORTED", report["verdict"], report)
        chown.assert_not_called()
        self.assertEqual((owner.st_uid, owner.st_gid), (report["published"]["uid"], report["published"]["gid"]))
        self.assertEqual((owner.st_uid, owner.st_gid), (self.station.stat().st_uid, self.station.stat().st_gid))

    def test_different_staging_owner_is_restored_before_mode_and_fsync(self):
        real_open, real_stat, real_chown, real_chmod, real_fsync = os.open, os.fstat, os.fchown, os.fchmod, os.fsync
        for uid_differs, gid_differs in [(True, False), (False, True), (True, True)]:
            with self.subTest(uid_differs=uid_differs, gid_differs=gid_differs):
                self.station.write_bytes(b"")
                self.backup = self.base / ("recovery-" + str(uid_differs) + "-" + str(gid_differs))
                owner = self.station.stat()
                inode, observed, events = [], [], []
                def opened(path, flags, *args):
                    fd = real_open(path, flags, *args)
                    if Path(path).name.startswith(gear.LEGACY_EMPTY_STAGING) and flags & os.O_CREAT:
                        info = real_stat(fd)
                        inode.append((info.st_dev, info.st_ino))
                    return fd
                def statted(fd):
                    info = real_stat(fd)
                    if inode and (info.st_dev, info.st_ino) == inode[0] and info.st_size == len(gear.LEGACY_EMPTY_CANONICAL) and not observed:
                        observed.append(True)
                        return self.ownership_view(info, st_uid=owner.st_uid + int(uid_differs), st_gid=owner.st_gid + int(gid_differs))
                    return info
                def chowned(fd, uid, gid):
                    self.assertEqual((owner.st_uid if uid_differs else -1, owner.st_gid if gid_differs else -1), (uid, gid))
                    events.append("chown")
                    return real_chown(fd, uid, gid)
                def chmodded(fd, mode):
                    events.append("chmod")
                    return real_chmod(fd, mode)
                def synced(fd):
                    info = real_stat(fd)
                    if inode and (info.st_dev, info.st_ino) == inode[0]: events.append("fsync")
                    return real_fsync(fd)
                with patch.object(gear.os, "open", side_effect=opened), patch.object(gear.os, "fstat", side_effect=statted), patch.object(gear.os, "fchown", side_effect=chowned), patch.object(gear.os, "fchmod", side_effect=chmodded), patch.object(gear.os, "fsync", side_effect=synced):
                    report = self.apply()
                self.assertEqual("IMPORTED", report["verdict"], report)
                self.assertEqual(["chown", "chmod", "fsync"], events)
                self.assertEqual((owner.st_uid, owner.st_gid), (report["published"]["uid"], report["published"]["gid"]))
                self.assertEqual(stat.S_IMODE(owner.st_mode), stat.S_IMODE(self.station.stat().st_mode))

    def test_silently_ignored_chown_cannot_publish_disagreeing_ownership(self):
        real_stat = os.fstat
        for field in ["st_uid", "st_gid"]:
            with self.subTest(field=field):
                self.backup = self.base / ("ignored-" + field)
                owner = self.station.stat()
                good = self.prepared()
                def statted(fd):
                    info = real_stat(fd)
                    if stat.S_ISREG(info.st_mode) and info.st_size == len(gear.LEGACY_EMPTY_CANONICAL):
                        return self.ownership_view(info, **{field: getattr(info, field) + 1})
                    return info
                with patch.object(gear.os, "fstat", side_effect=statted), patch.object(gear.os, "fchown", return_value=None) as chown:
                    self.refused(lambda: self.preview(**good), "staging changed")
                chown.assert_called_once()
                self.assertEqual((owner.st_uid if field == "st_uid" else -1, owner.st_gid if field == "st_gid" else -1), chown.call_args.args[1:])
                self.assertTrue((self.backup / "IMPORT-PLAN.json").exists())
                self.assertFalse((self.backup / "IMPORT-RESULT.json").exists())
                self.assertFalse(list(self.root.glob(gear.LEGACY_EMPTY_STAGING + "*")))

    def test_unavailable_chown_refuses_without_replacing_original(self):
        good = self.prepared()
        real_stat = os.fstat
        def statted(fd):
            info = real_stat(fd)
            if stat.S_ISREG(info.st_mode) and info.st_size == len(gear.LEGACY_EMPTY_CANONICAL) and stat.S_IMODE(info.st_mode) == 0o600:
                return self.ownership_view(info, st_uid=info.st_uid + 1)
            return info
        with patch.object(gear.os, "fstat", side_effect=statted), patch.object(gear.os, "fchown", side_effect=PermissionError("ownership is not permitted")) as chown:
            self.refused(lambda: self.preview(**good), "ownership is not permitted")
        chown.assert_called_once()
        self.assertTrue((self.backup / "IMPORT-PLAN.json").is_file())
        self.assertFalse((self.backup / "IMPORT-RESULT.json").exists())
        self.assertFalse(list(self.root.glob(gear.LEGACY_EMPTY_STAGING + "*")))

    def test_staged_owner_or_group_disagreement_refuses_before_rename(self):
        real_read = gear._legacy_empty_read
        for field in ["st_uid", "st_gid"]:
            with self.subTest(field=field):
                import shutil
                if self.backup.exists(): shutil.rmtree(self.backup)
                self.station.write_bytes(b"")
                good = self.prepared()
                def read(path, label):
                    info, data = real_read(path, label)
                    if label == "staging": info = self.ownership_view(info, **{field: getattr(info, field) + 1})
                    return info, data
                with patch.object(gear, "_legacy_empty_read", side_effect=read), patch.object(gear.os, "replace", wraps=os.replace) as replace:
                    self.refused(lambda: self.preview(**good), "staging changed")
                self.assertFalse(any(call.args[1] == self.station for call in replace.call_args_list))
                self.assertTrue((self.backup / "IMPORT-PLAN.json").is_file())
                self.assertFalse((self.backup / "IMPORT-RESULT.json").exists())
                self.assertFalse(list(self.root.glob(gear.LEGACY_EMPTY_STAGING + "*")))
                import shutil
                shutil.rmtree(self.backup)

    def test_published_owner_or_group_disagreement_is_unconfirmed(self):
        real_read, real_replace = gear._legacy_empty_read, os.replace
        for field in ["st_uid", "st_gid"]:
            with self.subTest(field=field):
                import shutil
                if self.backup.exists(): shutil.rmtree(self.backup)
                self.station.write_bytes(b"")
                good = self.prepared()
                replaced = []
                def replace(source, target):
                    result = real_replace(source, target)
                    if target == self.station: replaced.append(True)
                    return result
                def read(path, label):
                    info, data = real_read(path, label)
                    if replaced and label == "station": info = self.ownership_view(info, **{field: getattr(info, field) + 1})
                    return info, data
                with patch.object(gear, "_legacy_empty_read", side_effect=read), patch.object(gear.os, "replace", side_effect=replace):
                    report = self.preview(**good)
                self.assertEqual("PUBLISHED_UNCONFIRMED", report["verdict"], report)
                self.assertIn("post-publication verification failed", report["error"])
                self.assertIn("Keep Paper stopped", report["recovery"])
                self.assertEqual(gear.LEGACY_EMPTY_CANONICAL, self.station.read_bytes())
                self.assertTrue((self.backup / "IMPORT-PLAN.json").is_file())
                self.assertFalse((self.backup / "IMPORT-RESULT.json").exists())
                self.assertFalse(list(self.root.glob(gear.LEGACY_EMPTY_STAGING + "*")))
                import shutil
                shutil.rmtree(self.backup)
                self.station.write_bytes(b"")

    def test_nonempty_or_noncanonical_documents_refuse_without_loosening_yaml(self):
        for data in [b"\n", b" \n\t\n", b"~\n", b"stations:\n", b"{}\n", b"stations: {}", b"stations: {}\nstations: {}\n", b"\xff"]:
            with self.subTest(data=data):
                self.station.write_bytes(data)
                self.refused(self.preview, "not exactly zero bytes")
        for data in [b"", b"~\n", b"[]\n", b"stations: {}\nstations: {}\n"]:
            with self.assertRaises((ValueError, yaml.YAMLError)):
                gear.raw_yaml(data)

    def test_station_and_data_path_shapes_refuse_without_following_aliases(self):
        self.station.unlink()
        self.refused(self.preview, "absent")
        target = self.base / "empty-target"
        target.write_bytes(b"")
        for linked in [target, self.base / "missing-target"]:
            self.station.symlink_to(linked)
            self.refused(self.preview, "nonsymlink")
            self.station.unlink()
        for content in [b"", gear.LEGACY_EMPTY_CANONICAL]:
            target.write_bytes(content)
            os.link(target, self.station)
            self.refused(self.preview, "hard links")
            self.station.unlink()
        self.station.mkdir()
        self.refused(self.preview, "regular")
        self.station.rmdir()
        os.mkfifo(self.station)
        self.refused(self.preview, "regular")
        self.station.unlink()
        self.station.write_bytes(b"")
        alias = self.base / "alias"
        alias.symlink_to(self.root.parent.parent, target_is_directory=True)
        for path in [Path("relative/data"), alias / "Magic/data", self.station]:
            self.refused(lambda: gear.legacy_empty_import(path, self.jar), "directory")

    def test_history_containers_hold_empty_malformed_and_dangling_states(self):
        for name in gear.LEGACY_EMPTY_HISTORY:
            path = self.root / name
            for kind in ["dir", "file", "dangling", "link-dir"]:
                with self.subTest(name=name, kind=kind):
                    if kind == "dir": path.mkdir()
                    elif kind == "file": path.write_bytes(b"malformed history")
                    else: path.symlink_to(self.base / "missing" if kind == "dangling" else self.base, target_is_directory=True)
                    self.refused(self.preview, "history exists")
                    path.rmdir() if kind == "dir" else path.unlink()

    def test_station_siblings_and_all_staging_shapes_hold(self):
        for name in ["gear-stations.yml.rejected-anything", "gear-stations.yml.bak", "gear-stations-other", "magic-1.tmp", gear.LEGACY_EMPTY_STAGING + "old.tmp"]:
            for kind in ["file", "dir", "dangling"]:
                with self.subTest(name=name, kind=kind):
                    path = self.root / name
                    if kind == "file": path.write_bytes(b"unknown evidence")
                    elif kind == "dir": path.mkdir()
                    else: path.symlink_to(self.base / "missing")
                    self.refused(self.preview, "staff review")
                    path.rmdir() if kind == "dir" else path.unlink()

    def test_producer_is_required_allowlisted_and_not_a_link_or_special_file(self):
        other = self.base / "other.jar"
        other.write_bytes(b"nonallowlisted ownership build")
        self.refused(lambda: gear.legacy_empty_import(self.root, other), "reviewed")
        self.refused(lambda: gear.legacy_empty_import(self.root, None), "--legacy-jar")
        link = self.base / "linked.jar"
        link.symlink_to(self.jar)
        self.refused(lambda: gear.legacy_empty_import(self.root, link), "nonsymlink")
        code, report = self.cli("--legacy-empty-import")
        self.assertEqual(2, code)
        self.assertEqual("REFUSED", report["verdict"])

    def test_apply_needs_exact_baseline_and_a_nonempty_attestation_file(self):
        good = self.prepared()
        for changed in [{"attest": None}, {"backup": None}, {"expect_baseline": None}, {"expect_baseline": "x" * 64}, {"expect_baseline": "0" * 64}]:
            self.refused(lambda: self.preview(**(good | changed)))
        for data in [b"", b" \n", b"unrelated old checkpoint", b"f" + good["expect_baseline"].encode() + b"f"]:
            self.attest.write_bytes(data)
            self.refused(lambda: self.preview(**good), "exact baseline")
        self.attest.unlink()
        self.refused(lambda: self.preview(**good))
        self.assertFalse(self.backup.exists())

    def test_touch_and_same_bytes_old_writer_restarts_break_the_baseline(self):
        for mutation in [lambda: os.utime(self.station, ns=(1, 1)), lambda: self.station.write_bytes(b""), lambda: (self.station.unlink(), self.station.write_bytes(b""), self.station.chmod(0o640))]:
            os.utime(self.station, ns=(2, 2))
            good = self.prepared()
            mutation()
            self.assertNotEqual(2, self.station.stat().st_mtime_ns)
            self.assertEqual(0o640, stat.S_IMODE(self.station.stat().st_mode))
            self.refused(lambda: self.preview(**good), "attested baseline")
            self.assertFalse(self.backup.exists())

    def test_backup_must_be_new_outside_and_failure_retains_source_evidence(self):
        self.backup.mkdir(parents=True)
        self.refused(lambda: self.apply(), "new directory")
        self.backup.rmdir()
        self.backup.symlink_to(self.base / "missing-backup")
        self.refused(lambda: self.apply(), "new directory")
        self.backup.unlink()
        self.refused(lambda: self.apply(backup=self.root / "inside"), "outside Magic")
        real = gear.durable
        def partial(path, data):
            if path.name == "SHA256.json": raise OSError("backup manifest failure")
            return real(path, data)
        with patch.object(gear, "durable", side_effect=partial):
            self.refused(lambda: self.apply())
        self.assertEqual(b"", (self.backup / "data/gear-stations.yml").read_bytes())

    def test_new_history_or_source_changes_during_backup_block_publication(self):
        real = gear.backup_gear
        for mutation in [lambda: (self.root / "gear-returns").mkdir(), lambda: self.station.write_bytes(b"new source"), lambda: (self.root / "gear-stations.yml.bak").write_bytes(b"other evidence")]:
            good = self.prepared()
            changed = []
            def racing(root, backup):
                result = real(root, backup)
                mutation()
                changed.append(self.snapshot())
                return result
            with patch.object(gear, "backup_gear", side_effect=racing):
                report = self.preview(**good)
            self.assertEqual("REFUSED", report["verdict"])
            self.assertEqual(changed[0], self.snapshot())
            import shutil
            shutil.rmtree(self.backup)
            for path in [self.root / "gear-returns", self.root / "gear-stations.yml.bak"]:
                if path.exists(): path.rmdir() if path.is_dir() else path.unlink()
            self.station.write_bytes(b"")

    def test_last_recheck_keeps_a_new_source_and_cleans_only_own_staging(self):
        real = os.fsync
        good = self.prepared()
        changed = []
        def racing(fd):
            result = real(fd)
            info = os.fstat(fd)
            if stat.S_ISREG(info.st_mode) and info.st_size == len(gear.LEGACY_EMPTY_CANONICAL):
                self.station.unlink()
                self.station.write_bytes(b"newer unknown station bytes")
                changed.append(self.station.read_bytes())
            return result
        with patch.object(gear.os, "fsync", side_effect=racing):
            report = self.preview(**good)
        self.assertEqual("REFUSED", report["verdict"])
        self.assertEqual([b"newer unknown station bytes"], changed)
        self.assertEqual(changed[0], self.station.read_bytes())
        self.assertFalse(list(self.root.glob(gear.LEGACY_EMPTY_STAGING + "*")))

    def test_prepublication_io_cuts_refuse_without_changing_source_inode(self):
        real_force, real_durable, real_open, real_write, real_fsync = gear.force, gear.durable, os.open, os.write, os.fsync
        for cut in ["preflight", "attestation", "plan", "open", "write", "chmod", "fsync"]:
            with self.subTest(cut=cut):
                good = self.prepared()
                def force(path):
                    if cut == "preflight" and path == self.root: raise OSError("preflight failure")
                    return real_force(path)
                def durable(path, data):
                    if (cut == "attestation" and path.name == "LINEAGE-ATTESTATION") or (cut == "plan" and path.name == "IMPORT-PLAN.json"): raise OSError("plan evidence failure")
                    return real_durable(path, data)
                def opened(path, flags, *rest):
                    if cut == "open" and Path(path).name.startswith(gear.LEGACY_EMPTY_STAGING): raise OSError("exclusive staging open failed")
                    return real_open(path, flags, *rest)
                def written(fd, data):
                    if cut == "write":
                        real_write(fd, data[:2])
                        raise OSError("partial staging write")
                    return real_write(fd, data)
                def synced(fd):
                    if cut == "fsync" and stat.S_ISREG(os.fstat(fd).st_mode) and os.fstat(fd).st_size == 13: raise OSError("staging fsync failed")
                    return real_fsync(fd)
                with patch.object(gear, "force", side_effect=force), patch.object(gear, "durable", side_effect=durable), patch.object(gear.os, "open", side_effect=opened), patch.object(gear.os, "write", side_effect=written), patch.object(gear.os, "fsync", side_effect=synced), patch.object(gear.os, "fchmod", side_effect=OSError("mode failed") if cut == "chmod" else os.fchmod):
                    self.refused(lambda: self.preview(**good))
                self.assertFalse(list(self.root.glob(gear.LEGACY_EMPTY_STAGING + "*")))
                if self.backup.exists():
                    import shutil
                    shutil.rmtree(self.backup)
        # Failed cleanup keeps explicit evidence and the original source; preview cannot ignore it.
        good = self.prepared()
        original = self.snapshot()["gear-stations.yml"]
        real_unlink = os.unlink
        def failed_write(fd, data):
            real_write(fd, data)
            raise OSError("staging write acknowledgment failed")
        def failed_cleanup(path, *args, **kwargs):
            if Path(path).name.startswith(gear.LEGACY_EMPTY_STAGING): raise OSError("cleanup storage failed")
            return real_unlink(path, *args, **kwargs)
        with patch.object(gear.os, "write", side_effect=failed_write), patch.object(gear.os, "unlink", side_effect=failed_cleanup):
            report = self.preview(**good)
        self.assertEqual("REFUSED", report["verdict"])
        self.assertIn("staging-cleanup-error", report)
        self.assertEqual(original, self.snapshot()["gear-stations.yml"])
        leftover = next(self.root.glob(gear.LEGACY_EMPTY_STAGING + "*"))
        self.assertEqual(gear.LEGACY_EMPTY_CANONICAL, leftover.read_bytes())
        self.refused(self.preview, "staging evidence")

    def test_replace_and_postpublication_io_failures_are_unconfirmed(self):
        real_replace, real_force, real_durable, real_read = os.replace, gear.force, gear.durable, gear._legacy_empty_read
        for cut in ["replace", "dirsync", "read", "result", "new-history"]:
            with self.subTest(cut=cut):
                good = self.prepared()
                original_inode = self.station.stat().st_ino
                renamed = []
                def replace(source, target):
                    if target == self.station:
                        if cut == "replace": raise OSError("rename failed")
                        real_replace(source, target)
                        renamed.append(True)
                        if cut == "new-history": (self.root / "gear-returns").mkdir()
                        return
                    return real_replace(source, target)
                def force(path):
                    if renamed and path == self.root and cut == "dirsync": raise OSError("publication directory fsync failed")
                    return real_force(path)
                def durable(path, data):
                    if cut == "result" and path.name == "IMPORT-RESULT.json": raise OSError("result receipt failure")
                    return real_durable(path, data)
                def read(path, label):
                    if renamed and cut == "read" and path == self.station: raise OSError("post-publication read failed")
                    return real_read(path, label)
                with patch.object(gear.os, "replace", side_effect=replace), patch.object(gear, "force", side_effect=force), patch.object(gear, "durable", side_effect=durable), patch.object(gear, "_legacy_empty_read", side_effect=read):
                    report = self.preview(**good)
                self.assertEqual("PUBLISHED_UNCONFIRMED", report["verdict"], report)
                self.assertIn("Keep Paper stopped", report["recovery"])
                self.assertFalse(list(self.root.glob(gear.LEGACY_EMPTY_STAGING + "*")))
                self.assertTrue((self.backup / "IMPORT-PLAN.json").exists())
                if cut == "replace":
                    self.assertEqual(original_inode, self.station.stat().st_ino)
                    self.assertEqual(b"", self.station.read_bytes())
                else:
                    self.assertEqual(gear.LEGACY_EMPTY_CANONICAL, self.station.read_bytes())
                import shutil
                shutil.rmtree(self.backup)
                if (self.root / "gear-returns").exists(): (self.root / "gear-returns").rmdir()
                self.station.write_bytes(b"")

    def test_same_mode_chmod_and_truncated_first_deposit_break_prior_baselines(self):
        good = self.prepared()
        time.sleep(0.02)  # Advance the local inode timestamp clock before same-mode chmod.
        self.station.chmod(0o640)
        self.refused(lambda: self.preview(**good), "attested baseline")
        good = self.prepared()
        staged = self.base / "synthetic-first-deposit.yml"
        staged.write_bytes(b"stations:\n  synthetic: {effect-state: depositing, item-bytes: AQ==}\n")
        os.replace(staged, self.station)  # Actual 0.6.0 uses atomic replacement; no history is needed.
        with self.station.open("r+b") as stream: stream.truncate(0)
        report = self.preview()
        self.assertEqual("ELIGIBLE_REQUIRES_LINEAGE_ATTESTATION", report["verdict"])
        self.assertNotEqual(good["expect_baseline"], report["baseline"])
        self.refused(lambda: self.preview(**good), "attested baseline")

    def test_cli_keeps_import_scope_and_explicit_attestation_distinct(self):
        for conflicting in [("--kind", "migration"), ("--journal", str(uuid.uuid4()))]:
            before = self.snapshot()
            code, report = self.cli("--legacy-empty-import", "--legacy-jar", self.jar, *conflicting)
            self.assertEqual(2, code)
            self.assertEqual("REFUSED", report["verdict"])
            self.assertEqual(before, self.snapshot())
        code, report = self.cli("--legacy-empty-import", "--legacy-jar", self.jar)
        self.assertEqual(0, code)
        self.assertEqual("ELIGIBLE_REQUIRES_LINEAGE_ATTESTATION", report["verdict"])
        code, report = self.cli("--legacy-empty-import", "--legacy-jar", self.jar, "--apply")
        self.assertEqual(2, code)
        self.assertEqual("REFUSED", report["verdict"])
        code, _ = self.cli("--legacy-jar", self.jar)
        self.assertEqual(2, code)


if __name__ == "__main__":
    unittest.main()
