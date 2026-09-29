package net.tfminecraft.magic;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.*;
import net.tfminecraft.interactiblefurniture.furniture.Furniture;
import net.tfminecraft.magic.attunement.AuraLog;
import net.tfminecraft.magic.meditation.*;
import net.tfminecraft.magic.profile.MagicProfileService;
import net.tfminecraft.magic.registry.ElementRegistry;
import net.tfminecraft.magic.session.ResonanceSession;
import org.junit.jupiter.api.*;
import org.mockbukkit.mockbukkit.*;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

class MeditationSessionTest {
  ServerMock server;
  PlayerMock player;
  MeditationCircle circle;
  Furniture post;
  UUID source;
  ResonanceSession resonance;

  @BeforeEach
  void setup() {
    server = MockBukkit.mock();
    player = server.addPlayer();
    source = UUID.randomUUID();
    post = mock(Furniture.class);
    when(post.getEntityId()).thenReturn(source);
    var center = player.getLocation();
    when(post.getOriginBlockLocation()).thenReturn(Optional.of(center));
    when(post.getLoc()).thenReturn(center);
    circle = mock(MeditationCircle.class);
    when(circle.getCenter()).thenReturn(center);
    when(circle.getPedestals()).thenReturn(List.of(post));
    when(circle.getArtifactPedestals()).thenReturn(List.of(post));
    when(circle.artifactFor(source)).thenReturn(new MeditationCache.ArtifactDef("fire", 5));
    when(circle.artifactIdOn(post)).thenReturn("artifact");
    when(circle.elementPower("fire")).thenReturn(5.);
    ElementRegistry.clear();
    ElementRegistry.register(DomainTest.element("fire"));
    Cache.defaultEquilibrium = 0;
    resonance = new ResonanceSession();
    MeditationCache.orbIntroTicks = 2;
    MeditationCache.orbLifetimeTicks = 20;
    MeditationCache.resonancePerHit = 1;
    MeditationCache.flowEquilibriumMin = .02;
    MeditationCache.flowEquilibriumMax = .02;
    MeditationCache.surgeEquilibriumMin = .03;
    MeditationCache.surgeEquilibriumMax = .03;
    MeditationCache.surgeLockSeconds = 1;
    AuraLog.configure(false, false, null);
  }

  @AfterEach
  void cleanup() {
    MockBukkit.unmock();
    ElementRegistry.clear();
    MeditationCache.orbIntroTicks = 20;
    MeditationCache.orbLifetimeTicks = 100;
    MeditationCache.resonancePerHit = 4;
    MeditationCache.flowEquilibriumMin = .02;
    MeditationCache.flowEquilibriumMax = .06;
    MeditationCache.surgeEquilibriumMin = .02;
    MeditationCache.surgeEquilibriumMax = .10;
    MeditationCache.surgeLockSeconds = 10;
  }

  MeditationSession session(double cap) {
    return new MeditationSession(
        player,
        circle,
        new MeditationSitYield(Map.of("artifact", cap), Map.of("artifact", 1)),
        resonance);
  }

  void activate(MeditationSession session) {
    var starter = session.livingOrbs().getFirst();
    assertTrue(starter.isStarter());
    assertFalse(session.onOrbHit(player, starter, resonance, null));
    for (int i = 0; i < 5; i++) session.tick(player, resonance);
  }

  @Test
  void starterFollowsPlayerAndDebouncesSwingsHitsAndNotices() {
    var session = session(2);
    assertSame(circle, session.getCircle());
    assertEquals(MeditationSession.Phase.START_ORB, session.getPhase());
    assertTrue(session.hasActiveOrbs());
    assertFalse(session.claimSwing(0));
    assertTrue(session.claimSwing(1));
    assertFalse(session.claimSwing(1));
    assertFalse(session.canHit(3));
    assertTrue(session.canHit(4));
    session.markHit(4);
    assertFalse(session.canHit(5));
    assertTrue(session.canHit(8));
    assertTrue(session.claimTiredNotice());
    assertFalse(session.claimTiredNotice());
    var before = session.livingOrbs().getFirst().getLocation().clone();
    player.teleport(player.getLocation().add(2, 0, 0));
    session.tick(player, resonance);
    assertNotEquals(before, session.livingOrbs().getFirst().getLocation());
    session.beginWindDown();
    assertTrue(session.isWindingDown());
    assertFalse(session.hasActiveOrbs());
    session.beginWindDown();
    session.tick(player, resonance);
    assertTrue(session.livingOrbs().isEmpty());
    session.end();
    assertFalse(session.isWindingDown());
  }

