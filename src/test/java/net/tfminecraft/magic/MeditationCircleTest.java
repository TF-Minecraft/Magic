package net.tfminecraft.magic;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.*;
import net.tfminecraft.interactiblefurniture.InteractibleFurniture;
import net.tfminecraft.interactiblefurniture.furniture.*;
import net.tfminecraft.magic.artifact.*;
import net.tfminecraft.magic.charge.*;
import net.tfminecraft.magic.meditation.*;
import net.tfminecraft.magic.registry.ElementRegistry;
import net.tfminecraft.magic.session.ResonanceSession;
import org.bukkit.*;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.*;
import org.mockbukkit.mockbukkit.*;
import org.mockito.MockedStatic;

class MeditationCircleTest {
  ServerMock server;
  World world;
  Location center;
  List<Furniture> furniture = new ArrayList<>();
  List<PlacedSlot> slots = new ArrayList<>();
  MockedStatic<InteractibleFurniture> bridge;
  InteractibleFurniture dependency;

  @BeforeEach
  void setup() {
    server = MockBukkit.mock();
    world = server.addSimpleWorld("circle");
    center = new Location(world, .5, 70, .5);
    Magic.plugin = mock(Magic.class);
    when(Magic.plugin.namespace()).thenReturn("magic");
    MockBukkit.createMockPlugin("InteractibleFurniture");
    dependency = mock(InteractibleFurniture.class, RETURNS_DEEP_STUBS);
    bridge = mockStatic(InteractibleFurniture.class);
    bridge.when(InteractibleFurniture::getInstance).thenReturn(dependency);
    when(dependency.getFurnitureManager().getFurnitureInChunk(any()))
        .thenAnswer(
            i -> {
              Chunk chunk = i.getArgument(0);
              return furniture.stream()
                  .filter(
                      f -> {
                        var l = f.getLoc();
                        return l != null
                            && l.getWorld() != null
                            && (l.getBlockX() >> 4) == chunk.getX()
                            && (l.getBlockZ() >> 4) == chunk.getZ();
                      })
                  .collect(java.util.stream.Collectors.toSet());
            });
    MeditationCache.cardinalOffset = 4;
    MeditationCache.diagonalOffset = 3;
    MeditationCache.pedestalId = "pedestal";
    MeditationCache.pedestalSlot = "*";
    ElementRegistry.clear();
    ElementRegistry.register(DomainTest.element("fire"));
    ChargeRegistry.clear();
    Cache.defaultEquilibrium = 0;
  }

  @AfterEach
  void cleanup() {
    bridge.close();
    Magic.plugin = null;
    MockBukkit.unmock();
    ElementRegistry.clear();
    ChargeRegistry.clear();
    MeditationCache.pedestalSlot = "*";
  }

  ItemStack artifact() {
    var item = new ItemStack(Material.STONE);
    var data = Artifact.create();
    data.setCap("fire", 10);
    data.setFill("fire", 5);
    data.persistPdc(item);
    ArtifactIds.writeNew(item);
    return item;
  }

  void putArtifact() {
    var item = artifact();
    when(slots.getFirst().getCurrentItem()).thenReturn(item);
  }

  void ring() {
    int[][] positions = {{4, 0}, {-4, 0}, {0, 4}, {0, -4}, {3, 3}, {3, -3}, {-3, 3}, {-3, -3}};
    for (var pos : positions) {
      var f = mock(Furniture.class);
      var l = new Location(world, pos[0] + .5, 70, pos[1] + .5);
      when(f.getLoc()).thenReturn(l);
      when(f.getOriginBlockLocation()).thenReturn(Optional.of(l));
      when(f.getId()).thenReturn("pedestal");
      when(f.getEntityId()).thenReturn(UUID.randomUUID());
      var slot = mock(PlacedSlot.class);
      when(slot.getId()).thenReturn("top");
      when(f.getActiveSlots()).thenReturn(Map.of("top", slot));
      when(f.getActiveSlot("top")).thenReturn(Optional.of(slot));
      furniture.add(f);
      slots.add(slot);
    }
  }

