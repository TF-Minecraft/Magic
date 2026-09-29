package net.tfminecraft.magic.artifact.shrine;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.*;
import net.tfminecraft.interactiblefurniture.furniture.*;
import net.tfminecraft.magic.*;
import net.tfminecraft.magic.artifact.*;
import net.tfminecraft.magic.artifact.aura.*;
import net.tfminecraft.magic.artifact.sacrifice.*;
import net.tfminecraft.magic.meditation.MeditationCircle;
import net.tfminecraft.magic.model.ElementDef;
import net.tfminecraft.magic.registry.ElementRegistry;
import net.tfminecraft.magic.util.PedestalFx;
import org.bukkit.*;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.*;
import org.mockbukkit.mockbukkit.*;

class ShrineFxTest {
  ServerMock server;
  World world;
  Furniture furniture;
  PlacedSlot slot;
  ItemStack item;

  @BeforeEach
  void setup() {
    server = MockBukkit.mock();
    world = mock(World.class);
    furniture = mock(Furniture.class);
    when(furniture.getLoc()).thenReturn(new Location(world, 0, 64, 0));
    when(furniture.getOriginBlockLocation()).thenReturn(Optional.empty());
    when(furniture.getEntityId()).thenReturn(UUID.randomUUID());
    slot = mock(PlacedSlot.class);
    when(furniture.getActiveSlot("main")).thenReturn(Optional.of(slot));
    Magic.plugin = mock(Magic.class);
    when(Magic.plugin.namespace()).thenReturn("magic");
    when(Magic.plugin.getServer()).thenReturn(server);
    when(Magic.plugin.isEnabled()).thenReturn(true);
    ShrineRegistry.clear();
    SacrificeRegistry.clear();
    ElementRegistry.clear();
    item = new ItemStack(Material.STONE);
    var a = Artifact.create();
    a.setCap("fire", 10);
    a.persistPdc(item);
    when(slot.getCurrentItem()).thenReturn(item);
  }

  @AfterEach
  void cleanup() {
    ShrineChargeService.clearAll();
    ShrineChargeFx.stopIfIdle();
    ElementRegistry.clear();
    Magic.plugin = null;
    MockBukkit.unmock();
  }

  @Test
  void startCompleteAndSacrificeReadyEmitConfiguredEffectsAtArtifactPoint() {
    assertEquals(Color.WHITE, ShrineChargeFx.elementColor("unknown"));
    var element = mock(ElementDef.class);
    when(element.getId()).thenReturn("fire");
    when(element.getColor()).thenReturn("#ff0000");
    ElementRegistry.register(element);
    assertEquals(Color.RED, ShrineChargeFx.elementColor("fire"));
    ShrineChargeFx.start(furniture, "fire");
    ShrineChargeFx.complete(furniture, "fire");
    ShrineChargeFx.sacrificeReady(furniture, "fire");
    verify(world, atLeastOnce())
        .spawnParticle(
            eq(Particle.DUST),
            any(Location.class),
            anyInt(),
            anyDouble(),
            anyDouble(),
            anyDouble(),
            any(Particle.DustOptions.class));
    verify(world, atLeastOnce())
        .playSound(
            any(Location.class),
            any(Sound.class),
            eq(SoundCategory.MASTER),
            anyFloat(),
            anyFloat());
    assertEquals(new Location(world, .5, 65.8, .5), PedestalFx.artifactPoint(furniture));
    when(furniture.getOriginBlockLocation()).thenReturn(Optional.of(new Location(world, 2, 60, 3)));
    assertEquals(new Location(world, 2.5, 61.8, 3.5), PedestalFx.artifactPoint(furniture));
  }

