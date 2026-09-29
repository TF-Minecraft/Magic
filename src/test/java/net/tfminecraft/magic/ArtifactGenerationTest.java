package net.tfminecraft.magic;

import static org.junit.jupiter.api.Assertions.*;

import java.util.*;
import net.tfminecraft.magic.artifact.*;
import net.tfminecraft.magic.artifact.config.*;
import net.tfminecraft.magic.artifact.generate.*;
import net.tfminecraft.magic.artifact.path.*;
import net.tfminecraft.magic.artifact.sacrifice.SacrificeRegistry;
import net.tfminecraft.magic.artifact.shrine.ShrineRegistry;
import org.junit.jupiter.api.*;

class ArtifactGenerationTest {
  @BeforeEach
  void reset() {
    ArtifactTypeRegistry.clear();
    ArtifactRarityRegistry.clear();
    ArtifactAffinityRegistry.clear();
    ArtifactGeneratorCache.reset();
    ShrineRegistry.clear();
    SacrificeRegistry.clear();
    Cache.artifactAuraCap = 150;
    Magic.plugin = null;
  }

  @AfterEach
  void clear() {
    reset();
  }

  static ArtifactTypeDef type(String id, boolean enabled, double weight) {
    return new ArtifactTypeDef(
        id,
        "names",
        "models",
        enabled,
        weight,
        Map.of("common", new CapRange(10, 20)),
        Map.of("common", new CapRange(1, 5)));
  }

  static ArtifactRarityDef rarity(String id, double weight, int min, int max) {
    return new ArtifactRarityDef(id, id, "#ffffff", weight, min, max, new CapRange(0, 100));
  }

  void defaults() {
    ArtifactRarityRegistry.register(rarity("common", 1, 2, 3));
    ArtifactTypeRegistry.register(type("fire", true, 1));
    ArtifactTypeRegistry.register(type("water", true, 1));
    ArtifactTypeRegistry.register(type("earth", true, 1));
    ArtifactAffinityRegistry.registerGroup("all", List.of("fire", "water", "earth"));
  }

  @Test
  void capRangesAndDefaultsStayWithinServerLimit() {
    for (String value : new String[] {null, " ", "3", "bad-3", "3-bad"})
      assertNull(CapRange.parse(value));
    var range = CapRange.parse(" 1.5 - 20 ");
    assertEquals(1.5, range.getMin());
    assertEquals(20, range.getMax());
    assertEquals("1.5-20.0", range.toString());
    assertEquals(150, ArtifactAuraCaps.clampRange(null).getMax());
    assertEquals(0, ArtifactAuraCaps.clampRange(new CapRange(-1, 200)).getMin());
    assertEquals(150, ArtifactAuraCaps.clampRange(new CapRange(-1, 200)).getMax());
    assertEquals(2, ArtifactAuraCaps.clampRange(new CapRange(10, 2)).getMin());
    Cache.artifactAuraCap = 0;
    assertEquals(150, ArtifactAuraCaps.clampRange(null).getMax());
    Cache.artifactAuraCap = 150;
    defaults();
    assertEquals(100, ArtifactAuraCaps.forRarity("common"));
    assertEquals(100, ArtifactAuraCaps.forRarity(" COMMON "));
    assertEquals(150, ArtifactAuraCaps.forRarity(null));
    assertEquals(150, ArtifactAuraCaps.forRarity(" "));
    assertEquals(150, ArtifactAuraCaps.forRarity("missing"));
    assertEquals(20, ArtifactAuraCaps.primaryRange(" FIRE ", "common").getMax());
    assertEquals(5, ArtifactAuraCaps.secondaryRange("fire", "common").getMax());
    assertEquals(100, ArtifactAuraCaps.primaryRange(null, "common").getMax());
    assertEquals(100, ArtifactAuraCaps.secondaryRange("missing", "common").getMax());
    assertEquals(150, ArtifactAuraCaps.forItem(null));
  }

