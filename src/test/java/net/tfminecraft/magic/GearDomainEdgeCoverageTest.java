package net.tfminecraft.magic;

import static org.junit.jupiter.api.Assertions.*;

import java.util.*;
import net.tfminecraft.magic.gear.*;
import net.tfminecraft.magic.gear.orb.OrbCache;
import org.junit.jupiter.api.*;

class GearDomainEdgeCoverageTest extends GearCoverageSupport {
  @AfterEach
  void registries() {
    PartTypeRegistry.clear();
    OrbCache.clearTiers();
    SocketLayout.clearLabels();
  }

  @Test
  void orbDifficultyFallsBackToLowestConfiguredTierRegardlessOfYamlOrder() {
    OrbCache.clearTiers();
    var high = new OrbCache.Tier(4, 0.7, 2, 40, 4);
    var low = new OrbCache.Tier(1, 1, 1, 30, 1);
    OrbCache.putTier(4, high);
    OrbCache.putTier(1, low);
    assertSame(low, OrbCache.tier(0));
  }

  @Test
  void invalidTiersAndNearestLowerRowsHaveDeterministicDefaults() {
    OrbCache.clearTiers();
    assertEquals(4, OrbCache.tier(0).live());
    OrbCache.putTier(0, new OrbCache.Tier(1, 1, 0, 20, 1));
    OrbCache.putTier(1, null);
    assertEquals(0, OrbCache.tierCount());
    var two = new OrbCache.Tier(2, .5, 1, 20, 2);
    var one = new OrbCache.Tier(1, .5, -1, 20, 1);
    var four = new OrbCache.Tier(4, .5, 1, 20, 4);
    OrbCache.putTier(2, two);
    OrbCache.putTier(1, one);
    OrbCache.putTier(4, four);
    assertSame(two, OrbCache.tier(3));
    assertSame(four, OrbCache.tier(4));
    assertEquals(1, one.speed());
    assertTrue(OrbCache.spawnGap(4, 0) > 0);
    assertEquals(1, OrbCache.spawnGap(1, 1));
  }

  @Test
  void registriesRejectMissingKeysAndApplyAllFilters() {
    PartRegistry.register(null);
    PartRegistry.register(
        new PartDef(
            null, " ", null, 0, Set.of(), null, null, null, null, null, null, null, 0, false));
    assertEquals(0, PartRegistry.size());
    assertNull(PartRegistry.get(null));
    var good = part("good", 1);
    PartRegistry.register(good);
    PartRegistry.register(
        new PartDef(
            "disabled",
            "name",
            "core",
            1,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            1,
            true));
    assertEquals(List.of(good), PartRegistry.matching(null, null));
    assertTrue(PartRegistry.matching("other", null).isEmpty());
    assertTrue(PartRegistry.matching(null, GearType.WAND).isEmpty());
    assertEquals(List.of(good), PartRegistry.matching("core", GearType.STAFF));
    assertSame(good, PartRegistry.firstMatching("core", GearType.STAFF));
    assertNull(PartRegistry.firstMatching("missing", GearType.STAFF));
    assertNotNull(GearKeys.partPick());
    assertNotNull(GearKeys.stationDisplay());
    PartTypeRegistry.register(null);
    PartTypeRegistry.register(new PartTypeDef(null, 0, null));
    assertNull(PartTypeRegistry.get(null));
    assertEquals(0, PartTypeRegistry.size());
    PartTypeRegistry.register(new PartTypeDef("core", 1, "Core"));
    assertEquals(1, PartTypeRegistry.getAll().size());
    assertEquals("core", PartTypeRegistry.idForSlot(1));
    assertNull(PartTypeRegistry.idForSlot(2));
    ArchetypeRegistry.register(null);
    ArchetypeRegistry.register(new ArchetypeDef(null, "name", null, null, false, null, null));
    assertEquals(0, ArchetypeRegistry.size());
    ArchetypeRegistry.register(GearDefinitionTest.archetype());
    assertEquals(1, ArchetypeRegistry.getAll().size());
  }

  @Test
  void slotsHandleWhitespaceDuplicatesAndRestrictedCoreCategories() {
    var archetype =
        new ArchetypeDef(
            GearType.STAFF,
            "name",
            null,
            null,
            false,
            List.of(" head ", "HEAD", " ", "grip"),
            null);
    assertEquals(List.of("head", "grip"), PartSlots.open(archetype, null));
    var core = GearDefinitionTest.part("core", 1, List.of("core", "head", "absent"), Map.of());
    assertEquals(List.of("head"), PartSlots.open(archetype, core));
    assertEquals(17, MajorityTierResolver.resolve(List.of(part("a", 17), part("b", 2))));
    assertEquals(List.of(), SocketLayout.colours(GearDefinitionTest.archetype(), null, 1));
    assertEquals(
        1, SocketLayout.colours(GearDefinitionTest.archetype(), List.of(part("one", 1)), 1).size());
    assertEquals(
        List.of(),
        SocketLayout.colours(GearDefinitionTest.archetype(), Arrays.asList((PartDef) null), 1));
  }

  @Test
  void socketCountsDoNotWrapAroundAndEraseSockets() {
    var archetype = GearDefinitionTest.archetype();
    var a = GearDefinitionTest.part("a", 1, List.of(), Map.of("spell", Integer.MAX_VALUE));
    var b = GearDefinitionTest.part("b", 1, List.of(), Map.of("spell", Integer.MAX_VALUE));
    assertEquals(4, SocketLayout.colours(archetype, List.of(a, b), 1).size());
    assertEquals(Integer.MAX_VALUE, SocketLayout.rawTotal(archetype, List.of(a, b)));
    assertEquals(5, SocketLayout.previewLines(archetype, List.of(a, b)).size());
  }
}