  @Test
  void hitsCreditResonanceAndNeverExceedCircleTotalOrYield() {
    var session = session(2);
    activate(session);
    assertEquals(MeditationSession.Phase.RUNNING, session.getPhase());
    assertEquals(2, session.livingOrbs().size());
    var profile = mock(MagicProfileService.class);
    var flow =
        session.livingOrbs().stream().filter(MeditationOrb::isFlow).findFirst().orElseThrow();
    assertFalse(session.onOrbHit(player, flow, resonance, profile));
    assertEquals(1, resonance.getResonance("fire"));
    assertEquals(.02, resonance.getEquilibrium());
    var surge = session.livingOrbs().getFirst();
    assertFalse(surge.isFlow());
    assertTrue(session.onOrbHit(player, surge, resonance, profile));
    assertEquals(2, resonance.getResonance("fire"));
    assertEquals(-.01, resonance.getEquilibrium());
    verify(profile, times(2)).savePlayer(player);
    session.playComplete(player);
    session.end();
    assertFalse(session.hasActiveOrbs());
  }

  @Test
  void exhaustedElementsNeverSpawnAndCloseWhenResonanceReachesCeiling() {
    resonance.setResonance("fire", 5);
    var capped = session(10);
    activate(capped);
    assertTrue(capped.livingOrbs().isEmpty());
    resonance.setResonance("fire", 4.5);
    var partial = session(10);
    activate(partial);
    var orb = partial.livingOrbs().getFirst();
    assertTrue(partial.onOrbHit(player, orb, resonance, null));
    assertEquals(5, resonance.getResonance("fire"));
    for (int i = 0; i < 50; i++) partial.tick(player, resonance);
    assertTrue(partial.livingOrbs().isEmpty());
    partial.end();
  }

  @Test
  void orbExpirationAndWindDownReturnToPedestalWithoutReward() {
    var session = session(10);
    activate(session);
    for (int i = 0; i < 4; i++) session.tick(player, resonance);
    session.beginWindDown();
    assertTrue(session.isWindingDown());
    assertTrue(session.hasActiveOrbs());
    assertTrue(session.livingOrbs().isEmpty());
    for (int i = 0; i < 4; i++) session.tick(player, resonance);
    assertFalse(session.hasActiveOrbs());
    assertEquals(0, resonance.getResonance("fire"));
    session.end();
    var expiry = session(10);
    activate(expiry);
    for (int i = 0; i < 25; i++) expiry.tick(player, resonance);
    assertTrue(expiry.livingOrbs().isEmpty());
    assertEquals(0, resonance.getResonance("fire"));
    expiry.end();
  }

  @Test
  void surgeLockRetintsRemainingOrbsAndLaterAnnouncesRecovery() throws Exception {
    var session = session(10);
    activate(session);
    var surge = session.livingOrbs().stream().filter(o -> !o.isFlow()).findFirst().orElseThrow();
    session.onOrbHit(player, surge, resonance, null);
    assertTrue(session.livingOrbs().stream().noneMatch(MeditationOrb::isFlow));
    session.tick(player, resonance);
    assertNotNull(player.nextMessage());
    Thread.sleep(1100);
    session.tick(player, resonance);
    assertNotNull(player.nextMessage());
    session.end();
  }

  @Test
  void missingYieldAndMissingPedestalItemCannotGrantResonance() {
    var empty = new MeditationSession(player, circle, null, null);
    empty.tick(player, null);
    empty.end();
    when(circle.artifactIdOn(post)).thenReturn(null);
    var missing = session(5);
    activate(missing);
    assertTrue(missing.livingOrbs().isEmpty());
    missing.end();
  }

