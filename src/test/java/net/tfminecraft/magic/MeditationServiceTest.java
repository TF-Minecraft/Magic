package net.tfminecraft.magic;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.*;
import net.tfminecraft.interactiblefurniture.furniture.Furniture;
import net.tfminecraft.magic.meditation.*;
import net.tfminecraft.magic.profile.MagicProfileService;
import net.tfminecraft.magic.session.ResonanceSessionManager;
import net.tfminecraft.rpcharacters.RPCharacters;
import net.tfminecraft.rpcharacters.focus.FocusService;
import org.bukkit.*;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.*;
import org.bukkit.util.RayTraceResult;
import org.junit.jupiter.api.*;
import org.mockbukkit.mockbukkit.*;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import org.mockbukkit.mockbukkit.world.WorldMock;
import org.mockito.*;

class MeditationServiceTest {
  ServerMock server;
  PlayerMock player;
  WorldMock world;
  MeditationService service;
  MeditationCircle circle;
  MagicProfileService profiles;
  ResonanceSessionManager sessions;
  MockedStatic<MeditationCircle> detection;
  MockedStatic<Messages> messages;
  MockedConstruction<MeditationSession> created;

  @BeforeEach
  void setup() {
    server = MockBukkit.mock();
    world = spy(server.addSimpleWorld("meditate"));
    player = server.addPlayer();
    player.teleport(new Location(world, 0, 64, 0));
    Magic.plugin = mock(Magic.class);
    when(Magic.plugin.getServer()).thenReturn(server);
    when(Magic.plugin.isEnabled()).thenReturn(true);
    profiles = mock(MagicProfileService.class);
    sessions = new ResonanceSessionManager();
    service = new MeditationService(sessions, profiles);
    circle = mock(MeditationCircle.class);
    when(circle.getCenter()).thenReturn(player.getLocation());
    when(circle.getTotalPower()).thenReturn(10d);
    when(circle.stillIntact()).thenReturn(true);
    var yield = new MeditationSitYield(Map.of("a", 10d), null);
    when(circle.snapshotYield(anyLong(), any())).thenReturn(yield);
    when(circle.stampAndSnapshot(any(), anyLong())).thenReturn(yield);
    when(circle.canGainResonance(any(), any(), any())).thenReturn(true);
    detection = mockStatic(MeditationCircle.class);
    detection.when(() -> MeditationCircle.detect(any())).thenReturn(circle);
    messages = mockStatic(Messages.class, i -> i.getArgument(0));
    created =
        mockConstruction(MeditationSession.class, (m, c) -> when(m.getCircle()).thenReturn(circle));
  }

  @AfterEach
  void cleanup() {
    service.stop();
    created.close();
    messages.close();
    detection.close();
    Magic.plugin = null;
    MockBukkit.unmock();
  }

  void sit() {
    var chair = new org.mockbukkit.mockbukkit.entity.ArmorStandMock(server, UUID.randomUUID());
    chair.teleport(player.getLocation());
    assertTrue(chair.addPassenger(player));
  }

  void tick() {
    server.getScheduler().performOneTick();
  }

  MeditationSession start() {
    sit();
    service.start();
    tick();
    assertEquals(1, created.constructed().size());
    return created.constructed().getFirst();
  }

  @Test
  void sittingStartsTicksLocksAndLeavingPersistsAfterWindDown() {
    assertFalse(MeditationService.locksFurniture(null));
    var session = start();
    var post = mock(Furniture.class);
    when(post.getEntityId()).thenReturn(UUID.randomUUID());
    when(circle.getPedestals()).thenReturn(List.of(post));
    assertTrue(MeditationService.locksFurniture(post));
    assertFalse(MeditationService.locksFurniture(mock(Furniture.class)));
    var other = mock(Furniture.class);
    when(other.getEntityId()).thenReturn(UUID.randomUUID());
    assertFalse(MeditationService.locksFurniture(other));
    tick();
    verify(session).tick(player, sessions.get(player));
    player.leaveVehicle();
    tick();
    verify(session).beginWindDown();
    verify(session).end();
    verify(profiles).savePlayer(player);
    assertEquals("meditation.stop", player.nextMessage());
    assertFalse(MeditationService.locksFurniture(post));
  }

