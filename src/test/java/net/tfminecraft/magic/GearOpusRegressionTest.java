package net.tfminecraft.magic;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.google.gson.JsonNull;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.tfminecraft.magic.gear.GearStationStore;
import net.tfminecraft.magic.gear.ownership.*;
import net.tfminecraft.tlibs.character.CharacterRef;
import net.tfminecraft.tlibs.character.estate.EstateDecision;
import net.tfminecraft.tlibs.character.estate.Recipient;
import org.bukkit.Location;
import org.junit.jupiter.api.Test;

class GearOpusRegressionTest extends GearStationCoverageSupport {
    GearIdentity.Token token(org.bukkit.entity.Player player) {
        return GearStationStore.property().identity().current(player).orElseThrow();
    }
    GearPropertyStore spyStore() throws Exception {
        GearPropertyStore store = spy(GearStationStore.property());
        setField("property", store);
        return store;
    }
    void display(GearStationStore.Occupancy occupancy, UUID id) throws Exception {
        var field = occupancy.getClass().getDeclaredField("displayId");
        field.setAccessible(true);
        field.set(occupancy, id);
    }
    String decision(UUID death, GearIdentity.Owner from, GearIdentity.Owner to) {
        var source = new CharacterRef(from.account(), from.character());
        var target = new CharacterRef(to.account(), to.character());
        return new EstateDecision(death, source, Optional.empty(), Instant.EPOCH, Optional.of(target),
                Optional.empty(), Optional.empty(), Recipient.FROZEN, Recipient.character(target),
                Recipient.FROZEN, Recipient.SENDER, 1).toString();
    }

    @Test void manyUnchangedOrNullDisplaysReadStationsOnceWithoutOwnershipScansOrWrites() throws Exception {
        var owner = server.addPlayer(); token(owner);
        var first = station();
        List<GearStationStore.Occupancy> cached = new ArrayList<>();
        for (int i = 0; i < 10; i++) cached.add(seedRest(new Location(first.getWorld(), i * 2, 2, 3), item(), owner.getUniqueId()));
        GearStationStore.save();
        display(occupied().values().iterator().next(), null);
        var store = spyStore();
        byte[] before = Files.readAllBytes(temp.resolve("data/gear-stations.yml"));
        try (var writes = mockStatic(GearFiles.class, CALLS_REAL_METHODS)) {
            GearStationStore.save();
            verify(store, times(1)).stations();
            verify(store, never()).station(any());
            verify(store, never()).requireUnlocked(any());
            verify(store, never()).patch(any(), any());
            writes.verifyNoInteractions();
        }
        assertArrayEquals(before, Files.readAllBytes(temp.resolve("data/gear-stations.yml")));
    }

    @Test void stationInventoryReadFailureLogsOnceAndNeverChecksLocksOrPublishes() throws Exception {
        var owner = server.addPlayer(); token(owner); var at = station();
        for (int i = 0; i < 3; i++) seedRest(new Location(at.getWorld(), i * 2, 2, 3), item(), owner.getUniqueId());
        var store = spyStore();
        doThrow(new IOException("station snapshot unreadable")).when(store).stations();
        var logger = mock(java.util.logging.Logger.class); when(Magic.plugin.getLogger()).thenReturn(logger);
        byte[] before = Files.readAllBytes(temp.resolve("data/gear-stations.yml"));
        try (var writes = mockStatic(GearFiles.class, CALLS_REAL_METHODS)) {
            GearStationStore.save();
            verify(store, times(1)).stations();
            verify(store, never()).requireUnlocked(any());
            verify(store, never()).patch(any(), any());
            writes.verifyNoInteractions();
        }
        verify(logger, times(1)).warning(contains("station snapshot unreadable"));
        assertArrayEquals(before, Files.readAllBytes(temp.resolve("data/gear-stations.yml")));
    }

