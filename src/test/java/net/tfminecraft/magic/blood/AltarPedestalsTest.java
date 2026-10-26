package net.tfminecraft.magic.blood;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyFloat;
import static org.mockito.Mockito.*;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import org.mockito.MockedStatic;

import net.tfminecraft.interactiblefurniture.InteractibleFurniture;
import net.tfminecraft.interactiblefurniture.events.FurnitureBreakEvent;
import net.tfminecraft.interactiblefurniture.events.FurnitureInteractEvent;
import net.tfminecraft.interactiblefurniture.furniture.Furniture;
import net.tfminecraft.interactiblefurniture.furniture.FurnitureType;
import net.tfminecraft.interactiblefurniture.loaders.FurnitureLoader;
import net.tfminecraft.interactiblefurniture.manager.FurnitureManager;
import net.tfminecraft.interactiblefurniture.manager.handlers.FurniturePlacementHandler;
import net.tfminecraft.interactiblefurniture.manager.handlers.InteractionHandler;

class AltarPedestalsTest {
  @TempDir Path temp;
  BloodFixture fx;
  MockedStatic<InteractibleFurniture> bridge;
  MockedStatic<FurniturePlacementHandler> placement;
  MockedStatic<InteractionHandler> interaction;
  InteractibleFurniture plugin;
  FurnitureManager manager;
  final Map<UUID, Furniture> placed = new HashMap<>();
  int spawns;
  org.bukkit.plugin.Plugin ifPlugin;

  @BeforeEach
  void setup() {
    fx = new BloodFixture(temp);
    ifPlugin = org.mockbukkit.mockbukkit.MockBukkit.createMockPlugin("InteractibleFurniture");
    plugin = mock(InteractibleFurniture.class);
    manager = mock(FurnitureManager.class);
    when(plugin.getFurnitureManager()).thenReturn(manager);
    when(manager.getPlacedFurniture()).thenReturn(placed);
    bridge = mockStatic(InteractibleFurniture.class);
    bridge.when(InteractibleFurniture::getInstance).thenReturn(plugin);
    placement = mockStatic(FurniturePlacementHandler.class);
    // The third free spot finds something already there.
    placement.when(() -> FurniturePlacementHandler.spawnDisplayEntity(any(), any(), anyFloat(), any()))
        .thenAnswer(i -> ++spawns == 3 ? null : fx.world.spawn(i.getArgument(1, Location.class), ItemDisplay.class));
    interaction = mockStatic(InteractionHandler.class);
    FurnitureType type = mock(FurnitureType.class);
    when(type.getId()).thenReturn("pedestal");
    when(type.getLayers()).thenReturn(List.of());
    FurnitureLoader.getMap().put("pedestal", type);
  }

  @AfterEach
  void cleanup() {
    FurnitureLoader.getMap().remove("pedestal");
    interaction.close();
    placement.close();
    bridge.close();
    fx.close();
  }

  @Test
  void theRingFacesTheAltar() {
    assertEquals(0f, AltarPedestals.yaw(0, -4));
    assertEquals(270f, AltarPedestals.yaw(4, 0));
    assertEquals(180f, AltarPedestals.yaw(0, 4));
    assertEquals(90f, AltarPedestals.yaw(-4, 0));
    assertEquals(315f, AltarPedestals.yaw(3, -3));
    assertEquals(225f, AltarPedestals.yaw(3, 3));
    assertEquals(135f, AltarPedestals.yaw(-3, 3));
    assertEquals(45f, AltarPedestals.yaw(-3, -3));
    assertEquals(8, AltarPedestals.ring().length);
    // An altar saved before it kept pedestals reads back with none.
    assertTrue(new com.google.gson.Gson().fromJson("{\"pedestals\":null}", BloodAltar.class).getPedestals().isEmpty());
  }

