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
import java.util.Set;
import java.util.UUID;
import java.util.logging.Logger;
import net.tfminecraft.magic.Magic;
import net.tfminecraft.magic.gear.GearStationStore;
import net.tfminecraft.magic.gear.orb.GearOrbService;
import net.tfminecraft.tlibs.character.*;
import net.tfminecraft.tlibs.character.estate.*;
import org.bukkit.Server;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.scheduler.BukkitScheduler;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;

class CharacterGearEstateTest {
    @TempDir Path root;
    ServerMock server;
    Magic plugin;
    Logger logger;
    CharacterIdentity characters;
    EstateService estates;
    GearIdentity identity;
    GearPropertyStore store;
    CharacterGearEstate participant;
    MockedStatic<GearStationStore> views;
    MockedStatic<GearOrbService> orbs;
    final CharacterRef from = new CharacterRef(UUID.randomUUID(), UUID.randomUUID());
    final CharacterRef to = new CharacterRef(UUID.randomUUID(), UUID.randomUUID());
    final UUID death = UUID.randomUUID();

    @BeforeEach void setup() {
        server = MockBukkit.mock();
        Plugin provider = MockBukkit.createMockPlugin();
        characters = mock(CharacterIdentity.class);
        estates = mock(EstateService.class);
        server.getServicesManager().register(CharacterIdentity.class, characters, provider, ServicePriority.Highest);
        server.getServicesManager().register(EstateService.class, estates, provider, ServicePriority.Highest);
        when(characters.info(from.characterId())).thenReturn(Optional.of(info(from, CharacterStatus.DEAD)));
        when(characters.info(to.characterId())).thenReturn(Optional.of(info(to, CharacterStatus.ALIVE)));
        when(characters.deathId(from.characterId())).thenReturn(Optional.of(death));
        when(estates.stateOf(from.characterId())).thenReturn(Optional.of(EstateState.DECIDED));
        plugin = mock(Magic.class);
        logger = mock(Logger.class);
        when(plugin.getLogger()).thenReturn(logger);
        identity = mock(GearIdentity.class);
        when(identity.heirsReady()).thenReturn(true);
        store = spy(new GearPropertyStore(root, identity));
        views = mockStatic(GearStationStore.class);
        views.when(GearStationStore::property).thenReturn(store);
        orbs = mockStatic(GearOrbService.class);
        participant = new CharacterGearEstate(plugin);
    }

    @AfterEach void teardown() {
        orbs.close();
        views.close();
        MockBukkit.unmock();
    }

    static CharacterInfo info(CharacterRef ref, CharacterStatus status) {
        return new CharacterInfo(ref, "Owner", status, null, Set.of(), 0, Instant.EPOCH);
    }
    static GearIdentity.Owner owner(CharacterRef ref) { return new GearIdentity.Owner(ref.ownerId(), ref.characterId()); }
    EstateDecision decision(Recipient property, long revision) {
        var value = new EstateDecision(death, from, Optional.empty(), Instant.EPOCH, Optional.of(to),
                Optional.empty(), Optional.empty(), Recipient.FROZEN, property, Recipient.WAITING, Recipient.SENDER, revision);
        when(estates.decision(death)).thenReturn(Optional.of(value));
        when(estates.decided()).thenReturn(List.of(value));
        return value;
    }
    UUID station() throws IOException {
        UUID id = UUID.randomUUID();
        Map<Object,Object> raw = new LinkedHashMap<>();
        raw.put("world", "unloaded-world"); raw.put("x", 3); raw.put("y", 64); raw.put("z", 5);
        raw.put("occupancy-id", id.toString()); raw.put("ownership", GearPropertyStore.ownership(owner(from)));
        raw.put("effect-state", "active"); raw.put("item-bytes", "AQID"); raw.put("extension", null);
        store.insert(raw);
        return id;
    }