  @Test
  void pathParserAcceptsTypedExtrasAndRejectsMalformedPaths() {
    defaults();
    for (String path :
        new String[] {
          null,
          " ",
          "other.artifact",
          "magic.",
          "magic.bad",
          "magic.(",
          "magic.(primary)",
          "magic.(primary=)",
          "magic.(rarity)",
          "magic.(rarity=)",
          "magic.(fire=bad)"
        }) assertNull(ArtifactPathParser.parse(path));
    assertTrue(ArtifactPathParser.parse(" MAGIC.ARTIFACT ").isFullyRandom());
    assertTrue(ArtifactPathParser.parse("magic.()").isFullyRandom());
    var spec =
        ArtifactPathParser.parse("magic.(element=FIRE;rarity=COMMON;water=2;earth;unknown=1;;=2)");
    assertEquals("fire", spec.getPrimaryId());
    assertEquals("common", spec.getRarityId());
    assertEquals(2, spec.getExtras().get("water"));
    assertTrue(spec.getExtras().containsKey("earth"));
    assertNull(spec.getExtras().get("earth"));
    assertFalse(spec.isFullyRandom());
    assertEquals("unknown", ArtifactPathParser.parse("magic.(primary=UNKNOWN)").getPrimaryId());
    assertThrows(UnsupportedOperationException.class, () -> spec.getExtras().clear());
    assertFalse(new ArtifactPathSpec(null, "common", null).isFullyRandom());
    assertFalse(
        new ArtifactPathSpec(null, null, new LinkedHashMap<>(Map.of("fire", 1.))).isFullyRandom());
  }

  @Test
  void affinityGroupsHonorSymmetricExclusionsAndEnabledTypes() {
    defaults();
    ArtifactTypeRegistry.register(type("disabled", false, 1));
    ArtifactAffinityRegistry.registerGroup(null, List.of());
    ArtifactAffinityRegistry.registerGroup(" ", List.of());
    ArtifactAffinityRegistry.registerGroup("bad", null);
    ArtifactAffinityRegistry.registerGroup(
        "second", List.of("FIRE", "WATER", "missing", "disabled"));
    ArtifactAffinityRegistry.registerGroup("unrelated", List.of("earth"));
    assertEquals(3, ArtifactAffinityRegistry.size());
    assertEquals(List.of(), ArtifactAffinityRegistry.getGroup(null));
    assertEquals(List.of(), ArtifactAffinityRegistry.getGroup(" "));
    assertEquals(List.of(), ArtifactAffinityRegistry.getGroup("missing"));
    assertEquals(List.of("fire", "water", "earth"), ArtifactAffinityRegistry.getGroup("ALL"));
    assertEquals(3, ArtifactAffinityRegistry.getGroups().size());
    ArtifactAffinityRegistry.registerExclude(null, List.of("fire"));
    ArtifactAffinityRegistry.registerExclude(List.of("fire"), List.of());
    ArtifactAffinityRegistry.registerExclude(Arrays.asList(null, " ", " fire "), List.of("earth"));
    assertEquals(1, ArtifactAffinityRegistry.excludeSize());
    assertFalse(ArtifactAffinityRegistry.compatible(null, "a"));
    assertFalse(ArtifactAffinityRegistry.compatible("a", null));
    assertFalse(ArtifactAffinityRegistry.compatible(" ", "a"));
    assertFalse(ArtifactAffinityRegistry.compatible("a", " "));
    assertTrue(ArtifactAffinityRegistry.compatible("fire", "FIRE"));
    assertFalse(ArtifactAffinityRegistry.compatible("fire", "earth"));
    assertFalse(ArtifactAffinityRegistry.compatible("earth", "fire"));
    assertTrue(ArtifactAffinityRegistry.compatible("water", "earth"));
    assertFalse(ArtifactAffinityRegistry.compatibleWith(null, null));
    assertFalse(ArtifactAffinityRegistry.compatibleWith(null, " "));
    assertTrue(ArtifactAffinityRegistry.compatibleWith(null, "fire"));
    assertTrue(ArtifactAffinityRegistry.compatibleWith(List.of(), "fire"));
    assertTrue(
        ArtifactAffinityRegistry.compatibleWith(Arrays.asList(null, " ", "fire", "water"), "fire"));
    assertFalse(ArtifactAffinityRegistry.compatibleWith(List.of("earth"), "fire"));
    assertEquals(List.of(), ArtifactAffinityRegistry.companions(null));
    assertEquals(List.of(), ArtifactAffinityRegistry.companions(" "));
    assertTrue(ArtifactAffinityRegistry.companions("fire").contains("water"));
    assertFalse(ArtifactAffinityRegistry.companions("fire").contains("earth"));
  }

