package net.tfminecraft.magic;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.nio.file.*;
import java.util.*;
import net.tfminecraft.magic.gear.*;
import net.tfminecraft.magic.gear.orb.OrbCache;
import net.tfminecraft.magic.loader.GearLoader;
import org.bukkit.*;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.mockbukkit.mockbukkit.MockBukkit;

class GearLoaderCoverageTest {
  @TempDir Path temp;
  List<String> names =
      List.of(
          "part-types.yml",
          "archetypes.yml",
          "socket-colours.yml",
          "model-schemes.yml",
          "parts.yml",
          "orbs.yml");
  Map<java.lang.reflect.Field, Object> orbDefaults = new HashMap<>();

  @BeforeEach
  void setup() throws Exception {
    MockBukkit.mock();
    Magic.plugin = mock(Magic.class);
    when(Magic.plugin.getLogger()).thenReturn(java.util.logging.Logger.getLogger("MagicTest"));
    for (var field : OrbCache.class.getFields())
      if (java.lang.reflect.Modifier.isStatic(field.getModifiers())
          && !java.lang.reflect.Modifier.isFinal(field.getModifiers()))
        orbDefaults.put(field, field.get(null));
    base();
  }

  @AfterEach
  void cleanup() throws Exception {
    for (var entry : orbDefaults.entrySet()) entry.getKey().set(null, entry.getValue());
    OrbCache.clearTiers();
    ArchetypeRegistry.clear();
    PartTypeRegistry.clear();
    PartRegistry.clear();
    GearModelSchemeRegistry.clear();
    SocketColourRegistry.clear();
    SocketLayout.clearLabels();
    Magic.plugin = null;
    MockBukkit.unmock();
  }

  void write(String name, String text) throws Exception {
    Files.writeString(temp.resolve(name), text);
  }

  void base() throws Exception {
    for (String name : names) write(name, "{}");
    write("part-types.yml", "core: {slot: 1, name: Core}\n");
    write("archetypes.yml", "staff: {template: m.STAFF.TEST, slots: {core: Core}}\n");
    write("model-schemes.yml", "scheme: {models: ['staff(v.STICK)']}\n");
  }

  @Test
  void missingMalformedAndUnreadableFilesReportFailureWithoutStaleEntries() throws Exception {
    var loader = new GearLoader();
    assertFalse(loader.loadFolder(null));
    assertFalse(loader.loadFolder(temp.resolve("missing").toFile()));
    for (String name : names) {
      base();
      Files.delete(temp.resolve(name));
      assertFalse(loader.loadFolder(temp.toFile()), name);
      base();
      write(name, "bad: [");
      assertFalse(loader.loadFolder(temp.toFile()), name);
    }
    base();
    Files.delete(temp.resolve("parts.yml"));
    Files.createDirectory(temp.resolve("parts.yml"));
    assertFalse(loader.loadFolder(temp.toFile()));
  }

  @Test
  void schemasSkipInvalidRowsAndPreserveValidDefinitions() throws Exception {
    write("part-types.yml", "core: {slot: 1, name: Core}\nscalar: 5\n");
    write("archetypes.yml", "staff: {slots: {core: Core}}\nwand: {}\nsword: scalar\nunknown: {}\n");
    write(
        "socket-colours.yml",
        "labels: {core: Core}\nprefix: {default: Common, 2: Rare, bad: Bad}\n");
    write(
        "model-schemes.yml",
        "' ': {}\n"
            + "scalar: nope\n"
            + "empty: {models: [' ', bad, '(bad)', 'staff(', 'bad(v.STICK)', 'staff()']}\n"
            + "SCHEME: {models: ['staff(v.STICK)']}\n"
            + "scheme: {models: ['wand(v.BLAZE_ROD)']}\n");
    var cfg = new YamlConfiguration();
    cfg.set("scalar", "text");
    cfg.set("unknown.part-type", "missing");
    cfg.set("no-types.part-type", "core");
    cfg.set("no-types.type", List.of("unknown"));
    int n = 0;
    for (String scheme : List.of("scheme(2)", "scheme(nope)", "scheme(", "scheme", "")) {
      String key = "part" + (n++);
      cfg.set(key + ".part-type", "core");
      cfg.set(key + ".type", List.of("staff", "invalid"));
      cfg.set(key + ".part-limit", List.of(" ", "unknown", "core", " CORE "));
      cfg.set(key + ".model-scheme", scheme);
      cfg.set(key + ".cost", List.of(" ", "v.STONE(2)", "v.APPLE", "bad(x)", "thing("));
      cfg.set(
          key + ".stats",
          List.of(" ", "bad", "(2)", "attack(", " (2)", "damage(bad)", "damage(2.5)"));
      cfg.set(key + ".lore", List.of("A line"));
      if (n == 1) cfg.set(key + ".sockets.core", 5);
    }
    cfg.save(temp.resolve("parts.yml").toFile());
    assertTrue(new GearLoader().loadFolder(temp.toFile()));
    assertEquals(2, ArchetypeRegistry.size());
    assertEquals(2, PartTypeRegistry.size());
    assertEquals(1, GearModelSchemeRegistry.size());
    assertEquals(5, PartRegistry.size());
    assertEquals(2, PartRegistry.get("part0").getSchemeWeight());
    assertEquals(1, PartRegistry.get("part1").getSchemeWeight());
    assertEquals("", PartRegistry.get("part2").getSchemeId());
    assertEquals(Map.of("damage", 2.5), PartRegistry.get("part0").getStats());
    assertEquals(2, PartRegistry.get("part0").getCost().get("v.STONE"));
  }