    @Test void realEstateGuardRejectsRevivalDifferentDeathWrongFullOwnerAndStaleDecision() throws Exception {
        var value = decision(Recipient.character(to), 1);
        when(characters.info(from.characterId())).thenReturn(Optional.of(info(from, CharacterStatus.ALIVE)));
        assertEquals(Outcome.Kind.RETRY, participant.settle(value).kind());
        when(characters.info(from.characterId())).thenReturn(Optional.of(info(new CharacterRef(UUID.randomUUID(), from.characterId()), CharacterStatus.DEAD)));
        assertEquals(Outcome.Kind.RETRY, participant.settle(value).kind());
        when(characters.info(from.characterId())).thenReturn(Optional.of(info(from, CharacterStatus.DEAD)));
        when(characters.deathId(from.characterId())).thenReturn(Optional.of(UUID.randomUUID()));
        assertEquals(Outcome.Kind.RETRY, participant.settle(value).kind());
        when(characters.deathId(from.characterId())).thenReturn(Optional.of(death));
        var newer = decision(Recipient.character(to), 2);
        assertEquals(Outcome.Kind.RETRY, participant.settle(value).kind());
        assertEquals(Outcome.Kind.DONE, participant.settle(newer).kind());
        assertFalse(Files.exists(root.resolve("gear-stations.yml")));
        verify(store, times(1)).validateEstate(any(), anyString(), any());
    }

    @Test void facadeDrivenTransferKeepsStationAndReturnPayloadsAndReportsCumulativeHoldings() throws Exception {
        UUID station = station(), returned = UUID.randomUUID();
        store.createReturn(owner(from), returned, List.of("AQID"), "pending");
        Path profiles = root.resolve("profiles.yml"); Files.writeString(profiles, "focus: retained\n");
        assertEquals("magic-gear", participant.id());
        assertEquals(Map.of("gear", 2), participant.describe(from.characterId()).counts());
        var value = decision(Recipient.character(to), 1);
        var done = assertInstanceOf(Outcome.Done.class, participant.settle(value));
        assertEquals(Map.of("gear", 2), done.counts());
        assertEquals(2, done.movedIds().size());
        assertEquals(owner(to), GearPropertyStore.owner(store.station(station).raw()));
        assertTrue(store.station(station).raw().containsKey("extension"));
        assertEquals(owner(to), GearPropertyStore.returnOwner(store.returns(owner(to)).getFirst()));
        assertEquals(Holdings.NONE, participant.describe(from.characterId()));
        assertEquals(Map.of("gear", 2), participant.describe(to.characterId()).counts());
        assertEquals(done, participant.settle(value));
        var frozen = decision(Recipient.FROZEN, 2);
        assertEquals(done, participant.settle(frozen));
        assertEquals("focus: retained\n", Files.readString(profiles));
        views.verify(GearStationStore::loadViews, times(3));
    }

    @Test void publicRunAndRecipientRunWaitWhileHeldEffectsAndIoFailuresHaveDifferentOutcomes() throws Exception {
        var value = decision(Recipient.character(to), 1);
        orbs.when(() -> GearOrbService.touches(owner(from))).thenReturn(true);
        assertEquals(Outcome.Kind.RETRY, participant.settle(value).kind());
        verify(store, never()).validateEstate(any(), anyString(), any());
        orbs.when(() -> GearOrbService.touches(owner(from))).thenReturn(false);
        orbs.when(() -> GearOrbService.touches(owner(to))).thenReturn(true);
        assertEquals(Outcome.Kind.RETRY, participant.settle(value).kind());
        orbs.when(() -> GearOrbService.touches(owner(to))).thenReturn(false);
        doThrow(new IOException("cannot flush ownership")).when(store).requireReady(owner(from));
        assertEquals(Outcome.Kind.RETRY, participant.settle(value).kind());
        doThrow(new GearPropertyStore.Conflict("held physical effect")).when(store).requireReady(owner(from));
        assertEquals(Outcome.Kind.BLOCKED, participant.settle(value).kind());
        views.verify(GearStationStore::loadViews, never());
    }