  @Test
  void theAltarSetsOutPedestalsOnFreeSpotsThatOnlyPurifyingRemoves() {
    fx.open(-4, 10, -4, 4, 12, 4);
    Block center = fx.world.getBlockAt(0, 10, 0);
    fx.world.getBlockAt(4, 10, 0).setType(Material.CHEST);
    fx.world.getBlockAt(-4, 9, 0).setType(Material.AIR);
    BloodAltar altar = BloodAltar.create(BloodFixture.id("mage"), "o", center, 0f, 1L);

    assertEquals(5, AltarPedestals.place(altar, center));
    assertEquals(5, altar.getPedestals().size());
    assertEquals(5, placed.size());
    verify(manager, times(5)).persistFurniture(any());
    interaction.verify(() -> InteractionHandler.spawnInteraction(any()), times(5));
    placement.verify(() -> FurniturePlacementHandler.placeBarrierBlocks(any(), any(), any(), eq(BlockFace.UP)), times(5));
    Furniture first = placed.values().iterator().next();
    assertNull(AltarPedestals.owner(first));
    BloodAltars.add(altar);
    assertSame(altar, AltarPedestals.owner(first));
    assertNull(AltarPedestals.owner(null));
    assertNull(AltarPedestals.owner(new Furniture("pedestal", center.getLocation(), null)));
    Furniture stray = new Furniture("pedestal", center.getLocation(), UUID.randomUUID());
    assertNull(AltarPedestals.owner(stray));

    BloodListener listener = new BloodListener();
    PlayerMock thief = fx.player("thief");
    FurnitureBreakEvent broken = new FurnitureBreakEvent(first, thief);
    listener.onFurnitureBreak(broken);
    assertTrue(broken.isCancelled());
    assertEquals("blood.altar.pedestal_bound", thief.nextMessage());
    FurnitureBreakEvent byPlugin = new FurnitureBreakEvent(first, null);
    listener.onFurnitureBreak(byPlugin);
    assertFalse(byPlugin.isCancelled());
    FurnitureBreakEvent other = new FurnitureBreakEvent(stray, thief);
    listener.onFurnitureBreak(other);
    assertFalse(other.isCancelled());

    thief.setSneaking(true);
    FurnitureInteractEvent carry = new FurnitureInteractEvent(thief, first);
    listener.onFurnitureInteract(carry);
    assertTrue(carry.isCancelled());
    assertEquals("blood.altar.pedestal_bound", thief.nextMessage());
    FurnitureInteractEvent strayCarry = new FurnitureInteractEvent(thief, stray);
    listener.onFurnitureInteract(strayCarry);
    assertFalse(strayCarry.isCancelled());
    thief.getInventory().setItemInMainHand(new ItemStack(Material.STICK));
    FurnitureInteractEvent withItem = new FurnitureInteractEvent(thief, first);
    listener.onFurnitureInteract(withItem);
    assertFalse(withItem.isCancelled());
    thief.setSneaking(false);
    thief.getInventory().setItemInMainHand(null);
    FurnitureInteractEvent use = new FurnitureInteractEvent(thief, first);
    listener.onFurnitureInteract(use);
    assertFalse(use.isCancelled());

    // One pedestal is not loaded when the altar is purified: it stays bound until its chunk loads.
    UUID unloaded = UUID.randomUUID();
    altar.getPedestals().add(unloaded.toString());
    AltarService.purify(altar, "test");
    verify(manager, times(5)).removePlugin(any());
    assertTrue(altar.getPedestals().isEmpty());
    assertEquals(java.util.Set.of(unloaded.toString()), AltarPedestals.leftovers());
    Furniture late = new Furniture("pedestal", center.getLocation(), unloaded);
    assertTrue(AltarPedestals.bound(late));
    assertFalse(AltarPedestals.bound(stray));
    assertFalse(AltarPedestals.bound(null));
    assertFalse(AltarPedestals.bound(new Furniture("pedestal", center.getLocation(), null)));
    BloodStore store = new BloodStore(new java.io.File(fx.data.toFile(), "blood"), fx.logger);
    assertEquals(List.of(unloaded.toString()), store.loadLeftovers());

    AltarPedestals.sweep();
    verify(manager, times(5)).removePlugin(any());
    placed.put(unloaded, late);
    AltarPedestals.sweep();
    verify(manager).removePlugin(late);
    assertTrue(AltarPedestals.leftovers().isEmpty());
    assertTrue(store.loadLeftovers().isEmpty());
    AltarPedestals.sweep();
  }

  @Test
  void leftoversSurviveARestartAndBadIdsAreDropped() throws Exception {
    java.io.File root = new java.io.File(fx.data.toFile(), "left");
    BloodStore store = new BloodStore(root, fx.logger);
    assertTrue(store.loadLeftovers().isEmpty());
    root.mkdirs();
    java.nio.file.Files.writeString(new java.io.File(root, "leftover_pedestals.json").toPath(),
        "[\"not-a-uuid\", null, \"\", \"" + UUID.randomUUID() + "\"]");
    AltarPedestals.init(store);
    assertEquals(2, AltarPedestals.leftovers().size());
    AltarPedestals.sweep();
    assertEquals(1, AltarPedestals.leftovers().size());
    assertEquals(1, store.loadLeftovers().size());
    when(plugin.getFurnitureManager()).thenReturn(null);
    AltarPedestals.sweep();
    assertEquals(1, AltarPedestals.leftovers().size());
    java.nio.file.Files.writeString(new java.io.File(root, "leftover_pedestals.json").toPath(), "{broken");
    assertTrue(store.loadLeftovers().isEmpty());
    AltarPedestals.clear();
    BloodAltar altar = BloodAltar.create("a", "o", fx.pocket(0, 10, 0), 0f, 1L);
    altar.getPedestals().add(UUID.randomUUID().toString());
    AltarPedestals.remove(altar);
    assertEquals(1, AltarPedestals.leftovers().size());
    altar.getPedestals().add(AltarPedestals.leftovers().iterator().next());
    AltarPedestals.remove(altar);
    assertEquals(1, AltarPedestals.leftovers().size());
  }

  @Test
  void withoutThePedestalTypeOrFurniturePluginNothingIsPlaced() {
    Block center = fx.pocket(0, 10, 0);
    BloodAltar altar = BloodAltar.create("a", "o", center, 0f, 1L);
    when(plugin.getFurnitureManager()).thenReturn(null);
    assertEquals(0, AltarPedestals.place(altar, center));
    altar.getPedestals().add(UUID.randomUUID().toString());
    AltarPedestals.remove(altar);
    assertTrue(altar.getPedestals().isEmpty());
    bridge.when(InteractibleFurniture::getInstance).thenReturn(null);
    assertEquals(0, AltarPedestals.place(altar, center));
    altar.getPedestals().add("x");
    AltarPedestals.remove(altar);
    assertTrue(altar.getPedestals().isEmpty());
    fx.server.getPluginManager().disablePlugin(ifPlugin);
    assertNull(AltarPedestals.manager());
    FurnitureLoader.getMap().remove("pedestal");
    assertEquals(0, AltarPedestals.place(altar, center));
  }
}
