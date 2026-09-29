package net.tfminecraft.magic;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.*;
import net.tfminecraft.magic.charge.*;
import net.tfminecraft.magic.gear.*;
import net.tfminecraft.magic.model.ElementVisibility;
import net.tfminecraft.magic.registry.ElementRegistry;
import org.bukkit.*;
import org.bukkit.inventory.*;
import org.bukkit.persistence.PersistentDataType;
import org.junit.jupiter.api.*;

class GearWeaponLoreCoverageTest extends GearCoverageSupport {
  @BeforeEach
  void elements() {
    ElementRegistry.clear();
    TierBands.clear();
    for (String id : List.of("fire", "water", "earth", "air"))
      ElementRegistry.register(DomainTest.element(id));
    TierBands.register("default", 1, 5);
  }

  @AfterEach
  void clear() {
    ElementRegistry.clear();
    TierBands.clear();
  }

  List<String> plain(ItemStack s) {
    return s.getItemMeta().getLore().stream().map(ChatColor::stripColor).toList();
  }

  @Test
  void loreUpdatesOnlyItsOwnBlockAndReflectsDamageRequirementsAndRift() {
    assertNull(WeaponLore.updateItem(null));
    var s = item();
    WeaponLore.apply(s);
    GearProvenance.stamp(s, GearType.STAFF, List.of(part("core", 2)));
    PartRegistry.register(part("core", 2));
    var meta = s.getItemMeta();
    meta.setLore(List.of("Flavor", ""));
    s.setItemMeta(meta);
    assertSame(s, WeaponLore.updateItem(s));
    assertTrue(plain(s).contains("Tier II"));
    assertTrue(plain(s).contains("Unattuned"));
    WeaponLore.apply(s);
    assertEquals(5, plain(s).size());
    var req = WeaponRequirement.fromItem(s);
    req.mergeAmounts(Map.of("fire", 5d));
    req.aura().setCap("water", 5);
    req.persist(s);
    WeaponRift.set(s, 15);
    tag(s, GearKeys.broken(), PersistentDataType.BYTE, (byte) 1);
    try (var visibility = mockStatic(ElementVisibility.class)) {
      visibility.when(() -> ElementVisibility.shownOnCharge("fire")).thenReturn(true);
      visibility.when(() -> ElementVisibility.shownOnCharge("water")).thenReturn(true);
      visibility.when(() -> ElementVisibility.shownOnCharge("earth")).thenReturn(true);
      WeaponLore.apply(s);
    }
    assertTrue(plain(s).contains("Resonance"));
    assertTrue(plain(s).contains("Damaged"));
    assertTrue(plain(s).contains("Rift 15%"));
    GearBrokenMarker.addOrphans(s, List.of(new GearBrokenMarker.Orphan("RUNE", "one")));
    PartRegistry.clear();
    WeaponLore.apply(s);
    assertTrue(plain(s).contains("Holding 1 loose rune"));
    assertTrue(plain(s).contains("Missing parts: core"));
    GearBrokenMarker.addOrphans(s, List.of(new GearBrokenMarker.Orphan("RUNE", "two")));
    WeaponLore.apply(s);
    assertTrue(plain(s).contains("Holding 2 loose runes"));
  }

  @Test
  void stripsBothMarkerFormatsAndLegacyLabelsWithoutTouchingFlavor() throws Exception {
    for (String marker :
        List.of("§0§1§4§rTier II", "§0§1§4 old", "Resonance", "Unattuned", "Tier IV")) {
      assertEquals(
          List.of("Flavor"),
          invoke(
              WeaponLore.class,
              "stripBlock",
              new Class[] {List.class},
              new ArrayList<>(List.of("Flavor", marker, "old"))));
    }
    var unchanged = Arrays.asList(null, "", "#ff00ff Flavor");
    assertEquals(
        unchanged, invoke(WeaponLore.class, "stripBlock", new Class[] {List.class}, unchanged));
    assertEquals(
        "name",
        invoke(WeaponLore.class, "plain", new Class[] {String.class}, "  §a#abcdef name   "));
  }

