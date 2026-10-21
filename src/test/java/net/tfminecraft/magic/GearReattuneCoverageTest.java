package net.tfminecraft.magic;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.io.IOException;
import java.util.*;
import net.tfminecraft.magic.gear.*;
import net.tfminecraft.magic.gear.ownership.*;
import net.tfminecraft.magic.util.ItemRef;
import net.tfminecraft.tlibs.character.CharacterSession;
import org.bukkit.*;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.junit.jupiter.api.*;

class GearReattuneCoverageTest extends GearStationCoverageSupport {
  double oldFraction;
  long oldConfirm;
  org.mockbukkit.mockbukkit.entity.PlayerMock player;
  Location loc;

  @BeforeEach
  void reattuneSetup() {
    oldFraction = GearCache.reattuneCostFraction;
    oldConfirm = GearCache.confirmMillis;
    GearCache.reattuneCostFraction = .5;
    GearCache.confirmMillis = 100000;
    player = server.addPlayer();
    loc = station();
    var api = mock(net.tfminecraft.tlibs.objects.api.ItemAPI.class, RETURNS_DEEP_STUBS);
    libs.when(net.tfminecraft.tlibs.TLibs::getItemAPI).thenReturn(api);
    when(api.getChecker().checkItemWithPath(any(), any()))
        .thenAnswer(call -> ((ItemStack) call.getArgument(0)).getType().name().equalsIgnoreCase(((String) call.getArgument(1)).substring(2)));
    PartRegistry.register(new PartDef("paid", "Paid", "core", 1, Set.of(GearType.STAFF), "v.STICK",
        Map.of("v.STONE", 4), List.of(), Map.of(), Map.of(), List.of(), null, 1, false));
    ArchetypeRegistry.register(new ArchetypeDef(GearType.STAFF, "Staff", "v.stick", "", false, List.of(), Map.of()));
  }

  @AfterEach
  void reattuneCleanup() {
    GearCache.reattuneCostFraction = oldFraction;
    GearCache.confirmMillis = oldConfirm;
    GearReattuneService.clear(player);
  }

  ItemStack weapon() {
    var weapon = attuned();
    tag(weapon, GearKeys.archetype(), PersistentDataType.STRING, "staff");
    tag(weapon, GearKeys.parts(), PersistentDataType.STRING, "paid");
    return weapon;
  }

  int count(Material material) {
    return player.getInventory().all(material).values().stream().mapToInt(ItemStack::getAmount).sum();
  }

  GearIdentity.Token token() {
    return GearStationStore.property().identity().current(player).orElseThrow();
  }

  GearStationStore.Occupancy depositReattune(ItemStack weapon, Map<String, Integer> fee) {
    player.getInventory().setItemInMainHand(weapon);
    var before = GearEffects.frame(player.getInventory().getContents());
    var after = GearEffects.cloneContents(player.getInventory().getContents());
    after[player.getInventory().getHeldItemSlot()] = null;
    return GearEffects.deposit(player, token(), loc, weapon, fee, true, true, after, before);
  }

  /** Rewrites the live row, as an older or hand-edited file would, and reloads the views. */
  GearStationStore.Occupancy rewrite(GearStationStore.Occupancy occupancy, Map<Object, Object> changes) throws Exception {
    var store = GearStationStore.property();
    var row = store.station(occupancy.generation());
    var after = GearYaml.copy(row.raw());
    changes.forEach((key, value) -> { if (value == null) after.remove(key); else after.put(key, value); });
    store.patch(row, after);
    GearStationStore.load();
    return GearStationStore.get(loc);
  }

