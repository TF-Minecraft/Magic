package net.tfminecraft.magic;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import dev.lone.itemsadder.api.Events.FurnitureBreakEvent;
import java.util.*;
import net.tfminecraft.magic.charge.ChargeKeys;
import net.tfminecraft.magic.gear.*;
import net.tfminecraft.magic.gear.gui.*;
import net.tfminecraft.magic.gear.orb.GearOrbService;
import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.inventory.*;
import org.bukkit.persistence.PersistentDataType;
import org.junit.jupiter.api.*;
import org.mockito.*;

class GearStationListenerCoverageTest extends GearStationCoverageSupport {
  GearStationListener listener;
  org.mockbukkit.mockbukkit.entity.PlayerMock player;
  Location loc;
  MockedStatic<GearOrbService> orbs;
  MockedConstruction<GearInventoryManager> menus;

  @BeforeEach
  void listenerSetup() {
    menus = mockConstruction(GearInventoryManager.class);
    listener = new GearStationListener();
    orbs = mockStatic(GearOrbService.class);
    player = server.addPlayer();
    loc = station();
    assertSame(menus.constructed().getFirst(), listener.inventory());
  }

  @AfterEach
  void listenerCleanup() {
    orbs.close();
    menus.close();
    OpenStationManager.clear(player);
  }

  PlayerInteractEvent click(Action action, EquipmentSlot hand, org.bukkit.block.Block block) {
    var e = mock(PlayerInteractEvent.class);
    when(e.getAction()).thenReturn(action);
    when(e.getHand()).thenReturn(hand);
    when(e.getClickedBlock()).thenReturn(block);
    when(e.getPlayer()).thenReturn(player);
    return e;
  }

  void right() {
    listener.onInteract(click(Action.RIGHT_CLICK_BLOCK, EquipmentSlot.HAND, loc.getBlock()));
  }

  void left() {
    listener.onInteract(click(Action.LEFT_CLICK_BLOCK, EquipmentSlot.HAND, loc.getBlock()));
  }

  FurnitureBreakEvent broken(String id, Entity entity) {
    var e = mock(FurnitureBreakEvent.class);
    when(e.getNamespacedID()).thenReturn(id);
    when(e.getBukkitEntity()).thenReturn(entity);
    return e;
  }

  @SuppressWarnings("unchecked")
  Map<String, Long> aborts() throws Exception {
    var f = GearStationListener.class.getDeclaredField("recentAborts");
    f.setAccessible(true);
    return (Map<String, Long>) f.get(listener);
  }

  @Test
  void interactionGuardsAndStationRouting() {
    listener.onInteract(click(Action.PHYSICAL, EquipmentSlot.HAND, null));
    listener.onInteract(click(Action.RIGHT_CLICK_BLOCK, EquipmentSlot.OFF_HAND, loc.getBlock()));
    listener.onInteract(click(Action.RIGHT_CLICK_BLOCK, EquipmentSlot.HAND, null));
    doReturn(false).when(checker).checkBlock(any(), anyString());
    right();
    when(blocks.getChecker().checkBlock(any(), anyString())).thenThrow(new IllegalStateException());
    right();
    doReturn(true).when(checker).checkBlock(any(), anyString());
    player.setSneaking(true);
    right();
    player.setSneaking(false);
    right();
    assertEquals(loc, OpenStationManager.get(player));
    verify(listener.inventory()).openAssembly(player);
    var occupancy = GearStationStore.occupy(loc, item(), null, Map.of());
    right();
    assertTrue(GearStationStore.isOccupied(loc));
    occupancy.setOrbSessionActive(true);
    right();
    occupancy.setOrbSessionActive(false);
    orbs.when(() -> GearOrbService.isActive(loc)).thenReturn(true);
    right();
    orbs.when(() -> GearOrbService.isActive(loc)).thenReturn(false);
    player.getInventory().setItemInMainHand(item());
    player.setSneaking(true);
    right();
    player.setSneaking(false);
    right();
    var charge = item();
    tag(charge, ChargeKeys.chargeTier(), PersistentDataType.INTEGER, 1);
    player.getInventory().setItemInMainHand(charge);
    try (var service = mockStatic(GearChargeService.class)) {
      right();
      service.verify(() -> GearChargeService.tryApply(eq(player), eq(loc), any()));
    }
    player.getInventory().setItemInMainHand(new ItemStack(Material.AIR));
    GearStationStore.update(loc, attuned());
    right();
    assertFalse(GearStationStore.isOccupied(loc));
  }

