package net.tfminecraft.magic.gear.ownership;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

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
import net.tfminecraft.tlibs.character.CharacterRef;
import net.tfminecraft.tlibs.character.estate.EstateDecision;
import net.tfminecraft.tlibs.character.estate.Recipient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class GearPropertyReviewRegressionTest {
    @TempDir Path root;
    final GearIdentity.Owner owner = new GearIdentity.Owner(UUID.randomUUID(), UUID.randomUUID());
    final GearIdentity.Owner recipient = new GearIdentity.Owner(UUID.randomUUID(), UUID.randomUUID());
    final UUID death = UUID.randomUUID();
    GearPropertyStore store;
    GearIdentity identity;

    @BeforeEach void setup() {
        identity = mock(GearIdentity.class); when(identity.heirsReady()).thenReturn(true);
        when(identity.heir(owner.account())).thenReturn(Optional.of(owner)); store = new GearPropertyStore(root, identity);
    }
    Map<Object, Object> row(int x) throws IOException {
        var row = new LinkedHashMap<Object, Object>();
        row.put("world", "world"); row.put("x", x); row.put("y", 64); row.put("z", 20);
        row.put("owner", owner.account().toString()); row.put("item-bytes", "AQID");
        row.put("future", GearYaml.read("kept: null\nbinary: !!binary AQID\n".getBytes()));
        return row;
    }
    Map<Object, Object> active(int x) throws IOException {
        var row = row(x); row.put("occupancy-id", UUID.randomUUID().toString());
        row.put("ownership", GearPropertyStore.ownership(recipient)); row.put("effect-state", "active"); return row;
    }
    void stations(Map<Object, Object> rows) throws IOException {
        var doc = new LinkedHashMap<Object, Object>(); doc.put("unknown-root", null); doc.put("stations", rows);
        GearFiles.write(root.resolve("gear-stations.yml"), GearYaml.bytes(doc), true);
    }
    Map<String, byte[]> snapshot() throws IOException {
        var result = new TreeMap<String, byte[]>();
        try (var files = Files.walk(root)) {
            for (Path file : files.filter(path -> Files.isRegularFile(path, java.nio.file.LinkOption.NOFOLLOW_LINKS)).toList())
                result.put(root.relativize(file).toString(), Files.readAllBytes(file));
        }
        return result;
    }
    void unchanged(Map<String, byte[]> before) throws IOException {
        var after = snapshot(); assertEquals(before.keySet(), after.keySet());
        before.forEach((path, bytes) -> assertArrayEquals(bytes, after.get(path), path));
    }
    String decision() {
        var from = new CharacterRef(owner.account(), owner.character()); var to = new CharacterRef(recipient.account(), recipient.character());
        return new EstateDecision(death, from, Optional.empty(), Instant.EPOCH, Optional.of(to), Optional.empty(), Optional.empty(),
                Recipient.FROZEN, Recipient.character(to), Recipient.WAITING, Recipient.SENDER, 1).toString();
    }
    List<Path> scannerFolders() {
        return List.of(root.resolve("gear-ownership/migration"), root.resolve("gear-ownership/estates"),
                root.resolve("gear-ownership/receipts").resolve(death.toString()), root.resolve("gear-ownership/completions").resolve(death.toString()),
                root.resolve("gear-returns").resolve(recipient.character().toString()), root.resolve("gear-returns"));
    }
    List<String> committedFixture() throws IOException {
        stations(new LinkedHashMap<>(Map.of("original", row(1), "opaque", "retained"))); store.migrate();
        store.createReturn(owner, UUID.randomUUID(), List.of("AQID"), "pending");
        return store.transfer(death, decision(), owner, recipient, () -> true);
    }
    void readCommitted(List<String> moved) throws IOException {
        store.requireUnlocked(owner); store.requireUnlocked(recipient); store.migrate();
        assertEquals(1, store.returns(recipient).size()); assertTrue(store.returns(owner).isEmpty());
        assertEquals(moved, store.transfer(death, decision(), owner, recipient, () -> true)); store.holdInterrupted();
    }

    @Test void quarantinedMalformedLocationsAllowUnrelatedDepositsWithoutAlteringTheirRawData() throws Exception {
        for (String field : List.of("world", "x", "y", "z")) {
            for (Object invalid : field.equals("world") ? List.of(7, "", " ", "world,ambiguous") : List.of("1", 2_147_483_648L, 1.5)) {
                var malformed = row(3); malformed.put(field, invalid); stations(new LinkedHashMap<>(Map.of("quarantined", malformed, "opaque", "retained"))); store.migrate();
                var held = store.stations().getFirst(); byte[] preserved = GearYaml.bytes(held.raw());
                assertEquals("HELD", ((Map<?, ?>) held.raw().get("ownership")).get("kind"), field + "=" + invalid);
                assertFalse(store.occupied("world,4,64,20")); var fresh = active(4); store.insert(fresh);
                assertTrue(store.occupied("world,4,64,20")); assertArrayEquals(preserved, GearYaml.bytes(store.station(GearPropertyStore.id(held.raw())).raw()));
                var doc = GearYaml.read(Files.readAllBytes(root.resolve("gear-stations.yml")));
                assertTrue(doc.containsKey("unknown-root")); assertEquals("retained", ((Map<?, ?>) doc.get("stations")).get("opaque"));
            }
        }
        var missing = row(3); missing.remove("world"); stations(new LinkedHashMap<>(Map.of("quarantined", missing))); store.migrate(); store.insert(active(4));
    }

    @Test void validHeldUnknownAndAmbiguousCoordinatesStayReserved() throws Exception {
        for (Object state : List.of("held", "unrecognised")) {
            var reserved = active(3); reserved.put("effect-state", state); stations(new LinkedHashMap<>(Map.of("reserved", reserved)));
            var before = snapshot(); assertTrue(store.occupied("world,3,64,20")); assertThrows(IOException.class, () -> store.insert(active(3))); unchanged(before);
        }
        var noState = active(3); noState.remove("effect-state"); stations(new LinkedHashMap<>(Map.of("reserved", noState)));
        var before = snapshot(); assertTrue(store.occupied("world,3,64,20")); assertThrows(IOException.class, () -> store.insert(active(3))); unchanged(before);
        var first = active(3); var second = active(3); stations(new LinkedHashMap<>(Map.of("first", first, "second", second)));
        before = snapshot(); assertThrows(IOException.class, () -> store.insert(active(4))); unchanged(before);
        second.put("effect-state", "released"); stations(new LinkedHashMap<>(Map.of("first", first, "second", second)));
        assertTrue(store.occupied("world,3,64,20")); var changed = GearYaml.copy(first); changed.put("effect-state", "released"); store.patch(store.station(GearPropertyStore.id(first)), changed);
        assertFalse(store.occupied("world,3,64,20")); store.insert(active(3));
    }

    @Test void crashStagingRegularFilesArePreservedAndIgnoredInEveryCommittedScanner() throws Exception {
        var moved = committedFixture();
        for (Path folder : scannerFolders()) {
            Files.createDirectories(folder); Files.write(folder.resolve("magic-12345.tmp"), new byte[]{0, 1, 2, 3, -1});
        }
        var before = snapshot(); readCommitted(moved); unchanged(before);
        var newId = UUID.randomUUID(); store.createReturn(recipient, newId, List.of("AQID"), "pending");
        assertEquals(2, store.returns(recipient).size());
        for (Path folder : scannerFolders()) assertArrayEquals(new byte[]{0, 1, 2, 3, -1}, Files.readAllBytes(folder.resolve("magic-12345.tmp")));
    }

    @Test void temporaryLookingDirectoriesSymlinksAndUnknownCommittedRecordsStillHold() throws Exception {
        var moved = committedFixture();
        for (Path folder : scannerFolders()) {
            Path suspect = folder.resolve("magic-12345.tmp"); Files.createDirectories(suspect);
            var before = snapshot(); assertThrows(IOException.class, () -> readCommitted(moved), folder.toString()); unchanged(before); assertTrue(Files.isDirectory(suspect)); Files.delete(suspect);
            Files.createSymbolicLink(suspect, folder.resolve("missing-target")); before = snapshot();
            assertThrows(IOException.class, () -> readCommitted(moved), folder.toString()); unchanged(before); assertTrue(Files.isSymbolicLink(suspect)); Files.delete(suspect);
            Path target = root.resolve("raw-preserved"); Files.writeString(target, "unknown bytes"); Files.createSymbolicLink(suspect, target); before = snapshot();
            assertThrows(IOException.class, () -> readCommitted(moved), folder.toString()); unchanged(before); assertEquals("unknown bytes", Files.readString(target)); Files.delete(suspect);
            if (!folder.equals(root.resolve("gear-returns"))) {
                for (String name : List.of("unknown.json", "magic-12345.json", "other.tmp")) {
                    suspect = folder.resolve(name); Files.writeString(suspect, "{malformed and unknown}"); before = snapshot();
                    assertThrows(IOException.class, () -> readCommitted(moved), folder.toString()); unchanged(before); Files.delete(suspect);
                }
            }
        }
        Files.delete(root.resolve("raw-preserved")); readCommitted(moved);
    }
}