  @Test
  void lonePreviewUserGetsFullYieldInsteadOfBeingCountedTwice() {
    ring();
    putArtifact();
    var circle = MeditationCircle.detect(center);
    assertNotNull(circle);
    var yield = circle.snapshotYield(1000, "new-character");
    String id = circle.artifactIdOn(furniture.getFirst());
    assertEquals(1, yield.users(id));
    assertEquals(5, yield.sessionCap(id));
  }

  @Test
  void completeRingDetectsArtifactPowerAndSnapshotsExistingUsers() {
    assertNull(MeditationCircle.detect(null));
    assertNull(MeditationCircle.detect(new Location(null, 0, 0, 0)));
    assertNull(MeditationCircle.detect(center));
    ring();
    var item = artifact();
    when(slots.getFirst().getCurrentItem()).thenReturn(item);
    var circle = MeditationCircle.detect(center);
    assertNotNull(circle);
    assertEquals(center, circle.getCenter());
    assertEquals(8, circle.getPedestals().size());
    assertEquals(1, circle.getArtifactPedestals().size());
    assertEquals(5, circle.getTotalPower());
    assertEquals(Map.of("fire", 5.), circle.getPowerByElement());
    assertEquals(5, circle.elementPower("fire"));
    assertEquals(5, circle.elementPower("FIRE"));
    assertEquals(0, circle.elementPower(null));
    assertEquals(0, circle.elementPower(" "));
    assertEquals(0, circle.elementPower("missing"));
    assertEquals("fire", circle.artifactFor(furniture.getFirst().getEntityId()).elementId);
    assertSame(item, circle.itemOn(furniture.getFirst()));
    assertNull(circle.itemOn(null));
    assertNull(circle.itemOn(furniture.get(1)));
    assertTrue(circle.stillIntact());
    assertTrue(MeditationCircle.containing(furniture.getFirst()));
    var yield = circle.stampAndSnapshot("user", 1000);
    String id = circle.artifactIdOn(furniture.getFirst());
    assertEquals(1, yield.users(id));
    assertEquals(5, yield.sessionCap(id));
    assertEquals(1, circle.snapshotYield(1000, "user").users(id));
    assertEquals(2, circle.snapshotYield(1000, "second").users(id));
    assertEquals(2.5, circle.snapshotYield(1000, "second").sessionCap(id));
    assertFalse(circle.isFullyAttuned(Map.of(), yield));
    assertTrue(circle.isFullyAttuned(Map.of(id, 5.), yield));
    assertTrue(circle.isFullyAttuned(Map.of(), null));
    var session = new ResonanceSession();
    assertFalse(circle.canGainResonance(null, null, yield));
    assertFalse(circle.canGainResonance(null, session, null));
    assertTrue(circle.canGainResonance(null, session, yield));
    session.setResonance("fire", 5);
    assertFalse(circle.canGainResonance(null, session, yield));
    assertFalse(circle.canGainResonance(null, session, MeditationSitYield.empty()));
    circle.stampArtifacts(null, Long.MAX_VALUE);
    circle.stampArtifacts(" ", Long.MAX_VALUE);
    assertTrue(ArtifactCareStore.readUsers(item).isEmpty());
  }

  @Test
  void carriedWrongTypeOrMisplacedPostBreaksDetection() {
    assertFalse(MeditationCircle.containing(null));
    ring();
    when(furniture.getFirst().isCarried()).thenReturn(true);
    assertNull(MeditationCircle.detect(center));
    assertFalse(MeditationCircle.containing(furniture.getFirst()));
    when(furniture.getFirst().isCarried()).thenReturn(false);
    when(furniture.getFirst().getId()).thenReturn("other");
    assertNull(MeditationCircle.detect(center));
    when(furniture.getFirst().getId()).thenReturn("pedestal");
    var far = new Location(world, 4.5, 75, .5);
    when(furniture.getFirst().getOriginBlockLocation()).thenReturn(Optional.of(far));
    assertNull(MeditationCircle.detect(center));
    when(furniture.getFirst().getOriginBlockLocation()).thenReturn(Optional.empty());
    assertNotNull(MeditationCircle.detect(center));
    when(furniture.getFirst().getLoc()).thenReturn(null);
    assertFalse(MeditationCircle.containing(furniture.getFirst()));
  }

