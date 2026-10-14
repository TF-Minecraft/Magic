package net.tfminecraft.magic;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.nio.file.Files;
import java.util.Map;
import java.util.UUID;
import net.tfminecraft.magic.gear.GearStationStore;
import net.tfminecraft.magic.gear.orb.GearOrbService;
import net.tfminecraft.magic.gear.ownership.*;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;

class GearReviewRegressionTest extends GearStationCoverageSupport {
    GearIdentity.Token token(org.bukkit.entity.Player player) {
        return GearStationStore.property().identity().current(player).orElseThrow();
    }

    @Test void staffOnTheSameAccountCannotAcquireAnotherCharactersDepositedWeapon() throws Exception {
        var player = server.addPlayer(); var original = token(player); var location = station();
        var occupancy = seedRest(location, item(), player.getUniqueId());
        player.addAttachment(Magic.plugin, "magic.admin", true);
        var incoming = new net.tfminecraft.tlibs.character.CharacterRef(player.getUniqueId(), UUID.randomUUID());
        characters.refs.put(player.getUniqueId(), incoming);
        characters.sessions.put(player.getUniqueId(), new net.tfminecraft.tlibs.character.CharacterSession(incoming, original.session() + 1));
        var actor = token(player); assertEquals(original.owner().account(), actor.owner().account());
        assertNotEquals(original.owner().character(), actor.owner().character());
        assertTrue(GearEffects.abortReturn(player, location, occupancy, Map.of()));
        assertTrue(player.getInventory().isEmpty(), "an incoming character must never acquire the original character's weapon");
        var store = GearStationStore.property(); assertTrue(store.returns(actor.owner()).isEmpty());
        var returned = store.returns(original.owner()).getFirst();
        assertEquals("pending", GearPropertyStore.text(returned, "state"));
        assertEquals(original.owner(), GearPropertyStore.returnOwner(returned));
        GearEffects.deliver(player, actor); GearEffects.deliver(player, original); assertTrue(player.getInventory().isEmpty());
    }

    @Test void staffAbortQueuesTheRestedWeaponForItsOfflineOwnerAndNeverGivesItToStaff() throws Exception {
        var owner = server.addPlayer(); var staff = server.addPlayer();
        var ownerToken = token(owner); var staffToken = token(staff);
        staff.addAttachment(Magic.plugin, "magic.admin", true);
        var location = station(); var weapon = item(); var occupancy = seedRest(location, weapon, owner.getUniqueId());
        owner.disconnect();
        assertTrue(GearEffects.abortReturn(staff, location, occupancy, Map.of()));
        assertTrue(staff.getInventory().isEmpty(), "staff must never receive the owner's weapon");
        var store = GearStationStore.property();
        assertTrue(store.returns(staffToken.owner()).isEmpty());
        var returned = store.returns(ownerToken.owner()).getFirst();
        assertEquals(ownerToken.owner(), GearPropertyStore.returnOwner(returned));
        assertEquals("pending", GearPropertyStore.text(returned, "state"));
        assertEquals(weapon, GearItems.item(GearPropertyStore.bytes(returned.getAsJsonArray("items").get(0).getAsString())));
        assertEquals("released", store.station(occupancy.generation()).raw().get("effect-state"));
        checkpoint.verifyNoInteractions();
    }

    @Test void staffAbortRefundsRecordedMaterialsToTheDepositingCharacter() throws Exception {
        var owner = server.addPlayer(); var staff = server.addPlayer();
        var ownerToken = token(owner); var staffToken = token(staff);
        staff.addAttachment(Magic.plugin, "magic.admin", true);
        var location = station(); var occupancy = seed(location, item(), owner.getUniqueId(), Map.of("v.DIAMOND", 65));
        try (var refs = mockStatic(net.tfminecraft.magic.util.ItemRef.class)) {
            refs.when(() -> net.tfminecraft.magic.util.ItemRef.build("v.DIAMOND")).thenReturn(new ItemStack(Material.DIAMOND));
            assertTrue(GearEffects.abortReturn(staff, location, occupancy, occupancy.getCharged()));
        }
        assertTrue(staff.getInventory().isEmpty()); assertTrue(owner.getInventory().isEmpty());
        var store = GearStationStore.property(); assertTrue(store.returns(staffToken.owner()).isEmpty());
        var returned = store.returns(ownerToken.owner()).getFirst();
        assertEquals("pending", GearPropertyStore.text(returned, "state"));
        var parts = returned.getAsJsonArray("items"); assertEquals(2, parts.size());
        assertEquals(new ItemStack(Material.DIAMOND, 64), GearItems.item(GearPropertyStore.bytes(parts.get(0).getAsString())));
        assertEquals(new ItemStack(Material.DIAMOND, 1), GearItems.item(GearPropertyStore.bytes(parts.get(1).getAsString())));
        GearEffects.deliver(owner, ownerToken);
        assertEquals(64, owner.getInventory().getItem(0).getAmount()); assertEquals(1, owner.getInventory().getItem(1).getAmount());
        assertTrue(staff.getInventory().isEmpty());
    }

