package net.tfminecraft.magic;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.*;
import net.tfminecraft.magic.gear.*;
import net.tfminecraft.magic.gear.orb.*;
import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.*;
import org.bukkit.inventory.*;
import org.bukkit.util.*;
import org.junit.jupiter.api.*;
import org.mockito.*;

class GearOrbServiceCoverageTest extends GearCoverageSupport {
  World world;
  Location station;
  MockedStatic<GearStationStore> stores;
  MockedConstruction<GearOrbSession> sessions;

  @BeforeEach
  void setupService() {
    world = mock(World.class);
    when(world.getName()).thenReturn("world");
    station = new Location(world, 0, 0, 0);
    stores = mockStatic(GearStationStore.class);
    stores.when(() -> GearStationStore.key(any())).thenCallRealMethod();
    sessions =
        mockConstruction(
            GearOrbSession.class,
            (s, c) -> {
              when(s.getStation()).thenReturn(((Location) c.arguments().get(0)).clone());
              when(s.getPlayerId()).thenReturn((UUID) c.arguments().get(1));
              when(s.displayPoint()).thenReturn(station);
              when(s.capturedFill()).thenReturn(Map.of("fire", 10d));
              when(s.goodTarget()).thenReturn(2);
            });
  }

  @AfterEach
  void cleanupService() {
    GearOrbService.stop();
    sessions.close();
    stores.close();
  }

  GearOrbSession begin(Player p) {
    assertTrue(GearOrbService.begin(p, station, Map.of(), "fire", 1));
    return sessions.constructed().getLast();
  }

  @Test
  void startAbortAndOwnershipFollowStationLifecycle() {
    var p = server.addPlayer();
    assertFalse(GearOrbService.isActive(null));
    assertFalse(GearOrbService.isActive(station));
    assertNull(GearOrbService.sessionOwner(null));
    assertNull(GearOrbService.sessionOwner(station));
    assertFalse(GearOrbService.begin(p, null, Map.of(), null, 1));
    assertFalse(GearOrbService.begin(p, new Location(null, 0, 0, 0), Map.of(), null, 1));
    var s = begin(p);
    assertFalse(GearOrbService.begin(p, station, Map.of(), null, 1));
    assertTrue(GearOrbService.isActive(station));
    assertEquals(p.getUniqueId(), GearOrbService.sessionOwner(station));
    GearOrbService.abort(null);
    GearOrbService.abort(station);
    verify(s).end();
    assertFalse(GearOrbService.isActive(station));
    var occupancy = mock(GearStationStore.Occupancy.class);
    stores.when(() -> GearStationStore.get(station)).thenReturn(occupancy);
    GearOrbService.abort(station);
    verify(occupancy).setOrbSessionActive(false);
    GearOrbService.start();
    GearOrbService.start();
    server.getScheduler().performOneTick();
    GearOrbService.stop();
  }

  @Test
  void timerHandlesOfflinePlayersFailedSessionsAndMissingStationContents() {
    var p = server.addPlayer();
    var s = begin(p);
    GearOrbService.start();
    verify(s).end();
    s = begin(p);
    server.getScheduler().performOneTick();
    verify(s).tick(p);
    when(s.isFinished()).thenReturn(true);
    var occupancy = mock(GearStationStore.Occupancy.class);
    stores.when(() -> GearStationStore.get(station)).thenReturn(occupancy);
    server.getScheduler().performOneTick();
    verify(occupancy).setOrbSessionActive(false);
    s = begin(p);
    doThrow(new IllegalStateException("broken")).when(s).tick(p);
    server.getScheduler().performOneTick();
    verify(s).end();
    s = begin(p);
    p.disconnect();
    server.getScheduler().performOneTick();
    verify(s).end();
  }