  @Test
  void unavailableFurnitureAndOptionalEffectsFailSafely() {
    ShrineChargeFx.start(null, "fire");
    ShrineChargeFx.complete(null, "fire");
    ShrineChargeFx.sacrificeReady(null, "fire");
    assertNull(PedestalFx.artifactPoint(null));
    when(furniture.getLoc()).thenReturn(null);
    ShrineChargeFx.start(furniture, "fire");
    ShrineChargeFx.complete(furniture, "fire");
    ShrineChargeFx.sacrificeReady(furniture, "fire");
    assertNull(PedestalFx.artifactPoint(furniture));
    when(furniture.getLoc()).thenReturn(new Location(null, 0, 0, 0));
    ShrineChargeFx.start(furniture, "fire");
    ShrineChargeFx.complete(furniture, "fire");
    ShrineChargeFx.sacrificeReady(furniture, "fire");
    assertNull(PedestalFx.artifactPoint(furniture));
    when(furniture.getLoc()).thenReturn(new Location(world, 0, 64, 0));
    ShrineRegistry.setDefaultFx(
        new ShrineFxDef(null, 0, 1, 0, null, 0, 1, null, 0, 1, null, 0, 1, null, null, null));
    ShrineChargeFx.start(furniture, "fire");
    ShrineChargeFx.complete(furniture, "fire");
    ShrineChargeFx.sacrificeReady(furniture, "fire");
    ShrineRegistry.setDefaultFx(ShrineFxDef.fallback());
    doThrow(new IllegalArgumentException("unsupported particle"))
        .when(world)
        .spawnParticle(
            any(Particle.class),
            any(Location.class),
            anyInt(),
            anyDouble(),
            anyDouble(),
            anyDouble(),
            anyDouble());
    ShrineChargeFx.start(furniture, "fire");
    ShrineChargeFx.complete(furniture, "fire");
  }

  @Test
  void scheduledStreamsAnimateContributingBlocksAndStopWhenIdle() {
    var locations = new ArrayList<Location>();
    locations.add(null);
    locations.add(new Location(null, 0, 0, 0));
    for (int n = 0; n < 20; n++) locations.add(new Location(world, n, 64, 0));
    var score =
        new ShrineScore(Map.of("fire", new ShrineElementScore("fire", 10, 1, null, locations)));
    try (var scoring = mockStatic(ShrineScorer.class);
        var circle = mockStatic(MeditationCircle.class)) {
      scoring.when(() -> ShrineScorer.score(any())).thenReturn(score);
      ShrineChargeService.tryStart(null, furniture, "main");
      assertTrue(ShrineChargeService.hasSessions());
      ShrineChargeFx.stopIfIdle();
      ShrineChargeFx.ensureRunning();
      server.getScheduler().performTicks(50);
      verify(world, atLeastOnce())
          .spawnParticle(
              eq(Particle.DUST),
              any(Location.class),
              eq(1),
              eq(0d),
              eq(0d),
              eq(0d),
              any(Particle.DustOptions.class));
      ShrineChargeFx.complete(furniture, AuraVessels.fromItem(item), item, score);
      when(slot.getCurrentItem()).thenReturn(null);
      ShrineChargeFx.tick();
      when(slot.getCurrentItem()).thenReturn(item);
      var a = Artifact.fromItem(item);
      a.setFill("fire", 10);
      a.persistPdc(item);
      ShrineChargeFx.tick();
      ShrineChargeService.clearAll();
      ShrineChargeFx.tick();
      Magic.plugin = null;
      ShrineChargeFx.ensureRunning();
    }
  }

  static Object call(String name, Class<?>[] types, Object... args) throws Exception {
    var method = ShrineChargeFx.class.getDeclaredMethod(name, types);
    method.setAccessible(true);
    return method.invoke(null, args);
  }

  @Test
  void originWithoutWorldAndMissingFurnitureCannotEmitPulls() throws Exception {
    when(furniture.getOriginBlockLocation()).thenReturn(Optional.of(new Location(null, 0, 0, 0)));
    ShrineChargeFx.start(furniture, "fire");
    ShrineChargeFx.complete(furniture, "fire");
    ShrineChargeFx.sacrificeReady(furniture, "fire");
    var session = new ShrineChargeSession(furniture, "main", new ShrineScore(Map.of()));
    call("spawnPulls", new Class[] {ShrineChargeSession.class}, session);
    when(furniture.getLoc()).thenReturn(null);
    call("spawnPulls", new Class[] {ShrineChargeSession.class}, session);
    when(furniture.getLoc()).thenReturn(new Location(null, 0, 0, 0));
    call("spawnPulls", new Class[] {ShrineChargeSession.class}, session);
  }