    @Test void aMissingCachedStationStillFallsThroughAndHoldsWithoutPublishing() throws Exception {
        var owner = server.addPlayer(); token(owner); var at = station();
        var occupancy = seedRest(at, item(), owner.getUniqueId());
        var file = temp.resolve("data/gear-stations.yml");
        var document = GearYaml.read(Files.readAllBytes(file));
        ((Map<?, ?>) document.get("stations")).clear();
        GearFiles.write(file, GearYaml.bytes(document), true);
        var store = spyStore(); var logger = mock(java.util.logging.Logger.class); when(Magic.plugin.getLogger()).thenReturn(logger);
        byte[] before = Files.readAllBytes(file);
        try (var writes = mockStatic(GearFiles.class, CALLS_REAL_METHODS)) {
            GearStationStore.save();
            verify(store).station(occupancy.generation());
            verify(store, never()).requireUnlocked(any());
            verify(store, never()).patch(any(), any());
            writes.verifyNoInteractions();
        }
        verify(logger).warning(contains("Occupancy disappeared"));
        assertArrayEquals(before, Files.readAllBytes(file));
    }

    @Test void aChangedDisplayStillRequiresTheFullCurrentOwnerAndUnlockedHistory() throws Exception {
        var owner = server.addPlayer(); var original = token(owner); var at = station();
        var occupancy = seedRest(at, item(), owner.getUniqueId());
        var store = spyStore();
        display(occupancy, UUID.randomUUID());
        doThrow(new GearPropertyStore.Conflict("ownership publication held")).when(store).requireUnlocked(original.owner());
        byte[] before = Files.readAllBytes(temp.resolve("data/gear-stations.yml"));
        GearStationStore.save();
        verify(store).requireUnlocked(original.owner()); verify(store, never()).patch(any(), any());
        assertArrayEquals(before, Files.readAllBytes(temp.resolve("data/gear-stations.yml")));
        doCallRealMethod().when(store).requireUnlocked(any());
        var row = store.station(occupancy.generation()); var transferred = GearYaml.copy(row.raw());
        transferred.put("ownership", GearPropertyStore.ownership(new GearIdentity.Owner(owner.getUniqueId(), UUID.randomUUID())));
        store.patch(row, transferred);
        clearInvocations(store);
        before = Files.readAllBytes(temp.resolve("data/gear-stations.yml"));
        GearStationStore.save();
        verify(store, never()).requireUnlocked(any()); verify(store, never()).patch(any(), any());
        assertArrayEquals(before, Files.readAllBytes(temp.resolve("data/gear-stations.yml")));
    }

    @Test void anAcknowledgedEmptyStaffAbortIsTerminalForTheOriginalOwnerWithoutPhysicalEffects() throws Exception {
        var owner = server.addPlayer(); var original = token(owner); var staff = server.addPlayer(); token(staff);
        staff.addAttachment(Magic.plugin, "magic.admin", true);
        var at = station(); var occupancy = seed(at, item(), owner.getUniqueId(), Map.of());
        owner.disconnect();
        assertTrue(GearEffects.abortReturn(staff, at, occupancy, Map.of()));
        var store = GearStationStore.property(); var returned = store.returns(original.owner()).getFirst();
        assertEquals("delivered", GearPropertyStore.text(returned, "state"));
        assertEquals(original.owner(), GearPropertyStore.returnOwner(returned));
        assertTrue(returned.getAsJsonArray("items").isEmpty());
        assertEquals("released", archived(occupancy.generation()).get("effect-state"));
        assertTrue(store.returns(token(staff).owner()).isEmpty());
        assertTrue(staff.getInventory().isEmpty()); checkpoint.verifyNoInteractions();
        UUID death = UUID.randomUUID();
        assertTrue(store.transfer(death, decision(death, original.owner(), token(staff).owner()), original.owner(), token(staff).owner(), () -> true).isEmpty());
    }

    @Test void unacknowledgedEmptyAbortKeepsItsFullOwnerAndHeldEvidence() throws Exception {
        var owner = server.addPlayer(); var original = token(owner); var at = station();
        var occupancy = seed(at, item(), owner.getUniqueId(), Map.of());
        try (var stations = mockStatic(GearStationStore.class, CALLS_REAL_METHODS)) {
            stations.when(() -> GearStationStore.acknowledgeRelease(at, occupancy.generation())).thenReturn(false);
            assertFalse(GearEffects.abortReturn(owner, at, occupancy, Map.of()));
        }
        var row = GearStationStore.property().returns(original.owner()).getFirst();
        assertEquals("held", GearPropertyStore.text(row, "state"));
        assertEquals(original.owner(), GearPropertyStore.returnOwner(row));
        assertTrue(row.getAsJsonArray("items").isEmpty());
        GearEffects.deliver(owner, original); assertEquals("held", GearPropertyStore.text(GearStationStore.property().returns(original.owner()).getFirst(), "state"));
        checkpoint.verifyNoInteractions();
    }