  @Test
  void multiplePedestalsSpawnOneOrbEachAndWindDownSkipsConsumedOrbs() {
    var posts = new ArrayList<Furniture>();
    var caps = new LinkedHashMap<String, Double>();
    for (int i = 0; i < 3; i++) {
      var p = mock(Furniture.class);
      var id = UUID.randomUUID();
      var at = player.getLocation().add(i, 0, 0);
      when(p.getEntityId()).thenReturn(id);
      when(p.getOriginBlockLocation()).thenReturn(Optional.of(at));
      when(p.getLoc()).thenReturn(at);
      when(circle.artifactFor(id)).thenReturn(new MeditationCache.ArtifactDef("fire", 10));
      when(circle.artifactIdOn(p)).thenReturn("a" + i);
      caps.put("a" + i, 10d);
      posts.add(p);
    }
    when(circle.getPedestals()).thenReturn(posts);
    when(circle.getArtifactPedestals()).thenReturn(posts);
    when(circle.elementPower("fire")).thenReturn(30d);
    long oldSpawn = MeditationCache.spawnIntervalTicks;
    int oldLife = MeditationCache.orbLifetimeTicks;
    try {
      MeditationCache.spawnIntervalTicks = 5;
      MeditationCache.orbLifetimeTicks = 100;
      var session =
          new MeditationSession(player, circle, new MeditationSitYield(caps, null), resonance);
      activate(session);
      for (int i = 0; i < 15; i++) session.tick(player, resonance);
      assertEquals(3, session.livingOrbs().size());
      assertEquals(
          3, session.livingOrbs().stream().map(MeditationOrb::getSourceId).distinct().count());
      var hit = session.livingOrbs().getFirst();
      session.onOrbHit(player, hit, resonance, null);
      session.beginWindDown();
      for (int i = 0; i < 25; i++) session.tick(player, resonance);
      assertFalse(session.hasActiveOrbs());
      for (double equilibrium : new double[] {-100, 100}) {
        resonance.setEquilibrium(equilibrium);
        var mixed =
            new MeditationSession(player, circle, new MeditationSitYield(caps, null), resonance);
        activate(mixed);
        for (int i = 0; i < 15; i++) mixed.tick(player, resonance);
        assertEquals(
            equilibrium < 0 ? 1 : 2,
            mixed.livingOrbs().stream().filter(MeditationOrb::isFlow).count());
        mixed.end();
      }
    } finally {
      MeditationCache.spawnIntervalTicks = oldSpawn;
      MeditationCache.orbLifetimeTicks = oldLife;
    }
  }

  @Test
  void itemRemovedBetweenOrbSpawnAndHitDoesNotCreditResonance() {
    var profiles = mock(MagicProfileService.class);
    var session = session(10);
    activate(session);
    var unknown =
        new MeditationOrb(
            player.getLocation(),
            true,
            false,
            0,
            player.getLocation(),
            0,
            0,
            0,
            0,
            0,
            0,
            UUID.randomUUID());
    session.onOrbHit(player, unknown, resonance, profiles);
    assertEquals(0, resonance.getResonance("fire"));
    verify(profiles).savePlayer(player);
    when(circle.artifactFor(source)).thenReturn(new MeditationCache.ArtifactDef("fire", 5));
    when(circle.artifactIdOn(post)).thenReturn(null);
    session.onOrbHit(player, session.livingOrbs().getFirst(), resonance, profiles);
    assertEquals(0, resonance.getResonance("fire"));
    verify(profiles, times(2)).savePlayer(player);
    session.end();
  }