  @Test
  void feeIsTheConfiguredShareOfTheWeaponsPartsRoundedUpPerMaterial() {
    assertEquals(Map.of(), GearCosts.fraction(null, .5));
    assertEquals(Map.of(), GearCosts.fraction(Map.of("v.STONE", 3), 0));
    assertEquals(Map.of(), GearCosts.fraction(Map.of("v.STONE", 3), Double.NaN));
    var costs = new HashMap<String, Integer>();
    costs.put("v.STONE", 3);
    costs.put("v.DIRT", null);
    costs.put("v.SAND", 0);
    costs.put("v.GRAVEL", -2);
    costs.put("v.BOOK", Integer.MAX_VALUE);
    assertEquals(Map.of("v.STONE", 2, "v.BOOK", 1073741824), GearCosts.fraction(costs, .5));
    assertEquals(Map.of("v.STONE", 6, "v.BOOK", Integer.MAX_VALUE), GearCosts.fraction(costs, 2));
    assertEquals(Map.of("v.STONE", 3), GearCosts.fraction(Map.of("v.STONE", 10), .3), "float error adds no item");
    assertEquals(Map.of("v.STONE", 1), GearCosts.fraction(Map.of("v.STONE", 1), 1e-12), "a nonzero share is never free");
    assertEquals(Map.of("v.STONE", 2), GearReattuneService.fee(weapon()));
    GearCache.reattuneCostFraction = 0;
    assertEquals(Map.of(), GearReattuneService.fee(weapon()));
    assertFalse(WeaponRequirement.empty().hasStored());
    assertEquals(0, WeaponRequirement.empty().highestBand());
  }

  @Test
  void aSecondMatchingSneakClickPaysTheFeeAndRestsOneWeaponForAReset() {
    var weapon = weapon();
    weapon.setAmount(2);
    player.getInventory().setItemInMainHand(weapon);
    player.getInventory().setItem(5, new ItemStack(Material.STONE, 3));
    GearReattuneService.request(player, loc, weapon);
    assertFalse(GearStationStore.isOccupied(loc), "the first click only names the fee");
    assertEquals(3, count(Material.STONE));
    GearReattuneService.request(player, loc, weapon);
    var occupancy = GearStationStore.get(loc);
    assertNotNull(occupancy);
    assertTrue(occupancy.isRested());
    assertTrue(occupancy.isReattune());
    assertEquals(Map.of("v.STONE", 2), occupancy.getCharged());
    assertEquals(1, occupancy.getItem().getAmount());
    assertEquals(1, player.getInventory().getItemInMainHand().getAmount());
    assertEquals(1, count(Material.STONE));
    assertEquals(Boolean.TRUE, rawRow(occupancy).get("reattune"));
    assertNull(GearStationStore.eject(loc), "a pending reset cannot be picked up for free");
    assertTrue(GearStationStore.isOccupied(loc));
  }

  Map<Object, Object> rawRow(GearStationStore.Occupancy occupancy) {
    try {
      return GearStationStore.property().station(occupancy.generation()).raw();
    } catch (IOException ex) {
      throw new RuntimeException(ex);
    }
  }

  @Test
  void aStaleOrDifferentConfirmationOnlyNamesTheFeeAgain() {
    var weapon = weapon();
    player.getInventory().setItemInMainHand(weapon);
    player.getInventory().setItem(5, new ItemStack(Material.STONE, 8));
    GearCache.confirmMillis = -1;
    GearReattuneService.request(player, loc, weapon);
    GearReattuneService.request(player, loc, weapon);
    assertFalse(GearStationStore.isOccupied(loc), "expired");
    GearCache.confirmMillis = 100000;
    GearReattuneService.request(player, loc.clone().add(5, 0, 0), weapon);
    GearReattuneService.request(player, loc, weapon);
    assertFalse(GearStationStore.isOccupied(loc), "another station");
    var other = weapon();
    tag(other, GearKeys.rift(), PersistentDataType.INTEGER, 7);
    GearReattuneService.request(player, loc, other);
    assertFalse(GearStationStore.isOccupied(loc), "another weapon");
    var current = characters.currentSession(player).orElseThrow();
    characters.sessions.put(player.getUniqueId(), new CharacterSession(current.ref(), current.sessionId() + 1));
    GearReattuneService.request(player, loc, weapon);
    assertFalse(GearStationStore.isOccupied(loc), "another session");
    GearReattuneService.clear(player);
    GearReattuneService.request(player, loc, weapon);
    assertFalse(GearStationStore.isOccupied(loc), "cleared");
    assertEquals(8, count(Material.STONE));
    var gone = server.addPlayer();
    gone.disconnect();
    GearReattuneService.request(gone, loc, weapon);
    assertFalse(GearStationStore.isOccupied(loc));
  }

