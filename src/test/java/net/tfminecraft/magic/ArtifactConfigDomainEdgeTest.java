package net.tfminecraft.magic;

import static org.junit.jupiter.api.Assertions.*;

import java.util.*;
import net.tfminecraft.magic.artifact.config.*;
import org.junit.jupiter.api.*;

class ArtifactConfigDomainEdgeTest {
  @BeforeEach
  @AfterEach
  void reset() {
    ArtifactTypeRegistry.clear();
    ArtifactRarityRegistry.clear();
    ArtifactNamingSchemeRegistry.clear();
    ArtifactModelSchemeRegistry.clear();
    ArtifactAdjectiveRegistry.clear();
    ArtifactAffinityRegistry.clear();
  }

  @Test
  void registriesRejectMissingAndBlankIdsAndExposeIds() {
    for (String id : Arrays.asList(null, " ")) {
      ArtifactTypeRegistry.register(new ArtifactTypeDef(id, "", "", true, 1, Map.of(), Map.of()));
      ArtifactRarityRegistry.register(new ArtifactRarityDef(id, "", "", 1, 1, 1, null));
      ArtifactNamingSchemeRegistry.register(new ArtifactNamingScheme(id, null));
      ArtifactModelSchemeRegistry.register(new ArtifactModelScheme(id, List.of()));
    }
    ArtifactTypeRegistry.register(null);
    ArtifactRarityRegistry.register(null);
    ArtifactNamingSchemeRegistry.register(null);
    ArtifactModelSchemeRegistry.register(null);
    assertEquals(0, ArtifactTypeRegistry.size());
    assertEquals(0, ArtifactRarityRegistry.size());
    ArtifactNamingSchemeRegistry.register(new ArtifactNamingScheme("names", null));
    ArtifactModelSchemeRegistry.register(new ArtifactModelScheme("models", List.of()));
    assertEquals(
        List.of("names"),
        ArtifactNamingSchemeRegistry.getAll().stream().map(ArtifactNamingScheme::getId).toList());
    assertEquals(
        List.of("models"),
        ArtifactModelSchemeRegistry.getAll().stream().map(ArtifactModelScheme::getId).toList());
  }

  @Test
  void capLookupsAreCaseInsensitiveAndHighestRangeWins() {
    var caps = new LinkedHashMap<String, CapRange>();
    caps.put(null, new CapRange(0, 5));
    caps.put("COMMON", new CapRange(1, 10));
    caps.put("rare", new CapRange(1, 30));
    caps.put("low", new CapRange(1, 20));
    var type = new ArtifactTypeDef("fire", "names", "models", true, 1, caps, caps);
    assertEquals(10, type.getCap(" common ").getMax());
    assertEquals(30, type.highestCap().getMax());
    assertTrue(type.hasRarity("COMMON"));
    assertFalse(type.hasRarity("missing"));
    assertNull(type.getPrimaryCap(null));
    assertNull(type.getSecondaryCap(" "));
    assertEquals(caps, type.getCapsByRarity());
    assertEquals(caps.keySet(), type.getRarityIds());
    assertNull(new ArtifactTypeDef("empty", "", "", true, 1, Map.of(), Map.of()).highestCap());
    assertEquals(0, new ArtifactRarityDef("r", "", "", 1, 1, 1, null).getAuraCapMax());
  }

  @Test
  void modelRangesRequireBothKnownBoundsAndAcceptValidSuffixForms() {
    ArtifactRarityRegistry.register(new ArtifactRarityDef("common", "", "", 1, 1, 1, null));
    for (String[] bounds :
        new String[][] {
          {null, "common"},
          {"common", null},
          {" ", "common"},
          {"missing", "common"},
          {"common", "missing"}
        })
      assertFalse(
          new ArtifactModelEntry("wand", "v.STICK", bounds[0], bounds[1]).eligible("common"));
    assertNotNull(ArtifactModelEntry.parse("wand(v.STICK)rare-"));
    assertNotNull(ArtifactModelEntry.parse("wand(v.STICK)-rare"));
    assertEquals(List.of(), ArtifactAdjectiveRegistry.pool(" ", "common"));
  }
}