    @Test void holdingsNeverInventAnOwnerWhenInfoOrRawAuthorityIsUnusable() throws Exception {
        assertEquals(Holdings.NONE, participant.describe(from.characterId()));
        when(characters.info(from.characterId())).thenReturn(Optional.empty());
        assertEquals(Map.of("heldGear", 1), participant.describe(from.characterId()).counts());
        when(characters.info(from.characterId())).thenReturn(Optional.of(info(to, CharacterStatus.ALIVE)));
        assertEquals(Map.of("heldGear", 1), participant.describe(from.characterId()).counts());
        when(characters.info(from.characterId())).thenReturn(Optional.of(info(from, CharacterStatus.DEAD)));
        UUID id = station();
        var before = store.station(id); var bad = GearYaml.copy(before.raw());
        bad.put("ownership", Map.of("format", 2, "kind", "CHARACTER")); store.patch(before, bad);
        assertEquals(Map.of("heldGear", 1), participant.describe(from.characterId()).counts());
    }

    @Test void releasedOwnerlessOpaqueAndDeliveredEvidenceIsRetainedWithoutClaimingCurrentHoldings() throws Exception {
        UUID id = station();
        var before = store.station(id); var ownerless = GearYaml.copy(before.raw());
        ownerless.put("ownership", Map.of("format", 1, "kind", "OWNERLESS")); store.patch(before, ownerless);
        assertEquals(Holdings.NONE, participant.describe(from.characterId()));
        var opaque = GearYaml.copy(ownerless); opaque.put("ownership", null); store.patch(store.station(id), opaque);
        assertEquals(Holdings.NONE, participant.describe(from.characterId()));
        var released = GearYaml.copy(before.raw()); released.put("effect-state", "released"); store.patch(store.station(id), released);
        store.createReturn(owner(from), UUID.randomUUID(), List.of("AQID"), "delivered");
        assertEquals(Holdings.NONE, participant.describe(from.characterId()));
        assertTrue(store.station(id).raw().containsKey("extension"));
        assertEquals(1, store.returns(owner(from)).size());
    }

    @Test void reportUnavailabilityAfterSettlementDoesNotSuppressLaterReporting() throws Exception {
        decision(Recipient.FROZEN, 1);
        var viewsLoaded = new java.util.concurrent.atomic.AtomicInteger();
        views.when(GearStationStore::loadViews).thenAnswer(call -> {
            if (viewsLoaded.incrementAndGet() == 2) server.getServicesManager().unregister(EstateService.class, estates);
            return null;
        });
        participant.reconcile();
        verify(estates, never()).report(any(), anyString(), anyLong(), any());
        server.getServicesManager().register(EstateService.class, estates, MockBukkit.createMockPlugin(), ServicePriority.Highest);
        participant.reconcile(); participant.reconcile();
        verify(estates, times(1)).report(eq(death), eq("magic-gear"), eq(1L), any());
    }

    @Test void registeredHourlyReconcileReportsOnlyChangedSuccessfulOutcomesAndSurvivesFailure() throws Exception {
        var value = decision(Recipient.FROZEN, 1);
        Server facade = mock(Server.class);
        BukkitScheduler scheduler = mock(BukkitScheduler.class);
        when(facade.getServicesManager()).thenReturn(server.getServicesManager());
        when(facade.getScheduler()).thenReturn(scheduler);
        when(plugin.getServer()).thenReturn(facade);
        CharacterGearEstate.start(plugin);
        var task = ArgumentCaptor.forClass(Runnable.class);
        verify(scheduler).runTaskTimer(eq(plugin), task.capture(), eq(1L), eq(72000L));
        assertTrue(Estates.participants().stream().anyMatch(p -> p instanceof CharacterGearEstate));
        task.getValue().run(); task.getValue().run();
        verify(estates, times(1)).report(eq(death), eq("magic-gear"), eq(1L), any());
        decision(Recipient.FROZEN, 2);
        doThrow(new IllegalStateException("report unavailable")).doNothing().when(estates).report(eq(death), eq("magic-gear"), eq(2L), any());
        task.getValue().run(); task.getValue().run();
        verify(estates, times(2)).report(eq(death), eq("magic-gear"), eq(2L), any());
        verify(logger).warning(contains("Gear estate held"));
        when(characters.deathId(from.characterId())).thenReturn(Optional.of(UUID.randomUUID()));
        doThrow(new IOException("legacy inventory unavailable")).when(store).migrate();
        task.getValue().run();
        views.verify(() -> GearStationStore.warning(any(IOException.class)));
        verify(estates, times(2)).report(eq(death), eq("magic-gear"), eq(2L), any());
    }
}
