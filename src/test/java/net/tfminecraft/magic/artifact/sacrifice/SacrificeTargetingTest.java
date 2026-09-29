package net.tfminecraft.magic.artifact.sacrifice;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.*;
import net.tfminecraft.interactiblefurniture.InteractibleFurniture;
import net.tfminecraft.interactiblefurniture.furniture.*;
import net.tfminecraft.magic.*;
import net.tfminecraft.magic.artifact.*;
import net.tfminecraft.magic.artifact.shrine.*;
import org.bukkit.*;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.*;
import org.mockbukkit.mockbukkit.*;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import org.mockito.MockedStatic;

class SacrificeTargetingTest {
  ServerMock server;
  World world;
  PlayerMock caster, victim;
  Furniture furniture;
  PlacedSlot slot;
  ItemStack item;
  ShrineScore score;
  List<Furniture> posts = new ArrayList<>();
  MockedStatic<InteractibleFurniture> bridge;
  MockedStatic<ShrineScorer> scoring;

  @BeforeEach
  void setup() {
    server = MockBukkit.mock();
    world = server.addSimpleWorld("targeting");
    caster = server.addPlayer();
    victim = server.addPlayer();
    caster.teleport(new Location(world, .5, 65, .5));
    victim.teleport(caster.getLocation());
    Magic.plugin = mock(Magic.class);
    when(Magic.plugin.namespace()).thenReturn("magic");
    var dependency = mock(InteractibleFurniture.class, RETURNS_DEEP_STUBS);
    bridge = mockStatic(InteractibleFurniture.class);
    bridge.when(InteractibleFurniture::getInstance).thenReturn(dependency);
    when(dependency.getFurnitureManager().getFurnitureInChunk(any()))
        .thenAnswer(
            i -> {
              Chunk chunk = i.getArgument(0);
              return posts.stream()
                  .filter(
                      p ->
                          p.getLoc() == null
                              || ((p.getLoc().getBlockX() >> 4) == chunk.getX()
                                  && (p.getLoc().getBlockZ() >> 4) == chunk.getZ()))
                  .collect(java.util.stream.Collectors.toSet());
            });
    furniture = mock(Furniture.class);
    when(furniture.getLoc()).thenReturn(new Location(world, 0, 64, 0));
    when(furniture.getId()).thenReturn("pedestal");
    when(furniture.getOriginBlockLocation()).thenReturn(Optional.empty());
    slot = mock(PlacedSlot.class);
    when(slot.getId()).thenReturn("main");
    when(slot.getCurrentItem()).thenAnswer(i -> item);
    when(furniture.getActiveSlots()).thenReturn(Map.of("main", slot));
    posts.add(furniture);
    artifact(0);
    score = new ShrineScore(Map.of("fire", new ShrineElementScore("fire", 100, 1, null, null)));
    scoring = mockStatic(ShrineScorer.class);
    scoring.when(() -> ShrineScorer.score(any())).thenAnswer(i -> score);
    SacrificeRegistry.clear();
    ShrineRegistry.clear();
  }

  @AfterEach
  void cleanup() {
    scoring.close();
    bridge.close();
    SacrificeRegistry.clear();
    ShrineRegistry.clear();
    Magic.plugin = null;
    MockBukkit.unmock();
  }

  void artifact(double fill) {
    item = new ItemStack(Material.STONE);
    var a = Artifact.create();
    a.setCap("fire", 10);
    a.setFill("fire", fill);
    a.persistPdc(item);
  }

  SacrificeTargeting.Result find() {
    return SacrificeTargeting.find(caster, "fire");
  }

  @Test
  void validTargetSelectsClosestPlayerAndAlphabeticalEqualDistanceSlot() {
    var second = mock(PlacedSlot.class);
    when(second.getId()).thenReturn("alpha");
    when(second.getCurrentItem()).thenAnswer(i -> item);
    var ordered = new LinkedHashMap<String, PlacedSlot>();
    ordered.put("main", slot);
    ordered.put("alpha", second);
    when(furniture.getActiveSlots()).thenReturn(ordered);
    var distant = server.addPlayer();
    distant.teleport(new Location(world, 3, 65, 0));
    var result = find();
    assertTrue(result.isOk());
    assertSame(furniture, result.getFurniture());
    assertSame(victim, result.getVictim());
    assertEquals("alpha", result.getSlotId());
    assertSame(score, result.getScore());
    assertEquals(SacrificeTargeting.Fail.NONE, result.getFail());
    assertEquals(
        1, SacrificeTargeting.chebyshevBlocks(caster.getLocation(), new Location(world, 1, 65, 0)));
    assertNotNull(SacrificeTargeting.originCenter(furniture));
  }

  @Test
  void failurePrioritiesExplainFullMissingScoreWeakScoreAndAbsentVictim() {
    artifact(10);
    assertEquals(SacrificeTargeting.Fail.FULL, find().getFail());
    artifact(0);
    score = new ShrineScore(null);
    assertEquals(SacrificeTargeting.Fail.NO_SCORE, find().getFail());
    score = new ShrineScore(Map.of("fire", new ShrineElementScore("fire", 20, 1, null, null)));
    assertEquals(SacrificeTargeting.Fail.MIN_SCORE, find().getFail());
    score = new ShrineScore(Map.of("fire", new ShrineElementScore("fire", 100, 1, null, null)));
    victim.teleport(new Location(world, 20, 65, 0));
    assertEquals(SacrificeTargeting.Fail.NO_TARGET, find().getFail());
    victim.disconnect();
    assertEquals(SacrificeTargeting.Fail.NO_TARGET, find().getFail());
  }