  @Test
  void orbSettingsClampBoundsAndRejectUnknownParticlesAndTiers() throws Exception {
    write(
        "orbs.yml",
        "hit_radius: -1\n"
            + "click_range: -1\n"
            + "intro_ticks: -2\n"
            + "good: {dust: [-10, 300, 25], particle: flame}\n"
            + "bad: {dust: [1], particle: invalid}\n"
            + "tiers:\n"
            + "  scalar: 1\n"
            + "  bad: {}\n"
            + "  1: {live: 3, good_ratio: 0.5, speed: 2, window_ticks: 40, good_target: 2}\n");
    assertTrue(new GearLoader().loadFolder(temp.toFile()));
    assertEquals(.1, OrbCache.hitRadius);
    assertEquals(1, OrbCache.clickRange);
    assertEquals(0, OrbCache.introTicks);
    assertEquals(Color.fromRGB(0, 255, 25), OrbCache.goodColor);
    assertEquals(Particle.FLAME, OrbCache.goodParticle);
    assertEquals(1, OrbCache.tierCount());
    write("orbs.yml", "good: {particle: ' '}\n");
    assertTrue(new GearLoader().loadFolder(temp.toFile()));
    assertEquals(0, OrbCache.tierCount());
  }

  @Test
  void negativeCostsDisableTheAffectedPartAndNonFiniteStatsAreIgnored() throws Exception {
    write(
        "parts.yml",
        "bad:\n"
            + "  part-type: core\n"
            + "  type: [staff]\n"
            + "  cost: ['v.STONE(-2)']\n"
            + "good:\n"
            + "  part-type: core\n"
            + "  type: [staff]\n"
            + "  cost: ['v.STONE(2)']\n"
            + "  stats: ['damage(NaN)', 'speed(Infinity)', 'attack(3)']\n");
    assertTrue(new GearLoader().loadFolder(temp.toFile()));
    assertNull(PartRegistry.get("bad"));
    assertNotNull(PartRegistry.get("good"));
    assertEquals(Map.of("attack", 3.), PartRegistry.get("good").getStats());
  }

  @Test
  void emptyRequiredRegistriesFailAndNullableParserInputsAreSafe() throws Exception {
    write("archetypes.yml", "{}");
    write("model-schemes.yml", "{}");
    assertFalse(new GearLoader().loadFolder(temp.toFile()));
    for (String method : List.of("parseCost", "parseStats")) {
      var m = GearLoader.class.getDeclaredMethod(method, List.class);
      m.setAccessible(true);
      assertEquals(Map.of(), m.invoke(null, (Object) null));
      assertEquals(Map.of(), m.invoke(null, Arrays.asList(null, " ")));
    }
    var read = GearLoader.class.getDeclaredMethod("read", java.io.File.class, String.class);
    read.setAccessible(true);
    assertNull(read.invoke(null, null, "test"));
    var color = GearLoader.class.getDeclaredMethod("colorOf", List.class, Color.class);
    color.setAccessible(true);
    assertEquals(Color.RED, color.invoke(null, null, Color.RED));
  }
}