  @Test
  void randomPoolCopiesAndNormalizesLookups() {
    assertTrue(ArtifactGeneratorCache.inRandomPool(null));
    ArtifactGeneratorCache.randomPool = null;
    assertTrue(ArtifactGeneratorCache.inRandomPool("any"));
    ArtifactGeneratorCache.setRandomPool(Arrays.asList(null, "fire"));
    assertFalse(ArtifactGeneratorCache.inRandomPool(null));
    assertFalse(ArtifactGeneratorCache.inRandomPool(" "));
    assertFalse(ArtifactGeneratorCache.inRandomPool("water"));
    assertTrue(ArtifactGeneratorCache.inRandomPool(" FIRE "));
    ArtifactGeneratorCache.setRandomPool(null);
    assertTrue(ArtifactGeneratorCache.inRandomPool("any"));
    ArtifactGeneratorCache.setRandomPool(List.of());
  }

  @Test
  void rollsReportConfigurationErrorsPrecisely() {
    var roller = new ArtifactRoller(new Random(1));
    assertEquals("artifact.roll.empty_rarities", roller.roll(null, null).getErrorKey());
    assertEquals("artifact.roll.unknown_rarity", roller.roll("random", "unknown").getErrorKey());
    assertEquals("artifact.roll.unknown_element", roller.roll("missing", "common").getErrorKey());
    ArtifactTypeRegistry.register(type("disabled", false, 1));
    assertEquals("artifact.roll.disabled", roller.roll("disabled", "common").getErrorKey());
    ArtifactTypeRegistry.register(type("fire", true, 1));
    assertEquals("artifact.roll.empty_rarities", roller.roll("fire", " ").getErrorKey());
    assertEquals("artifact.roll.unknown_rarity", roller.roll("fire", "missing").getErrorKey());
    ArtifactRarityRegistry.register(rarity("rare", 1, 1, 1));
    assertEquals("artifact.roll.no_bracket", roller.roll("fire", "rare").getErrorKey());
    assertEquals("artifact.roll.empty_types", roller.roll("random", "rare").getErrorKey());
    assertEquals(
        "artifact.roll.unknown_element",
        roller.roll(new ArtifactPathSpec("unknown", null, null)).getErrorKey());
    assertEquals(
        "artifact.roll.disabled",
        roller.roll(new ArtifactPathSpec("disabled", null, null)).getErrorKey());
    assertEquals(
        "artifact.roll.empty_rarities",
        roller.roll(new ArtifactPathSpec("fire", "unknown", null)).getErrorKey());
    assertEquals(
        "artifact.roll.no_bracket",
        roller.roll(new ArtifactPathSpec("fire", "rare", null)).getErrorKey());
  }

  @Test
  void seededRandomRollsRespectRangesAffinitiesAndConfiguredPool() {
    defaults();
    ArtifactGeneratorCache.setRandomPool(List.of("fire"));
    ArtifactTypeRegistry.register(type("disabled", false, 20));
    ArtifactTypeRegistry.register(type("zero", true, 0));
    ArtifactRarityRegistry.register(rarity("zero", 0, 1, 1));
    var roller = new ArtifactRoller(new Random(234));
    for (int i = 0; i < 30; i++) {
      var roll = roller.roll("random", "roll");
      assertFalse(roll.isError());
      assertEquals(ArtifactRoll.Status.OK, roll.getStatus());
      assertNull(roll.getErrorKey());
      assertEquals("fire", roll.getPrimaryId());
      assertEquals("common", roll.getRarityId());
      assertTrue(roll.getSlots().size() >= 2);
      assertTrue(roll.getSlots().size() <= 3);
      assertTrue(roll.getSlots().getFirst().getCap() >= 10);
      assertTrue(roll.getSlots().getFirst().getCap() <= 20);
      assertEquals(
          roll.getSlots().size(),
          roll.getSlots().stream().map(ArtifactAuraSlot::getElementId).distinct().count());
    }
    assertFalse(roller.roll((ArtifactPathSpec) null).isError());
    assertFalse(roller.roll(new ArtifactPathSpec(" ", "common", null)).isError());
    assertFalse(roller.roll("FIRE", "COMMON").isError());
    assertNotNull(new ArtifactRoller());
    assertNotNull(new ArtifactRoller(null));
  }

