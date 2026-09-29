package net.tfminecraft.magic;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.*;
import java.util.*;
import net.tfminecraft.magic.artifact.*;
import net.tfminecraft.magic.artifact.config.*;
import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

class ArtifactConfigEdgeTest {
  @TempDir Path temp;
  ArtifactConfigurationTest f = new ArtifactConfigurationTest();

  @BeforeEach
  void setup() throws Exception {
    f.temp = temp;
    f.setup();
    org.mockito.Mockito.when(Magic.plugin.namespace()).thenReturn("magic");
  }

  @AfterEach
  void cleanup() {
    f.cleanup();
  }

  Object call(String name, Class<?>[] types, Object... args) throws Exception {
    var m = ArtifactConfigLoader.class.getDeclaredMethod(name, types);
    m.setAccessible(true);
    return m.invoke(null, args);
  }

  YamlConfiguration yaml(String text) throws Exception {
    var c = new YamlConfiguration();
    c.loadFromString(text);
    return c;
  }

  @Test
  void duplicateDefinitionsAreRejectedOnRepeatedLoads() throws Exception {
    assertTrue(f.load());
    for (String[] pair :
        new String[][] {
          {"loadModelSchemes", "model-schemes.yml"},
          {"loadNamingSchemes", "naming-schemes.yml"},
          {"loadGenerator", "generator.yml"}
        })
      assertEquals(
          false, call(pair[0], new Class[] {java.io.File.class}, temp.resolve(pair[1]).toFile()));
    var c = yaml("planar_four: [oseni]\n");
    assertEquals(
        false,
        call(
            "loadGroups",
            new Class[] {org.bukkit.configuration.ConfigurationSection.class, String.class},
            c,
            "test"));
  }

  @Test
  void rarityAndCapValidationRejectsReversedAndEmptyRangesAndClampsCeiling() throws Exception {
    for (String[] values :
        new String[][] {
          {"2-1", "0-10"},
          {"0-1", "0-10"},
          {"1-1", "5-2"},
          {"1-1", "0-0"},
          {"1-1", "bad"},
          {"1-1", "200-300"}
        }) {
      var c = yaml("bad: {elements: '" + values[0] + "', aura_cap: '" + values[1] + "'}\n");
      assertEquals(
          false,
          call(
              "loadRarities",
              new Class[] {org.bukkit.configuration.ConfigurationSection.class, String.class},
              c,
              "test"));
    }
    var c = yaml("clamped: {elements: '1-2', aura_cap: '0-200'}\n");
    assertEquals(
        true,
        call(
            "loadRarities",
            new Class[] {org.bukkit.configuration.ConfigurationSection.class, String.class},
            c,
            "test"));
    assertEquals(100, ArtifactRarityRegistry.getById("clamped").getAuraCapMax());
    for (String value : List.of("5-2", "0-0", "200-300")) {
      assertNull(
          call(
              "parseCapMap",
              new Class[] {
                org.bukkit.configuration.ConfigurationSection.class,
                String.class,
                String.class,
                String.class
              },
              yaml("common: '" + value + "'\n"),
              "test",
              "fire",
              "aura_cap"));
    }
  }

  @Test
  void missingPoolsAndCaseInsensitiveCapKeysResolveWithoutInventingTypes() throws Exception {
    assertTrue(f.load());
    var c = yaml("{}\n");
    assertEquals(
        true,
        call("loadRandomPool", new Class[] {YamlConfiguration.class, String.class}, c, "test"));
    assertFalse(ArtifactGeneratorCache.randomPool.isEmpty());
    ArtifactAffinityRegistry.clear();
    assertEquals(
        true,
        call("loadRandomPool", new Class[] {YamlConfiguration.class, String.class}, c, "test"));
    assertTrue(ArtifactGeneratorCache.randomPool.isEmpty());
    var ranges = Map.of("COMMON", new CapRange(1, 2), "other", new CapRange(3, 4));
    assertSame(
        ranges.get("COMMON"),
        call("lookupIgnoreCase", new Class[] {Map.class, String.class}, ranges, "common"));
    assertNull(call("lookupIgnoreCase", new Class[] {Map.class, String.class}, ranges, "missing"));
  }

  @Test
  void nonFiniteRangeEndpointsAreRejected() {
    for (String raw : List.of("NaN-10", "1-NaN", "Infinity-10", "1-Infinity"))
      assertNull(CapRange.parse(raw), raw);
  }

  @Test
  void optionalAdjectiveSectionsAndBlankWordsAreSupported() throws Exception {
    assertTrue(ArtifactAdjectiveLoader.load(null));
    Files.writeString(
        temp.resolve("adjectives.yml"),
        "global: {common: [' ', 'Hot']}\nby_element: {scalar: bad, fire: {common: [Fire]}}\n");
    ArtifactAdjectiveRegistry.clear();
    assertTrue(ArtifactAdjectiveLoader.load(temp.resolve("adjectives.yml").toFile()));
    assertEquals(List.of("Hot", "Fire"), ArtifactAdjectiveRegistry.pool("fire", "common"));
    Files.writeString(temp.resolve("adjectives.yml"), "");
    assertTrue(ArtifactAdjectiveLoader.load(temp.resolve("adjectives.yml").toFile()));
  }

