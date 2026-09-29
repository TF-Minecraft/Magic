package net.tfminecraft.magic;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.nio.file.*;
import java.util.*;
import java.util.function.Consumer;
import java.util.logging.Logger;
import net.tfminecraft.magic.artifact.config.*;
import net.tfminecraft.magic.artifact.sacrifice.*;
import net.tfminecraft.magic.artifact.shrine.*;
import net.tfminecraft.magic.loader.ElementsLoader;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.mockbukkit.mockbukkit.MockBukkit;

class ArtifactConfigurationTest {
  @TempDir Path temp;

  @BeforeEach
  void setup() throws Exception {
    MockBukkit.mock();
    Magic.plugin = mock(Magic.class);
    when(Magic.plugin.getLogger()).thenReturn(Logger.getAnonymousLogger());
    new ElementsLoader().loadFolder(Path.of("src/main/resources/elements").toFile());
    Cache.artifactAuraCap = 100;
    reset();
  }

  @AfterEach
  void cleanup() {
    Magic.plugin = null;
    MockBukkit.unmock();
  }

  void reset() throws Exception {
    try (var files = Files.list(Path.of("src/main/resources/artifacts"))) {
      for (Path file : files.toList())
        Files.copy(file, temp.resolve(file.getFileName()), StandardCopyOption.REPLACE_EXISTING);
    }
  }

  void mutate(String name, Consumer<YamlConfiguration> change) throws Exception {
    var file = temp.resolve(name).toFile();
    var yaml = YamlConfiguration.loadConfiguration(file);
    change.accept(yaml);
    yaml.save(file);
  }

  boolean load() {
    return ArtifactConfigLoader.loadFolder(temp.toFile());
  }

  @Test
  void missingFolderMissingFilesAndMalformedYamlFailWithoutStaleRegistries() throws Exception {
    assertFalse(ArtifactConfigLoader.loadFolder(null));
    assertFalse(ArtifactConfigLoader.loadFolder(temp.resolve("absent").toFile()));
    assertFalse(ArtifactConfigLoader.loadFolder(temp.resolve("generator.yml").toFile()));
    for (String name :
        List.of(
            "model-schemes.yml",
            "naming-schemes.yml",
            "generator.yml",
            "adjectives.yml",
            "shrines.yml",
            "sacrifice.yml")) {
      reset();
      Files.delete(temp.resolve(name));
      assertEquals(
          List.of("adjectives.yml", "shrines.yml", "sacrifice.yml").contains(name), load(), name);
      reset();
      Files.writeString(temp.resolve(name), "broken: [unterminated");
      assertEquals(List.of("shrines.yml", "sacrifice.yml").contains(name), load(), name);
    }
  }

  @Test
  void malformedModelNamingAndRarityEntriesAreRejected() throws Exception {
    for (String content :
        List.of(
            "' ': {models: [sword.1]}\n",
            "scalar: bad\n",
            "empty: {}\n",
            "bad: {models: [broken]}\n")) {
      reset();
      Files.writeString(temp.resolve("model-schemes.yml"), content);
      assertFalse(load());
    }
    for (String content :
        List.of("' ': {sword: [name]}\n", "scalar: bad\n", "empty: {sword: [' ', '']}\n")) {
      reset();
      Files.writeString(temp.resolve("naming-schemes.yml"), content);
      assertFalse(load());
    }
    for (Object entry :
        List.of(
            "scalar",
            Map.of("elements", "bad"),
            Map.of("elements", "0"),
            Map.of("elements", "1", "aura_cap", "bad"),
            Map.of("elements", "1", "aura_cap", "0"),
            Map.of("elements", "1", "aura_cap", "101"))) {
      reset();
      mutate("generator.yml", c -> c.set("rarities", Map.of("bad", entry)));
      assertFalse(load());
    }
    reset();
    mutate(
        "generator.yml",
        c -> c.set("rarities", Map.of(" ", Map.of("elements", "1", "aura_cap", "10"))));
    assertFalse(load());
  }

  @Test
  void missingGeneratorFieldsInvalidGroupsAndExcludesAreDiagnosed() throws Exception {
    for (String path : List.of("template", "rarities", "types")) {
      reset();
      mutate("generator.yml", c -> c.set(path, null));
      assertFalse(load(), path);
    }
    reset();
    mutate("generator.yml", c -> c.set("affinity.groups", Map.of(" ", List.of("fire"))));
    assertFalse(load());
    reset();
    mutate(
        "generator.yml",
        c -> {
          c.set("affinity.groups", Map.of("custom", Arrays.asList("fire", "missing", " ")));
          c.set(
              "affinity.exclude",
              List.of(
                  Map.of("never", "fire", "with", List.of("water")),
                  Map.of("never", List.of("fire"))));
        });
    assertFalse(load());
    reset();
    mutate(
        "generator.yml",
        c ->
            c.set(
                "affinity.exclude",
                List.of(
                    Map.of(
                        "never",
                        Arrays.asList(null, " ", "missing", 12),
                        "with",
                        List.of("missing-two")))));
    assertTrue(load());
  }