  @Test
  void explicitRollsClampRequestedCapsAndSkipUnavailableExtras() {
    defaults();
    ArtifactTypeRegistry.register(type("disabled", false, 1));
    ArtifactTypeRegistry.register(
        new ArtifactTypeDef("nobracket", "", "", true, 1, Map.of(), Map.of()));
    var extras = new LinkedHashMap<String, Double>();
    extras.put("fire", 100.);
    extras.put("water", -1.);
    extras.put("earth", null);
    extras.put(null, 1.);
    extras.put("missing", 1.);
    extras.put("disabled", 1.);
    extras.put("nobracket", 1.);
    var roll =
        new ArtifactRoller(new Random(1)).roll(new ArtifactPathSpec("fire", "common", extras));
    assertFalse(roll.isError());
    assertEquals(3, roll.getSlots().size());
    assertEquals(20, roll.getSlots().getFirst().getCap());
    assertEquals(1, roll.getSlots().get(1).getCap());
    ArtifactAffinityRegistry.registerExclude(List.of("fire"), List.of("water", "earth"));
    roll = new ArtifactRoller(new Random(1)).roll(new ArtifactPathSpec("fire", "common", extras));
    assertEquals(1, roll.getSlots().size());
    assertEquals(List.of(), ArtifactRoll.ok("fire", "common", null).getSlots());
    assertTrue(ArtifactRoll.error("bad").isError());
  }

  @Test
  void namingAndModelSchemesValidateAndCopyContent() {
    defaults();
    ArtifactRarityRegistry.register(rarity("rare", 1, 1, 1));
    ArtifactRarityRegistry.register(rarity("epic", 1, 1, 1));
    var names = new LinkedHashMap<String, List<String>>();
    names.put(null, List.of("bad"));
    names.put(" ", List.of("bad"));
    names.put("missing", null);
    names.put("empty", List.of());
    names.put("WAND", List.of("Spark"));
    var scheme = new ArtifactNamingScheme("fire", names);
    assertEquals("fire", scheme.getId());
    assertFalse(scheme.isEmpty());
    assertEquals(List.of("Spark"), scheme.namesFor("wand"));
    assertEquals(List.of(), scheme.namesFor(null));
    assertEquals(List.of(), scheme.namesFor(" "));
    assertEquals(List.of(), scheme.namesFor("missing"));
    assertEquals(List.of("Spark"), scheme.firstNonEmptyKindNames());
    assertTrue(new ArtifactNamingScheme("empty", null).isEmpty());
    assertEquals(List.of(), new ArtifactNamingScheme("empty", null).firstNonEmptyKindNames());
    for (String raw : new String[] {null, " ", "bad", "(path)", "wand(", "wand()", "  (path)"})
      assertNull(ArtifactModelEntry.parse(raw));
    var model = ArtifactModelEntry.parse("wand(v.STICK)epic-common");
    assertEquals("wand", model.getKind());
    assertEquals("v.STICK", model.getPath());
    assertEquals("epic", model.getMinRarityId());
    assertEquals("common", model.getMaxRarityId());
    assertTrue(model.eligible("rare"));
    assertFalse(model.eligible(null));
    assertFalse(model.eligible(" "));
    assertFalse(model.eligible("unknown"));
    assertTrue(ArtifactModelEntry.parse("wand(v.STICK)all").eligible("common"));
    assertFalse(ArtifactModelEntry.parse("wand(v.STICK)").eligible("unknown"));
    assertTrue(ArtifactModelEntry.parse("wand(v.STICK)rare").eligible("rare"));
    assertFalse(ArtifactModelEntry.parse("wand(v.STICK)rare").eligible("common"));
    assertFalse(ArtifactModelEntry.parse("wand(v.STICK)rare").eligible("epic"));
    var models = new ArtifactModelScheme("x", List.of(model));
    assertEquals("x", models.getId());
    assertEquals(List.of(model), models.getModels());
    assertThrows(UnsupportedOperationException.class, () -> models.getModels().clear());
  }
}