  @Test
  void completionPersistsCaptureRiftAndSocketLockDecisions() throws Exception {
    var p = server.addPlayer();
    var weapon = item();
    var updated = item();
    var occupancy = mock(GearStationStore.Occupancy.class);
    when(occupancy.getItem()).thenReturn(weapon);
    stores.when(() -> GearStationStore.get(station)).thenReturn(occupancy);
    try (var req = mockStatic(WeaponRequirement.class);
        var rift = mockStatic(WeaponRift.class);
        var provenance = mockStatic(GearProvenance.class);
        var builder = mockStatic(GearItemBuilder.class);
        var lore = mockStatic(WeaponLore.class);
        var chat = mockStatic(WeaponAttunementChat.class)) {
      var requirement = mock(WeaponRequirement.class);
      req.when(() -> WeaponRequirement.fromItem(weapon)).thenReturn(requirement);
      builder.when(() -> GearItemBuilder.rewriteSockets(weapon, 2)).thenReturn(updated);
      lore.when(() -> WeaponLore.updateItem(weapon)).thenReturn(updated);
      for (int mode = 0; mode < 4; mode++) {
        var s = begin(p);
        when(s.capturedFraction()).thenReturn(.75);
        when(s.riftDelta()).thenReturn(5);
        req.when(() -> WeaponRequirement.highestBand(anyMap())).thenReturn(mode == 0 ? 0 : 2);
        provenance.when(() -> GearProvenance.socketsLocked(weapon)).thenReturn(mode == 2);
        rift.when(() -> WeaponRift.get(weapon)).thenReturn(mode == 0 ? 0 : 20);
        invoke(
            GearOrbService.class,
            "complete",
            new Class[] {GearOrbSession.class, boolean.class},
            s,
            mode != 3);
        verify(s).end();
        if (mode != 3) verify(s).playComplete();
      }
      verify(requirement, times(4)).mergeAmounts(Map.of("fire", 10d));
      verify(requirement, times(4)).persist(weapon);
      builder.verify(() -> GearItemBuilder.rewriteSockets(weapon, 2), times(2));
      stores.verify(() -> GearStationStore.update(station, updated), times(4));
      chat.verify(() -> WeaponAttunementChat.sendPostChargeSummary(p, updated), times(3));
    }
  }

  GearOrb orb(Location location) {
    var orb = new GearOrb(location, true, 0, 1, 0, 0, 0, 0, 20);
    orb.setLocation(location);
    return orb;
  }

  @Test
  void hitscanUsesNearestVisibleOrbAndSkipsInvalidHistory() throws Exception {
    var p = mock(Player.class);
    when(p.getEyeLocation()).thenReturn(new Location(null, 0, 0, 0));
    var session = mock(GearOrbSession.class);
    assertNull(
        invoke(
            GearOrbService.class,
            "hitscan",
            new Class[] {Player.class, GearOrbSession.class},
            p,
            session));
    when(p.getEyeLocation()).thenReturn(station);
    var other = mock(World.class);
    var noHistory = new GearOrb(station, true, 0, 1, 0, 0, 0, 0, 20);
    when(session.livingOrbs())
        .thenReturn(
            List.of(
                noHistory,
                orb(new Location(null, 0, 0, 2)),
                orb(new Location(other, 0, 0, 2)),
                orb(new Location(world, 0, 0, -1)),
                orb(new Location(world, 0, 0, 99)),
                orb(new Location(world, 2, 0, 2))));
    assertNull(
        invoke(
            GearOrbService.class,
            "hitscan",
            new Class[] {Player.class, GearOrbSession.class},
            p,
            session));
    var near = orb(new Location(world, 0, 0, 2));
    when(session.livingOrbs()).thenReturn(List.of(near, orb(new Location(world, 0, 0, 3))));
    assertSame(
        near,
        invoke(
            GearOrbService.class,
            "hitscan",
            new Class[] {Player.class, GearOrbSession.class},
            p,
            session));
    when(world.rayTraceBlocks(any(), any(), anyDouble(), any(), anyBoolean()))
        .thenReturn(new RayTraceResult(new org.bukkit.util.Vector(0, 0, 1)));
    assertNull(
        invoke(
            GearOrbService.class,
            "hitscan",
            new Class[] {Player.class, GearOrbSession.class},
            p,
            session));
    when(world.rayTraceBlocks(any(), any(), anyDouble(), any(), anyBoolean()))
        .thenReturn(new RayTraceResult(new org.bukkit.util.Vector(0, 0, 3)));
    assertSame(
        near,
        invoke(
            GearOrbService.class,
            "hitscan",
            new Class[] {Player.class, GearOrbSession.class},
            p,
            session));
  }

