package net.tfminecraft.magic;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import dev.lone.itemsadder.api.Events.FurnitureBreakEvent;
import java.util.*;
import net.tfminecraft.magic.charge.ChargeKeys;
import net.tfminecraft.magic.gear.*;
import net.tfminecraft.magic.gear.gui.*;
import net.tfminecraft.magic.gear.orb.GearOrbService;
import net.tfminecraft.magic.gear.ownership.GearEffects;
import net.tfminecraft.magic.gear.ownership.GearItems;
import net.tfminecraft.magic.gear.ownership.GearPropertyStore;
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
    var occupancy = seed(loc, item(), null, Map.of());
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
    seed(loc, item(), null, null);
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
    seed(loc, attuned(), null, null);
    left();
    assertTrue(GearStationStore.isOccupied(loc));
    var foreign = seed(loc, item(), UUID.randomUUID(), Map.of("v.stone", 2));
    left();
    assertTrue(GearStationStore.isOccupied(loc));
    try (var costs = mockStatic(GearCosts.class); var refs = mockStatic(net.tfminecraft.magic.util.ItemRef.class)) {
      refs.when(() -> net.tfminecraft.magic.util.ItemRef.build("v.stone")).thenReturn(new ItemStack(Material.STONE));
      refs.when(() -> net.tfminecraft.magic.util.ItemRef.build("v.gold_ingot")).thenReturn(new ItemStack(Material.GOLD_INGOT));
      player.addAttachment(Magic.plugin, "magic.admin", true);
      aborts().put("old", 0L);
      aborts().put("new", System.currentTimeMillis());
      left();
      assertTrue(player.getInventory().isEmpty(), "staff does not acquire another character's refund");
      var store = GearStationStore.property();
      assertTrue(store.returns(store.identity().current(player).orElseThrow().owner()).isEmpty());
      var returned = store.returns(foreign.characterOwner()).getFirst();
      assertEquals(foreign.characterOwner(), GearPropertyStore.returnOwner(returned));
      assertEquals("pending", GearPropertyStore.text(returned, "state"));
      assertEquals(new ItemStack(Material.STONE, 2), GearItems.item(GearPropertyStore.bytes(returned.getAsJsonArray("items").get(0).getAsString())));
      assertFalse(GearStationStore.isOccupied(loc));
      assertFalse(aborts().containsKey("old"));
      seed(loc, item(), player.getUniqueId(), Map.of());
      left();
      seed(loc, item(), null, null);
      costs.when(() -> GearCosts.total(anyCollection())).thenReturn(Map.of("v.gold_ingot", 1));
      left();
      assertEquals(0, player.getInventory().all(Material.GOLD_INGOT).values().stream().mapToInt(ItemStack::getAmount).sum());
      assertTrue(GearStationStore.isOccupied(loc), "unproven legacy payment is conserved for staff");
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
    seed(loc, item(), player.getUniqueId(), Map.of());
    orbs.when(() -> GearOrbService.abort(loc))
        .thenAnswer(
            a -> {
              var captured = GearStationStore.get(loc);
              GearStationStore.takeForAbort(loc);
              GearStationStore.acknowledgeRelease(loc, captured.generation());
              return null;
            });
    left();
    assertFalse(GearStationStore.isOccupied(loc));
    assertTrue(player.getInventory().isEmpty());
    assertTrue(GearStationStore.property().returns(GearStationStore.property().identity().current(player).orElseThrow().owner()).isEmpty());
    seed(loc, item(), null, Map.of());
    var entity = mock(Entity.class);
    when(entity.getLocation()).thenReturn(loc.clone().add(.5, .5, .5));
    listener.onFurnitureBroken(broken("test:station", entity));
    assertFalse(GearStationStore.isOccupied(loc));
  }

  @Test
  void emptyStationAcceptsAWeaponItCanCreateSoItCanBeChargedAgain() {
    player.getInventory().setItemInMainHand(item());
    right();
    verify(listener.inventory()).openAssembly(player);
    assertFalse(GearStationStore.isOccupied(loc));
    OpenStationManager.clear(player);

    var foreign = item();
    tag(foreign, GearKeys.archetype(), PersistentDataType.STRING, "staff");
    player.getInventory().setItemInMainHand(foreign);
    right();
    assertFalse(GearStationStore.isOccupied(loc));
    verify(listener.inventory(), times(1)).openAssembly(player);

    ArchetypeRegistry.register(
        new ArchetypeDef(GearType.STAFF, "Staff", "v.stick", "", false, List.of(), Map.of()));
    player.setSneaking(true);
    right();
    assertFalse(GearStationStore.isOccupied(loc));
    player.setSneaking(false);

    var stacked = attuned();
    tag(stacked, GearKeys.archetype(), PersistentDataType.STRING, "staff");
    stacked.setAmount(2);
    player.getInventory().setItemInMainHand(stacked);
    right();
    var occupancy = GearStationStore.get(loc);
    assertNotNull(occupancy);
    assertTrue(occupancy.isRested());
    assertEquals(player.getUniqueId(), occupancy.getOwner());
    assertEquals(Map.of(), occupancy.getCharged());
    assertEquals(1, occupancy.getItem().getAmount());
    assertTrue(GearStationStore.isAttuned(occupancy.getItem()));
    assertEquals(1, player.getInventory().getItemInMainHand().getAmount());

    var sitting = occupancy.getItem();
    var another = attuned();
    tag(another, GearKeys.archetype(), PersistentDataType.STRING, "staff");
    player.getInventory().setItemInMainHand(another);
    right();
    assertEquals(sitting, GearStationStore.get(loc).getItem());

    var charge = item();
    tag(charge, ChargeKeys.chargeTier(), PersistentDataType.INTEGER, 1);
    player.getInventory().setItemInMainHand(charge);
    try (var service = mockStatic(GearChargeService.class)) {
      right();
      service.verify(() -> GearChargeService.tryApply(eq(player), eq(loc), any()));
    }

    player.getInventory().setItemInMainHand(new ItemStack(Material.AIR));
    right();
    assertFalse(GearStationStore.isOccupied(loc));
    assertTrue(
        loc.getWorld().getEntitiesByClass(Item.class).stream()
            .anyMatch(dropped -> GearProvenance.stationCanCreate(dropped.getItemStack())));
  }

  @Test
  void restedUnattunedWeaponCanBePickedUpAndReturnedFromARunningCharge() throws Exception {
    ArchetypeRegistry.register(
        new ArchetypeDef(GearType.WAND, "Wand", "v.blaze_rod", "", false, List.of(), Map.of()));
    var weapon = item();
    tag(weapon, GearKeys.archetype(), PersistentDataType.STRING, "wand");
    player.getInventory().setItemInMainHand(weapon);
    right();
    assertTrue(GearStationStore.get(loc).isRested());
    assertFalse(GearStationStore.isAttuned(GearStationStore.get(loc).getItem()));

    player.setSneaking(true);
    left();
    assertTrue(GearStationStore.isOccupied(loc));
    player.setSneaking(false);
    player.getInventory().setItemInMainHand(new ItemStack(Material.AIR));
    right();
    assertFalse(GearStationStore.isOccupied(loc));

    player.setSneaking(true);
    seedRest(loc, weapon.clone(), player.getUniqueId());
    orbs.when(() -> GearOrbService.isActive(loc)).thenReturn(true);
    left();
    assertFalse(GearStationStore.isOccupied(loc));
    orbs.when(() -> GearOrbService.isActive(loc)).thenReturn(false);

    var ownerless = seedRest(loc, weapon.clone(), null);
    GearStationStore.get(loc).setOrbSessionActive(true);
    orbs.when(() -> GearOrbService.sessionOwner(loc)).thenReturn(null);
    var rawOwnerless = GearStationStore.property().station(ownerless.generation()).raw();
    left();
    assertTrue(GearStationStore.isOccupied(loc), "unknown recipient cannot become the aborting character");
    assertSame(ownerless, GearStationStore.get(loc));
    assertTrue(net.tfminecraft.magic.gear.ownership.GearYaml.equal(rawOwnerless, GearStationStore.property().station(ownerless.generation()).raw()));

    var foreignActor = new net.tfminecraft.magic.gear.ownership.GearIdentity.Token(new net.tfminecraft.magic.gear.ownership.GearIdentity.Owner(UUID.randomUUID(), UUID.randomUUID()),1);
    orbs.when(() -> GearOrbService.actor(loc)).thenReturn(foreignActor);
    seedRest(loc, weapon.clone(), null);
    GearStationStore.get(loc).setOrbSessionActive(true);
    left();
    assertTrue(GearStationStore.isOccupied(loc));

    var foreign = seedRest(loc, weapon.clone(), UUID.randomUUID());
    GearStationStore.get(loc).setOrbSessionActive(true);
    left();
    assertTrue(GearStationStore.isOccupied(loc));
    player.addAttachment(Magic.plugin, "magic.admin", true);
    aborts().put("old", 0L);
    aborts().put("fresh", System.currentTimeMillis());
    for (int i = 0; i < player.getInventory().getSize(); i++) {
      player.getInventory().setItem(i, new ItemStack(Material.STONE, 64));
    }
    var inventoryBefore = GearEffects.frame(player.getInventory().getContents());
    int dropsBefore = loc.getWorld().getEntitiesByClass(Item.class).size();
    var store = GearStationStore.property();
    var actor = store.identity().current(player).orElseThrow().owner();
    int actorReturnsBefore = store.returns(actor).size();
    left();
    assertFalse(GearStationStore.isOccupied(loc));
    assertFalse(aborts().containsKey("old"));
    assertTrue(aborts().containsKey("fresh"));
    assertEquals(inventoryBefore, GearEffects.frame(player.getInventory().getContents()));
    assertEquals(dropsBefore, loc.getWorld().getEntitiesByClass(Item.class).size(), "staff abort does not create a public drop");
    assertEquals(actorReturnsBefore, store.returns(actor).size());
    var returned = store.returns(foreign.characterOwner()).getFirst();
    assertEquals(foreign.characterOwner(), GearPropertyStore.returnOwner(returned));
    assertEquals("pending", GearPropertyStore.text(returned, "state"));
    assertEquals(weapon, GearItems.item(GearPropertyStore.bytes(returned.getAsJsonArray("items").get(0).getAsString())));

    seedRest(loc, weapon.clone(), player.getUniqueId());
    GearStationStore.get(loc).setOrbSessionActive(true);
    orbs.when(() -> GearOrbService.abort(loc))
        .thenAnswer(
            a -> {
              var captured = GearStationStore.get(loc);
              GearStationStore.takeForAbort(loc);
              GearStationStore.acknowledgeRelease(loc, captured.generation());
              return null;
            });
    left();
    assertFalse(GearStationStore.isOccupied(loc));
  }

  ItemStack craftable() {
    ArchetypeRegistry.register(
        new ArchetypeDef(GearType.STAFF, "Staff", "v.stick", "", false, List.of(), Map.of()));
    var weapon = attuned();
    tag(weapon, GearKeys.archetype(), PersistentDataType.STRING, "staff");
    return weapon;
  }

  GearStationStore.Occupancy reattuneRow(ItemStack weapon, UUID owner, Map<String, Integer> fee) throws Exception {
    var occupancy = seed(loc, weapon, owner, fee, true);
    var store = GearStationStore.property();
    var row = store.station(occupancy.generation());
    var after = net.tfminecraft.magic.gear.ownership.GearYaml.copy(row.raw());
    after.put("reattune", true);
    store.patch(row, after);
    GearStationStore.load();
    assertTrue(GearStationStore.get(loc).isReattune());
    return GearStationStore.get(loc);
  }

  @Test
  void sneakClickingAnEmptyStationWithAnAttunedCraftableWeaponAsksForAReset() {
    try (var service = mockStatic(GearReattuneService.class)) {
      player.setSneaking(true);
      var unattuned = craftable();
      WeaponRequirement.empty().persist(unattuned);
      player.getInventory().setItemInMainHand(unattuned);
      right();
      var foreign = attuned();
      player.getInventory().setItemInMainHand(foreign);
      right();
      var broken = craftable();
      GearBrokenMarker.mark(broken);
      player.getInventory().setItemInMainHand(broken);
      right();
      service.verifyNoInteractions();
      var weapon = craftable();
      player.getInventory().setItemInMainHand(weapon);
      right();
      service.verify(() -> GearReattuneService.request(eq(player), eq(loc), eq(weapon)));
      assertFalse(GearStationStore.isOccupied(loc));
    }
  }

  @Test
  void aPendingResetStaysOnTheStationUntilItsOwnerTakesItAndTheFeeBack() throws Exception {
    try (var refs = mockStatic(net.tfminecraft.magic.util.ItemRef.class)) {
      refs.when(() -> net.tfminecraft.magic.util.ItemRef.build("v.stone")).thenReturn(new ItemStack(Material.STONE));
      var weapon = craftable();
      reattuneRow(weapon, player.getUniqueId(), Map.of("v.stone", 2));
      right();
      assertTrue(GearStationStore.isOccupied(loc), "an empty hand cannot pick up a paid reset");
      player.setSneaking(true);
      reattuneRow(weapon, UUID.randomUUID(), Map.of("v.stone", 2));
      left();
      assertTrue(GearStationStore.isOccupied(loc), "only the owner calls a reset off");
      var unrecorded = reattuneRow(weapon, player.getUniqueId(), Map.of("v.stone", 2));
      var store = GearStationStore.property();
      var row = store.station(unrecorded.generation());
      var after = net.tfminecraft.magic.gear.ownership.GearYaml.copy(row.raw());
      after.put("charged", "lost");
      store.patch(row, after);
      GearStationStore.load();
      left();
      assertTrue(GearStationStore.isOccupied(loc), "a lost fee record is held for staff");
      reattuneRow(weapon, player.getUniqueId(), Map.of("v.stone", 2));
      orbs.when(() -> GearOrbService.isActive(loc)).thenReturn(true);
      left();
      assertTrue(GearStationStore.isOccupied(loc), "a running charge cannot be called off for a refund");
      orbs.when(() -> GearOrbService.isActive(loc)).thenReturn(false);
      GearStationStore.get(loc).setOrbSessionActive(true);
      left();
      assertTrue(GearStationStore.isOccupied(loc));
      GearStationStore.get(loc).setOrbSessionActive(false);
      aborts().put("old", 0L);
      aborts().put("fresh", System.currentTimeMillis());
      left();
      assertFalse(GearStationStore.isOccupied(loc));
      assertFalse(aborts().containsKey("old"));
      assertTrue(aborts().containsKey("fresh"));
      assertTrue(aborts().containsKey(GearStationStore.key(loc)));
      assertEquals(2, player.getInventory().all(Material.STONE).values().stream().mapToInt(ItemStack::getAmount).sum());
      assertTrue(player.getInventory().contains(weapon));
    }
  }

  @Test
  void aBrokenStationDropsAPendingResetsFeeWithTheWeapon() throws Exception {
    try (var refs = mockStatic(net.tfminecraft.magic.util.ItemRef.class)) {
      refs.when(() -> net.tfminecraft.magic.util.ItemRef.build("v.stone")).thenReturn(new ItemStack(Material.STONE));
      reattuneRow(craftable(), player.getUniqueId(), Map.of("v.stone", 3));
      var entity = mock(Entity.class);
      when(entity.getLocation()).thenReturn(loc.clone().add(.5, .5, .5));
      listener.onFurnitureBroken(broken("test:station", entity));
      assertFalse(GearStationStore.isOccupied(loc));
      assertTrue(
          loc.getWorld().getEntitiesByClass(Item.class).stream()
              .anyMatch(dropped -> dropped.getItemStack().getType() == Material.STONE && dropped.getItemStack().getAmount() == 3));
    }
  }
}
