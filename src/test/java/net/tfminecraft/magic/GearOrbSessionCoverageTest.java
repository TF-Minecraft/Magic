package net.tfminecraft.magic;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import net.tfminecraft.magic.gear.orb.*;
import net.tfminecraft.magic.model.ElementDef;
import net.tfminecraft.magic.registry.ElementRegistry;
import org.bukkit.*;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.*;

class GearOrbSessionCoverageTest extends GearCoverageSupport {
  @AfterEach
  void resetElements() {
    ElementRegistry.clear();
  }

  GearOrb orb(Location at, boolean good, int life) {
    return new GearOrb(at, good, 0, 1, 0, .1, 0, 0, life);
  }

  @Test
  void sessionCopiesInputsTracksHitsAndAppliesMissPenalty() {
    World world = mock(World.class);
    Location at = new Location(world, 1, 2, 3);
    var fills = new LinkedHashMap<String, Double>();
    fills.put("fire", 100d);
    fills.put("null", null);
    fills.put("zero", 0d);
    fills.put("negative", -1d);
    var id = UUID.randomUUID();
    var s = new GearOrbSession(at, id, fills, null, new OrbCache.Tier(2, 1, 1, 20, 2));
    at.setX(99);
    fills.put("fire", 999d);
    assertEquals(1, s.getStation().getX());
    assertEquals(id, s.getPlayerId());
    assertEquals(1.5, s.anchor().getX());
    assertEquals(3.15, s.displayPoint().getY());
    assertEquals(0, s.ticks());
    assertFalse(s.isFinished());
    assertTrue(s.claimSwing(1));
    assertFalse(s.claimSwing(1));
    assertTrue(s.claimSwing(2));
    assertTrue(s.canHit(1));
    s.markHit(2);
    assertFalse(s.canHit(5));
    assertTrue(s.canHit(6));
    assertEquals(2, s.goodTarget());
    assertEquals(0d, s.capturedFill().get("fire"));
    var player = mock(Player.class, RETURNS_DEEP_STUBS);
    when(player.getLocation()).thenReturn(at);
    s.onHit(player, orb(at, true, 2));
    assertEquals(.5, s.capturedFraction());
    s.onHit(null, orb(at, true, 2));
    s.onHit(null, orb(at, true, 2));
    assertEquals(1, s.capturedFraction());
    assertEquals(100d, s.capturedFill().get("fire"));
    s.onHit(player, orb(at, false, 2));
    assertEquals(OrbCache.riftPerBad, s.riftDelta());
    assertEquals(3, s.goodHits());
    assertEquals(0, s.misses());
    s.playComplete();
    verify(world)
        .playSound(any(Location.class), eq(Sound.BLOCK_ENCHANTMENT_TABLE_USE), eq(1f), eq(1.2f));
  }

  @Test
  void orbitSpawnsBothKindsExpiresAndPrunesConsumedOrbs() {
    var rng = mock(ThreadLocalRandom.class);
    when(rng.nextDouble()).thenReturn(.1);
    when(rng.nextBoolean()).thenReturn(true, false);
    OrbCache.introTicks = 1;
    OrbCache.lifetimeTicks = 4;
    OrbCache.spawnIntervalTicks = 1;
    OrbCache.spawnBurstTicks = 1;
    World world = mock(World.class);
    Location at = new Location(world, 0, 0, 0);
    var player = mock(Player.class, RETURNS_DEEP_STUBS);
    when(player.getLocation()).thenReturn(at);
    try (var random = mockStatic(ThreadLocalRandom.class)) {
      random.when(ThreadLocalRandom::current).thenReturn(rng);
      for (double ratio : new double[] {1, 0}) {
        var s =
            new GearOrbSession(
                at,
                UUID.randomUUID(),
                Map.of("fire", 10d),
                "unknown",
                new OrbCache.Tier(2, ratio, 1, 20, 2));
        s.tick(null);
        assertEquals(1, s.livingOrbs().size());
        var first = s.livingOrbs().getFirst();
        assertEquals(ratio == 1, first.isGood());
        s.onHit(null, first);
        assertTrue(s.livingOrbs().isEmpty());
        s.tick(player);
        for (int i = 0; i < 22; i++) s.tick(player);
        assertTrue(s.isFinished());
        assertEquals(24, s.ticks());
        if (ratio == 1) {
          assertTrue(s.misses() > 0);
          assertEquals(0, s.capturedFraction());
        }
        s.end();
        assertTrue(s.livingOrbs().isEmpty());
      }
    }
  }

  @Test
  void missingWorldStillExpiresAndFinishesWithoutEffects() {
    Location at = new Location(null, 0, 0, 0);
    OrbCache.lifetimeTicks = 1;
    var s =
        new GearOrbSession(at, UUID.randomUUID(), Map.of(), "", new OrbCache.Tier(1, 1, 1, 20, 1));
    s.tick(null);
    assertEquals(1, s.livingOrbs().size());
    s.tick(null);
    assertEquals(1, s.misses());
    s.onHit(null, orb(at, false, 1));
    s.playComplete();
    s.end();
    assertTrue(s.livingOrbs().isEmpty());
  }

  @Test
  void knownElementSuppliesBeamColor() {
    var cfg = new YamlConfiguration();
    cfg.set("color", "#123456");
    ElementRegistry.register(new ElementDef("fire", cfg));
    var world = mock(World.class);
    var s =
        new GearOrbSession(
            new Location(world, 0, 0, 0),
            UUID.randomUUID(),
            Map.of(),
            "fire",
            new OrbCache.Tier(1, 1, 1, 20, 1));
    s.playComplete();
    var dust = org.mockito.ArgumentCaptor.forClass(Particle.DustOptions.class);
    verify(world)
        .spawnParticle(
            eq(Particle.DUST),
            any(Location.class),
            eq(20),
            eq(.35),
            eq(.4),
            eq(.35),
            dust.capture());
    assertEquals(Color.fromRGB(0x123456), dust.getValue().getColor());
  }

  @Test
  void spawnPacingWaitsForIntervalAndCapacity() {
    OrbCache.lifetimeTicks = 100;
    OrbCache.spawnIntervalTicks = 4;
    OrbCache.spawnBurstTicks = 20;
    var s =
        new GearOrbSession(
            new Location(mock(World.class), 0, 0, 0),
            UUID.randomUUID(),
            Map.of(),
            null,
            new OrbCache.Tier(2, 1, 1, 20, 1));
    s.tick(null);
    assertEquals(1, s.livingOrbs().size());
    s.tick(null);
    assertEquals(1, s.livingOrbs().size());
    for (int i = 0; i < 8; i++) s.tick(null);
    assertEquals(2, s.livingOrbs().size());
  }
}