  @Test
  void specificSlotAndEmptyArtifactsDoNotContributePower() {
    ring();
    MeditationCache.pedestalSlot = "missing";
    assertEquals(0, MeditationCircle.detect(center).getTotalPower());
    MeditationCache.pedestalSlot = "top";
    when(slots.getFirst().getCurrentItem()).thenReturn(new ItemStack(Material.AIR));
    assertEquals(0, MeditationCircle.detect(center).getTotalPower());
    when(slots.getFirst().getCurrentItem()).thenReturn(new ItemStack(Material.STONE));
    assertEquals(0, MeditationCircle.detect(center).getTotalPower());
    var empty = artifact();
    var a = Artifact.fromItem(empty);
    a.setFill("fire", 0);
    a.persistPdc(empty);
    when(slots.getFirst().getCurrentItem()).thenReturn(empty);
    assertEquals(0, MeditationCircle.detect(center).getTotalPower());
    putArtifact();
    assertEquals(5, MeditationCircle.detect(center).getTotalPower());
  }

  @Test
  void removedItemsInvalidateYieldAndUnlockedElementsControlGains() {
    ring();
    putArtifact();
    var circle = MeditationCircle.detect(center);
    var yield = circle.snapshotYield(1000);
    var session = new ResonanceSession();
    var cfg = new org.bukkit.configuration.file.YamlConfiguration();
    cfg.set("permission", "secret.fire");
    ElementRegistry.register(new net.tfminecraft.magic.model.ElementDef("fire", cfg));
    assertFalse(circle.canGainResonance(server.addPlayer(), session, yield));
    ElementRegistry.clear();
    assertTrue(circle.canGainResonance(null, session, yield));
    when(slots.getFirst().getCurrentItem()).thenReturn(null);
    circle.stampArtifacts("user", 1000);
    assertTrue(circle.snapshotYield(1000).exhausted(Map.of()));
    assertFalse(circle.canGainResonance(null, session, yield));
    when(slots.getFirst().getCurrentItem()).thenReturn(new ItemStack(Material.AIR));
    circle.stampArtifacts("user", 1000);
    when(slots.getFirst().getCurrentItem()).thenReturn(new ItemStack(Material.STONE));
    circle.stampArtifacts("user", 1000);
  }

  @Test
  void displayFallbackAndMultipleElementsContributeOnlyStoredArtifactFill() {
    ring();
    var item = artifact();
    var aura = Artifact.fromItem(item);
    aura.setCap("water", 10);
    aura.setFill("water", 3);
    aura.setCap("unknown", 10);
    aura.setFill("unknown", 2);
    aura.setCap("empty", 10);
    aura.persistPdc(item);
    ElementRegistry.register(DomainTest.element("water"));
    ElementRegistry.register(DomainTest.element("empty"));
    var display = new org.mockbukkit.mockbukkit.entity.ItemDisplayMock(server, UUID.randomUUID());
    display.teleport(center);
    server.registerEntity(display);
    display.setItemStack(item);
    when(slots.getFirst().getDisplayStandId()).thenReturn(display.getUniqueId());
    var circle = MeditationCircle.detect(center);
    assertEquals(8, circle.getTotalPower());
    verify(slots.getFirst(), atLeastOnce()).setModel(item);
    MeditationCache.pedestalSlot = "top";
    circle.stampArtifacts("user", 1000);
    MeditationCache.pedestalSlot = "";
    assertEquals(8, MeditationCircle.detect(center).getTotalPower());
    MeditationCache.pedestalSlot = null;
    assertEquals(8, MeditationCircle.detect(center).getTotalPower());
    display.setItemStack(new ItemStack(Material.AIR));
    assertEquals(0, MeditationCircle.detect(center).getTotalPower());
    var charge = artifact();
    var meta = charge.getItemMeta();
    meta.getPersistentDataContainer()
        .set(ChargeKeys.chargeTier(), org.bukkit.persistence.PersistentDataType.INTEGER, 1);
    charge.setItemMeta(meta);
    when(slots.getFirst().getCurrentItem()).thenReturn(charge);
    assertEquals(0, MeditationCircle.detect(center).getTotalPower());
  }

