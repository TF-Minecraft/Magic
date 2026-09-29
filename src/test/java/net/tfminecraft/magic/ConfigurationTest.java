package net.tfminecraft.magic;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.nio.file.*;
import java.util.*;
import java.util.logging.Logger;
import net.tfminecraft.magic.artifact.config.*;
import net.tfminecraft.magic.charge.*;
import net.tfminecraft.magic.gear.*;
import net.tfminecraft.magic.loader.*;
import net.tfminecraft.magic.registry.*;
import net.tfminecraft.magic.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

class ConfigurationTest {
  @TempDir Path temp;

  @BeforeEach
  void plugin() {
    org.mockbukkit.mockbukkit.MockBukkit.mock();
    Magic.plugin = mock(Magic.class);
    when(Magic.plugin.getLogger()).thenReturn(Logger.getAnonymousLogger());
    when(Magic.plugin.getDataFolder()).thenReturn(temp.toFile());
  }

  @AfterEach
  void cleanup() {
    Magic.plugin = null;
    org.mockbukkit.mockbukkit.MockBukkit.unmock();
  }

  Path write(String name, String data) throws Exception {
    Path file = temp.resolve(name);
    Files.writeString(file, data);
    return file;
  }

  @Test
  void shippedConfigurationLoadsConsistentRegistries() {
    Path root = Path.of("src/main/resources");
    assertTrue(new ConfigLoader().loadSafe(root.resolve("config.yml").toFile()));
    assertTrue(new GuiLoader().loadSafe(root.resolve("gui.yml").toFile()));
    assertTrue(new ElementsLoader().loadFolder(root.resolve("elements").toFile()));
    assertTrue(ElementRegistry.size() > 0);
    assertTrue(new SkillsLoader().loadSafe(root.resolve("skills.yml").toFile()));
    assertTrue(SkillElementRegistry.size() > 0);
    assertTrue(new ChargesLoader().loadSafe(root.resolve("charges.yml").toFile()));
    assertTrue(ChargeRegistry.size() > 0);
    assertTrue(new GearLoader().loadFolder(root.resolve("gear").toFile()));
    assertTrue(ArtifactConfigLoader.loadFolder(root.resolve("artifacts").toFile()));
    assertTrue(ArtifactTypeRegistry.size() > 0);
  }

  @Test
  void missingAndMalformedFilesReportFailure() throws Exception {
    var missing = temp.resolve("missing").toFile();
    var malformed = write("bad.yml", "bad: [broken").toFile();
    for (var file : List.of(missing, malformed)) {
      assertFalse(new ConfigLoader().loadSafe(file));
      assertFalse(new GuiLoader().loadSafe(file));
      assertFalse(new SkillsLoader().loadSafe(file));
      assertFalse(new ChargesLoader().loadSafe(file));
    }
    assertFalse(new SkillsLoader().loadSafe(null));
    assertFalse(new ChargesLoader().loadSafe(null));
    new ConfigLoader().load(malformed);
    new GuiLoader().load(malformed);
  }

  @Test
  void emptyConfigurationUsesDefaultsAndNoEntries() throws Exception {
    var empty = write("empty.yml", "").toFile();
    assertTrue(new ConfigLoader().loadSafe(empty));
    assertTrue(new GuiLoader().loadSafe(empty));
    assertTrue(new SkillsLoader().loadSafe(empty));
    assertTrue(new ChargesLoader().loadSafe(empty));
    assertEquals(0, SkillElementRegistry.size());
    assertEquals(0, ChargeRegistry.size());
  }

  @Test
  void skillBindingsNormalizeValidateAndClampTiers() throws Exception {
    ElementRegistry.clear();
    ElementRegistry.register(DomainTest.element("fire"));
    var file =
        write(
            "skills.yml",
            "plain: fire\n"
                + "high:\n"
                + "  element: ' FIRE '\n"
                + "  tier: 99\n"
                + "low:\n"
                + "  element: fire\n"
                + "  tier: -1\n"
                + "normal:\n"
                + "  element: fire\n"
                + "  tier: 2\n"
                + "missing:\n"
                + "  tier: 1\n"
                + "blank: ''\n"
                + "unknown: water\n' ': fire\nno_tier: {element: fire}\n");
    assertTrue(new SkillsLoader().loadSafe(file.toFile()));
    assertEquals(5, SkillElementRegistry.size());
    assertEquals(4, SkillElementRegistry.tierOf("high"));
    assertEquals(1, SkillElementRegistry.tierOf("low"));
    assertEquals(2, SkillElementRegistry.tierOf("normal"));
  }