  @Test
  void invalidTypeFieldsAndCapRangesFailAndPoolAliasesRemainCaseInsensitive() throws Exception {
    String type =
        YamlConfiguration.loadConfiguration(temp.resolve("generator.yml").toFile())
            .getConfigurationSection("types")
            .getKeys(false)
            .iterator()
            .next();
    for (String path :
        List.of("naming-scheme", "model-scheme", "aura_cap", "secondary_cap", "rarities")) {
      reset();
      mutate("generator.yml", c -> c.set("types." + type + "." + path, null));
      assertFalse(load(), path);
    }
    for (String field : List.of("naming-scheme", "model-scheme")) {
      reset();
      mutate("generator.yml", c -> c.set("types." + type + "." + field, "unknown"));
      assertFalse(load());
    }
    reset();
    mutate("generator.yml", c -> c.set("types." + type + ".weight", 0));
    assertFalse(load());
    for (String bad : List.of("invalid", "0", "101")) {
      reset();
      mutate("generator.yml", c -> c.set("types." + type + ".aura_cap", Map.of("common", bad)));
      assertFalse(load());
    }
    reset();
    mutate("generator.yml", c -> c.set("types", Map.of(" ", "scalar", "bad", "scalar")));
    assertFalse(load());
    reset();
    mutate("generator.yml", c -> c.set("random_pool", List.of("unknown")));
    assertFalse(load());
    reset();
    mutate(
        "generator.yml",
        c -> c.set("random_pool", Arrays.asList("", type, type.toUpperCase(Locale.ROOT))));
    assertTrue(load());
    assertEquals(List.of(type), ArtifactGeneratorCache.randomPool);
    reset();
    mutate("generator.yml", c -> c.set("random_pool", type));
    assertTrue(load());
    Cache.artifactAuraCap = 0;
    Cache.debug = true;
    assertTrue(load());
    Cache.debug = false;
  }

  @Test
  void sacrificeLegacyDaggerSectionsResolveActualItemPaths() throws Exception {
    var cases = new LinkedHashMap<Map<String, Object>, String>();
    cases.put(Map.of("path", "v.STONE"), "v.STONE");
    cases.put(Map.of("itemsadder", "runes:dagger"), "ia.runes:dagger");
    cases.put(Map.of("itemsadder", "ia.runes:dagger"), "ia.runes:dagger");
    cases.put(
        Map.of("material", "GOLDEN_SWORD", "custom_model_data", 12),
        "modeled.(type=golden_sword;model=12)");
    cases.put(Map.of("material", ""), "v.golden_sword");
    for (var entry : cases.entrySet()) {
      reset();
      mutate("sacrifice.yml", c -> c.set("dagger", entry.getKey()));
      assertTrue(SacrificeConfigLoader.load(temp.resolve("sacrifice.yml").toFile()));
      assertEquals(entry.getValue(), SacrificeRegistry.getDagger().getPath());
    }
  }

  @Test
  void shrineConfigurationSkipsInvalidEntriesAndKeepsValidFamiliesAndFx() throws Exception {
    var file = temp.resolve("shrines.yml");
    Files.writeString(
        file,
        """
        radius: 2
        fx:
          scalar: bad
          ' ': {}
          default:
            ambient_sound: missing_sound
            start_sound: ' '
            trail_particle: invalid_particle
            emitter_particle: ''
          oseni: {complete_sound: block.beacon.activate, burst_particle: flame}
        elements:
          missing: {}
          oseni:
            families:
              scalar: bad
              ' ': {}
              empty: {blocks: [UNKNOWN, STICK, ''], tags: [' ', missing_tag]}
              good: {blocks: [STONE], tags: [minecraft:logs]}
          seithr: scalar
          cerrith: {}
          mitlan: {families: {}}
        """);
    ShrineRegistry.clear();
    Cache.debug = true;
    assertTrue(ShrineConfigLoader.load(file.toFile()));
    Cache.debug = false;
    assertEquals(1, ShrineRegistry.size());
    assertEquals(1, ShrineRegistry.getById("oseni").getFamilies().size());
    assertEquals(org.bukkit.Particle.FLAME, ShrineRegistry.fx("oseni").getBurstParticle());
    Files.writeString(file, "");
    assertTrue(ShrineConfigLoader.load(file.toFile()));
    assertTrue(ShrineConfigLoader.load(null));
  }

  @Test
  void sacrificeConfigurationHandlesEmptyTiersDuplicateWordsAndOptionalLore() throws Exception {
    var file = temp.resolve("sacrifice.yml");
    Files.writeString(
        file,
        """
        dagger: ''
        case_insensitive: true
        tiers: {scalar: bad, none: {min: 0, below: 1}}
        fx: {surge_tiers: [' ', soul], surge_sound: invalid_sound, boom_sound: ' '}
        elements:
          missing: {}
          seithr: scalar
          oseni: {words: [' ', SAME, SAME]}
          cerrith: {words: [same], lore: {death: old soul}, min_scenery_aura: 3}
        """);
    SacrificeRegistry.clear();
    Cache.debug = true;
    assertTrue(SacrificeConfigLoader.load(file.toFile()));
    Cache.debug = false;
    assertEquals(2, SacrificeRegistry.size());
    assertEquals(2, SacrificeRegistry.getById("oseni").getWords().size());
    mutate(
        "sacrifice.yml",
        c -> {
          c.set("case_insensitive", false);
          c.set("tiers", null);
          c.set("fx", null);
          c.set("elements", null);
        });
    assertTrue(SacrificeConfigLoader.load(file.toFile()));
    assertTrue(SacrificeConfigLoader.load(null));
  }
}
