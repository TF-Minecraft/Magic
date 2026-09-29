package net.tfminecraft.magic;

import static net.tfminecraft.magic.ArtifactGenerationTest.rarity;
import static net.tfminecraft.magic.ArtifactGenerationTest.type;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.*;
import net.tfminecraft.magic.artifact.config.*;
import net.tfminecraft.magic.artifact.generate.*;
import net.tfminecraft.magic.artifact.path.*;
import net.tfminecraft.magic.artifact.shrine.*;
import org.junit.jupiter.api.*;

class ArtifactRollerEdgeTest {
  @BeforeEach
  @AfterEach
  void reset() {
    new ArtifactGenerationTest().reset();
  }

  void defaults() {
    new ArtifactGenerationTest().defaults();
  }

  @Test
  void randomAndLockedErrorsHandleEachRandomRarityForm() {
    var roller = new ArtifactRoller(new Random(1));
    for (String rarity : new String[] {null, " ", "roll"})
      assertEquals("artifact.roll.empty_rarities", roller.roll("random", rarity).getErrorKey());
    ArtifactTypeRegistry.register(type("fire", true, 1));
    for (String rarity : new String[] {null, " ", "roll"})
      assertEquals("artifact.roll.empty_rarities", roller.roll("fire", rarity).getErrorKey());
    assertEquals(
        "artifact.roll.empty_rarities",
        roller.roll(new ArtifactPathSpec(null, null, null)).getErrorKey());
  }

  @Test
  void overflowedWeightsStillRespectProbabilities() {
    var random = mock(Random.class);
    when(random.nextDouble()).thenReturn(.25);
    var roller = new ArtifactRoller(random);
    ArtifactRarityRegistry.register(rarity("common", 1, 1, 1));
    ArtifactTypeRegistry.register(type("fire", true, Double.MAX_VALUE));
    ArtifactTypeRegistry.register(type("water", true, Double.MAX_VALUE));
    assertEquals("fire", roller.roll("random", "common").getPrimaryId());
  }

  @Test
  void nonFiniteCompanionWeightsCannotWin() {
    ArtifactRarityRegistry.register(rarity("common", 1, 2, 2));
    ArtifactTypeRegistry.register(type("fire", true, 1));
    ArtifactTypeRegistry.register(type("bad", true, Double.NaN));
    ArtifactTypeRegistry.register(type("water", true, 1));
    ArtifactTypeRegistry.register(type("earth", true, 1));
    ArtifactAffinityRegistry.registerGroup("all", List.of("fire", "bad", "water", "earth"));
    var random = mock(Random.class);
    when(random.nextDouble()).thenReturn(.1);
    var result = new ArtifactRoller(random).roll("fire", "common");
    assertEquals(
        List.of("fire", "water"),
        result.getSlots().stream().map(ArtifactAuraSlot::getElementId).toList());
  }

  @Test
  void nonPositiveCompanionPoolAndExclusionsStopSelection() {
    defaults();
    ArtifactTypeRegistry.register(type("water", true, 0));
    ArtifactTypeRegistry.register(type("earth", true, -1));
    assertEquals(1, new ArtifactRoller(new Random(1)).roll("fire", "common").getSlots().size());
    ArtifactTypeRegistry.register(type("water", true, 1));
    ArtifactTypeRegistry.register(type("earth", true, 1));
    ArtifactAffinityRegistry.registerExclude(List.of("water"), List.of("earth"));
    ArtifactRarityRegistry.register(rarity("common", 1, 3, 3));
    assertEquals(2, new ArtifactRoller(new Random(1)).roll("fire", "common").getSlots().size());
  }

  @Test
  void hiddenCompanionsAndMissingBracketsAreSkippedForRandomAndLockedRolls() {
    defaults();
    ArtifactTypeRegistry.register(
        new ArtifactTypeDef("earth", "names", "models", true, 1, Map.of(), Map.of()));
    ShrineRegistry.register(new ShrineElementDef("water", false, List.of()));
    var roller = new ArtifactRoller(new Random(1));
    assertEquals(1, roller.roll("fire", "common").getSlots().size());
    assertEquals(
        1,
        roller
            .roll(new ArtifactPathSpec("fire", "common", new LinkedHashMap<>(Map.of("water", 1.))))
            .getSlots()
            .size());
  }

  @Test
  void zeroAndFixedCapRangesUsePositiveMinimumAndBoundedFallback() {
    ArtifactRarityRegistry.register(rarity("common", 1, 1, 1));
    ArtifactTypeRegistry.register(
        new ArtifactTypeDef(
            "fire", "names", "models", true, 1, Map.of("common", new CapRange(0, 0)), Map.of()));
    var roller = new ArtifactRoller(new Random(1));
    assertEquals(.01, roller.roll("fire", "common").getSlots().getFirst().getCap());
    assertEquals(
        .01,
        roller
            .roll(new ArtifactPathSpec("fire", "common", new LinkedHashMap<>(Map.of("fire", 0.))))
            .getSlots()
            .getFirst()
            .getCap());
    ArtifactTypeRegistry.register(
        new ArtifactTypeDef(
            "fire", "names", "models", true, 1, Map.of("common", new CapRange(10, 10)), Map.of()));
    assertEquals(10, roller.roll("fire", "common").getSlots().getFirst().getCap());
    assertEquals(
        10,
        roller
            .roll(new ArtifactPathSpec("fire", "common", new LinkedHashMap<>(Map.of("fire", 20.))))
            .getSlots()
            .getFirst()
            .getCap());
  }

  @Test
  void infiniteWeightsAreIgnoredAndBlankPrimaryRollsRandomly() {
    defaults();
    ArtifactTypeRegistry.register(type("infinite", true, Double.POSITIVE_INFINITY));
    ArtifactRarityRegistry.register(rarity("infinite", Double.POSITIVE_INFINITY, 1, 1));
    var roller = new ArtifactRoller(new Random(1));
    assertFalse(roller.roll(" ", "roll").isError());
    var result =
        roller.roll(
            new ArtifactPathSpec(
                "fire", "common", new LinkedHashMap<>(Map.of("water", Double.NaN))));
    assertEquals(1, result.getSlots().size());
  }
}