  @Test
  void sessionsWithoutResonanceStillAllowVisualWindDownAndMissingSourcesClose() {
    var visual =
        new MeditationSession(
            player, circle, new MeditationSitYield(Map.of("artifact", 2d), null), null);
    var starter = visual.livingOrbs().getFirst();
    visual.onOrbHit(player, starter, resonance, null);
    for (int i = 0; i < 5; i++) visual.tick(player, resonance);
    visual.onOrbHit(player, visual.livingOrbs().getFirst(), null, null);
    visual.beginWindDown();
    visual.end();
    when(circle.artifactFor(source)).thenReturn(new MeditationCache.ArtifactDef("fire", 5));
    when(circle.artifactIdOn(post)).thenReturn(null);
    var missing = session(3);
    activate(missing);
    assertTrue(missing.livingOrbs().isEmpty());
    missing.end();
  }

  @Test
  void externallyRaisedResonanceClosesPendingOrbsWithoutAdditionalCredit() {
    var session = session(10);
    activate(session);
    resonance.setResonance("fire", 5);
    session.onOrbHit(player, session.livingOrbs().getFirst(), resonance, null);
    assertEquals(5, resonance.getResonance("fire"));
    session.end();
  }

  @Test
  void lockedElementsAndZeroYieldNeverOfferAnOrb() {
    var config = new org.bukkit.configuration.file.YamlConfiguration();
    config.set("permission", "magic.secret");
    ElementRegistry.register(new net.tfminecraft.magic.model.ElementDef("fire", config));
    var denied = session(10);
    activate(denied);
    assertTrue(denied.livingOrbs().isEmpty());
    denied.end();
    ElementRegistry.clear();
    var noYield = session(0);
    activate(noYield);
    assertTrue(noYield.livingOrbs().isEmpty());
    noYield.end();
    var unregistered = session(2);
    activate(unregistered);
    unregistered.onOrbHit(player, unregistered.livingOrbs().getFirst(), resonance, null);
    assertEquals(0, resonance.getResonance("fire"));
    unregistered.end();
  }

  @Test
  void repeatedSurgeHitsExtendLockAndSpawnOnlySurgeDuringIt() {
    long oldSpawn = MeditationCache.spawnIntervalTicks;
    try {
      MeditationCache.spawnIntervalTicks = 5;
      var session = session(10);
      activate(session);
      var surge = session.livingOrbs().stream().filter(o -> !o.isFlow()).findFirst().orElseThrow();
      session.onOrbHit(player, surge, resonance, null);
      session.onOrbHit(player, session.livingOrbs().getFirst(), resonance, null);
      assertNotNull(player.nextMessage());
      assertNull(player.nextMessage());
      for (int i = 0; i < 6; i++) session.tick(player, resonance);
      assertFalse(session.livingOrbs().isEmpty());
      assertTrue(session.livingOrbs().stream().noneMatch(MeditationOrb::isFlow));
      session.end();
    } finally {
      MeditationCache.spawnIntervalTicks = oldSpawn;
    }
  }

  @Test
  void vanishedPedestalLocationsSkipSpawnAndCompletionEffects() {
    when(post.getOriginBlockLocation()).thenReturn(Optional.empty());
    when(post.getLoc()).thenReturn(null);
    var session = session(10);
    activate(session);
    assertTrue(session.livingOrbs().isEmpty());
    session.playComplete(player);
    session.end();
  }

  @Test
  void savedLocationsWithoutWorldSkipEffectsAndReturnSafely() {
    var session = session(10);
    activate(session);
    var orbs = session.livingOrbs();
    orbs.getFirst().getSpawnLocation().setWorld(null);
    orbs.get(1).getSpawnLocation().setWorld(null);
    orbs.get(1).getLocation().setWorld(null);
    session.beginWindDown();
    session.tick(null, null);
    assertTrue(orbs.get(1).isConsumed());
    for (int i = 0; i < 5; i++) session.tick(null, null);
    assertFalse(session.hasActiveOrbs());
    var complete = session(10);
    circle.getCenter().setWorld(null);
    complete.playComplete(player);
    complete.onOrbHit(player, complete.livingOrbs().getFirst(), resonance, null);
    complete.end();
  }