    @Test void unknownRecipientAndLockedOwnerHoldBeforeAbortingOrPublishingAnything() throws Exception {
        var staff = server.addPlayer(); var actor = token(staff); staff.addAttachment(Magic.plugin, "magic.admin", true);
        var location = station(); var unknown = seedRest(location, item(), null);
        var store = spy(GearStationStore.property()); setField("property", store);
        byte[] before = Files.readAllBytes(temp.resolve("data/gear-stations.yml"));
        try (var orbs = mockStatic(GearOrbService.class)) {
            assertFalse(GearEffects.abortReturn(staff, location, unknown, Map.of()));
            assertSame(unknown, GearStationStore.get(location));
            assertArrayEquals(before, Files.readAllBytes(temp.resolve("data/gear-stations.yml")));
            assertTrue(store.returns(actor.owner()).isEmpty()); orbs.verifyNoInteractions();
        }
        var owner = server.addPlayer(); var recipient = token(owner).owner();
        var owned = seedRest(location, item(), owner.getUniqueId());
        before = Files.readAllBytes(temp.resolve("data/gear-stations.yml"));
        doThrow(new GearPropertyStore.Conflict("recipient publication is locked")).when(store).requireUnlocked(recipient);
        try (var orbs = mockStatic(GearOrbService.class)) {
            assertFalse(GearEffects.abortReturn(staff, location, owned, Map.of()));
            assertSame(owned, GearStationStore.get(location));
            assertArrayEquals(before, Files.readAllBytes(temp.resolve("data/gear-stations.yml")));
            verify(store, never()).createReturn(any(), any(), any(), anyString()); orbs.verifyNoInteractions();
        }
    }

    @Test void ownerAbortDeliversOnlyToTheCapturedSessionAndAChangedAuthorityCannotRelease() throws Exception {
        var owner = server.addPlayer(); var original = token(owner); var location = station();
        var occupancy = seedRest(location, item(), owner.getUniqueId());
        var store = spy(GearStationStore.property()); setField("property", store);
        doAnswer(call -> {
            call.callRealMethod();
            var current = characters.currentSession(owner).orElseThrow();
            characters.sessions.put(owner.getUniqueId(), new net.tfminecraft.tlibs.character.CharacterSession(current.ref(), current.sessionId() + 1));
            return null;
        }).when(store).createReturn(any(), any(), any(), anyString());
        assertTrue(GearEffects.abortReturn(owner, location, occupancy, Map.of()));
        assertTrue(owner.getInventory().isEmpty());
        assertEquals("pending", GearPropertyStore.text(store.returns(original.owner()).getFirst(), "state"));
        GearEffects.deliver(owner, original); assertTrue(owner.getInventory().isEmpty());
        GearEffects.deliver(owner, token(owner)); assertEquals(item(), owner.getInventory().getItem(0));
        doCallRealMethod().when(store).createReturn(any(), any(), any(), anyString());
        var stale = seedRest(location, item(), owner.getUniqueId()); var row = store.station(stale.generation());
        var altered = GearYaml.copy(row.raw()); altered.put("ownership", GearPropertyStore.ownership(new GearIdentity.Owner(owner.getUniqueId(), UUID.randomUUID()))); store.patch(row, altered);
        byte[] before = Files.readAllBytes(temp.resolve("data/gear-stations.yml"));
        assertFalse(GearEffects.abortReturn(owner, location, stale, Map.of()));
        assertSame(stale, GearStationStore.get(location)); assertArrayEquals(before, Files.readAllBytes(temp.resolve("data/gear-stations.yml")));
        assertEquals(1, store.returns(original.owner()).size());
    }

    @Test void unchangedDisplayAndNullDisplayDoNotPublishDurableStationWrites() throws Exception {
        var owner = server.addPlayer(); token(owner); var location = station(); seedRest(location, item(), owner.getUniqueId());
        GearStationStore.save(); var file = temp.resolve("data/gear-stations.yml"); byte[] before = Files.readAllBytes(file);
        try (var writes = mockStatic(GearFiles.class, CALLS_REAL_METHODS)) {
            GearStationStore.save(); GearStationStore.save(); writes.verifyNoInteractions();
        }
        assertArrayEquals(before, Files.readAllBytes(file));
        var occupancy = GearStationStore.get(location); var display = occupancy.getClass().getDeclaredField("displayId"); display.setAccessible(true); display.set(occupancy, null);
        try (var writes = mockStatic(GearFiles.class, CALLS_REAL_METHODS)) {
            GearStationStore.save(); writes.verifyNoInteractions();
        }
        assertArrayEquals(before, Files.readAllBytes(file));
    }

    @Test void aNewDisplayPublishesOnceAndPreservesRawUnknownData() throws Exception {
        var owner = server.addPlayer(); token(owner); var location = station(); var occupancy = seedRest(location, item(), owner.getUniqueId());
        GearStationStore.save(); var store = GearStationStore.property(); var row = store.station(occupancy.generation());
        var enriched = GearYaml.copy(row.raw()); enriched.put("future", GearYaml.read("kept: null\nbinary: !!binary AQID\n".getBytes())); store.patch(row, enriched);
        UUID replacement = UUID.randomUUID(); var display = occupancy.getClass().getDeclaredField("displayId"); display.setAccessible(true); display.set(occupancy, replacement);
        try (var writes = mockStatic(GearFiles.class, CALLS_REAL_METHODS)) {
            GearStationStore.save(); GearStationStore.save();
            writes.verify(() -> GearFiles.write(eq(temp.resolve("data/gear-stations.yml")), any(), eq(true)), times(1));
        }
        var saved = store.station(occupancy.generation()).raw(); assertEquals(replacement.toString(), saved.get("display"));
        saved.put("display", enriched.get("display")); assertTrue(GearYaml.equal(enriched, saved));
    }
}
