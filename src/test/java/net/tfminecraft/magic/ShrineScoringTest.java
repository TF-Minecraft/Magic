package net.tfminecraft.magic;

import static org.junit.jupiter.api.Assertions.*;

import java.util.*;
import net.tfminecraft.magic.artifact.shrine.*;
import org.bukkit.*;
import org.junit.jupiter.api.*;
import org.mockbukkit.mockbukkit.*;

class ShrineScoringTest {
  ServerMock server;

  @BeforeEach
  void setup() {
    server = MockBukkit.mock();
    ShrineRegistry.clear();
    Cache.artifactAuraCap = 100;
  }

  @AfterEach
  void cleanup() {
    ShrineRegistry.clear();
    MockBukkit.unmock();
    Cache.artifactAuraCap = 150;
  }

  @Test
  void invalidCentersAndSparseShrinesGiveNoAura() {
    assertTrue(ShrineScorer.score(null).getByElement().isEmpty());
    assertTrue(ShrineScorer.score(new Location(null, 0, 0, 0)).getByElement().isEmpty());
    var world = server.addSimpleWorld("world");
    var center = new Location(world, 0, 70, 0);
    ShrineRegistry.setScan(1, .2, 10, 2);
    ShrineRegistry.register(
        new ShrineElementDef(
            "fire",
            List.of(
                new ShrineFamily("stone", 10, 1, Set.of(Material.STONE)),
                new ShrineFamily("wood", 10, 1, Set.of(Material.OAK_PLANKS)))));
    world.getBlockAt(0, 70, 0).setType(Material.STONE);
    assertEquals(0, ShrineScorer.score(center).get("fire").getMaxAura());
    world.getBlockAt(1, 70, 0).setType(Material.STONE);
    var score = ShrineScorer.score(center).get("fire");
    assertEquals(0, score.getMaxAura());
    assertEquals(List.of("stone"), score.getActiveFamilyIds());
    assertEquals(List.of(), score.getContributingBlocks());
  }

  @Test
  void varietyAndFamilyCapsDetermineAuraAndEmittersAreUniqueStableAndBounded() {
    var world = server.addSimpleWorld("world");
    var center = new Location(world, 0, 70, 0);
    ShrineRegistry.setScan(2, 0, 10, 2);
    var stone = new ShrineFamily("stone", 4, 2, Set.of(Material.STONE, Material.COBBLESTONE));
    var wood = new ShrineFamily("wood", 2, 1, Set.of(Material.OAK_PLANKS));
    ShrineRegistry.register(new ShrineElementDef("fire", List.of(stone, wood)));
    ShrineRegistry.register(new ShrineElementDef("empty", null));
    for (int x = -2; x <= 2; x++)
      for (int z = -2; z <= 2; z++) world.getBlockAt(x, 70, z).setType(Material.STONE);
    world.getBlockAt(-2, 70, -2).setType(Material.OAK_PLANKS);
    world.getBlockAt(2, 70, 2).setType(Material.OAK_PLANKS);
    var first = ShrineScorer.score(center);
    var fire = first.get("fire");
    assertEquals(100, fire.getMaxAura());
    assertEquals(10, fire.getAuraPerSecond());
    assertEquals(List.of("stone", "wood"), fire.getActiveFamilyIds());
    assertEquals(14, fire.getContributingBlocks().size());
    assertEquals(14, new HashSet<>(fire.getContributingBlocks()).size());
    assertEquals(
        fire.getContributingBlocks(),
        ShrineScorer.score(center).get("fire").getContributingBlocks());
    assertEquals(0, first.get("empty").getMaxAura());
    assertEquals(List.of(), first.get("empty").getContributingBlocks());
  }

  @Test
  void familyAndRegistryDefinitionsNormalizeAndDefendCopies() {
    var family = new ShrineFamily("f", 0, -1, null);
    assertEquals("f", family.getId());
    assertEquals(1, family.getMaxCount());
    assertEquals(1, family.getWeight());
    assertFalse(family.matches(null));
    assertFalse(family.matches(Material.STONE));
    assertTrue(new ShrineFamily("s", 1, 1, Set.of(Material.STONE)).matches(Material.STONE));
    var def =
        new ShrineElementDef(
            " FIRE ", false, List.of(new ShrineFamily("s", 2, 3, Set.of(Material.STONE))));
    assertEquals("fire", def.getElementId());
    assertFalse(def.isSceneryCharge());
    assertEquals(6, def.perfectScore());
    assertEquals("", new ShrineElementDef(null, null).getElementId());
    ShrineRegistry.register(null);
    ShrineRegistry.register(new ShrineElementDef(" ", null));
    assertEquals(0, ShrineRegistry.size());
    ShrineRegistry.register(def);
    assertSame(def, ShrineRegistry.getById(" FIRE "));
    assertNull(ShrineRegistry.getById(null));
    assertNull(ShrineRegistry.getById(" "));
    assertEquals(List.of(def), ShrineRegistry.getAll());
    ShrineRegistry.setScan(0, -1, -1, 0);
    assertEquals(1, ShrineRegistry.getRadius());
    assertEquals(0, ShrineRegistry.getMinScore());
    assertEquals(15, ShrineRegistry.getFullChargeSeconds());
    assertEquals(1, ShrineRegistry.getMinFamilies());
    ShrineRegistry.setDefaultFx(null);
    var fallback = ShrineRegistry.fx(null);
    assertSame(fallback, ShrineRegistry.fx(" "));
    assertSame(fallback, ShrineRegistry.fx("missing"));
    ShrineRegistry.setElementFx(null, fallback);
    ShrineRegistry.setElementFx(" ", fallback);
    ShrineRegistry.setElementFx("fire", null);
    var specific = ShrineFxDef.fallback();
    ShrineRegistry.setElementFx(" FIRE ", specific);
    assertSame(specific, ShrineRegistry.fx("fire"));
    ShrineRegistry.setDefaultFx(specific);
    assertSame(specific, ShrineRegistry.fx(null));
  }

  static Object call(String name, Class<?>[] types, Object... args) throws Exception {
    var method = ShrineScorer.class.getDeclaredMethod(name, types);
    method.setAccessible(true);
    return method.invoke(null, args);
  }

  @Test
  void emitterSelectionDeduplicatesFamiliesAndHandlesSparseInvalidCoordinates() throws Exception {
    var world = server.addSimpleWorld("world");
    var a = new Location(world, 0, 0, 0);
    var b = new Location(world, 1, 0, 0);
    var c = new Location(world, -1, 0, 0);
    var groups = Arrays.asList(null, Arrays.asList(null, a, a, b, c));
    assertEquals(List.of(a, b, c), call("uniqueLocations", new Class[] {List.class}, groups));
    assertEquals(
        b,
        call(
            "farthestFrom",
            new Class[] {List.class, Location.class, Set.class},
            Arrays.asList(null, a, b, c),
            a,
            Set.of()));
    assertEquals("|0|0|0", call("key", new Class[] {Location.class}, new Location(null, 0, 0, 0)));
    assertEquals(
        3,
        ((List<?>)
                call(
                    "pickEmitters",
                    new Class[] {List.class, Location.class, long.class},
                    List.of(List.of(a, b, c), List.of(a, b, c)),
                    a,
                    1L))
            .size());
    assertEquals(
        2,
        ((List<?>)
                call(
                    "pickEmitters",
                    new Class[] {List.class, Location.class, long.class},
                    List.of(List.of(a, b)),
                    new Location(world, Double.NaN, 0, 0),
                    1L))
            .size());
  }
}