  @Test
  void missingMaterialsOrAFeeThatWouldTakeTheWeaponItselfRefuse() {
    var weapon = weapon();
    player.getInventory().setItemInMainHand(weapon);
    player.getInventory().setItem(5, new ItemStack(Material.STONE, 1));
    GearReattuneService.request(player, loc, weapon);
    GearReattuneService.request(player, loc, weapon);
    assertFalse(GearStationStore.isOccupied(loc), "lacking");
    assertEquals(1, count(Material.STONE));
    PartRegistry.register(new PartDef("paid", "Paid", "core", 1, Set.of(GearType.STAFF), "v.STICK",
        Map.of("v.STICK", 2), List.of(), Map.of(), Map.of(), List.of(), null, 1, false));
    GearReattuneService.request(player, loc, weapon);
    GearReattuneService.request(player, loc, weapon);
    assertFalse(GearStationStore.isOccupied(loc), "the fee would consume the held weapon");
    var stacked = weapon.clone();
    stacked.setAmount(2);
    player.getInventory().setItemInMainHand(stacked);
    GearReattuneService.request(player, loc, stacked);
    GearReattuneService.request(player, loc, stacked);
    assertFalse(GearStationStore.isOccupied(loc), "the fee would change the held stack");
    assertEquals(2, player.getInventory().getItemInMainHand().getAmount());
  }

  @Test
  void staffBypassIsFreeAndAFailedDepositKeepsTheInventory() throws Exception {
    var weapon = weapon();
    player.getInventory().setItemInMainHand(weapon);
    player.getInventory().setItem(5, new ItemStack(Material.STONE, 4));
    var store = spy(GearStationStore.property());
    setField("property", store);
    doThrow(new IOException("disk unavailable")).when(store).insert(any());
    GearReattuneService.request(player, loc, weapon);
    GearReattuneService.request(player, loc, weapon);
    assertFalse(GearStationStore.isOccupied(loc));
    assertEquals(4, count(Material.STONE));
    assertEquals(weapon, player.getInventory().getItemInMainHand());
    doCallRealMethod().when(store).insert(any());
    player.addAttachment(Magic.plugin, "magic.bypass_crafting_cost", true);
    GearReattuneService.request(player, loc, weapon);
    GearReattuneService.request(player, loc, weapon);
    var occupancy = GearStationStore.get(loc);
    assertTrue(occupancy.isReattune());
    assertEquals(Map.of(), occupancy.getCharged());
    assertEquals(4, count(Material.STONE));
  }

  @Test
  void onlyARestedRowCarriesTheResetAndCompletingItSpendsTheFee() throws Exception {
    var plain = GearEffects.deposit(player, token(), loc, weapon(), Map.of(), false, true,
        player.getInventory().getContents(), GearEffects.frame(player.getInventory().getContents()));
    assertFalse(plain.isReattune());
    assertFalse(rawRow(plain).containsKey("reattune"));
    assertFalse(GearStationStore.completeReattune(loc, plain, weapon()));
    assertFalse(GearStationStore.completeReattune(loc, null, weapon()));
    var forged = rewrite(plain, Map.of("reattune", true));
    assertFalse(forged.isReattune(), "an unrested row is never a reset");
    takeForAbort(loc);

    var occupancy = depositReattune(weapon(), Map.of("v.STONE", 2));
    assertTrue(GearStationStore.isOccupied(loc));
    GearStationStore.clearAll();
    GearStationStore.load();
    occupancy = GearStationStore.get(loc);
    assertTrue(occupancy.isReattune(), "the reset survives a restart");
    assertEquals(Map.of("v.STONE", 2), occupancy.getCharged());
    var reset = item();
    assertFalse(GearStationStore.completeReattune(loc.clone().add(9, 0, 0), occupancy, reset), "another station");
    assertTrue(occupancy.isReattune());
    assertTrue(GearStationStore.completeReattune(loc, occupancy, reset));
    assertFalse(occupancy.isReattune());
    assertEquals(Map.of(), occupancy.getCharged());
    assertEquals(reset, occupancy.getItem());
    var row = rawRow(occupancy);
    assertFalse(row.containsKey("reattune"));
    assertEquals(Map.of(), row.get("charged"));
    assertEquals("active", row.get("effect-state"));
    assertTrue(GearStationStore.isAttuned(reset) || GearStationStore.get(loc).isRested());
    assertFalse(GearStationStore.completeReattune(loc, occupancy, reset), "a finished reset is a plain rest");
    assertNotNull(eject(loc));
  }

