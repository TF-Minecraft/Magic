package net.tfminecraft.magic;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.*;
import net.Indyuce.mmoitems.ItemStats;
import net.Indyuce.mmoitems.MMOItems;
import net.Indyuce.mmoitems.api.item.mmoitem.MMOItem;
import net.Indyuce.mmoitems.stat.data.*;
import net.Indyuce.mmoitems.stat.type.StatHistory;
import net.tfminecraft.magic.gear.*;
import org.bukkit.*;
import org.bukkit.inventory.*;
import org.junit.jupiter.api.*;
import org.mockbukkit.mockbukkit.MockBukkit;

class GearHandStatCoverageTest extends GearMmoCoverageSupport {
  PartDef stats(String id, Map<String, Double> values) {
    return new PartDef(
        id,
        id,
        "core",
        1,
        Set.of(GearType.STAFF),
        "v.STICK",
        Map.of(),
        List.of(),
        values,
        Map.of(),
        List.of(),
        null,
        1,
        false);
  }

  @Test
  void handSelectionPrioritizesMainAndStaffCountsIgnoreArmor() throws Exception {
    assertEquals(
        0, invoke(GearHand.class, "staffAmount", new Class[] {ItemStack.class}, (Object) null));
    assertNull(GearHand.held(null));
    assertNull(GearHand.heldSlot(null));
    assertEquals(0, GearHand.staffCount(null));
    var p = server.addPlayer();
    assertEquals(0, GearHand.staffCount(p));
    assertNull(GearHand.heldSlot(p));
    assertNull(GearHand.held(p));
    var staff = item();
    staff.setAmount(2);
    GearProvenance.stamp(staff, GearType.STAFF, null);
    var wand = item();
    wand.setAmount(3);
    GearProvenance.stamp(wand, GearType.WAND, null);
    GearHand.setHeld(null, GearHand.HeldSlot.MAIN_HAND, staff);
    GearHand.setHeld(p, null, staff);
    GearHand.setHeld(p, GearHand.HeldSlot.OFF_HAND, staff);
    assertEquals(GearHand.HeldSlot.OFF_HAND, GearHand.heldSlot(p));
    assertEquals(staff, GearHand.held(p));
    GearHand.setHeld(p, GearHand.HeldSlot.MAIN_HAND, wand);
    assertEquals(GearHand.HeldSlot.MAIN_HAND, GearHand.heldSlot(p));
    assertEquals(wand, GearHand.held(p));
    p.getInventory().setItem(2, staff);
    assertEquals(4, GearHand.staffCount(p));
  }

  @Test
  void statTotalsSkipIncompleteValuesAndClearPriorManagedContributions() {
    assertTrue(GearStatApplicator.sum(null).isEmpty());
    var malformed = new LinkedHashMap<String, Double>();
    malformed.put(null, 1d);
    malformed.put("empty", null);
    malformed.put("damage", 2d);
    var a = stats("a", malformed);
    var b = stats("b", Map.of("damage", 3d));
    assertEquals(
        Map.of("damage", 5d),
        GearStatApplicator.sum(Arrays.asList(null, stats("empty", Map.of()), a, b)));
    var mmo = mock(MMOItem.class);
    GearStatApplicator.apply(null, List.of());
    GearStatApplicator.apply(mmo, List.of());
    MockBukkit.createMockPlugin("MMOItems");
    MockBukkit.createMockPlugin("MythicLib");
    GearStatApplicator.apply(mmo, List.of());
    PartRegistry.register(a);
    PartRegistry.register(b);
    PartRegistry.register(
        stats("unknown", new LinkedHashMap<>(Map.of("gear_test_unknown", 1d, " ", 1d))));
    when(MMOItems.plugin.getStats().get("GEAR_TEST_UNKNOWN")).thenReturn(null);
    when(MMOItems.plugin.getStats().get("EMPTY")).thenReturn(null);
    var attackDamage = ItemStats.ATTACK_DAMAGE;
    when(MMOItems.plugin.getStats().get("DAMAGE")).thenReturn(attackDamage);
    var hist = mock(StatHistory.class);
    var original = new DoubleData(50);
    when(hist.getOriginalData()).thenReturn(original);
    when(mmo.computeStatHistory(ItemStats.ATTACK_DAMAGE)).thenReturn(hist);
    GearStatApplicator.apply(mmo, List.of(a, b));
    assertEquals(0, original.getValue());
    verify(hist).clearExternalData();
    var values = org.mockito.ArgumentCaptor.forClass(DoubleData.class);
    verify(mmo, times(2)).setData(eq(ItemStats.ATTACK_DAMAGE), values.capture());
    assertEquals(5, values.getValue().getValue());
    verify(hist).registerExternalData(values.getValue());
    when(hist.getOriginalData()).thenReturn(new StringData("not numeric"));
    GearStatApplicator.apply(mmo, List.of());
    when(mmo.computeStatHistory(ItemStats.ATTACK_DAMAGE)).thenReturn(null);
    GearStatApplicator.apply(mmo, List.of());
  }
}