  @Test
  void itemAuraCapsResolvePrimaryRarityAndFallbacks() {
    ArtifactTypeRegistry.clear();
    ArtifactRarityRegistry.clear();
    var item = new ItemStack(Material.STONE);
    assertEquals(100, ArtifactAuraCaps.forItem(item));
    assertEquals(100, ArtifactAuraCaps.forItem(new ItemStack(Material.AIR)));
    var meta = item.getItemMeta();
    meta.setDisplayName("item");
    item.setItemMeta(meta);
    assertEquals(100, ArtifactAuraCaps.forItem(item));
    meta.getPersistentDataContainer()
        .set(ArtifactKeys.artifactRarity(), PersistentDataType.STRING, "common");
    item.setItemMeta(meta);
    ArtifactRarityRegistry.register(new ArtifactRarityDef("common", "Common", "", 1, 1, 1, null));
    assertEquals(100, ArtifactAuraCaps.forItem(item));
    ArtifactRarityRegistry.register(
        new ArtifactRarityDef("common", "Common", "", 1, 1, 1, new CapRange(0, 20)));
    assertEquals(20, ArtifactAuraCaps.forItem(item));
    meta.getPersistentDataContainer()
        .set(ArtifactKeys.artifactPrimary(), PersistentDataType.STRING, " ");
    item.setItemMeta(meta);
    assertEquals(20, ArtifactAuraCaps.forItem(item));
    meta.getPersistentDataContainer()
        .set(ArtifactKeys.artifactPrimary(), PersistentDataType.STRING, "fire");
    item.setItemMeta(meta);
    assertEquals(20, ArtifactAuraCaps.forItem(item));
    Cache.artifactAuraCap = 0;
    assertEquals(150, ArtifactAuraCaps.forItem(null));
    assertEquals(20, ArtifactAuraCaps.primaryRange(" ", "common").getMax());
  }

  @Test
  void nullableHelpersAndUnknownTypesHavePredictableFallbacks() throws Exception {
    assertEquals(
        true, call("loadExcludes", new Class[] {List.class, String.class}, List.of(), "test"));
    assertEquals(true, call("loadExcludes", new Class[] {List.class, String.class}, null, "test"));
    assertEquals(
        true,
        call(
            "loadExcludes",
            new Class[] {List.class, String.class},
            Arrays.asList((Object) null),
            "test"));
    assertNull(call("lookupIgnoreCase", new Class[] {Map.class, String.class}, null, "common"));
    assertNull(call("lookupIgnoreCase", new Class[] {Map.class, String.class}, Map.of(), null));
    var map = new LinkedHashMap<String, CapRange>();
    map.put(null, new CapRange(1, 2));
    assertNull(call("lookupIgnoreCase", new Class[] {Map.class, String.class}, map, "common"));
    assertNull(call("loadYaml", new Class[] {java.io.File.class}, (Object) null));
    var config = YamlConfiguration.loadConfiguration(temp.resolve("generator.yml").toFile());
    var type = config.getConfigurationSection("types").getKeys(false).iterator().next();
    for (String mode : List.of("blank", "missingPrimary", "missingSecondary", "case")) {
      f.reset();
      f.mutate(
          "generator.yml",
          c -> {
            String root = "types." + type;
            c.set(root + ".rarities", Map.of(" ", 1, "COMMON", 1));
            if (mode.equals("missingPrimary")) c.set(root + ".aura_cap", Map.of("other", "1-10"));
            if (mode.equals("missingSecondary"))
              c.set(root + ".secondary_cap", Map.of("other", "1-10"));
            if (mode.equals("blank")) {
              c.set(root + ".aura_cap", Map.of(" ", "1-10"));
              c.set(root + ".secondary_cap", Map.of(" ", "1-10"));
            }
          });
      assertEquals(mode.equals("case"), f.load(), mode);
    }
    f.reset();
    f.mutate(
        "generator.yml",
        c -> {
          var values = c.getConfigurationSection("types." + type).getValues(true);
          c.set("types", null);
          for (var e : values.entrySet()) c.set("types.unknown." + e.getKey(), e.getValue());
          c.set("random_pool", List.of("unknown"));
          c.set("affinity.groups", null);
        });
    assertTrue(f.load());
  }

  @Test
  void unrepresentableElementCountsAndNonFiniteTypeWeightsAreRejected() throws Exception {
    f.mutate("generator.yml", y -> y.set("types.oseni.disable", true));
    assertTrue(f.load());
    f.reset();
    var c = yaml("bad: {elements: '1-2147483648', aura_cap: '0-10'}\n");
    assertEquals(
        false,
        call(
            "loadRarities",
            new Class[] {org.bukkit.configuration.ConfigurationSection.class, String.class},
            c,
            "test"));
    var config = YamlConfiguration.loadConfiguration(temp.resolve("generator.yml").toFile());
    var type = config.getConfigurationSection("types").getKeys(false).iterator().next();
    for (double weight : new double[] {Double.NaN, Double.POSITIVE_INFINITY}) {
      f.reset();
      f.mutate("generator.yml", y -> y.set("types." + type + ".weight", weight));
      assertFalse(f.load());
    }
  }
}