  @Test
  void chargeLoaderSkipsMalformedTierAndBandEntries() throws Exception {
    ElementRegistry.clear();
    ElementRegistry.register(DomainTest.element("fire"));
    var file =
        write(
            "charges.yml",
            "tiers:\n"
                + "  scalar: value\n"
                + "  text: {item: v.STONE, aura_cap: 10}\n"
                + "  1: {item: v.STONE, aura_cap: 10}\n"
                + "  2: {aura_cap: 10}\n"
                + "  3: {item: '', aura_cap: 10}\n"
                + "  4: {item: v.STONE, aura_cap: 0}\n"
                + "bands:\n"
                + "  scalar: 2\n"
                + "  unknown: {1: 2}\n"
                + "  fire: {bad: 1, 1: 2}\n"
                + "  default: {1: 3}\n");
    assertTrue(new ChargesLoader().loadSafe(file.toFile()));
    assertEquals(1, ChargeRegistry.size());
    assertEquals(1, TierBands.bandOf("fire", 2));
  }

  @Test
  void guiRejectsInvalidAndDuplicateSlots() throws Exception {
    var file =
        write(
            "gui.yml",
            "slots: {character_head: -1, cast_mode_left: 54, cast_mode_right: 55, element_row: 9}\n"
                + "artifact_create:\n"
                + "  slots: {preview: -1, cap_minus: 5, cap_plus: 5}\n");
    assertFalse(new GuiLoader().loadSafe(file.toFile()));
    assertTrue(new GuiLoader().loadSafe(Path.of("src/main/resources/gui.yml").toFile()));
  }

  @Test
  void gridPartitionsEverySlotExactlyOnce() {
    assertEquals(20, GridLayout.slot(2, 2));
    assertEquals(GuiCache.characterHeadSlot, GridLayout.characterHeadSlot());
    assertTrue(GridLayout.isCharacterHeadSlot(GuiCache.characterHeadSlot));
    assertFalse(GridLayout.isCharacterHeadSlot(-1));
    assertTrue(GridLayout.isCastModeSlot(GuiCache.castModeLeftSlot));
    assertTrue(GridLayout.isCastModeSlot(GuiCache.castModeRightSlot));
    assertFalse(GridLayout.isCastModeSlot(-1));
    Set<Integer> all = new HashSet<>(GridLayout.reservedSlots());
    all.addAll(GridLayout.fillerSlots());
    assertEquals(54, all.size());
    assertEquals(
        GridLayout.fillerSlots().size(),
        GridLayout.borderFillerSlots().size() + GridLayout.innerFillerSlots().size());
    assertFalse(GridLayout.isBorderSlot(-1));
    assertFalse(GridLayout.isBorderSlot(54));
    for (int i = 0; i < 54; i++) {
      assertEquals(GridLayout.elementSlots().contains(i), GridLayout.isElementSlot(i));
      assertEquals(GridLayout.fillerSlots().contains(i), GridLayout.isFillerSlot(i));
    }
    assertTrue(GridLayout.validateSlots());
  }

