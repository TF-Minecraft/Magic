package net.tfminecraft.magic.blood;

import static org.junit.jupiter.api.Assertions.*;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import org.bukkit.Location;
import org.bukkit.Particle;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

class AltarCandlesTest {
  @TempDir Path temp;
  BloodFixture fx;
  final List<Particle> lit = new ArrayList<>();

  @BeforeEach
  void setup() {
    fx = new BloodFixture(temp);
    assertTrue(BloodConfig.load(new File("src/main/resources/wildmagic/bloodmagic"), fx.logger));
    AltarCandles.spawner = (particle, at) -> lit.add(particle);
  }

  @AfterEach
  void cleanup() {
    AltarCandles.stop();
    AltarCandles.spawner = (particle, at) -> at.getWorld().spawnParticle(particle, at, 1, 0, 0, 0, 0);
    AltarCandles.random = new Random();
    fx.close();
  }

  void ticks(int count) {
    for (int i = 0; i < count; i++) {
      AltarCandles.tick();
    }
  }

  @Test
  void wicksFollowTheAltarsFacing() {
    Location center = new Location(fx.world, 0.5, 10.5, 0.5);
    AltarCandles.Wick wick = new AltarCandles.Wick(13.5, 16.4, 2.5, 5);
    Location facingSouth = AltarCandles.position(center, 0f, wick);
    assertEquals(0.5 - 5.5 / 16, facingSouth.getX(), 1e-9);
    assertEquals(10.5 + 8.4 / 16, facingSouth.getY(), 1e-9);
    assertEquals(0.5 + 5.5 / 16, facingSouth.getZ(), 1e-9);
    Location facingWest = AltarCandles.position(center, 90f, wick);
    assertEquals(0.5 - 5.5 / 16, facingWest.getX(), 1e-9);
    assertEquals(0.5 - 5.5 / 16, facingWest.getZ(), 1e-9);
  }

  @Test
  void flamesBurnOnlyWhileSomeoneIsNearAndOnlyOnCandlesThatAreThere() throws Exception {
    BloodAltar altar = BloodAltar.create("a", "o", fx.pocket(0, 10, 0), 0f, 1L);
    BloodAltars.add(altar);
    ticks(16);
    assertTrue(lit.isEmpty());
    PlayerMock watcher = fx.player("watcher");
    AltarCandles.random = new Random() {
      @Override public double nextDouble() { return 0.99; }
    };
    ticks(8);
    assertEquals(4, lit.size());
    assertFalse(lit.contains(Particle.SMOKE));
    lit.clear();
    altar.setLevel(4);
    ticks(8);
    assertEquals(3, lit.size());
    lit.clear();
    AltarCandles.random = new Random() {
      @Override public double nextDouble() { return 0.0; }
    };
    ticks(8);
    assertEquals(6, lit.size());
    assertTrue(lit.contains(Particle.SMOKE));
    lit.clear();

    watcher.teleport(new Location(fx.world, 100, 10, 100));
    ticks(8);
    assertTrue(lit.isEmpty());
    watcher.teleport(new Location(fx.world, 0.5, 11, 0.5));
    fx.world.unloadChunk(0, 0);
    ticks(8);
    assertTrue(lit.isEmpty());
    fx.world.loadChunk(0, 0);
    BloodAltars.add(BloodAltar.create("gone", "o2", new org.mockbukkit.mockbukkit.world.WorldMock().getBlockAt(0, 0, 0), 0f, 1L));
    ticks(8);
    assertFalse(lit.isEmpty());

    Files.writeString(temp.resolve("c.yml"), String.join("\n", "enabled: true", "fx:", "  candles:",
        "    enabled: false", ""));
    assertTrue(BloodConfig.load(temp.resolve("c.yml").toFile(), fx.logger));
    lit.clear();
    ticks(16);
    assertTrue(lit.isEmpty());
    Files.writeString(temp.resolve("w.yml"), String.join("\n", "enabled: true", "fx:", "  candles:",
        "    particle: nope", "    every_ticks: 0", "    smoke_chance: 3", "    view_range: 0",
        "    wicks: [[1, 2, 3], [x, 1, 2], [4]]", ""));
    assertTrue(BloodConfig.load(temp.resolve("w.yml").toFile(), fx.logger));
    lit.clear();
    ticks(1);
    assertEquals(List.of(Particle.SMALL_FLAME, Particle.SMOKE), lit);
    Files.writeString(temp.resolve("n.yml"), "enabled: true\nfx:\n  enabled: true\n");
    assertTrue(BloodConfig.load(temp.resolve("n.yml").toFile(), fx.logger));
    lit.clear();
    ticks(16);
    assertTrue(lit.isEmpty());

    var plugin = MockBukkit.createMockPlugin("Candles");
    AltarCandles.startTicking(plugin);
    AltarCandles.startTicking(plugin);
    AltarCandles.stop();
    AltarCandles.stop();
  }
}