  @Test
  void invalidArgumentsPedestalsItemsAndSchoolAreRejected() {
    assertEquals(
        SacrificeTargeting.Fail.NO_TARGET, SacrificeTargeting.find(null, "fire").getFail());
    assertFalse(SacrificeTargeting.find(caster, null).isOk());
    assertFalse(SacrificeTargeting.find(caster, "").isOk());
    assertFalse(SacrificeTargeting.find(caster, "water").isOk());
    when(furniture.isCarried()).thenReturn(true);
    assertFalse(find().isOk());
    when(furniture.isCarried()).thenReturn(false);
    when(furniture.getId()).thenReturn("chair");
    assertFalse(find().isOk());
    when(furniture.getId()).thenReturn("pedestal");
    when(furniture.getLoc()).thenReturn(new Location(world, 20, 64, 0));
    assertFalse(find().isOk());
    when(furniture.getLoc()).thenReturn(null);
    assertFalse(find().isOk());
    when(furniture.getLoc()).thenReturn(new Location(world, 0, 64, 0));
    item = null;
    assertFalse(find().isOk());
    item = new ItemStack(Material.STONE);
    assertFalse(find().isOk());
    artifact(0);
    when(slot.getId()).thenReturn(null);
    assertFalse(find().isOk());
    bridge.when(InteractibleFurniture::getInstance).thenReturn(null);
    assertTrue(SacrificeTargeting.collectNearby(world, 0, 0).isEmpty());
  }

  @Test
  void explicitSlotFilterAndDisabledSceneryFloorAreRespected() {
    SacrificeRegistry.setGlobals(
        10, 4, null, true, false, 50, 20, true, true, true, "pedestal", "other", null);
    assertFalse(find().isOk());
    SacrificeRegistry.setGlobals(
        10, 4, null, true, false, 50, 20, true, true, true, "pedestal", "MAIN", null);
    score = null;
    assertTrue(find().isOk());
  }

  @Test
  void resultsRequireCompleteTargetsAndUnavailableOriginsAreSkipped() {
    assertFalse(SacrificeTargeting.Result.ok(null, "main", victim, score).isOk());
    assertFalse(SacrificeTargeting.Result.ok(furniture, null, victim, score).isOk());
    assertFalse(SacrificeTargeting.Result.ok(furniture, "main", null, score).isOk());
    when(furniture.getLoc()).thenReturn(new Location(server.addSimpleWorld("foreign"), 0, 64, 0));
    assertFalse(find().isOk());
    when(furniture.getLoc()).thenReturn(new Location(null, 0, 64, 0));
    assertFalse(find().isOk());
  }

  @Test
  void displayStandDistanceBeatsSlotOrderAndDeadVictimsAreExcluded() {
    var second = mock(PlacedSlot.class);
    when(second.getId()).thenReturn("zeta");
    when(second.getCurrentItem()).thenAnswer(i -> item);
    var display = new org.mockbukkit.mockbukkit.entity.ItemDisplayMock(server, UUID.randomUUID());
    display.teleport(caster.getLocation());
    server.registerEntity(display);
    when(second.getDisplayStandId()).thenReturn(display.getUniqueId());
    var ordered = new LinkedHashMap<String, PlacedSlot>();
    ordered.put("main", slot);
    ordered.put("zeta", second);
    when(furniture.getActiveSlots()).thenReturn(ordered);
    assertEquals("zeta", find().getSlotId());
    display.teleport(new Location(world, 3, 65, 0));
    assertEquals("main", find().getSlotId());
    display.teleport(new Location(server.addSimpleWorld("otherdisplay"), 0, 65, 0));
    assertEquals("main", find().getSlotId());
    when(second.getDisplayStandId()).thenReturn(UUID.randomUUID());
    assertEquals("main", find().getSlotId());
    victim.setHealth(0);
    assertFalse(find().isOk());
  }

  @Test
  void missingFurnitureAndSlotEntriesFromDependencyAreSkipped() {
    var manager = InteractibleFurniture.getInstance().getFurnitureManager();
    doReturn(new LinkedHashSet<>(Arrays.asList(null, furniture)))
        .when(manager)
        .getFurnitureInChunk(any());
    var slots = new LinkedHashMap<String, PlacedSlot>();
    slots.put("empty", null);
    slots.put("main", slot);
    when(furniture.getActiveSlots()).thenReturn(slots);
    assertTrue(find().isOk());
  }

  @Test
  void nearestPedestalWinsIndependentlyOfDependencyIterationOrder() {
    var near = mock(Furniture.class);
    when(near.getLoc()).thenReturn(new Location(world, 0, 64, 0));
    when(near.getOriginBlockLocation()).thenReturn(Optional.empty());
    when(near.getId()).thenReturn("pedestal");
    when(near.getActiveSlots()).thenReturn(Map.of("main", slot));
    when(furniture.getLoc()).thenReturn(new Location(world, 2, 64, 0));
    var manager = InteractibleFurniture.getInstance().getFurnitureManager();
    for (var order : List.of(List.of(furniture, near), List.of(near, furniture))) {
      doReturn(new LinkedHashSet<>(order)).when(manager).getFurnitureInChunk(any());
      var result = find();
      assertTrue(result.isOk());
      assertSame(near, result.getFurniture());
    }
    SacrificeRegistry.setGlobals(
        10, 4, null, true, false, 50, 20, true, true, true, "pedestal", "*", null);
    assertTrue(SacrificeTargeting.find(caster, " FIRE ").isOk());
  }
}