  @Test
  void aStationThatLosesItsFurnitureDropsThePendingFeeWithTheWeapon() throws Exception {
    GearStationStore.dropReattuneFee(null, null);
    GearStationStore.dropReattuneFee(loc, null);
    try (var refs = mockStatic(ItemRef.class)) {
      refs.when(() -> ItemRef.build("v.STONE")).thenReturn(new ItemStack(Material.STONE));
      refs.when(() -> ItemRef.build("v.AIR")).thenReturn(new ItemStack(Material.AIR));
      var plain = seedRest(loc, weapon(), player.getUniqueId());
      GearStationStore.dropReattuneFee(loc, plain);
      takeForAbort(loc);
      var malformed = rewrite(depositReattune(weapon(), Map.of("v.STONE", 2)), Map.of("charged", Map.of("0", Map.of("path", "", "amount", 1))));
      assertTrue(malformed.isReattune());
      assertNull(malformed.getCharged());
      GearStationStore.dropReattuneFee(loc, malformed);
      takeForAbort(loc);
      var occupancy = depositReattune(weapon(), Map.of("v.STONE", 70, "v.MISSING", 1, "v.AIR", 1));
      GearStationStore.dropReattuneFee(loc, occupancy);
      assertEquals(List.of(64, 6), stoneDrops());
      for (var dropped : loc.getWorld().getEntitiesByClass(Item.class)) dropped.remove();
      doReturn(false).when(checker).checkBlock(any(), anyString());
      GearStationStore.reconcile(loc.getChunk());
      assertFalse(GearStationStore.isOccupied(loc));
      assertEquals(List.of(64, 6), stoneDrops());
    }
  }

  List<Integer> stoneDrops() {
    return loc.getWorld().getEntitiesByClass(Item.class).stream()
        .map(Item::getItemStack)
        .filter(stack -> stack.getType() == Material.STONE)
        .map(ItemStack::getAmount)
        .sorted(Comparator.reverseOrder())
        .toList();
  }

  @Test
  void abortReturnsTheWeaponWithTheFeeButHoldsAMalformedFee() throws Exception {
    try (var refs = mockStatic(ItemRef.class)) {
      refs.when(() -> ItemRef.build("v.STONE")).thenReturn(new ItemStack(Material.STONE));
      var weapon = weapon();
      var malformed = rewrite(depositReattune(weapon, Map.of("v.STONE", 2)), Map.of("charged", "broken"));
      assertFalse(GearEffects.abortReturn(player, loc, malformed, malformed.getCharged()));
      assertTrue(GearStationStore.isOccupied(loc));
      takeForAbort(loc);
      var legacy = rewrite(seedRest(loc, weapon, player.getUniqueId()), new HashMap<>(Collections.singletonMap("charged", null)));
      assertNull(legacy.getCharged());
      assertTrue(GearEffects.abortReturn(player, loc, legacy, null), "a plain rest needs no recorded payment");
      assertEquals(weapon, player.getInventory().getItemInMainHand());
      player.getInventory().clear();
      var occupancy = depositReattune(weapon, Map.of("v.STONE", 2));
      assertTrue(GearEffects.abortReturn(player, loc, occupancy, occupancy.getCharged()));
      assertFalse(GearStationStore.isOccupied(loc));
      assertEquals(1, player.getInventory().all(Material.STICK).size());
      assertEquals(2, count(Material.STONE));
    }
  }
}