  @Test
  void elementFolderRejectsDuplicatesInvalidSlotsAndNonSections() throws Exception {
    var loader = new ElementsLoader();
    assertFalse(loader.loadFolder(temp.resolve("missing").toFile()));
    var scalar = write("plain.txt", "text");
    assertFalse(loader.loadFolder(scalar.toFile()));
    Path folder = Files.createDirectory(temp.resolve("elements"));
    assertFalse(loader.loadFolder(folder.toFile()));
    Files.writeString(
        folder.resolve("a.YAML"), "fire: {slot: 10, icon: v.STONE}\nblank: {slot: 11, icon: ''}\n");
    Files.writeString(
        folder.resolve("b.yml"),
        "fire: {slot: 12}\n"
            + "duplicate: {slot: 10}\n"
            + "negative: {slot: -1}\n"
            + "overflow: {slot: 54}\n"
            + "scalar: text\n"
            + "' ': {slot: 13}\n"
            + "water: {slot: 14}\n");
    Files.writeString(folder.resolve("ignore.txt"), "not yaml");
    Files.createDirectory(folder.resolve("directory.yml"));
    assertFalse(loader.loadFolder(folder.toFile()));
    assertEquals(3, ElementRegistry.size());
    assertEquals(2, YamlFolder.listYamlFiles(folder.toFile()).size());
    assertFalse(loader.loadSafe(write("malformed.yml", "bad: [").toFile()));
    loader.load(temp.resolve("missing").toFile());
    assertEquals(List.of(), YamlFolder.listYamlFiles(null));
    assertEquals(List.of(), YamlFolder.listYamlFiles(temp.resolve("absent").toFile()));
    assertEquals(List.of(), YamlFolder.listYamlFiles(scalar.toFile()));
  }

  @Test
  void absentArtifactConfigPreservesBothDisabledAndPositiveAuraCaps() throws Exception {
    var loader = new ConfigLoader();
    var file = write("empty.yml", "{}").toFile();
    double previous = Cache.artifactAuraCap;
    try {
      Cache.artifactAuraCap = 0;
      assertTrue(loader.loadSafe(file));
      assertEquals(0, Cache.artifactAuraCap);
      Cache.artifactAuraCap = 25;
      assertTrue(loader.loadSafe(file));
      assertEquals(25, Cache.artifactAuraCap);
    } finally {
      Cache.artifactAuraCap = previous;
    }
  }

  @Test
  void runtimeConfigValidatesBoundsAndSupportsLegacyFields() throws Exception {
    var loader = new ConfigLoader();
    assertTrue(
        loader.loadSafe(
            write(
                    "legacy.yml",
                    "default_corruption_tranquility: 7\n"
                        + "tick: {interval_ticks: -1, seconds_per_hour: 0}\n"
                        + "cast_drift: {mana_divisor: 0, min: 0}\n"
                        + "artifacts: {aura_cap: -1}\n"
                        + "runes: {types: [' ', ' STAFF '], keybinds: [' ', 'invalid', 'CAST']}\n"
                        + "gear: {station: ' ', output-slot: -1, alignment: {enabled: true, text:"
                        + " {mana: 1}, scalar: true, 2: {mana: 0.1}}}\n"
                        + "attunement: {display_furniture: ' ', muffled: {legacy: 2}}\n")
                .toFile()));
    assertEquals(7, Cache.defaultEquilibrium);
    assertEquals(3600, Cache.secondsPerHour);
    assertEquals(Set.of("staff"), Cache.runeTypes);
    assertTrue(GearCache.alignmentEnabled);
    assertEquals(.1, GearCache.alignment(2).mana());
    assertTrue(
        loader.loadSafe(
            write(
                    "fallback.yml",
                    "equilibrium: {surge: {}, tranquility: {}}\n"
                        + "gear: {output-slot: 99}\n"
                        + "attunement: {users: {ttl_days: 0}, muffled: {off_per_hour: -1,"
                        + " recover_per_hour: -1}}\n")
                .toFile()));
    assertFalse(GearCache.alignmentEnabled);
    assertEquals(1, net.tfminecraft.magic.attunement.ArtifactCareCache.usersTtlDays);
    assertEquals(0, net.tfminecraft.magic.attunement.ArtifactCareCache.muffledOffPerHour);
    loader.load(Path.of("src/main/resources/config.yml").toFile());
  }

  @Test
  void attunementWithoutMuffledSectionDisablesMuffling() throws Exception {
    assertTrue(
        new ConfigLoader()
            .loadSafe(write("no-muffle.yml", "attunement: {users: {ttl_days: 3}}\n").toFile()));
    assertFalse(net.tfminecraft.magic.attunement.ArtifactCareCache.muffledEnabled);
  }
}
