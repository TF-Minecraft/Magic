package net.tfminecraft.magic.blood;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyFloat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.logging.Handler;
import java.util.logging.LogRecord;

import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Registry;
import org.bukkit.World;
import org.bukkit.block.data.BlockData;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class BloodFxTest {
  @TempDir Path temp;
  BloodFixture fx;

  @BeforeEach
  void setup() {
    fx = new BloodFixture(temp);
  }

  @AfterEach
  void cleanup() {
    fx.close();
  }

  static BloodFx.Layer layer(Map<String, Object> raw) {
    return BloodFx.layer(raw);
  }

  @Test
  void everyBundledEffectIsARealParticleOrSound() {
    YamlConfiguration yaml = YamlConfiguration.loadConfiguration(new File("src/main/resources/bloodmagic.yml"));
    var moments = yaml.getConfigurationSection("fx.moments");
    BloodConfig.load(new File("src/main/resources/bloodmagic.yml"), fx.logger);
    assertTrue(BloodFx.isEnabled());
    int layers = 0;
    for (String moment : moments.getKeys(false)) {
      List<BloodFx.Layer> loaded = BloodFx.moment(moment);
      assertEquals(moments.getMapList(moment).size(), loaded.size(), moment);
      for (BloodFx.Layer layer : loaded) {
        if (layer.sound() != null) {
          assertNotNull(Registry.SOUNDS.get(NamespacedKey.fromString(layer.sound())), moment + " " + layer.sound());
        }
        layers++;
      }
    }
    assertTrue(layers > 100);
    assertTrue(BloodFx.moment("nothing").isEmpty());
  }

  @Test
  void layersReadEveryField() {
    BloodFx.Layer sound = layer(Map.of("sound", "entity.warden.heartbeat", "volume", 0.5, "pitch", 2, "delay", 4,
        "private", true));
    assertEquals("minecraft:entity.warden.heartbeat", sound.sound());
    assertEquals(0.5f, sound.volume());
    assertEquals(2f, sound.pitch());
    assertEquals(4, sound.delay());
    assertTrue(sound.personal());
    assertEquals("custom:sting", layer(Map.of("sound", "custom:sting")).sound());

    assertNull(layer(Map.of()));
    assertNull(layer(Map.of("sound", " ")));
    assertNull(layer(Map.of("particle", "NOT_A_PARTICLE")));
    assertNull(layer(Map.of("particle", 3)));

    BloodFx.Layer smoke = layer(Map.of("particle", "smoke", "count", -3, "spread", 0.5, "shape", "spiral",
        "radius", 2, "points", 0, "height", 3, "y", 1.5, "speed", 0.1, "delay", -1));
    assertEquals(Particle.SMOKE, smoke.particle());
    assertNull(smoke.data());
    assertEquals(0, smoke.count());
    assertEquals(0.5, smoke.spreadY());
    assertEquals(BloodFx.Shape.SPIRAL, smoke.shape());
    assertEquals(1, smoke.points());
    assertEquals(0, smoke.delay());
    assertFalse(smoke.personal());
    assertEquals(BloodFx.Shape.POINT, layer(Map.of("particle", "SMOKE", "shape", "cube")).shape());
    assertEquals(1, layer(Map.of("particle", "SMOKE")).count());

    BloodFx.Layer spread = layer(Map.of("particle", "SMOKE", "spread", List.of(1, 2, 3)));
    assertEquals(1, spread.spreadX());
    assertEquals(3, spread.spreadZ());
    assertEquals(0, layer(Map.of("particle", "SMOKE", "spread", List.of(1, 2))).spreadX());
    assertEquals(0, layer(Map.of("particle", "SMOKE", "spread", List.of(1, "x", 3))).spreadX());
    assertEquals(0, layer(Map.of("particle", "SMOKE", "spread", List.of("x", 2, 3))).spreadX());
    assertEquals(0, layer(Map.of("particle", "SMOKE", "spread", List.of(1, 2, "x"))).spreadX());
    assertEquals(0, layer(Map.of("particle", "SMOKE", "spread", "wide")).spreadX());
  }

  @Test
  void particleDataFollowsTheParticleType() {
    Particle.DustOptions dust = (Particle.DustOptions) layer(Map.of("particle", "DUST", "color", "#ff0000", "size", 2)).data();
    assertEquals(Color.RED, dust.getColor());
    assertEquals(2f, dust.getSize());
    assertEquals(Color.fromRGB(0x8a0303),
        ((Particle.DustOptions) layer(Map.of("particle", "DUST", "color", "nope")).data()).getColor());
    assertEquals(Color.fromRGB(0x00ff00),
        ((Particle.DustOptions) layer(Map.of("particle", "DUST", "color", "00ff00")).data()).getColor());
    Particle.DustTransition transition = (Particle.DustTransition) layer(Map.of("particle", "DUST_COLOR_TRANSITION",
        "color", "#ff0000", "to", "#0000ff")).data();
    assertEquals(Color.BLUE, transition.getToColor());
    assertEquals(Color.fromRGB(0x1a0000),
        ((Particle.DustTransition) layer(Map.of("particle", "DUST_COLOR_TRANSITION")).data()).getToColor());
    assertInstanceOf(BlockData.class, BloodFx.data(layer(Map.of("particle", "BLOCK", "block", "redstone_block"))));
    assertNull(layer(Map.of("particle", "BLOCK", "block", "diamond")));
    assertNull(layer(Map.of("particle", "BLOCK", "block", "nothing")));
    assertInstanceOf(ItemStack.class, BloodFx.data(layer(Map.of("particle", "ITEM", "item", "bone"))));
    assertNull(BloodFx.data(layer(Map.of("particle", "SMOKE"))));
    assertNull(layer(Map.of("particle", "ITEM", "item", "water")));
    assertNull(layer(Map.of("particle", "ITEM")));
    assertEquals(Color.RED, layer(Map.of("particle", "ENTITY_EFFECT", "color", "#ff0000")).data());
    assertEquals(0.5f, layer(Map.of("particle", "SCULK_CHARGE", "value", 0.5)).data());
    assertEquals(3, layer(Map.of("particle", "SHRIEK", "value", 3)).data());
    assertNull(layer(Map.of("particle", "VIBRATION")));
  }

  @Test
  void shapesPlacePointsAroundTheSpot() {
    Location at = new Location(fx.world, 0, 10, 0);
    assertEquals(List.of(at.clone().add(0, 1, 0)),
        BloodFx.points(layer(Map.of("particle", "SMOKE", "y", 1)), at));
    List<Location> ring = BloodFx.points(layer(Map.of("particle", "SMOKE", "shape", "ring", "radius", 2, "points", 4)), at);
    assertEquals(4, ring.size());
    assertEquals(2, ring.get(0).getX(), 1e-9);
    List<Location> column = BloodFx.points(layer(Map.of("particle", "SMOKE", "shape", "column", "height", 2, "points", 3)), at);
    assertEquals(12, column.get(2).getY(), 1e-9);
    assertEquals(1, BloodFx.points(layer(Map.of("particle", "SMOKE", "shape", "column", "points", 1)), at).size());
    List<Location> spiral = BloodFx.points(layer(Map.of("particle", "SMOKE", "shape", "spiral", "points", 8, "height", 4)), at);
    assertEquals(8, spiral.size());
    assertEquals(13.5, spiral.get(7).getY(), 1e-9);
  }

  @Test
  void playingSendsLayersToTheWorldOrTheViewer() throws Exception {
    Files.writeString(temp.resolve("fx.yml"), String.join("\n",
        "enabled: true",
        "fx:",
        "  moments:",
        "    test:",
        "      - { sound: block.stone.place }",
        "      - { sound: block.bell.use, private: true }",
        "      - { particle: SMOKE, count: 2 }",
        "      - { particle: SMOKE, private: true, shape: ring, points: 3 }",
        "      - { sound: block.chain.place, delay: 5 }",
        "      - { what: ever }",
        ""));
    var warnings = new java.util.ArrayList<String>();
    Handler handler = new Handler() {
      @Override public void publish(LogRecord record) { warnings.add(record.getMessage()); }
      @Override public void flush() {}
      @Override public void close() {}
    };
    fx.logger.addHandler(handler);
    BloodConfig.load(temp.resolve("fx.yml").toFile(), fx.logger);
    fx.logger.removeHandler(handler);
    assertTrue(warnings.stream().anyMatch(w -> w.contains("fx.test")));
    assertEquals(5, BloodFx.moment("test").size());

    World world = mock(World.class);
    Location at = new Location(world, 1, 2, 3);
    Player viewer = mock(Player.class);
    BloodFx.play("test", at, viewer);
    verify(world).playSound(any(Location.class), eq("minecraft:block.stone.place"), anyFloat(), anyFloat());
    verify(viewer).playSound(any(Location.class), eq("minecraft:block.bell.use"), anyFloat(), anyFloat());
    verify(world).spawnParticle(eq(Particle.SMOKE), any(Location.class), eq(2), anyDouble(), anyDouble(), anyDouble(),
        anyDouble(), any());
    verify(viewer, times(3)).spawnParticle(eq(Particle.SMOKE), any(Location.class), anyInt(), anyDouble(), anyDouble(),
        anyDouble(), anyDouble(), any());
    verify(world, never()).playSound(any(Location.class), eq("minecraft:block.chain.place"), anyFloat(), anyFloat());
    fx.server.getScheduler().performTicks(6);
    verify(world).playSound(any(Location.class), eq("minecraft:block.chain.place"), anyFloat(), anyFloat());

    World quiet = mock(World.class);
    BloodFx.play("test", new Location(quiet, 0, 0, 0));
    verify(quiet).playSound(any(Location.class), eq("minecraft:block.stone.place"), anyFloat(), anyFloat());
    verify(quiet, never()).playSound(any(Location.class), eq("minecraft:block.bell.use"), anyFloat(), anyFloat());

    World broken = mock(World.class);
    doThrow(new IllegalStateException("no sound")).when(broken).playSound(any(Location.class), anyString(), anyFloat(), anyFloat());
    BloodFx.play("test", new Location(broken, 0, 0, 0));
    fx.server.getScheduler().performTicks(6);
    verify(broken).spawnParticle(eq(Particle.SMOKE), any(Location.class), eq(2), anyDouble(), anyDouble(), anyDouble(),
        anyDouble(), any());

    BloodFx.play("test", null);
    BloodFx.play("test", new Location(null, 0, 0, 0));
    BloodFx.play("unknown", at);

    Files.writeString(temp.resolve("off.yml"), "enabled: true\nfx:\n  enabled: false\n  moments:\n    test:\n      - { sound: a.b }\n");
    BloodConfig.load(temp.resolve("off.yml").toFile(), fx.logger);
    assertFalse(BloodFx.isEnabled());
    World silent = mock(World.class);
    BloodFx.play("test", new Location(silent, 0, 0, 0));
    verifyNoInteractions(silent);

    Files.writeString(temp.resolve("none.yml"), "enabled: true\nfx:\n  enabled: true\n");
    BloodConfig.load(temp.resolve("none.yml").toFile(), fx.logger);
    assertTrue(BloodFx.moment("test").isEmpty());
    Files.writeString(temp.resolve("empty.yml"), "enabled: true\n");
    BloodConfig.load(temp.resolve("empty.yml").toFile(), fx.logger);
    assertTrue(BloodFx.isEnabled());
  }

  @Test
  void altarsBreatheOnlyWhileSomeoneIsNear() throws Exception {
    Files.writeString(temp.resolve("amb.yml"),
        "enabled: true\nfx:\n  moments:\n    altar_ambient_2:\n      - { particle: SMOKE, count: 7 }\n");
    BloodConfig.load(temp.resolve("amb.yml").toFile(), fx.logger);
    BloodAltar near = BloodAltar.create("a", "o", fx.pocket(0, 10, 0), 0f, 1L);
    near.setLevel(2);
    BloodAltars.add(near);
    BloodAltar lost = BloodAltar.create("b", "o", fx.pocket(4, 10, 0), 0f, 1L);
    BloodAltars.add(lost);
    var field = BloodAltar.class.getDeclaredField("world");
    field.setAccessible(true);
    field.set(lost, "gone");
    BloodAltar unloaded = BloodAltar.create("c", "o", fx.world.getBlockAt(5000, 10, 5000), 0f, 1L);
    fx.world.unloadChunk(5000 >> 4, 5000 >> 4);
    BloodAltars.add(unloaded);
    BloodMagic.ambient();
    var far = fx.server.addPlayer();
    far.teleport(new Location(fx.world, 200, 10, 0));
    var close = fx.server.addPlayer();
    close.teleport(new Location(fx.world, 3, 10, 0));
    BloodMagic.ambient();
  }
}