    @Test void failedEmptyReturnCompletionKeepsHeldEvidenceAfterAcknowledgedRelease() throws Exception {
        var owner = server.addPlayer(); var original = token(owner); var at = station();
        var occupancy = seed(at, item(), owner.getUniqueId(), Map.of());
        var store = spyStore();
        doThrow(new IOException("empty return completion unavailable")).when(store).saveReturn(any(), any());
        assertFalse(GearEffects.abortReturn(owner, at, occupancy, Map.of()));
        var row = store.returns(original.owner()).getFirst();
        assertEquals("held", GearPropertyStore.text(row, "state"));
        assertEquals(original.owner(), GearPropertyStore.returnOwner(row)); assertTrue(row.getAsJsonArray("items").isEmpty());
        assertEquals("released", archived(occupancy.generation()).get("effect-state"));
        byte[] held = Files.readAllBytes(store.returnFile(original.owner(), UUID.fromString(GearPropertyStore.text(row, "id"))));
        doCallRealMethod().when(store).saveReturn(any(), any());
        GearEffects.deliver(owner, original);
        assertArrayEquals(held, Files.readAllBytes(store.returnFile(original.owner(), UUID.fromString(GearPropertyStore.text(row, "id")))));
        assertTrue(owner.getInventory().isEmpty()); checkpoint.verifyNoInteractions();
    }

    @Test void inheritedEmptyPendingReturnBecomesTerminalWithoutChangingItsReceiptOrUnknownFields() throws Exception {
        var owner = server.addPlayer(); var original = token(owner); var heir = server.addPlayer(); var recipient = token(heir);
        var store = GearStationStore.property(); var operation = UUID.randomUUID();
        store.createReturn(original.owner(), operation, List.of(), "pending");
        var expected = store.returns(original.owner()).getFirst(); var enriched = expected.deepCopy(); enriched.add("future", JsonNull.INSTANCE); store.saveReturn(expected, enriched);
        UUID death = UUID.randomUUID();
        assertEquals(1, store.transfer(death, decision(death, original.owner(), recipient.owner()), original.owner(), recipient.owner(), () -> true).size());
        List<Path> receipts;
        try (var files = Files.walk(temp.resolve("data/gear-ownership"))) { receipts = files.filter(Files::isRegularFile).toList(); }
        var history = new java.util.HashMap<Path, byte[]>(); for (Path file : receipts) history.put(file, Files.readAllBytes(file));
        GearEffects.deliver(heir, recipient);
        var after = store.returns(recipient.owner()).getFirst();
        assertEquals("delivered", GearPropertyStore.text(after, "state"));
        assertEquals(recipient.owner(), GearPropertyStore.returnOwner(after)); assertEquals(operation.toString(), GearPropertyStore.text(after, "id"));
        assertTrue(after.get("future").isJsonNull()); assertTrue(after.getAsJsonArray("items").isEmpty());
        assertTrue(heir.getInventory().isEmpty()); checkpoint.verifyNoInteractions();
        for (var saved : history.entrySet()) assertArrayEquals(saved.getValue(), Files.readAllBytes(saved.getKey()));
        byte[] terminal = Files.readAllBytes(store.returnFile(recipient.owner(), operation));
        GearEffects.deliver(heir, recipient); assertArrayEquals(terminal, Files.readAllBytes(store.returnFile(recipient.owner(), operation)));
        UUID nextDeath = UUID.randomUUID();
        assertTrue(store.transfer(nextDeath, decision(nextDeath, recipient.owner(), original.owner()), recipient.owner(), original.owner(), () -> true).isEmpty());
        assertTrue(store.returns(original.owner()).isEmpty());
    }
}