  @Test
  void nonmemberPedestalsAndUnavailableDependencyCannotFormCircles() {
    ring();
    var lone = mock(Furniture.class);
    var far = new Location(world, 100, 70, 100);
    when(lone.getLoc()).thenReturn(far);
    when(lone.getOriginBlockLocation()).thenReturn(Optional.of(far));
    when(lone.getEntityId()).thenReturn(UUID.randomUUID());
    assertFalse(MeditationCircle.containing(lone));
    when(lone.getEntityId()).thenReturn(null);
    assertFalse(MeditationCircle.containing(lone));
    when(lone.getLoc()).thenReturn(new Location(null, 0, 0, 0));
    when(lone.getOriginBlockLocation()).thenReturn(Optional.empty());
    assertFalse(MeditationCircle.containing(lone));
    var plugin = server.getPluginManager().getPlugin("InteractibleFurniture");
    server.getPluginManager().disablePlugin(plugin);
    assertNull(MeditationCircle.detect(center));
  }

  @Test
  void snapshotsTolerateItemChangesAndPublicPowerMapLegacyValues() {
    ring();
    putArtifact();
    var circle = MeditationCircle.detect(center);
    var charge = artifact();
    var meta = charge.getItemMeta();
    meta.getPersistentDataContainer()
        .set(ChargeKeys.chargeTier(), org.bukkit.persistence.PersistentDataType.INTEGER, 1);
    charge.setItemMeta(meta);
    when(slots.getFirst().getCurrentItem()).thenReturn(charge);
    assertFalse(circle.snapshotYield(1, " ").hasAnyCap());
    circle.getPowerByElement().put(null, 3d);
    circle.getPowerByElement().put("Fire", null);
    circle.getPowerByElement().remove("fire");
    assertEquals(0, circle.elementPower("FIRE"));
    var alien = mock(Furniture.class);
    var at = furniture.getFirst().getLoc();
    when(alien.getLoc()).thenReturn(at);
    when(alien.getOriginBlockLocation()).thenReturn(Optional.of(at));
    when(alien.getEntityId()).thenReturn(UUID.randomUUID());
    assertFalse(MeditationCircle.containing(alien));
    when(furniture.getFirst().getOriginBlockLocation())
        .thenReturn(Optional.of(new Location(null, 0, 0, 0)));
    assertFalse(circle.stillIntact());
  }

  @Test
  void expiredPreviewUsersAndMutableIntegrationSlotsAreHandledSafely() {
    ring();
    var item = artifact();
    when(slots.getFirst().getCurrentItem()).thenReturn(item);
    var circle = MeditationCircle.detect(center);
    ArtifactCareStore.stampUser(item, "expired", 1);
    assertEquals(
        1,
        circle
            .snapshotYield(Long.MAX_VALUE, "expired")
            .users(circle.artifactIdOn(furniture.getFirst())));
    var active = new HashMap<String, PlacedSlot>();
    active.put("top", slots.getFirst());
    active.put("removed", null);
    when(furniture.getFirst().getActiveSlots()).thenReturn(active);
    for (String filter : new String[] {null, ""}) {
      MeditationCache.pedestalSlot = filter;
      circle.stampArtifacts("user", 1000);
    }
    var manager = dependency.getFurnitureManager();
    doReturn(new LinkedHashSet<>(furniture)).when(manager).getFurnitureInChunk(any());
    when(furniture.getFirst().getOriginBlockLocation()).thenReturn(Optional.empty());
    when(furniture.getFirst().getLoc()).thenReturn(null);
    assertNull(MeditationCircle.detect(center));
    server.getPluginManager().clearPlugins();
    assertNull(MeditationCircle.detect(center));
  }
}