  @Test
  void hitEffectsIgnoreInvalidatedSavedWorldsAndOneRemainingMixSpawnsItsCounterpart() {
    long oldSpawn = MeditationCache.spawnIntervalTicks;
    try {
      MeditationCache.spawnIntervalTicks = 5;
      var session = session(10);
      activate(session);
      var flow =
          session.livingOrbs().stream().filter(MeditationOrb::isFlow).findFirst().orElseThrow();
      flow.getLocation().setWorld(null);
      session.onOrbHit(player, flow, resonance, null);
      for (int i = 0; i < 5; i++) session.tick(player, resonance);
      assertEquals(2, session.livingOrbs().size());
      var next =
          session.livingOrbs().stream().filter(MeditationOrb::isFlow).findFirst().orElseThrow();
      next.getSpawnLocation().setWorld(null);
      session.onOrbHit(player, next, resonance, null);
      session.end();
    } finally {
      MeditationCache.spawnIntervalTicks = oldSpawn;
    }
  }

  @Test
  void closingOneSchoolSkipsOtherSchoolsAndPedestalsWhoseItemsWereRemoved() {
    var empty = mock(Furniture.class);
    var emptyId = UUID.randomUUID();
    when(empty.getEntityId()).thenReturn(emptyId);
    var water = mock(Furniture.class);
    var waterId = UUID.randomUUID();
    when(water.getEntityId()).thenReturn(waterId);
    when(circle.getPedestals()).thenReturn(List.of(empty, water, post));
    when(circle.getArtifactPedestals()).thenReturn(List.of(empty, water, post));
    when(circle.artifactFor(emptyId)).thenReturn(new MeditationCache.ArtifactDef("fire", 2));
    when(circle.artifactFor(waterId)).thenReturn(new MeditationCache.ArtifactDef("water", 2));
    when(circle.artifactIdOn(water)).thenReturn("water-item");
    var session = session(10);
    activate(session);
    resonance.setResonance("fire", 5);
    session.onOrbHit(player, session.livingOrbs().getFirst(), resonance, null);
    assertEquals(5, resonance.getResonance("fire"));
    session.end();
  }

  @Test
  void consumedOrbImmediatelyBeforeSpawnDoesNotUseItsPedestalCapacity() {
    long oldSpawn = MeditationCache.spawnIntervalTicks;
    try {
      MeditationCache.spawnIntervalTicks = 5;
      var session = session(10);
      activate(session);
      for (int i = 0; i < 4; i++) session.tick(player, resonance);
      var flow =
          session.livingOrbs().stream().filter(MeditationOrb::isFlow).findFirst().orElseThrow();
      session.onOrbHit(player, flow, resonance, null);
      session.tick(player, resonance);
      assertEquals(2, session.livingOrbs().size());
      session.end();
    } finally {
      MeditationCache.spawnIntervalTicks = oldSpawn;
    }
  }

  @Test
  void returningOrbStillOccupiesCapacityWhenTheOtherOrbIsHitBeforeRespawn() {
    long oldSpawn = MeditationCache.spawnIntervalTicks;
    try {
      MeditationCache.spawnIntervalTicks = 5;
      var session = session(10);
      activate(session);
      for (int i = 0; i < 4; i++) session.tick(player, resonance);
      var flow =
          session.livingOrbs().stream().filter(MeditationOrb::isFlow).findFirst().orElseThrow();
      session.onOrbHit(player, flow, resonance, null);
      session.tick(player, resonance);
      for (int i = 0; i < 14; i++) session.tick(player, resonance);
      var replacement =
          session.livingOrbs().stream().filter(MeditationOrb::isFlow).findFirst().orElseThrow();
      session.onOrbHit(player, replacement, resonance, null);
      session.tick(player, resonance);
      assertEquals(1, session.livingOrbs().size());
      session.end();
    } finally {
      MeditationCache.spawnIntervalTicks = oldSpawn;
    }
  }

  @Test
  void sourceLessPublicOrbCannotCreditArtifactResonance() {
    var session = session(10);
    var orb = new MeditationOrb(player.getLocation(), true, false, 0);
    session.onOrbHit(player, orb, resonance, null);
    assertEquals(0, resonance.getResonance("fire"));
    session.end();
  }
}