  @Test
  void furnitureEventsRespectStationIdOccupancyAndRecentAbortGuard() throws Exception {
    for (String setting : Arrays.asList(null, "v.stone", "iaf(broken", "iaf(test:station)")) {
      GearCache.station = setting;
      for (String id : Arrays.asList(null, "other")) {
        listener.onFurnitureBreak(broken(id, null));
        listener.onFurnitureBroken(broken(id, null));
      }
    }
    GearCache.station = "iaf(test:station)";
    listener.onFurnitureBreak(broken("test:station", null));
    listener.onFurnitureBroken(broken("test:station", null));
    var entity = mock(Entity.class);
    when(entity.getLocation()).thenReturn(loc.clone().add(.5, .5, .5));
    var e = broken("test:station", entity);
    listener.onFurnitureBreak(e);
    listener.onFurnitureBroken(e);
    verify(e, never()).setCancelled(true);
    GearStationStore.occupy(loc, item(), null, null);
    assertSame(entity, e.getBukkitEntity());
    assertEquals(loc, GearStationStore.occupiedWithin(entity.getLocation(), .75));
    listener.onFurnitureBreak(e);
    verify(e).setCancelled(true);
    listener.onFurnitureBroken(e);
    assertFalse(GearStationStore.isOccupied(loc));
    orbs.verify(() -> GearOrbService.abort(loc));
    aborts().put("missing,1,2,3", System.currentTimeMillis());
    aborts().put("world,20,2,3", System.currentTimeMillis());
    aborts().put("world,5,2,3", 0L);
    listener.onFurnitureBreak(broken("test:station", entity));
    aborts().put(GearStationStore.key(loc), System.currentTimeMillis());
    var guarded = broken("test:station", entity);
    listener.onFurnitureBreak(guarded);
    verify(guarded).setCancelled(true);
    assertFalse(aborts().containsKey("world,5,2,3"));
    listener.onChunkLoad(new ChunkLoadEvent(loc.getChunk(), false));
  }

  @Test
  void abortRespectsOwnershipAttunementAndRecordedRefunds() throws Exception {
    listener.onInteract(click(Action.LEFT_CLICK_BLOCK, EquipmentSlot.OFF_HAND, loc.getBlock()));
    left();
    player.setSneaking(true);
    listener.onInteract(click(Action.LEFT_CLICK_BLOCK, EquipmentSlot.HAND, null));
    doReturn(false).when(checker).checkBlock(any(), anyString());
    left();
    when(blocks.getChecker().checkBlock(any(), anyString())).thenThrow(new IllegalStateException());
    left();
    doReturn(true).when(checker).checkBlock(any(), anyString());
    left();
    GearStationStore.occupy(loc, attuned(), null, null);
    left();
    assertTrue(GearStationStore.isOccupied(loc));
    GearStationStore.occupy(loc, item(), UUID.randomUUID(), Map.of("v.stone", 2));
    left();
    assertTrue(GearStationStore.isOccupied(loc));
    try (var costs = mockStatic(GearCosts.class)) {
      player.addAttachment(Magic.plugin, "magic.admin", true);
      aborts().put("old", 0L);
      aborts().put("new", System.currentTimeMillis());
      left();
      costs.verify(() -> GearCosts.refund(player, Map.of("v.stone", 2), loc));
      assertFalse(GearStationStore.isOccupied(loc));
      assertFalse(aborts().containsKey("old"));
      GearStationStore.occupy(loc, item(), player.getUniqueId(), Map.of());
      left();
      GearStationStore.occupy(loc, item(), null, null);
      costs.when(() -> GearCosts.total(anyCollection())).thenReturn(Map.of("v.gold_ingot", 1));
      left();
      costs.verify(() -> GearCosts.refund(player, Map.of("v.gold_ingot", 1), loc));
    }
  }

  @Test
  void reclaimRepairsMissingPartsAndReturnsOrphanItems() throws Exception {
    try (var refresher = mockStatic(GearRefresher.class);
        var marker = mockStatic(GearBrokenMarker.class, CALLS_REAL_METHODS)) {
      var weapon = item();
      GearBrokenMarker.mark(weapon);
      player.getInventory().setItemInMainHand(weapon);
      right();
      assertFalse(GearBrokenMarker.isBroken(player.getInventory().getItemInMainHand()));
      GearBrokenMarker.mark(weapon);
      refresher.when(() -> GearRefresher.refresh(any(), eq(player), eq(true))).thenReturn(item());
      player.getInventory().setItemInMainHand(weapon);
      right();
      assertFalse(GearBrokenMarker.isBroken(player.getInventory().getItemInMainHand()));
      GearBrokenMarker.addOrphans(weapon, List.of(new GearBrokenMarker.Orphan("RUNE", "TEST")));
      tag(weapon, GearKeys.parts(), PersistentDataType.STRING, "core=missing");
      marker
          .when(() -> GearBrokenMarker.buildOrphanItems(any()))
          .thenReturn(List.of(new ItemStack(Material.DIAMOND)));
      for (int i = 0; i < player.getInventory().getSize(); i++)
        player.getInventory().setItem(i, new ItemStack(Material.STONE, 64));
      player.getInventory().setItemInMainHand(weapon);
      right();
      assertTrue(
          GearBrokenMarker.isBroken(player.getInventory().getItemInMainHand()),
          "missing part stays broken");
      assertTrue(
          loc.getWorld().getEntitiesByClass(Item.class).stream()
              .anyMatch(e -> e.getItemStack().getType() == Material.DIAMOND),
          "full inventory drops reclaimed rune");
    }
  }

  @Test
  void synchronousOrbCompletionCanAlreadyClearTheStation() throws Exception {
    invoke(
        GearStationListener.class,
        "eject",
        new Class[] {Player.class, Location.class},
        player,
        loc);
    player.setSneaking(true);
    GearStationStore.occupy(loc, item(), null, null);
    orbs.when(() -> GearOrbService.abort(loc))
        .thenAnswer(
            a -> {
              GearStationStore.takeForAbort(loc);
              return null;
            });
    left();
    assertFalse(GearStationStore.isOccupied(loc));
    GearStationStore.occupy(loc, item(), null, null);
    var entity = mock(Entity.class);
    when(entity.getLocation()).thenReturn(loc.clone().add(.5, .5, .5));
    listener.onFurnitureBroken(broken("test:station", entity));
    assertFalse(GearStationStore.isOccupied(loc));
  }
}