  @Test
  void requirementMergesHighestVisibleAmountsAndPreservesCaps() {
    var s = item();
    var req = WeaponRequirement.fromItem(s);
    assertFalse(req.hasStored());
    req.mergeFrom(null);
    req.mergeAmounts(null);
    var incoming = new LinkedHashMap<String, Double>();
    incoming.put("fire", null);
    incoming.put("water", 0d);
    incoming.put("earth", -1d);
    incoming.put("hidden", 100d);
    try (var visibility = mockStatic(ElementVisibility.class)) {
      req.mergeAmounts(incoming);
      visibility.when(() -> ElementVisibility.shownOnCharge("fire")).thenReturn(true);
      req.mergeAmounts(Map.of("fire", 8d));
      req.mergeAmounts(Map.of("fire", 3d));
    }
    assertEquals(8, req.aura().getFill("fire"));
    assertEquals(8, req.aura().getCap("fire"));
    req.persist(s);
    assertTrue(WeaponRequirement.fromItem(s).hasStored());
    assertEquals(1, req.highestBand());
    assertEquals(0, WeaponRequirement.highestBand((Charge) null));
    assertEquals(0, WeaponRequirement.highestBand((Map<String, Double>) null));
    assertEquals(1, WeaponRequirement.highestBand(incoming));
    var charge = mock(Charge.class);
    when(charge.getCappedElementIds()).thenReturn(Set.of("fire"));
    when(charge.getFill("fire")).thenReturn(15d);
    req.mergeFrom(charge);
    assertEquals(15, req.aura().getFill("fire"));
    assertEquals(1, WeaponRequirement.highestBand(charge));
  }

  @Test
  void riftAndAttunementSummariesCoverUnattunedAndImbuedWeapons() throws Exception {
    for (ItemStack empty : Arrays.asList(null, new ItemStack(Material.AIR))) {
      assertEquals(0, WeaponRift.get(empty));
      WeaponRift.set(empty, 5);
    }
    var s = item();
    assertEquals(0, WeaponRift.get(s));
    WeaponRift.set(s, 0);
    WeaponRift.copy(s, item());
    WeaponRift.set(s, 10);
    var copy = item();
    WeaponRift.copy(s, copy);
    assertEquals(10, WeaponRift.get(copy));
    var p = server.addPlayer();
    WeaponAttunementChat.sendPostChargeSummary(null, s);
    WeaponAttunementChat.sendPostChargeSummary(p, null);
    WeaponAttunementChat.sendPostChargeSummary(p, s);
    WeaponRift.set(s, 0);
    WeaponAttunementChat.sendPostChargeSummary(p, s);
    var req = WeaponRequirement.fromItem(s);
    var lines = new ArrayList<String>();
    WeaponResonanceDisplay.appendElementLines(lines, null, null);
    WeaponResonanceDisplay.appendElementLines(lines, req, null);
    req.mergeAmounts(Map.of("fire", 5d));
    req.aura().setCap("water", 5);
    req.persist(s);
    try (var visibility = mockStatic(ElementVisibility.class)) {
      for (String id : List.of("fire", "water", "earth"))
        visibility.when(() -> ElementVisibility.shownOnCharge(id)).thenReturn(true);
      WeaponResonanceDisplay.appendElementLines(lines, req, null);
      assertEquals(2, lines.size());
      WeaponAttunementChat.sendPostChargeSummary(p, s);
    }
    assertFalse(
        (boolean)
            invoke(
                WeaponResonanceDisplay.class,
                "includeElement",
                new Class[] {WeaponRequirement.class, String.class},
                null,
                "fire"));
    assertFalse(
        (boolean)
            invoke(
                WeaponResonanceDisplay.class,
                "includeElement",
                new Class[] {WeaponRequirement.class, String.class},
                req,
                null));
    assertTrue(
        (boolean)
            invoke(
                WeaponResonanceDisplay.class,
                "includeAuraEntry",
                new Class[] {double.class, double.class},
                0d,
                1d));
  }

  @Test
  void loreAcceptsLegacyFillWithoutACorrespondingCap() {
    var s = item();
    GearProvenance.stamp(s, GearType.WAND, List.of());
    tag(s, GearKeys.weaponReq(), PersistentDataType.STRING, "fire:10:10,water:0:5");
    WeaponLore.apply(s);
    assertTrue(plain(s).contains("Resonance"));
    assertEquals(3, plain(s).size());
  }
}
