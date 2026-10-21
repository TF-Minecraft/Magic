package net.tfminecraft.magic.gear.ownership;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class GearStationArchiveTest {
    @TempDir Path root;
    GearPropertyStore store;
    final GearIdentity.Owner owner = new GearIdentity.Owner(UUID.randomUUID(), UUID.randomUUID());

    @BeforeEach void setup() { store = new GearPropertyStore(root, mock(GearIdentity.class)); }

    Map<Object, Object> row(UUID id, int x, String state) {
        var row = new LinkedHashMap<Object, Object>(); row.put("world", "world"); row.put("x", x); row.put("y", 64); row.put("z", 20);
        row.put("owner", owner.account().toString()); row.put("occupancy-id", id.toString()); row.put("ownership", GearPropertyStore.ownership(owner));
        row.put("effect-state", state); row.put("item-bytes", "AQID"); row.put("release-intent", Map.of("operation", UUID.randomUUID().toString(), "item", "AQID"));
        return row;
    }
    void stations(Map<Object, Object> rows) throws Exception {
        var document = new LinkedHashMap<Object, Object>(); document.put("root-future", "kept"); document.put("stations", rows);
        GearFiles.write(file(), GearYaml.bytes(document), true);
    }
    Path file() { return root.resolve("gear-stations.yml"); }
    Path archive(UUID id) { return root.resolve("gear-stations-archive").resolve(id + ".yml"); }
    Map<?, ?> live() throws Exception { return (Map<?, ?>) GearYaml.read(Files.readAllBytes(file())).get("stations"); }
    void evidence(String relative, String json) throws Exception { GearFiles.write(root.resolve(relative), json.getBytes(java.nio.charset.StandardCharsets.UTF_8), true); }

    @Test void releasedRowsMoveToNoClobberCopiesAndOpenRowsStayByteForByte() throws Exception {
        UUID released = UUID.randomUUID(), active = UUID.randomUUID(), cancelled = UUID.randomUUID();
        var settled = row(released, 1, "released"); var rows = new LinkedHashMap<Object, Object>(); rows.put(released.toString(), settled); rows.put(active.toString(), row(active, 2, "active"));
        var ownerless = row(cancelled, 3, "released"); ownerless.put("ownership", Map.of("format", 1, "kind", "OWNERLESS")); rows.put(7, ownerless);
        stations(rows); var activeBefore = store.station(active);
        var result = store.archiveReleased();
        assertEquals(2, result.archived()); assertEquals(List.of(), result.kept());
        assertEquals(Set.of(active.toString()), live().keySet()); assertTrue(GearYaml.equal((Map<?, ?>) live().get(active.toString()), activeBefore.raw()));
        assertEquals("kept", GearYaml.read(Files.readAllBytes(file())).get("root-future"));
        var copy = (Map<?, ?>) GearYaml.read(Files.readAllBytes(archive(released))).get("stations");
        assertEquals(Set.of(released.toString()), copy.keySet()); assertTrue(GearYaml.equal((Map<?, ?>) copy.get(released.toString()), settled));
        assertEquals(Set.of(7), ((Map<?, ?>) GearYaml.read(Files.readAllBytes(archive(cancelled))).get("stations")).keySet());
        byte[] liveBytes = Files.readAllBytes(file());
        assertEquals(new GearPropertyStore.Archived(0, List.of()), store.archiveReleased());
        assertArrayEquals(liveBytes, Files.readAllBytes(file()));
    }

    @Test void aCrashAfterTheCopyOnlyRepeatsTheRemovalAndADifferingCopyKeepsTheRow() throws Exception {
        UUID id = UUID.randomUUID(); var rows = new LinkedHashMap<Object, Object>(); rows.put(id.toString(), row(id, 1, "released"));
        stations(rows); byte[] before = Files.readAllBytes(file());
        store.archiveReleased(); byte[] copy = Files.readAllBytes(archive(id));
        GearFiles.write(file(), before, true);
        assertEquals(new GearPropertyStore.Archived(1, List.of()), store.archiveReleased());
        assertTrue(live().isEmpty()); assertArrayEquals(copy, Files.readAllBytes(archive(id)));
        GearFiles.write(file(), before, true); GearFiles.write(archive(id), "stations: {}\n".getBytes(java.nio.charset.StandardCharsets.UTF_8), true);
        assertEquals(new GearPropertyStore.Archived(0, List.of(id + " (archive copy differs)")), store.archiveReleased());
        assertArrayEquals(before, Files.readAllBytes(file()));
    }

    @Test void onlySettledWellFormedReleasedRowsAreArchived() throws Exception {
        var rows = new LinkedHashMap<Object, Object>(); int x = 0;
        for (Object ownership : new Object[] {null, "scalar", Map.of("format", 2, "kind", "CHARACTER"), Map.of("format", 1, "kind", "FUTURE"),
                Map.of("format", 1, "kind", "CHARACTER", "account", "bad", "character", "bad"), Map.of("format", 1), nullKind()}) {
            UUID id = UUID.randomUUID(); var raw = row(id, x++, "released"); if (ownership == null) raw.remove("ownership"); else raw.put("ownership", ownership); rows.put(id.toString(), raw);
        }
        var badId = row(UUID.randomUUID(), x++, "released"); badId.put("occupancy-id", "not-a-uuid"); rows.put("bad-id", badId);
        for (String state : List.of("active", "held", "releasing")) { UUID id = UUID.randomUUID(); rows.put(id.toString(), row(id, x++, state)); }
        UUID held = UUID.randomUUID(); var heldRow = row(held, x++, "released"); heldRow.put("ownership", Map.of("format", 1, "kind", "HELD")); rows.put(held.toString(), heldRow);
        stations(rows);
        assertEquals(1, store.archiveReleased().archived());
        assertEquals(rows.size() - 1, live().size()); assertFalse(live().containsKey(held.toString())); assertTrue(Files.exists(archive(held)));
    }

    static Map<Object, Object> nullKind() { var own = new LinkedHashMap<Object, Object>(); own.put("format", 1); own.put("kind", null); return own; }

    @Test void rowsNamedByOwnershipEvidenceStayInTheLiveFile() throws Exception {
        UUID estate = UUID.randomUUID(), migration = UUID.randomUUID(), receipt = UUID.randomUUID(), free = UUID.randomUUID();
        var rows = new LinkedHashMap<Object, Object>();
        for (UUID id : List.of(estate, migration, receipt, free)) rows.put(id.toString(), row(id, rows.size(), "released"));
        stations(rows);
        evidence("gear-ownership/estates/" + UUID.randomUUID() + ".json", "{\"entries\":[{\"id\":\"" + estate + "\"}]}");
        evidence("gear-ownership/migration/" + UUID.randomUUID() + ".json", "{\"run\":\"x\",\"entries\":[{\"id\":\"" + migration + "\"}]}");
        evidence("gear-ownership/receipts/" + UUID.randomUUID() + "/a.json", "{\"id\":\"" + receipt + "\"}");
        assertEquals(1, store.archiveReleased().archived());
        assertEquals(Set.of(estate.toString(), migration.toString(), receipt.toString()), live().keySet());
    }

    @Test void unreadableEvidenceChangesNothing() throws Exception {
        UUID id = UUID.randomUUID(); var rows = new LinkedHashMap<Object, Object>(); rows.put(id.toString(), row(id, 1, "released")); stations(rows);
        byte[] before = Files.readAllBytes(file());
        evidence("gear-ownership/estates/a.json", "{\"entries\":{}}");
        assertThrows(GearPropertyStore.Conflict.class, store::archiveReleased);
        evidence("gear-ownership/estates/a.json", "{\"entries\":[{\"id\":1}]}");
        assertThrows(GearPropertyStore.Conflict.class, store::archiveReleased);
        assertArrayEquals(before, Files.readAllBytes(file())); assertFalse(Files.exists(archive(id)));
    }

    @Test void aRowThatChangesWhileItsCopyIsWrittenStaysLive() throws Exception {
        for (boolean removed : new boolean[] {true, false}) {
            UUID id = UUID.randomUUID(); var rows = new LinkedHashMap<Object, Object>(); rows.put(id.toString(), row(id, 1, "released")); stations(rows);
            var changed = new LinkedHashMap<Object, Object>(); if (!removed) { var other = row(id, 1, "released"); other.put("x", 9); changed.put(id.toString(), other); }
            try (var files = mockStatic(GearFiles.class, CALLS_REAL_METHODS)) {
                files.when(() -> GearFiles.write(eq(archive(id)), any(), eq(false))).thenAnswer(call -> {
                    call.callRealMethod(); var document = new LinkedHashMap<Object, Object>(); document.put("stations", changed);
                    Files.write(file(), GearYaml.bytes(document)); return null;
                });
                byte[] before = GearYaml.bytes(Map.of("stations", changed));
                assertEquals(new GearPropertyStore.Archived(0, List.of(id + " (changed while archiving)")), store.archiveReleased());
                assertArrayEquals(before, Files.readAllBytes(file()));
            }
        }
    }

    @Test void aRowThatCannotBeCopiedOnlySkipsItself() throws Exception {
        UUID bad = UUID.randomUUID(), good = UUID.randomUUID(); var rows = new LinkedHashMap<Object, Object>();
        rows.put(bad.toString(), row(bad, 1, "released")); rows.put(good.toString(), row(good, 2, "released")); stations(rows);
        try (var files = mockStatic(GearFiles.class, CALLS_REAL_METHODS)) {
            files.when(() -> GearFiles.write(eq(archive(bad)), any(), eq(false))).thenThrow(new java.io.IOException("disk full"));
            assertEquals(new GearPropertyStore.Archived(1, List.of(bad + " (disk full)")), store.archiveReleased());
        }
        assertEquals(Set.of(bad.toString()), live().keySet()); assertFalse(Files.exists(archive(bad))); assertTrue(Files.exists(archive(good)));
        assertEquals(new GearPropertyStore.Archived(1, List.of()), store.archiveReleased()); assertTrue(live().isEmpty());
    }

    @Test void aCopyLinkedButNotYetSyncedIsMadeDurableBeforeItsRowLeaves() throws Exception {
        UUID id = UUID.randomUUID(); var rows = new LinkedHashMap<Object, Object>(); rows.put(id.toString(), row(id, 1, "released")); stations(rows);
        Path folder = archive(id).getParent();
        try (var files = mockStatic(GearFiles.class, CALLS_REAL_METHODS)) {
            files.when(() -> GearFiles.force(folder)).thenThrow(new java.io.IOException("power cut before the archive link was synced"));
            assertEquals(new GearPropertyStore.Archived(0, List.of(id + " (power cut before the archive link was synced)")), store.archiveReleased());
        }
        assertTrue(Files.exists(archive(id))); assertEquals(Set.of(id.toString()), live().keySet());
        try (var files = mockStatic(GearFiles.class, CALLS_REAL_METHODS)) {
            files.when(() -> GearFiles.durable(archive(id))).thenThrow(new java.io.IOException("archive sync failed"));
            assertEquals(new GearPropertyStore.Archived(0, List.of(id + " (archive sync failed)")), store.archiveReleased());
            assertEquals(Set.of(id.toString()), live().keySet());
        }
        try (var files = mockStatic(GearFiles.class, CALLS_REAL_METHODS)) {
            assertEquals(new GearPropertyStore.Archived(1, List.of()), store.archiveReleased());
            files.verify(() -> GearFiles.durable(archive(id))); files.verify(() -> GearFiles.force(folder), atLeastOnce());
        }
        assertTrue(live().isEmpty());
    }
}