  @Test
  void noticesAreDeduplicatedUntilPlayerLeavesAndInvalidCircleCannotStart() {
    service.start();
    tick();
    assertTrue(created.constructed().isEmpty());
    sit();
    detection.when(() -> MeditationCircle.detect(any())).thenReturn(null);
    tick();
    assertTrue(created.constructed().isEmpty());
    detection.when(() -> MeditationCircle.detect(any())).thenReturn(circle);
    when(circle.getCenter()).thenReturn(new Location(world, 1, 64, 0));
    tick();
    assertTrue(created.constructed().isEmpty());
    when(circle.getCenter()).thenReturn(new Location(world, 0, 65, 0));
    tick();
    when(circle.getCenter()).thenReturn(new Location(world, 0, 64, 1));
    tick();
    when(circle.getCenter()).thenReturn(player.getLocation());
    when(circle.getTotalPower()).thenReturn(0d);
    tick();
    tick();
    assertEquals("meditation.no_artifacts", player.nextMessage());
    assertNull(player.nextMessage());
    player.leaveVehicle();
    tick();
    sit();
    tick();
    assertEquals("meditation.no_artifacts", player.nextMessage());
    player.leaveVehicle();
    tick();
    sit();
    when(circle.getTotalPower()).thenReturn(10d);
    when(circle.snapshotYield(anyLong(), any())).thenReturn(MeditationSitYield.empty());
    tick();
    assertEquals("meditation.nothing", player.nextMessage());
    player.leaveVehicle();
    tick();
    sit();
    when(circle.snapshotYield(anyLong(), any()))
        .thenReturn(new MeditationSitYield(Map.of("a", 1d), null));
    when(circle.canGainResonance(any(), any(), any())).thenReturn(false);
    tick();
    assertEquals("meditation.nothing", player.nextMessage());
    player.disconnect();
    tick();
  }

  @Test
  void disconnectedAndWindingSessionsFinishOnlyAfterLastOrb() {
    var session = start();
    when(session.isWindingDown()).thenReturn(true);
    when(session.hasActiveOrbs()).thenReturn(true);
    tick();
    verify(session, never()).end();
    when(session.hasActiveOrbs()).thenReturn(false);
    player.disconnect();
    tick();
    verify(session).end();
    verify(profiles, never()).savePlayer(any());
  }

  @Test
  void brokenCircleBeginsWindDownAndStopEndsActiveSessions() {
    var session = start();
    when(circle.stillIntact()).thenReturn(false);
    when(session.hasActiveOrbs()).thenReturn(true);
    tick();
    verify(session).beginWindDown();
    service.stop();
    verify(session).end();
    service.start();
    service.stop();
  }

  @Test
  void swingsRespectGatesFocusAndCompletion() {
    var session = start();
    var orb = mock(MeditationOrb.class);
    when(orb.getLocation(anyInt())).thenAnswer(i -> player.getEyeLocation().add(0, 0, 2));
    when(session.livingOrbs()).thenReturn(List.of(orb));
    when(session.claimSwing(anyLong())).thenReturn(true);
    when(session.canHit(anyLong())).thenReturn(true);
    doReturn(null).when(world).rayTraceBlocks(any(), any(), anyDouble(), any(), anyBoolean());
    try (var rp = mockStatic(RPCharacters.class)) {
      when(session.claimTiredNotice()).thenReturn(true);
      service.onSwing(new PlayerAnimationEvent(player));
      assertEquals("meditation.tired", player.nextMessage());
      when(session.claimTiredNotice()).thenReturn(false);
      service.onSwing(new PlayerAnimationEvent(player));
      assertNull(player.nextMessage());
      var focus = mock(FocusService.class);
      rp.when(RPCharacters::getFocusService).thenReturn(focus);
      service.onSwing(new PlayerAnimationEvent(player));
      verify(focus).trySpend(player, MeditationCache.mentalCostPerHit);
      when(focus.trySpend(any(), anyInt())).thenReturn(true);
      service.onInteract(new PlayerInteractEvent(player, Action.LEFT_CLICK_AIR, null, null, null));
      verify(session).markHit(anyLong());
      when(session.onOrbHit(any(), any(), any(), any())).thenReturn(true);
      service.onInteract(
          new PlayerInteractEvent(player, Action.LEFT_CLICK_BLOCK, null, null, null));
      verify(session).playComplete(player);
      verify(session).beginWindDown();
      assertEquals("meditation.complete", player.nextMessage());
      service.onInteract(new PlayerInteractEvent(player, Action.RIGHT_CLICK_AIR, null, null, null));
      service.onSwing(new PlayerAnimationEvent(player, PlayerAnimationType.OFF_ARM_SWING));
      when(session.isWindingDown()).thenReturn(true);
      service.onSwing(new PlayerAnimationEvent(player));
      when(session.isWindingDown()).thenReturn(false);
      when(session.claimSwing(anyLong())).thenReturn(false);
      service.onSwing(new PlayerAnimationEvent(player));
      when(session.claimSwing(anyLong())).thenReturn(true);
      when(session.canHit(anyLong())).thenReturn(false);
      service.onSwing(new PlayerAnimationEvent(player));
    }
  }

