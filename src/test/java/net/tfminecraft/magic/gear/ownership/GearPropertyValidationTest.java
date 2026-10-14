package net.tfminecraft.magic.gear.ownership;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.google.gson.JsonArray;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import net.tfminecraft.tlibs.character.CharacterRef;
import net.tfminecraft.tlibs.character.estate.EstateDecision;
import net.tfminecraft.tlibs.character.estate.Recipient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class GearPropertyValidationTest {
    @TempDir Path root;
    final GearIdentity.Owner owner = new GearIdentity.Owner(UUID.randomUUID(), UUID.randomUUID());
    final GearIdentity.Owner recipient = new GearIdentity.Owner(UUID.randomUUID(), UUID.randomUUID());
    final UUID death = UUID.randomUUID();
    GearIdentity identity;
    GearPropertyStore store;

    @BeforeEach void setup() {
        identity = mock(GearIdentity.class);
        when(identity.heirsReady()).thenReturn(true);
        when(identity.heir(owner.account())).thenReturn(Optional.of(owner));
        store = new GearPropertyStore(root, identity);
    }

    Map<Object, Object> legacy(int x) {
        var row = new LinkedHashMap<Object, Object>();
        row.put("owner", owner.account().toString());
        row.put("world", "world"); row.put("x", x); row.put("y", 64); row.put("z", 20);
        row.put("item-bytes", "AQID");
        var unknown = new LinkedHashMap<Object, Object>();
        unknown.put("null", null); unknown.put("binary", new byte[]{4, 5});
        row.put("unknown", unknown);
        return row;
    }

    void stations(Map<Object, Object> rows) throws IOException {
        var document = new LinkedHashMap<Object, Object>();
        document.put("unknown-root", null); document.put("stations", rows);
        GearFiles.write(root.resolve("gear-stations.yml"), GearYaml.bytes(document), true);
    }

    Path pendingMigration(int count) throws IOException {
        var rows = new LinkedHashMap<Object, Object>();
        for (int i = 0; i < count; i++) rows.put(i, legacy(i));
        rows.put("opaque", "preserved"); stations(rows);
        when(identity.heirsReady()).thenReturn(true, false);
        assertThrows(IOException.class, store::migrate);
        when(identity.heirsReady()).thenReturn(true);
        try (var files = Files.list(root.resolve("gear-ownership/migration"))) {
            return files.findFirst().orElseThrow();
        }
    }

    JsonObject entry(JsonObject journal) { return GearPropertyStore.entries(journal).getFirst(); }

    Map<String, byte[]> snapshot() throws IOException {
        var result = new TreeMap<String, byte[]>();
        try (var files = Files.walk(store.root())) {
            for (Path file : files.filter(Files::isRegularFile).toList())
                result.put(store.root().relativize(file).toString(), Files.readAllBytes(file));
        }
        return result;
    }

    void unchanged(Map<String, byte[]> before) throws IOException {
        var after = snapshot(); assertEquals(before.keySet(), after.keySet());
        for (var file : before.entrySet()) assertArrayEquals(file.getValue(), after.get(file.getKey()), file.getKey());
    }

    void invalidMigration(Path journal, Consumer<JsonObject> tamper) throws IOException {
        byte[] original = Files.readAllBytes(journal);
        JsonObject changed = GearPropertyStore.read(journal); tamper.accept(changed);
        GearPropertyStore.write(journal, changed);
        var before = snapshot(); assertThrows(IOException.class, store::migrate); unchanged(before);
        GearFiles.write(journal, original, true);
    }

    static void payload(JsonObject entry, String field, byte[] bytes) {
        entry.addProperty(field, GearPropertyStore.b64(bytes));
        entry.addProperty(field + "-hash", GearPropertyStore.hash(bytes));
    }

    String decision(long revision) {
        var from = new CharacterRef(owner.account(), owner.character());
        var to = new CharacterRef(recipient.account(), recipient.character());
        return new EstateDecision(death, from, Optional.empty(), Instant.EPOCH, Optional.of(to), Optional.empty(),
                Optional.empty(), Recipient.FROZEN, Recipient.character(to), Recipient.WAITING, Recipient.SENDER, revision).toString();
    }

    String retained(String kind, long revision) {
        return decision(revision).replace("property=Character[ref=" + new CharacterRef(recipient.account(), recipient.character()) + "]", "property=" + kind + "[]");
    }

    Map<Object, Object> active(UUID id, int x) {
        var row = legacy(x); row.put("ownership", GearPropertyStore.ownership(owner));
        row.put("occupancy-id", id.toString()); row.put("effect-state", "active"); return row;
    }

    Path estate() { return root.resolve("gear-ownership/estates").resolve(death + ".json"); }

    void invalidEstate(Consumer<JsonObject> tamper) throws IOException {
        byte[] original = Files.readAllBytes(estate()); var raw = GearPropertyStore.read(estate());
        tamper.accept(raw); GearPropertyStore.write(estate(), raw); var before = snapshot();
        assertThrows(IOException.class, () -> store.validateEstate(death, retained("Waiting", 9), owner)); unchanged(before);
        GearFiles.write(estate(), original, true);
    }

    @Test void unsupportedMigrationEnvelopesAndMalformedEntryArraysLeaveEveryFileUntouched() throws Exception {
        Path journal = pendingMigration(1);
        for (String field : List.of("format", "kind", "run")) {
            invalidMigration(journal, raw -> raw.remove(field));
            invalidMigration(journal, raw -> raw.addProperty(field, "unsupported"));
        }
        invalidMigration(journal, raw -> raw.addProperty("format", 1.5));
        invalidMigration(journal, raw -> raw.addProperty("run", UUID.randomUUID().toString()));
        invalidMigration(journal, raw -> entry(raw).addProperty("type", "return"));
        invalidMigration(journal, raw -> raw.add("entries", JsonNull.INSTANCE));
        invalidMigration(journal, raw -> raw.addProperty("entries", "opaque"));
        invalidMigration(journal, raw -> { var rows = new JsonArray(); rows.add("opaque"); raw.add("entries", rows); });
        for (String field : List.of("type", "phase", "to-account", "to-character", "before", "after", "before-hash", "after-hash")) {
            invalidMigration(journal, raw -> entry(raw).remove(field));
            invalidMigration(journal, raw -> entry(raw).addProperty(field, "unsupported"));
        }
    }

    @Test void duplicatePropertyIdsAndSelectorsAreRejectedBeforeTheFirstPublication() throws Exception {
        Path journal = pendingMigration(2);
        invalidMigration(journal, raw -> raw.getAsJsonArray("entries").add(entry(raw).deepCopy()));
        invalidMigration(journal, raw -> {
            var second = GearPropertyStore.entries(raw).get(1);
            second.add("selector", entry(raw).get("selector").deepCopy());
        });
        byte[] original = Files.readAllBytes(journal);
        JsonObject raw = GearPropertyStore.read(journal);
        var first = entry(raw); var second = GearPropertyStore.entries(raw).get(1);
        var after = GearYaml.read(GearPropertyStore.bytes(GearPropertyStore.text(second, "after")));
        after.put("occupancy-id", GearPropertyStore.text(first, "id"));
        second.addProperty("id", GearPropertyStore.text(first, "id"));
        second.addProperty("property", "station:" + GearPropertyStore.location(after) + "@" + after.get("occupancy-id"));
        payload(second, "after", GearYaml.bytes(after)); GearPropertyStore.write(journal, raw);
        var before = snapshot(); assertThrows(IOException.class, store::migrate); unchanged(before);
        GearFiles.write(journal, original, true);
    }

    @Test void hashConsistentMigrationSourceAndRecipientTamperingCannotRedirectOrChangeItems() throws Exception {
        Path journal = pendingMigration(1); byte[] original = Files.readAllBytes(journal);
        for (String field : List.of("ownership", "occupancy-id", "effect-state")) {
            JsonObject raw = GearPropertyStore.read(journal); var entry = entry(raw);
            var before = GearYaml.read(GearPropertyStore.bytes(GearPropertyStore.text(entry, "before")));
            before.put(field, "preexisting"); byte[] bytes = GearYaml.bytes(before);
            payload(entry, "before", bytes);
            GearFiles.write(root.resolve("gear-ownership/backups").resolve(GearPropertyStore.hash(bytes) + ".bin"), bytes, false);
            GearPropertyStore.write(journal, raw);
            var files = snapshot(); assertThrows(IOException.class, store::migrate); unchanged(files);
            GearFiles.write(journal, original, true);
        }
        for (String field : List.of("item-bytes", "unknown", "effect-state", "occupancy-id")) {
            JsonObject raw = GearPropertyStore.read(journal); var entry = entry(raw);
            var after = GearYaml.read(GearPropertyStore.bytes(GearPropertyStore.text(entry, "after")));
            after.put(field, "tampered"); payload(entry, "after", GearYaml.bytes(after)); GearPropertyStore.write(journal, raw);
            var files = snapshot(); assertThrows(IOException.class, store::migrate); unchanged(files);
            GearFiles.write(journal, original, true);
        }
        invalidMigration(journal, raw -> entry(raw).addProperty("from-account", recipient.account().toString()));
        invalidMigration(journal, raw -> entry(raw).addProperty("to-account", recipient.account().toString()));
        JsonObject raw = GearPropertyStore.read(journal); var entry = entry(raw);
        var after = GearYaml.read(GearPropertyStore.bytes(GearPropertyStore.text(entry, "after")));
        after.put("ownership", GearPropertyStore.ownership(recipient));
        entry.addProperty("to-account", recipient.account().toString()); entry.addProperty("to-character", recipient.character().toString());
        payload(entry, "after", GearYaml.bytes(after)); GearPropertyStore.write(journal, raw);
        var files = snapshot(); assertThrows(IOException.class, store::migrate); unchanged(files);
    }

    @Test void selectorsPathsAndSymlinksCannotPublishOutsideTheCapturedStationFile() throws Exception {
        Path journal = pendingMigration(1);
        for (Map<?, ?> selector : List.of(Map.of(), Map.of("key", true), Map.of("key", 1L), Map.of("key", 0, "extra", "x")))
            invalidMigration(journal, raw -> entry(raw).addProperty("selector", GearPropertyStore.b64(GearYaml.bytes(selector))));
        for (String path : List.of("../escape.yml", "gear-stations.yml/../gear-stations.yml", "other.yml", root.resolve("gear-stations.yml").toString())) {
            invalidMigration(journal, raw -> entry(raw).addProperty("source", path));
            invalidMigration(journal, raw -> entry(raw).addProperty("target", path));
        }
        Path actual = root.resolve("actual-stations.yml");
        Files.move(root.resolve("gear-stations.yml"), actual);
        Files.createSymbolicLink(root.resolve("gear-stations.yml"), actual);
        byte[] before = Files.readAllBytes(actual);
        assertThrows(IOException.class, store::migrate); assertArrayEquals(before, Files.readAllBytes(actual));
        assertTrue(Files.isSymbolicLink(root.resolve("gear-stations.yml")));
    }

    @Test void missingOrChangedImmutableMigrationBackupsAndCurrentSourceHoldReplay() throws Exception {
        Path journal = pendingMigration(1); JsonObject raw = GearPropertyStore.read(journal);
        byte[] original = GearPropertyStore.bytes(GearPropertyStore.text(entry(raw), "before"));
        Path backup = root.resolve("gear-ownership/backups").resolve(GearPropertyStore.hash(original) + ".bin");
        Files.delete(backup); var before = snapshot(); assertThrows(IOException.class, store::migrate); unchanged(before);
        GearFiles.write(backup, new byte[]{9}, false); before = snapshot(); assertThrows(IOException.class, store::migrate); unchanged(before);
        GearFiles.write(backup, original, true);
        var changed = legacy(0); changed.put("unrelated-care", 99); stations(new LinkedHashMap<>(Map.of(0, changed)));
        before = snapshot(); assertThrows(IOException.class, store::migrate); unchanged(before);
        stations(new LinkedHashMap<>(Map.of(0, "row disappeared")));
        before = snapshot(); assertThrows(IOException.class, store::migrate); unchanged(before);
    }

    @Test void migrationRechecksTheWholeDocumentAfterProviderCallbacksAndPreservesTheirCare() throws Exception {
        Path journal = pendingMigration(1); var calls = new AtomicInteger();
        byte[][] callbackBytes = {null};
        when(identity.heirsReady()).thenAnswer(invocation -> {
            if (calls.incrementAndGet() == 2) {
                var changed = legacy(0); changed.put("care", 42);
                stations(new LinkedHashMap<>(Map.of(0, changed)));
                callbackBytes[0] = Files.readAllBytes(root.resolve("gear-stations.yml"));
            }
            return true;
        });
        assertThrows(IOException.class, store::migrate);
        assertArrayEquals(callbackBytes[0], Files.readAllBytes(root.resolve("gear-stations.yml")));
        assertEquals("planned", GearPropertyStore.text(entry(GearPropertyStore.read(journal)), "phase"));
        assertFalse(store.stations().getFirst().raw().containsKey("ownership"));
    }

    @Test void publishedButUnacknowledgedMigrationRecoversOnlyItsExactFixedFrameOnRestart() throws Exception {
        Path journal = pendingMigration(1); JsonObject raw = GearPropertyStore.read(journal); var entry = entry(raw);
        Map<Object, Object> after = GearYaml.read(GearPropertyStore.bytes(GearPropertyStore.text(entry, "after")));
        stations(new LinkedHashMap<>(Map.of(0, after, "opaque", "preserved")));
        entry.addProperty("phase", "published"); GearPropertyStore.write(journal, raw);
        byte[] source = Files.readAllBytes(root.resolve("gear-stations.yml"));
        var other = new GearIdentity.Owner(owner.account(), UUID.randomUUID());
        when(identity.heir(owner.account())).thenReturn(Optional.of(other));
        new GearPropertyStore(root, identity).migrate();
        assertArrayEquals(source, Files.readAllBytes(root.resolve("gear-stations.yml")));
        assertEquals(owner, GearPropertyStore.owner(store.stations().getFirst().raw()));
        assertEquals("moved", GearPropertyStore.text(entry(GearPropertyStore.read(journal)), "phase"));
        store.requireUnlocked(owner);
    }

    @Test void returnSchemaPathAndItemCorruptionAreReadOnlyAndDoNotPublishANewReturn() throws Exception {
        UUID id = UUID.randomUUID(); store.createReturn(owner, id, List.of("AQID"), "pending");
        Path file = store.returnFile(owner, id); byte[] original = Files.readAllBytes(file);
        for (String field : List.of("format", "id", "owner-account", "owner-character", "state", "items")) {
            JsonObject row = GearPropertyStore.read(file); row.remove(field); GearPropertyStore.write(file, row);
            var before = snapshot(); assertThrows(IOException.class, () -> store.returns(owner));
            assertThrows(IOException.class, () -> store.createReturn(owner, UUID.randomUUID(), List.of("AQID"), "pending"));
            unchanged(before); GearFiles.write(file, original, true);
        }
        for (String value : List.of("{\"format\":1.5}", "{\"format\":\"1\"}", "{\"items\":null}", "{\"items\":{}}", "{\"items\":[{}]}", "{\"items\":[null]}", "{\"items\":[\"!\"]}", "{\"state\":\"unknown\"}")) {
            JsonObject row = GearPropertyStore.read(file), delta = GearJson.parse(value);
            delta.entrySet().forEach(change -> row.add(change.getKey(), change.getValue())); GearPropertyStore.write(file, row);
            var before = snapshot(); assertThrows(IOException.class, () -> store.returns(owner)); unchanged(before);
            GearFiles.write(file, original, true);
        }
        Path wrong = file.resolveSibling(UUID.randomUUID() + ".json"); Files.move(file, wrong);
        var before = snapshot(); assertThrows(IOException.class, () -> store.returns(owner)); unchanged(before);
    }

    @Test void checkedReturnSaveCannotOverwriteExternalCareOrChangeTheFullOwner() throws Exception {
        UUID id = UUID.randomUUID(); store.createReturn(owner, id, List.of("AQID"), "pending");
        var expected = store.returns(owner).getFirst(); var external = expected.deepCopy();
        external.add("unknown", GearJson.parse("{\"kept\":null,\"care\":9}"));
        GearPropertyStore.write(store.returnFile(owner, id), external);
        var after = expected.deepCopy(); after.addProperty("state", "delivered");
        var before = snapshot(); assertThrows(IOException.class, () -> store.saveReturn(expected, after)); unchanged(before);
        var wrongOwner = external.deepCopy(); GearPropertyStore.setReturnOwner(wrongOwner, recipient);
        assertThrows(IOException.class, () -> store.saveReturn(external, wrongOwner)); unchanged(before);
        store.saveReturn(external, external.deepCopy());
        assertEquals(external, new GearPropertyStore(root, identity).returns(owner).getFirst());
        assertTrue(store.returns(recipient).isEmpty());
    }

    @Test void activeFixedMigrationBlocksOnlyItsRecipientUntilRecovery() throws Exception {
        Path journal = pendingMigration(1); var before = snapshot();
        assertThrows(IOException.class, () -> store.requireUnlocked(owner)); unchanged(before);
        store.requireUnlocked(recipient); unchanged(before);
        when(identity.heirsReady()).thenReturn(false);
        assertThrows(IOException.class, () -> store.requireReady(owner)); unchanged(before);
        when(identity.heirsReady()).thenReturn(true); store.migrate(); store.requireReady(owner);
        assertEquals("moved", GearPropertyStore.text(entry(GearPropertyStore.read(journal)), "phase"));
    }

    @Test void hashConsistentReturnPlansMustPreserveTheExactSourcePayloadAndDestination() throws Exception {
        UUID id = UUID.randomUUID(); store.createReturn(owner, id, List.of("AQID"), "pending");
        assertThrows(IOException.class, () -> store.transfer(death, decision(1), owner, recipient, () -> false));
        Path journal = root.resolve("gear-ownership/estates").resolve(death + ".json");
        byte[] original = Files.readAllBytes(journal);
        for (String field : List.of("source", "target", "property")) {
            JsonObject raw = GearPropertyStore.read(journal); entry(raw).addProperty(field, "wrong");
            GearPropertyStore.write(journal, raw); var before = snapshot();
            assertThrows(IOException.class, () -> store.transfer(death, decision(1), owner, recipient, () -> true));
            unchanged(before); GearFiles.write(journal, original, true);
        }
        JsonObject raw = GearPropertyStore.read(journal);
        entry(raw).addProperty("selector", "unsupported"); GearPropertyStore.write(journal, raw);
        var before = snapshot(); assertThrows(IOException.class, () -> store.requireUnlocked(owner)); unchanged(before);
        GearFiles.write(journal, original, true);
        for (String side : List.of("before", "after")) {
            for (String field : List.of("id", "owner-account", "owner-character", "items", "unknown", "state")) {
                raw = GearPropertyStore.read(journal); var plan = entry(raw);
                byte[] bytes = GearPropertyStore.bytes(GearPropertyStore.text(plan, side));
                JsonObject document = GearJson.parse(new String(bytes, java.nio.charset.StandardCharsets.UTF_8));
                if (field.equals("items")) { var changed = new JsonArray(); changed.add("BAUG"); document.add(field, changed); }
                else if (field.equals("state")) document.addProperty(field, "held");
                else if (field.equals("unknown")) document.add(field, GearJson.parse("{\"care\":9,\"kept\":null}"));
                else document.addProperty(field, UUID.randomUUID().toString());
                bytes = GearJson.bytes(document); payload(plan, side, bytes);
                if (side.equals("before")) {
                    Path backup = root.resolve("gear-ownership/backups").resolve(GearPropertyStore.hash(bytes) + ".bin");
                    if (!Files.exists(backup)) GearFiles.write(backup, bytes, false);
                }
                GearPropertyStore.write(journal, raw); before = snapshot();
                assertThrows(IOException.class, () -> store.transfer(death, decision(1), owner, recipient, () -> true), side + ":" + field);
                unchanged(before); GearFiles.write(journal, original, true);
            }
        }
        assertEquals(owner, GearPropertyStore.returnOwner(store.returns(owner).getFirst()));
        assertTrue(store.returns(recipient).isEmpty());
    }

    @Test void interruptedReturnCopiesAreAcceptedOnlyAsTheExactFixedPairThenRecoverWithoutDuplication() throws Exception {
        UUID id = UUID.randomUUID(); store.createReturn(owner, id, List.of("AQID"), "pending");
        var initial = store.returns(owner).getFirst(); var enriched = initial.deepCopy();
        enriched.add("future", GearJson.parse("{\"kept\":null,\"care\":7}")); store.saveReturn(initial, enriched);
        var calls = new AtomicInteger();
        assertThrows(IOException.class, () -> store.transfer(death, decision(1), owner, recipient, () -> calls.incrementAndGet() == 1));
        Path source = store.returnFile(owner, id), target = store.returnFile(recipient, id);
        byte[] sourceBytes = Files.readAllBytes(source), targetBytes = Files.readAllBytes(target);
        assertEquals(1, store.returns(owner).size()); assertEquals(1, store.returns(recipient).size());
        byte[][] callbackBytes = {null};
        assertThrows(IOException.class, () -> store.transfer(death, decision(1), owner, recipient, () -> {
            try {
                var edited = GearPropertyStore.read(source); edited.addProperty("new-care", 19); GearPropertyStore.write(source, edited);
                callbackBytes[0] = Files.readAllBytes(source);
            } catch (IOException ex) { throw new RuntimeException(ex); }
            return true;
        }));
        assertArrayEquals(callbackBytes[0], Files.readAllBytes(source)); assertArrayEquals(targetBytes, Files.readAllBytes(target));
        GearFiles.write(source, sourceBytes, true);
        for (Path file : List.of(source, target)) {
            JsonObject changed = GearPropertyStore.read(file); changed.addProperty("later-care", 11);
            GearPropertyStore.write(file, changed); var before = snapshot();
            assertThrows(IOException.class, () -> store.returns(owner));
            assertThrows(IOException.class, () -> store.transfer(death, decision(1), owner, recipient, () -> true));
            unchanged(before); GearFiles.write(file, file.equals(source) ? sourceBytes : targetBytes, true);
        }
        var third = new GearIdentity.Owner(UUID.randomUUID(), UUID.randomUUID());
        JsonObject extra = GearPropertyStore.read(source); GearPropertyStore.setReturnOwner(extra, third);
        Path extraFile = store.returnFile(third, id); GearFiles.write(extraFile, GearJson.bytes(extra), false);
        var before = snapshot(); assertThrows(IOException.class, () -> store.returns(owner)); unchanged(before);
        Files.delete(extraFile);
        var moved = new GearPropertyStore(root, identity).transfer(death, decision(1), owner, recipient, () -> true);
        assertEquals(List.of("return:" + id), moved); assertFalse(Files.exists(source));
        assertArrayEquals(targetBytes, Files.readAllBytes(target));
        assertEquals(1, store.returns(recipient).size()); assertTrue(store.returns(owner).isEmpty());
        before = snapshot(); assertEquals(moved, store.transfer(death, decision(1), owner, recipient, () -> true)); unchanged(before);
        GearFiles.write(source, sourceBytes, false); before = snapshot();
        assertThrows(IOException.class, () -> store.requireUnlocked(recipient)); unchanged(before);
        assertThrows(IOException.class, () -> store.returns(owner)); unchanged(before);
        var journal = GearPropertyStore.read(estate()); entry(journal).addProperty("phase", "published");
        GearPropertyStore.write(estate(), journal); before = snapshot();
        assertThrows(IOException.class, () -> store.returns(owner)); unchanged(before);
    }

    @Test void duplicateOccupancyAndCoordinateAmbiguityRetainTheCompleteRawInventory() throws Exception {
        UUID id = UUID.randomUUID(); var first = active(id, 1); var second = active(id, 2);
        stations(new LinkedHashMap<>(Map.of("one", first, "two", second)));
        var before = snapshot(); assertThrows(IOException.class, store::stations); unchanged(before);
        second.put("occupancy-id", UUID.randomUUID().toString()); second.put("x", 1);
        stations(new LinkedHashMap<>(Map.of("one", first, "two", second)));
        before = snapshot(); assertThrows(IOException.class, store::stations); unchanged(before);
        second.put("effect-state", "released"); stations(new LinkedHashMap<>(Map.of("one", first, "two", second)));
        before = snapshot(); assertEquals(2, store.stations().size()); unchanged(before);
        var stale = store.station(id); stations(new LinkedHashMap<>(Map.of("one", "quarantined", "two", second)));
        before = snapshot(); assertThrows(IOException.class, () -> store.patch(stale, first)); unchanged(before);
        for (String coordinate : List.of("world", "x", "y", "z")) {
            var malformed = legacy(3); malformed.put(coordinate, "unresolved");
            if (coordinate.equals("world")) malformed.put(coordinate, 3);
            stations(new LinkedHashMap<>(Map.of("bad", malformed))); store.migrate();
            var held = store.stations().getFirst().raw(); assertEquals("HELD", ((Map<?, ?>) held.get("ownership")).get("kind"));
            held.remove("ownership"); held.remove("effect-state"); held.remove("occupancy-id"); assertTrue(GearYaml.equal(malformed, held));
        }
        var unsupported = legacy(4); stations(new LinkedHashMap<>(Map.of(true, unsupported))); store.migrate();
        assertEquals("HELD", ((Map<?, ?>) store.stations().getFirst().raw().get("ownership")).get("kind"));
    }

    @Test void readinessSeparatesUnresolvedLegacyHeldAuthorityAndAnotherCharactersEffects() throws Exception {
        when(identity.heir(owner.account())).thenReturn(Optional.empty());
        var unresolved = legacy(0); stations(new LinkedHashMap<>(Map.of(0, unresolved)));
        var before = snapshot(); assertThrows(IOException.class, () -> store.requireReady(owner)); unchanged(before);
        store.requireReady(recipient); unchanged(before);
        for (Object authority : List.of("unresolved", Map.of("format", 1, "kind", "HELD"))) {
            var held = legacy(0); held.put("ownership", authority); stations(new LinkedHashMap<>(Map.of(0, held)));
            before = snapshot(); assertThrows(IOException.class, () -> store.requireReady(owner)); unchanged(before);
            store.requireReady(recipient); unchanged(before);
        }
        var held = active(UUID.randomUUID(), 0); held.put("effect-state", "held");
        stations(new LinkedHashMap<>(Map.of(0, held))); before = snapshot();
        store.requireReady(recipient); unchanged(before);
        assertThrows(IOException.class, () -> store.requireReady(owner)); unchanged(before);
        Files.delete(root.resolve("gear-stations.yml"));
        UUID id = UUID.randomUUID(); store.createReturn(owner, id, List.of("AQID"), "pending");
        store.requireReady(owner); var expected = store.returns(owner).getFirst(); var blocked = expected.deepCopy();
        blocked.addProperty("state", "held"); store.saveReturn(expected, blocked); before = snapshot();
        assertThrows(IOException.class, () -> store.requireReady(owner)); unchanged(before);
        assertThrows(IOException.class, () -> store.createReturn(owner, id, List.of("AQID"), "pending")); unchanged(before);
    }

    @Test void estatePlanningIgnoresUnrelatedAndReleasedPropertyButHoldsUnfinishedEffects() throws Exception {
        var other = active(UUID.randomUUID(), 1); other.put("ownership", GearPropertyStore.ownership(recipient));
        var released = active(UUID.randomUUID(), 2); released.put("effect-state", "released");
        var ownerless = legacy(3); ownerless.put("ownership", Map.of("format", 1, "kind", "OWNERLESS"));
        stations(new LinkedHashMap<>(Map.of(0, legacy(0), 1, other, 2, released, 3, ownerless)));
        UUID returned = UUID.randomUUID(); store.createReturn(owner, returned, List.of("AQID"), "delivered");
        byte[] source = Files.readAllBytes(root.resolve("gear-stations.yml"));
        assertTrue(store.transfer(death, decision(1), owner, recipient, () -> true).isEmpty());
        assertArrayEquals(source, Files.readAllBytes(root.resolve("gear-stations.yml")));
        assertEquals("delivered", GearPropertyStore.text(store.returns(owner).getFirst(), "state"));
        UUID blocked = UUID.randomUUID(); var unfinished = active(blocked, 4); unfinished.put("effect-state", "held");
        var document = GearYaml.read(source); ((Map<Object, Object>) document.get("stations")).put(4, unfinished);
        GearFiles.write(root.resolve("gear-stations.yml"), GearYaml.bytes(document), true); var before = snapshot();
        assertThrows(IOException.class, () -> store.transfer(death, decision(2), owner, recipient, () -> true)); unchanged(before);
        ((Map<Object, Object>) document.get("stations")).remove(4); GearFiles.write(root.resolve("gear-stations.yml"), GearYaml.bytes(document), true);
        store.createReturn(owner, UUID.randomUUID(), List.of("AQID"), "held"); before = snapshot();
        assertThrows(IOException.class, () -> store.transfer(death, decision(2), owner, recipient, () -> true)); unchanged(before);
    }

    @Test void retainedRequestsAndLongRevisionsMustMatchTheCapturedFullDecisionWithoutEffects() throws Exception {
        var before = snapshot();
        for (String malformed : List.of("wrong", decision(1).replace(", property=", ", other="),
                decision(1).replace(", guildLocks=", ", other="), retained("Sender", 1),
                retained("Waiting", 1).replace("revision=1]", "revision=1.5]"),
                retained("Waiting", 1).replace("revision=1]", "revision=9223372036854775808]"))) {
            assertThrows(IOException.class, () -> store.validateEstate(death, malformed, owner)); unchanged(before);
        }
        assertThrows(IOException.class, () -> GearPropertyStore.decision(decision(1).replace("revision=1]", "revision=9223372036854775808]"), death, owner, recipient)); unchanged(before);
        assertThrows(IOException.class, () -> GearPropertyStore.decision(decision(1).replace("property=Character", "property=Other"), death, owner, recipient)); unchanged(before);
        assertThrows(IOException.class, () -> GearPropertyStore.decision(decision(1).replace("revision=1]", "revision=1.5]"), death, owner, recipient)); unchanged(before);
        assertThrows(IOException.class, () -> store.retained(death, decision(1), owner, () -> true)); unchanged(before);
        store.retained(death, retained("Waiting", 2), owner, () -> true); before = snapshot();
        assertThrows(IOException.class, () -> store.retained(death, retained("Waiting", 1), owner, () -> true)); unchanged(before);
        assertThrows(IOException.class, () -> store.retained(death, retained("Frozen", 2), owner, () -> true)); unchanged(before);
    }

    @Test void completedRevisionSchemaAndIndependentEvidenceRejectValidPrefixCorruption() throws Exception {
        store.retained(death, retained("Waiting", 1), owner, () -> true);
        invalidEstate(raw -> raw.remove("completions"));
        invalidEstate(raw -> raw.add("completions", JsonNull.INSTANCE));
        invalidEstate(raw -> { var row = raw.getAsJsonObject("completions").remove("1"); raw.getAsJsonObject("completions").add("2", row); });
        for (String field : List.of("death", "from-account", "from-character"))
            invalidEstate(raw -> raw.getAsJsonObject("completions").getAsJsonObject("1").addProperty(field, UUID.randomUUID().toString()));
        invalidEstate(raw -> raw.getAsJsonObject("completions").getAsJsonObject("1").addProperty("revision", 1.5));
        for (String field : List.of("to-account", "to-character"))
            invalidEstate(raw -> raw.getAsJsonObject("completions").getAsJsonObject("1").addProperty(field, recipient.character().toString()));
        invalidEstate(raw -> raw.getAsJsonObject("completions").getAsJsonObject("1").getAsJsonObject("counts").addProperty("gear", 1));
        for (String array : List.of("[1]", "[null]", "[{}]", "[\"station:world,1,2,3\"]",
                "[\"return:00000000-0000-0000-0000-000000000001\",\"return:00000000-0000-0000-0000-000000000001\"]",
                "[\"return:00000000-0000-0000-0000-000000000002\",\"return:00000000-0000-0000-0000-000000000001\"]")) {
            invalidEstate(raw -> raw.getAsJsonObject("completions").getAsJsonObject("1").add("retained", com.google.gson.JsonParser.parseString(array)));
        }
        Path independent = root.resolve("gear-ownership/completions").resolve(death.toString()).resolve("1.json");
        byte[] original = Files.readAllBytes(independent); Files.delete(independent); var before = snapshot();
        assertThrows(IOException.class, () -> store.validateEstate(death, retained("Waiting", 2), owner)); unchanged(before);
        GearFiles.write(independent, new byte[]{1}, false); before = snapshot();
        assertThrows(IOException.class, () -> store.validateEstate(death, retained("Waiting", 2), owner)); unchanged(before);
        GearFiles.write(independent, original, true);
    }

    @Test void independentReceiptsBindEveryEnvelopeFieldAndFullPayloadBeforeOrdinaryWriters() throws Exception {
        UUID id = UUID.randomUUID(); store.insert(active(id, 0)); store.transfer(death, decision(1), owner, recipient, () -> true);
        Path receipt;
        try (var files = Files.list(root.resolve("gear-ownership/receipts").resolve(death.toString()))) { receipt = files.findFirst().orElseThrow(); }
        byte[] original = Files.readAllBytes(receipt);
        for (String field : List.of("format", "kind", "phase", "property")) {
            var row = GearPropertyStore.read(receipt); row.addProperty(field, "changed"); GearPropertyStore.write(receipt, row);
            var before = snapshot(); assertThrows(IOException.class, () -> store.requireUnlocked(recipient)); unchanged(before);
            GearFiles.write(receipt, original, true);
        }
        var row = GearPropertyStore.read(receipt); row.add("unknown", GearJson.parse("{\"kept\":null}")); GearPropertyStore.write(receipt, row);
        var before = snapshot(); assertThrows(IOException.class, () -> store.requireUnlocked(recipient)); unchanged(before); GearFiles.write(receipt, original, true);
        invalidEstate(raw -> {
            var completion = raw.getAsJsonObject("completions").getAsJsonObject("1");
            completion.add("retained", completion.get("moved").deepCopy());
        });
        invalidEstate(raw -> raw.getAsJsonObject("completions").getAsJsonObject("1").addProperty("to-account", owner.account().toString()));
        invalidEstate(raw -> raw.getAsJsonObject("completions").getAsJsonObject("1").addProperty("to-character", owner.character().toString()));
        Path wrongName = receipt.resolveSibling("wrong.json"); Files.move(receipt, wrongName); before = snapshot();
        assertThrows(IOException.class, () -> store.requireUnlocked(recipient)); unchanged(before); Files.move(wrongName, receipt);
    }

    @Test void pendingEstateEnvelopeAndStationSourceAuthorityCannotBeRewrittenWithConsistentHashes() throws Exception {
        UUID id = UUID.randomUUID(); store.insert(active(id, 0));
        assertThrows(IOException.class, () -> store.transfer(death, decision(1), owner, recipient, () -> false));
        for (String field : List.of("death", "from-account", "from-character")) {
            invalidEstate(raw -> raw.addProperty(field, UUID.randomUUID().toString()));
            invalidEstate(raw -> entry(raw).addProperty(field, UUID.randomUUID().toString()));
        }
        invalidEstate(raw -> {
            var entry = entry(raw); entry.addProperty("to-account", owner.account().toString()); entry.addProperty("to-character", owner.character().toString());
        });
        byte[] original = Files.readAllBytes(estate());
        for (String side : List.of("before", "after")) for (String field : List.of("ownership", "occupancy-id", "effect-state")) {
            var raw = GearPropertyStore.read(estate()); var plan = entry(raw);
            var document = GearYaml.read(GearPropertyStore.bytes(GearPropertyStore.text(plan, side)));
            if (field.equals("ownership")) document.put(field, GearPropertyStore.ownership(new GearIdentity.Owner(owner.account(), UUID.randomUUID())));
            else document.put(field, field.equals("occupancy-id") ? UUID.randomUUID().toString() : "held");
            byte[] bytes = GearYaml.bytes(document); payload(plan, side, bytes);
            if (side.equals("before")) GearFiles.write(root.resolve("gear-ownership/backups").resolve(GearPropertyStore.hash(bytes) + ".bin"), bytes, false);
            GearPropertyStore.write(estate(), raw); var before = snapshot();
            assertThrows(IOException.class, () -> store.validateEstate(death, retained("Waiting", 2), owner)); unchanged(before);
            GearFiles.write(estate(), original, true);
        }
        for (String field : List.of("type", "phase"))
            invalidEstate(raw -> entry(raw).add(field, field.equals("phase") ? JsonNull.INSTANCE : new com.google.gson.JsonPrimitive(1)));
        Path wrongName = estate().resolveSibling(UUID.randomUUID() + ".json"); Files.move(estate(), wrongName);
        var before = snapshot(); assertThrows(IOException.class, () -> store.requireUnlocked(owner)); unchanged(before); Files.move(wrongName, estate());
        store.requireUnlocked(new GearIdentity.Owner(UUID.randomUUID(), UUID.randomUUID()));
        assertEquals(List.of("station:world,0,64,20@" + id), store.transfer(death, decision(1), owner, recipient, () -> true));
        assertEquals(recipient, GearPropertyStore.owner(store.station(id).raw()));
    }

    @Test void exactPendingStationReplayAndHistoricalWaitingCompletionKeepReceiptsSeparate() throws Exception {
        store.retained(death, retained("Waiting", 0), owner, () -> true);
        UUID id = UUID.randomUUID(); store.insert(active(id, 0)); var calls = new AtomicInteger();
        assertThrows(IOException.class, () -> store.transfer(death, decision(1), owner, recipient, () -> calls.incrementAndGet() == 1));
        assertEquals(recipient, GearPropertyStore.owner(store.station(id).raw()));
        assertEquals("published", GearPropertyStore.text(entry(GearPropertyStore.read(estate())), "phase"));
        var before = snapshot();
        assertTrue(store.retained(death, retained("Waiting", 0), owner, () -> true).isEmpty()); unchanged(before);
        assertThrows(IOException.class, () -> store.validateEstate(death, retained("Waiting", 1), owner)); unchanged(before);
        byte[] source = Files.readAllBytes(root.resolve("gear-stations.yml"));
        assertEquals(List.of("station:world,0,64,20@" + id), store.transfer(death, decision(1), owner, recipient, () -> true));
        assertArrayEquals(source, Files.readAllBytes(root.resolve("gear-stations.yml")));
        before = snapshot(); store.validateEstate(death, retained("Waiting", 2), owner); unchanged(before);
    }

    @Test void untouchedStationAndReturnPlansCanBeRetainedButPublishedCopiesCannotBeSuperseded() throws Exception {
        UUID station = UUID.randomUUID(), returned = UUID.randomUUID();
        store.insert(active(station, 0)); store.createReturn(owner, returned, List.of("AQID"), "pending");
        assertThrows(IOException.class, () -> store.transfer(death, decision(2), owner, recipient, () -> false));
        var before = snapshot(); assertThrows(IOException.class, () -> store.retained(death, retained("Waiting", 1), owner, () -> true)); unchanged(before);
        assertTrue(store.retained(death, retained("Waiting", 3), owner, () -> true).isEmpty());
        assertEquals(owner, GearPropertyStore.owner(store.station(station).raw())); assertEquals(1, store.returns(owner).size());
        assertTrue(GearPropertyStore.entries(GearPropertyStore.read(estate())).isEmpty());
        assertThrows(IOException.class, () -> store.transfer(death, decision(4), owner, recipient, () -> false));
        var raw = GearPropertyStore.read(estate()); byte[] original = Files.readAllBytes(estate());
        var source = GearYaml.read(GearPropertyStore.bytes(GearPropertyStore.text(entry(raw), "before")));
        source.put("new-care", 5); var current = store.station(station); store.patch(current, source); before = snapshot();
        assertThrows(IOException.class, () -> store.retained(death, retained("Waiting", 5), owner, () -> true)); unchanged(before);
        store.patch(store.station(station), current.raw());
        var second = GearPropertyStore.entries(raw).get(1);
        GearFiles.write(store.returnFile(recipient, returned), GearPropertyStore.bytes(GearPropertyStore.text(second, "after")), false);
        before = snapshot(); assertThrows(IOException.class, () -> store.retained(death, retained("Waiting", 5), owner, () -> true)); unchanged(before);
        assertArrayEquals(original, Files.readAllBytes(estate()));
    }

    @Test void completionAndStationGuardCallbacksCannotOverwriteNewSourceHoldingsOrUnloadedRows() throws Exception {
        var before = snapshot(); assertThrows(IOException.class, () -> store.retained(death, retained("Waiting", 1), owner, () -> false)); unchanged(before);
        UUID id = UUID.randomUUID(); byte[][] callback = {null};
        assertThrows(IOException.class, () -> store.retained(death, retained("Waiting", 1), owner, () -> {
            try { store.insert(active(id, 0)); callback[0] = Files.readAllBytes(root.resolve("gear-stations.yml")); }
            catch (IOException ex) { throw new RuntimeException(ex); } return true;
        }));
        assertArrayEquals(callback[0], Files.readAllBytes(root.resolve("gear-stations.yml"))); assertFalse(Files.exists(estate()));
        assertEquals(owner, GearPropertyStore.owner(store.station(id).raw()));
        assertThrows(IOException.class, () -> store.transfer(death, decision(1), owner, recipient, () -> {
            try { stations(new LinkedHashMap<>(Map.of(id.toString(), "held opaque row"))); callback[0] = Files.readAllBytes(root.resolve("gear-stations.yml")); }
            catch (IOException ex) { throw new RuntimeException(ex); } return true;
        }));
        assertArrayEquals(callback[0], Files.readAllBytes(root.resolve("gear-stations.yml")));
        assertEquals("planned", GearPropertyStore.text(entry(GearPropertyStore.read(estate())), "phase"));
        assertFalse(Files.exists(root.resolve("gear-ownership/receipts").resolve(death.toString())));
    }

    @Test void aNewBackupCannotReplaceConflictingImmutableEvidence() throws Exception {
        UUID id = UUID.randomUUID(); var source = active(id, 0); store.insert(source);
        byte[] bytes = GearYaml.bytes(source); Path backup = root.resolve("gear-ownership/backups").resolve(GearPropertyStore.hash(bytes) + ".bin");
        GearFiles.write(backup, new byte[]{9}, false); var before = snapshot();
        assertThrows(IOException.class, () -> store.transfer(death, decision(1), owner, recipient, () -> true)); unchanged(before);
        GearFiles.write(backup, bytes, true); store.transfer(death, decision(1), owner, recipient, () -> true);
        assertEquals(recipient, GearPropertyStore.owner(store.station(id).raw()));
    }

    @Test void duplicateReturnCopiesCannotBorrowAnotherPlanOrReplaceEitherFixedPath() throws Exception {
        UUID station = UUID.randomUUID(), returned = UUID.randomUUID();
        store.insert(active(station, 0)); store.createReturn(owner, returned, List.of("AQID"), "pending");
        var calls = new AtomicInteger(); assertThrows(IOException.class,
                () -> store.transfer(death, decision(1), owner, recipient, () -> calls.incrementAndGet() <= 3));
        Path source = store.returnFile(owner, returned), target = store.returnFile(recipient, returned);
        byte[] originalSource = Files.readAllBytes(source), originalTarget = Files.readAllBytes(target);
        var third = new GearIdentity.Owner(UUID.randomUUID(), UUID.randomUUID());
        var other = GearPropertyStore.read(source); GearPropertyStore.setReturnOwner(other, third);
        Path outsidePair = store.returnFile(third, returned);
        for (Path absent : List.of(source, target)) {
            GearFiles.write(outsidePair, GearJson.bytes(other), false); Files.delete(absent); var before = snapshot();
            assertThrows(IOException.class, () -> store.returns(owner)); unchanged(before);
            GearFiles.write(absent, absent.equals(source) ? originalSource : originalTarget, false); Files.delete(outsidePair);
        }
        UUID unplanned = UUID.randomUUID(); store.createReturn(owner, unplanned, List.of("AQID"), "pending");
        var unrelated = store.returns(owner).stream().filter(row -> unplanned.toString().equals(row.get("id").getAsString())).findFirst().orElseThrow().deepCopy();
        GearPropertyStore.setReturnOwner(unrelated, third); GearFiles.write(store.returnFile(third, unplanned), GearJson.bytes(unrelated), false);
        var before = snapshot(); assertThrows(IOException.class, () -> store.returns(owner)); unchanged(before);
    }

    @Test void returnReplayRequiresAtLeastOneExactCopyAndNeverRecreatesAMissingPlannedSource() throws Exception {
        UUID id = UUID.randomUUID(); store.createReturn(owner, id, List.of("AQID"), "pending");
        assertThrows(IOException.class, () -> store.transfer(death, decision(1), owner, recipient, () -> false));
        Path source = store.returnFile(owner, id), target = store.returnFile(recipient, id);
        byte[] originalSource = Files.readAllBytes(source), originalJournal = Files.readAllBytes(estate());
        var changed = GearPropertyStore.read(source); changed.add("care", GearJson.parse("{\"kept\":null,\"value\":9}"));
        GearPropertyStore.write(source, changed); var before = snapshot();
        assertThrows(IOException.class, () -> store.transfer(death, decision(1), owner, recipient, () -> true)); unchanged(before);
        GearFiles.write(source, originalSource, true); Files.delete(source); before = snapshot();
        assertThrows(IOException.class, () -> store.transfer(death, decision(1), owner, recipient, () -> true)); unchanged(before);
        assertThrows(IOException.class, () -> store.retained(death, retained("Waiting", 2), owner, () -> true)); unchanged(before);
        var raw = GearPropertyStore.read(estate()); entry(raw).addProperty("phase", "published"); GearPropertyStore.write(estate(), raw);
        before = snapshot(); assertThrows(IOException.class, () -> store.transfer(death, decision(1), owner, recipient, () -> true)); unchanged(before);
        byte[] expected = GearPropertyStore.bytes(GearPropertyStore.text(entry(raw), "after"));
        var conflict = GearJson.parse(new String(expected, java.nio.charset.StandardCharsets.UTF_8)); conflict.addProperty("care", 11);
        GearFiles.write(target, GearJson.bytes(conflict), false); before = snapshot();
        assertThrows(IOException.class, () -> store.transfer(death, decision(1), owner, recipient, () -> true)); unchanged(before);
        GearFiles.write(target, expected, true); before = snapshot();
        assertThrows(IOException.class, () -> store.transfer(death, decision(1), owner, recipient, () -> false)); unchanged(before);
        assertEquals(List.of("return:" + id), new GearPropertyStore(root, identity).transfer(death, decision(1), owner, recipient, () -> true));
        assertFalse(Files.exists(source)); assertArrayEquals(expected, Files.readAllBytes(target));
        assertFalse(java.util.Arrays.equals(originalJournal, Files.readAllBytes(estate())));
    }

    void raceLaterStation(boolean remove) throws Exception {
        UUID first = UUID.randomUUID(), second = UUID.randomUUID();
        store.insert(active(first, 0)); var original = active(second, 1); store.insert(original);
        var edited = GearYaml.copy(original); edited.put("care", 42); var calls = new AtomicInteger();
        assertThrows(IOException.class, () -> store.transfer(death, decision(1), owner, recipient, () -> {
            if (calls.incrementAndGet() == 1) try {
                var document = GearYaml.read(Files.readAllBytes(root.resolve("gear-stations.yml")));
                ((Map<Object, Object>) document.get("stations")).put(second.toString(), remove ? "held opaque row" : edited);
                GearFiles.write(root.resolve("gear-stations.yml"), GearYaml.bytes(document), true);
            } catch (IOException ex) { throw new RuntimeException(ex); }
            return true;
        }));
        assertEquals(recipient, GearPropertyStore.owner(store.station(first).raw()));
        var document = GearYaml.read(Files.readAllBytes(root.resolve("gear-stations.yml")));
        Object retained = ((Map<?, ?>) document.get("stations")).get(second.toString());
        if (remove) assertEquals("held opaque row", retained); else assertTrue(GearYaml.equal(edited, (Map<?, ?>) retained));
        var entries = GearPropertyStore.entries(GearPropertyStore.read(estate()));
        assertEquals("moved", GearPropertyStore.text(entries.getFirst(), "phase"));
        assertEquals("planned", GearPropertyStore.text(entries.getLast(), "phase"));
        var before = snapshot(); assertThrows(IOException.class, () -> store.transfer(death, decision(2), owner, recipient, () -> true)); unchanged(before);
        assertEquals(2, calls.get());
    }

    @Test void aLaterStationRemovedAfterWholePlanPreflightRemainsHeldWithoutLosingEarlierReceipt() throws Exception {
        raceLaterStation(true);
    }

    @Test void aLaterStationsCareChangedAfterWholePlanPreflightCannotBeOverwritten() throws Exception {
        raceLaterStation(false);
    }

    void raceLaterReturn(String effect) throws Exception {
        UUID station = UUID.randomUUID(), returned = UUID.randomUUID();
        store.insert(active(station, 0)); store.createReturn(owner, returned, List.of("AQID"), "pending");
        Path source = store.returnFile(owner, returned), target = store.returnFile(recipient, returned);
        byte[] original = Files.readAllBytes(source); byte[][] editedBytes = {null}; var calls = new AtomicInteger();
        assertThrows(IOException.class, () -> store.transfer(death, decision(1), owner, recipient, () -> {
            if (calls.incrementAndGet() == 1) try {
                if (effect.equals("remove-source")) Files.delete(source);
                else {
                    var edited = GearPropertyStore.read(source); edited.add("care", GearJson.parse("{\"kept\":null,\"value\":9}"));
                    if (effect.equals("target")) GearPropertyStore.setReturnOwner(edited, recipient);
                    Path changed = effect.equals("target") ? target : source;
                    GearPropertyStore.write(changed, edited); editedBytes[0] = Files.readAllBytes(changed);
                }
            } catch (IOException ex) { throw new RuntimeException(ex); }
            return true;
        }));
        assertEquals(recipient, GearPropertyStore.owner(store.station(station).raw()));
        if (effect.equals("remove-source")) { assertFalse(Files.exists(source)); assertFalse(Files.exists(target)); }
        else if (effect.equals("target")) { assertArrayEquals(original, Files.readAllBytes(source)); assertArrayEquals(editedBytes[0], Files.readAllBytes(target)); }
        else { assertArrayEquals(editedBytes[0], Files.readAllBytes(source)); assertFalse(Files.exists(target)); }
        var entries = GearPropertyStore.entries(GearPropertyStore.read(estate()));
        assertEquals("moved", GearPropertyStore.text(entries.getFirst(), "phase"));
        assertEquals("planned", GearPropertyStore.text(entries.getLast(), "phase"));
        var before = snapshot(); assertThrows(IOException.class, () -> store.transfer(death, decision(2), owner, recipient, () -> true)); unchanged(before);
        assertEquals(2, calls.get());
        if (effect.equals("remove-source")) {
            GearFiles.write(source, original, false);
            assertEquals(2, store.transfer(death, decision(1), owner, recipient, () -> true).size());
            assertFalse(Files.exists(source)); assertEquals(1, store.returns(recipient).size());
        }
    }

    @Test void aLaterReturnChangedAfterWholePlanPreflightKeepsItsExactCareBytes() throws Exception {
        raceLaterReturn("source");
    }

    @Test void aLaterReturnRemovedAfterWholePlanPreflightRequiresReviewedRepairBeforeRetry() throws Exception {
        raceLaterReturn("remove-source");
    }

    @Test void aConflictingReturnTargetIntroducedAfterWholePlanPreflightIsNeverOverwritten() throws Exception {
        raceLaterReturn("target");
    }
}