  @Test
  void swingAndInteractionEventsDeduplicateAndRespectHitCooldown() throws Exception {
    var listener = new GearOrbService();
    var p = server.addPlayer();
    p.setSneaking(true);
    listener.onInteract(
        new PlayerInteractEvent(
            p, Action.RIGHT_CLICK_AIR, null, null, org.bukkit.block.BlockFace.UP));
    listener.onSwing(new PlayerAnimationEvent(p, PlayerAnimationType.OFF_ARM_SWING));
    listener.onSwing(new PlayerAnimationEvent(p, PlayerAnimationType.ARM_SWING));
    p.setSneaking(false);
    listener.onSwing(new PlayerAnimationEvent(p, PlayerAnimationType.ARM_SWING));
    var s = begin(p);
    listener.onSwing(new PlayerAnimationEvent(p, PlayerAnimationType.ARM_SWING));
    when(s.claimSwing(anyLong())).thenReturn(true);
    listener.onSwing(new PlayerAnimationEvent(p, PlayerAnimationType.ARM_SWING));
    when(s.canHit(anyLong())).thenReturn(true);
    when(s.livingOrbs()).thenReturn(List.of());
    listener.onInteract(
        new PlayerInteractEvent(
            p, Action.LEFT_CLICK_BLOCK, null, null, org.bukkit.block.BlockFace.UP));
    var fake = mock(Player.class);
    when(fake.getUniqueId()).thenReturn(p.getUniqueId());
    when(fake.getEyeLocation()).thenReturn(station);
    var hit = orb(new Location(world, 0, 0, 2));
    when(s.livingOrbs()).thenReturn(List.of(hit));
    listener.onInteract(
        new PlayerInteractEvent(
            fake, Action.LEFT_CLICK_AIR, null, null, org.bukkit.block.BlockFace.UP));
    verify(s).onHit(fake, hit);
    verify(s).markHit(anyLong());
    var different = server.addPlayer();
    listener.onSwing(new PlayerAnimationEvent(different, PlayerAnimationType.ARM_SWING));
  }

  @Test
  void completionHandlesDisconnectedPlayers() throws Exception {
    var player = mock(Player.class);
    var id = UUID.randomUUID();
    when(player.getUniqueId()).thenReturn(id);
    var weapon = item();
    var occupancy = mock(GearStationStore.Occupancy.class);
    when(occupancy.getItem()).thenReturn(weapon);
    stores.when(() -> GearStationStore.get(station)).thenReturn(occupancy);
    try (var bukkit = mockStatic(Bukkit.class, CALLS_REAL_METHODS);
        var req = mockStatic(WeaponRequirement.class);
        var lore = mockStatic(WeaponLore.class);
        var chat = mockStatic(WeaponAttunementChat.class)) {
      var requirement = mock(WeaponRequirement.class);
      req.when(() -> WeaponRequirement.fromItem(weapon)).thenReturn(requirement);
      lore.when(() -> WeaponLore.updateItem(weapon)).thenReturn(weapon);
      for (int mode = 0; mode < 2; mode++) {
        var session = begin(player);
        bukkit.when(() -> Bukkit.getPlayer(id)).thenReturn(mode == 0 ? null : player);
        when(player.isOnline()).thenReturn(false);
        invoke(
            GearOrbService.class,
            "complete",
            new Class[] {GearOrbSession.class, boolean.class},
            session,
            true);
        verify(session).playComplete();
      }
      var session = begin(player);
      when(player.isOnline()).thenReturn(false);
      invoke(GearOrbService.class, "tick", new Class[] {});
      verify(session).end();
    }
  }

  @Test
  void cancellationDuringOneSessionSkipsRemovedSessionsInSnapshot() throws Exception {
    var p = server.addPlayer();
    for (boolean stopping : new boolean[] {false, true}) {
      Location second = new Location(world, 1, 0, 0);
      var first = begin(p);
      assertTrue(GearOrbService.begin(p, second, Map.of(), "fire", 1));
      var last = sessions.constructed().getLast();
      var f = GearOrbService.class.getDeclaredField("SESSIONS");
      f.setAccessible(true);
      var map = (Map<String, GearOrbSession>) f.get(null);
      var iterator = new ArrayList<>(map.values());
      var one = iterator.getFirst();
      var two = iterator.getLast();
      if (stopping) {
        stores
            .when(() -> GearStationStore.get(one.getStation()))
            .thenAnswer(
                i -> {
                  GearOrbService.abort(two.getStation());
                  return null;
                });
        GearOrbService.stop();
        stores.when(() -> GearStationStore.get(one.getStation())).thenReturn(null);
      } else {
        doAnswer(
                i -> {
                  GearOrbService.abort(two.getStation());
                  return null;
                })
            .when(one)
            .tick(p);
        invoke(GearOrbService.class, "tick", new Class[] {});
        verify(two, never()).tick(any());
        GearOrbService.abort(one.getStation());
      }
      verify(two).end();
    }
  }
}