  @Test
  void hitscanRejectsMissingFarBehindOffAxisAndOccludedOrbs() {
    var session = start();
    when(session.claimSwing(anyLong())).thenReturn(true);
    when(session.canHit(anyLong())).thenReturn(true);
    var orb = mock(MeditationOrb.class);
    when(session.livingOrbs()).thenReturn(List.of(orb));
    doReturn(null).when(world).rayTraceBlocks(any(), any(), anyDouble(), any(), anyBoolean());
    for (Location location :
        Arrays.asList(
            null,
            new Location(null, 0, 0, 0),
            new Location(server.addSimpleWorld("other"), 0, 64, 2),
            player.getEyeLocation().add(0, 0, -2),
            player.getEyeLocation().add(0, 0, 100),
            player.getEyeLocation().add(10, 0, 2))) {
      when(orb.getLocation(anyInt())).thenReturn(location);
      service.onSwing(new PlayerAnimationEvent(player));
    }
    when(orb.getLocation(anyInt())).thenAnswer(i -> player.getEyeLocation().add(0, 0, 2));
    doReturn(new RayTraceResult(player.getEyeLocation().add(0, 0, 1).toVector()))
        .when(world)
        .rayTraceBlocks(any(), any(), anyDouble(), any(), anyBoolean());
    service.onSwing(new PlayerAnimationEvent(player));
    verify(session, never()).markHit(anyLong());
  }

  Object call(String name, Class<?>[] types, Object... args) throws Exception {
    var method = MeditationService.class.getDeclaredMethod(name, types);
    method.setAccessible(true);
    return method.invoke(service, args);
  }

  @Test
  void lifecycleHandlesDisconnectedSessionsAbsentProfilesAndMutablePedestalLists()
      throws Exception {
    call("tryStart", new Class[] {org.bukkit.entity.Player.class}, player);
    assertTrue(created.constructed().isEmpty());
    var session = start();
    assertFalse(MeditationService.locksFurniture(null));
    var post = mock(Furniture.class);
    when(post.getEntityId()).thenReturn(UUID.randomUUID());
    when(circle.getPedestals()).thenReturn(Arrays.asList(null, post));
    assertTrue(MeditationService.locksFurniture(post));
    player.disconnect();
    tick();
    verify(session).beginWindDown();
    verify(session).end();
    player = server.addPlayer();
    player.teleport(new Location(world, 0, 64, 0));
    when(circle.getCenter()).thenReturn(player.getLocation());
    service.stop();
    service = new MeditationService(sessions, null);
    sit();
    service.start();
    tick();
    var next = created.constructed().getLast();
    player.leaveVehicle();
    tick();
    verify(next).end();
    call(
        "finishSession",
        new Class[] {UUID.class, org.bukkit.entity.Player.class},
        player.getUniqueId(),
        player);
  }

  @Test
  void movingAwayWhileSeatedInvalidatesTheSession() {
    var session = start();
    when(circle.getCenter()).thenReturn(player.getLocation().add(2, 0, 0));
    tick();
    verify(session).beginWindDown();
  }

  @Test
  void sessionSnapshotToleratesServiceStoppingFromAnOrbCallback() {
    var first = start();
    var other = server.addPlayer();
    other.teleport(player.getLocation());
    var chair = new org.mockbukkit.mockbukkit.entity.ArmorStandMock(server, UUID.randomUUID());
    chair.teleport(other.getLocation());
    assertTrue(chair.addPassenger(other));
    tick();
    assertEquals(2, created.constructed().size());
    var second = created.constructed().getLast();
    org.mockito.stubbing.Answer<Void> stop =
        a -> {
          player.leaveVehicle();
          other.leaveVehicle();
          service.stop();
          return null;
        };
    doAnswer(stop).when(first).tick(any(), any());
    doAnswer(stop).when(second).tick(any(), any());
    tick();
    verify(first).end();
    verify(second).end();
  }

  @Test
  void swingsWithoutSessionAndBlocksBehindTheOrbAreHandled() {
    service.onSwing(new PlayerAnimationEvent(player));
    var session = start();
    when(session.claimSwing(anyLong())).thenReturn(true);
    when(session.canHit(anyLong())).thenReturn(true);
    var orb = mock(MeditationOrb.class);
    when(orb.getLocation(anyInt())).thenAnswer(a -> player.getEyeLocation().add(0, 0, 2));
    when(session.livingOrbs()).thenReturn(List.of(orb));
    doReturn(new RayTraceResult(player.getEyeLocation().add(0, 0, 3).toVector()))
        .when(world)
        .rayTraceBlocks(any(), any(), anyDouble(), any(), anyBoolean());
    try (var rp = mockStatic(RPCharacters.class)) {
      service.onSwing(new PlayerAnimationEvent(player));
      verify(session).claimTiredNotice();
    }
  }
}