  @Test
  void forcedStreamsAndSceneryFiltersRespectCapsAndOptionalAmbient() throws Exception {
    var empty = new ShrineScore(Map.of());
    var vessel = Artifact.fromItem(item);
    var session = new ShrineChargeSession(furniture, "main", empty, "fire", 5, true);
    call("spawnPulls", new Class[] {ShrineChargeSession.class}, session);
    session = new ShrineChargeSession(furniture, "main", empty, null, 5, true);
    call("spawnPulls", new Class[] {ShrineChargeSession.class}, session);
    session = new ShrineChargeSession(furniture, "main", empty, "missing", 5, true);
    call("spawnPulls", new Class[] {ShrineChargeSession.class}, session);
    vessel.setFill("fire", 10);
    vessel.persistPdc(item);
    call(
        "spawnPulls",
        new Class[] {ShrineChargeSession.class},
        new ShrineChargeSession(furniture, "main", empty, "fire", 5, true));
    for (ShrineScore score :
        List.of(
            empty,
            new ShrineScore(Map.of("fire", new ShrineElementScore("fire", 0, 1, null, null))),
            new ShrineScore(Map.of("fire", new ShrineElementScore("fire", 10, 0, null, null)))))
      call(
          "spawnPulls",
          new Class[] {ShrineChargeSession.class},
          new ShrineChargeSession(furniture, "main", score));
    ShrineRegistry.register(new ShrineElementDef("fire", false, List.of()));
    call(
        "spawnPulls",
        new Class[] {ShrineChargeSession.class},
        new ShrineChargeSession(furniture, "main", empty));
    var blank = Artifact.create();
    blank.persistPdc(item);
    call(
        "spawnPulls",
        new Class[] {ShrineChargeSession.class},
        new ShrineChargeSession(furniture, "main", empty));
  }

  @Test
  void animationRestartAndOptionalParticleBoundariesAreSafe() throws Exception {
    ShrineChargeFx.ensureRunning();
    var field = ShrineChargeFx.class.getDeclaredField("task");
    field.setAccessible(true);
    ((org.bukkit.scheduler.BukkitTask) field.get(null)).cancel();
    ShrineChargeFx.ensureRunning();
    ShrineChargeFx.stopIfIdle();
    ShrineChargeFx.stopIfIdle();
    var at = new Location(world, 0, 0, 0);
    var spawnTypes =
        new Class[] {
          World.class,
          Particle.class,
          Location.class,
          int.class,
          double.class,
          double.class,
          double.class,
          double.class
        };
    call("spawn", spawnTypes, null, Particle.CLOUD, at, 1, 0., 0., 0., 0.);
    call("spawn", spawnTypes, world, Particle.CLOUD, null, 1, 0., 0., 0., 0.);
    call("spawn", spawnTypes, world, Particle.CLOUD, at, 0, 0., 0., 0., 0.);
    call(
        "play",
        new Class[] {World.class, Location.class, Sound.class, float.class, float.class},
        null,
        at,
        Sound.BLOCK_BEACON_ACTIVATE,
        1f,
        1f);
    call(
        "play",
        new Class[] {World.class, Location.class, Sound.class, float.class, float.class},
        world,
        null,
        Sound.BLOCK_BEACON_ACTIVATE,
        1f,
        1f);
  }

  @Test
  void forcedExistingScoreAlsoAnimatesContributions() throws Exception {
    var score =
        new ShrineScore(
            Map.of(
                "fire",
                new ShrineElementScore(
                    "fire", 10, 1, List.of(), List.of(new Location(world, 1, 1, 1)))));
    call(
        "spawnPulls",
        new Class[] {ShrineChargeSession.class},
        new ShrineChargeSession(furniture, "main", score, "fire", 5, true));
  }
}
