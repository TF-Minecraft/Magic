package net.tfminecraft.magic;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import io.lumine.mythic.lib.api.item.NBTItem;
import java.util.*;
import net.Indyuce.mmoitems.ItemStats;
import net.Indyuce.mmoitems.api.item.build.ItemStackBuilder;
import net.Indyuce.mmoitems.api.item.mmoitem.LiveMMOItem;
import net.Indyuce.mmoitems.api.item.mmoitem.MMOItem;
import net.Indyuce.mmoitems.stat.data.StringData;
import net.Indyuce.mmoitems.stat.type.NameData;
import net.Indyuce.mmoitems.stat.type.StatHistory;
import net.tfminecraft.magic.gear.*;
import net.tfminecraft.magic.util.ItemRef;
import org.bukkit.*;
import org.bukkit.inventory.*;
import org.bukkit.persistence.*;
import org.junit.jupiter.api.*;
import org.mockbukkit.mockbukkit.MockBukkit;

class GearNameCoverageTest extends GearMmoCoverageSupport {
  static final String IRON = "§x§d§8§d§8§d§8Iron";
  static final String IRON_STAFF = IRON + " Mage Staff";

  static PartDef core(String name, String material) {
    return new PartDef(
        "core",
        name,
        "core",
        2,
        Set.of(GearType.STAFF),
        "v.STICK",
        Map.of(),
        List.of(),
        Map.of(),
        Map.of("spell", 1),
        List.of(),
        "",
        1,
        false,
        material);
  }

  static PartDef handle() {
    return new PartDef(
        "handle", "§dOak Magical Handle", "handle", 1, Set.of(GearType.STAFF), "v.STICK",
        Map.of(), List.of(), Map.of(), Map.of(), List.of(), "", 1, false);
  }

  static ArchetypeDef staff(String itemName) {
    return new ArchetypeDef(
        GearType.STAFF,
        "Staff",
        "v.STICK",
        "v.STICK",
        false,
        List.of(),
        Map.of("spell", "Spell"),
        itemName);
  }

  static String stored(ItemStack stack) {
    return stack
        .getItemMeta()
        .getPersistentDataContainer()
        .get(GearKeys.gearName(), PersistentDataType.STRING);
  }

  ItemStack crafted(PartDef core) {
    PartRegistry.register(core);
    ArchetypeRegistry.register(staff("%material% Mage Staff"));
    var item = item();
    GearProvenance.stamp(item, GearType.STAFF, List.of(core));
    return item;
  }

  @Test
  void definitionsDefaultTheirMaterialAndItemName() {
    assertEquals("", core("Name", null).getMaterial());
    assertEquals(IRON, core("Name", " " + IRON + " ").getMaterial());
    assertEquals("", handle().getMaterial());
    assertEquals("%material% Staff", staff(null).getItemName());
    assertEquals("%material% Staff", staff(" ").getItemName());
    assertEquals("%material% Mage Staff", staff("%material% Mage Staff").getItemName());
    assertEquals(
        "%material% Staff",
        new ArchetypeDef(GearType.STAFF, "", "", "", false, List.of(), Map.of()).getItemName());
  }

  @Test
  void materialComesFromTheCoreOrItsName() throws Exception {
    var core = core("Name", IRON);
    assertNull(invokeCore(null));
    assertNull(invokeCore(Arrays.asList(null, handle())));
    assertSame(core, invokeCore(List.of(handle(), core)));
    assertEquals("", invokeMaterial(null));
    assertEquals(IRON, invokeMaterial(core));
    assertEquals(IRON, invokeMaterial(core(IRON + " Magical Core", "")));
    assertEquals("§dOak", invokeMaterial(core("§dOak  core", "")));
    assertEquals("", invokeMaterial(core("Heart of the Forest", "")));
  }

  @Test
  void expectedNameFillsTheArchetypeAndTakesTheMaterialColour() {
    assertNull(GearName.expected(GearType.STAFF, List.of(core("Name", IRON))));
    ArchetypeRegistry.register(staff("%material% Mage Staff"));
    assertEquals(IRON_STAFF, GearName.expected(GearType.STAFF, List.of(core("Name", IRON))));
    assertEquals("Iron Mage Staff", GearName.expected(GearType.STAFF, List.of(core("Name", "Iron"))));
    assertNull(GearName.expected(GearType.STAFF, List.of(core("Name", "§x§d§8§d§8§d§8 "))));
    assertNull(GearName.expected(GearType.STAFF, List.of(handle())));
    assertNull(GearName.expected(GearType.STAFF, null));
    ArchetypeRegistry.register(staff("§lRelic"));
    assertEquals("§x§d§8§d§8§d§8§lRelic", GearName.expected(GearType.STAFF, List.of(core("Name", IRON))));
  }

  @Test
  void outdatedOnlyWhenTheCoreNamesSomethingElse() {
    var item = crafted(core("Name", IRON));
    assertTrue(GearName.isOutdated(item));
    GearName.stamp(item, "§7Old Mage Staff");
    assertTrue(GearName.isOutdated(item));
    GearName.stamp(item, IRON_STAFF);
    assertFalse(GearName.isOutdated(item));
    PartRegistry.clear();
    var plain = crafted(core("Name", ""));
    assertFalse(GearName.isOutdated(plain));
    assertFalse(GearName.isOutdated(null));
  }

  @Test
  void stampAndStoredSkipMissingStacksNamesAndMeta() throws Exception {
    GearName.stamp(null, IRON_STAFF);
    var item = item();
    GearName.stamp(item, null);
    assertNull(stored(item));
    var air = new ItemStack(Material.AIR);
    GearName.stamp(air, IRON_STAFF);
    assertFalse(air.hasItemMeta());
    assertNull(invoke(GearName.class, "stored", new Class[] {ItemStack.class}, (Object) null));
    assertNull(invoke(GearName.class, "stored", new Class[] {ItemStack.class}, air));
    GearName.stamp(item, IRON_STAFF);
    assertEquals(IRON_STAFF, invoke(GearName.class, "stored", new Class[] {ItemStack.class}, item));
  }

  @Test
  void writeRenamesTheItemAndItsOriginalSoMmoItemsUpdatesKeepIt() {
    var untouched = mock(MMOItem.class);
    GearName.write(null, IRON_STAFF);
    GearName.write(untouched, null);
    verifyNoInteractions(untouched);

    var mmo = mock(MMOItem.class);
    var current = new NameData("Mage Staff");
    var original = new NameData("Mage Staff");
    var history = mock(StatHistory.class);
    when(history.getOriginalData()).thenReturn(original);
    when(mmo.getData(ItemStats.NAME)).thenReturn(current);
    when(mmo.computeStatHistory(ItemStats.NAME)).thenReturn(history);
    GearName.write(mmo, IRON_STAFF);
    assertEquals(IRON_STAFF, current.getString());
    assertEquals(IRON_STAFF, original.getString());
    verify(mmo).replaceData(ItemStats.NAME, current);
    verify(mmo).setStatHistory(ItemStats.NAME, history);

    var bare = mock(MMOItem.class);
    when(bare.getData(ItemStats.NAME)).thenReturn(new StringData("plain"));
    var otherHistory = mock(StatHistory.class);
    when(otherHistory.getOriginalData()).thenReturn(new StringData("plain"));
    when(bare.computeStatHistory(ItemStats.NAME)).thenReturn(otherHistory);
    GearName.write(bare, IRON_STAFF);
    verify(bare)
        .replaceData(
            eq(ItemStats.NAME),
            argThat(d -> d instanceof NameData n && IRON_STAFF.equals(n.getString())));
    verify(bare, never()).setStatHistory(any(), any());

    var fresh = mock(MMOItem.class);
    GearName.write(fresh, IRON_STAFF);
    verify(fresh).replaceData(eq(ItemStats.NAME), any(NameData.class));
    verify(fresh, never()).setStatHistory(any(), any());
  }

  @Test
  void craftingNamesTheWeaponAndThePreviewShowsIt() {
    MockBukkit.createMockPlugin("MMOItems");
    MockBukkit.createMockPlugin("MythicLib");
    var core = core("Name", IRON);
    PartRegistry.register(core);
    ArchetypeRegistry.register(staff("%material% Mage Staff"));
    var built = item();
    try (var refs = mockStatic(ItemRef.class);
        var nbt = mockStatic(NBTItem.class);
        var stats = mockStatic(GearStatApplicator.class);
        var models = mockStatic(GearModelResolver.class);
        var lore = mockStatic(WeaponLore.class);
        var items =
            mockConstruction(
                LiveMMOItem.class,
                (m, c) -> {
                  var builder = mock(ItemStackBuilder.class);
                  when(m.newBuilder()).thenReturn(builder);
                  when(builder.build()).thenReturn(built);
                })) {
      refs.when(() -> ItemRef.build("v.STICK")).thenAnswer(i -> item());
      models
          .when(() -> GearModelResolver.apply(any(), any(), any()))
          .thenAnswer(i -> i.getArgument(0));
      lore.when(() -> WeaponLore.updateItem(any())).thenAnswer(i -> i.getArgument(0));
      var result = GearItemBuilder.prepare(GearType.STAFF, List.of(core));
      assertSame(built, result);
      assertEquals(IRON_STAFF, stored(result));
      verify(items.constructed().getFirst())
          .replaceData(
              eq(ItemStats.NAME),
              argThat(d -> d instanceof NameData n && IRON_STAFF.equals(n.getString())));
      var preview = GearItemBuilder.preview(GearType.STAFF, List.of(core));
      assertEquals(IRON_STAFF, preview.getItemMeta().getDisplayName());
    }
  }

  @Test
  void socketRewritesRenameTheWeapon() {
    MockBukkit.createMockPlugin("MMOItems");
    MockBukkit.createMockPlugin("MythicLib");
    var item = crafted(core("Name", IRON));
    var built = item();
    try (var nbt = mockStatic(NBTItem.class);
        var stats = mockStatic(GearStatApplicator.class);
        var models = mockStatic(GearModelResolver.class);
        var skin = mockStatic(GearSkin.class, i -> i.getArgument(1));
        var lore = mockStatic(WeaponLore.class);
        var items =
            mockConstruction(
                LiveMMOItem.class,
                (m, c) -> {
                  var builder = mock(ItemStackBuilder.class);
                  when(m.newBuilder()).thenReturn(builder);
                  when(builder.build()).thenReturn(built);
                })) {
      models
          .when(() -> GearModelResolver.apply(any(), any(), any()))
          .thenAnswer(i -> i.getArgument(0));
      lore.when(() -> WeaponLore.updateItem(any())).thenAnswer(i -> i.getArgument(0));
      var result = GearItemBuilder.rewriteSockets(item, 1);
      assertSame(built, result);
      assertEquals(IRON_STAFF, stored(result));
      assertTrue(GearProvenance.socketsLocked(result));
    }
  }

  @Test
  void refreshRenamesWeaponsCraftedBeforeTheirCoreNamedThemOnce() {
    MockBukkit.createMockPlugin("MMOItems");
    MockBukkit.createMockPlugin("MythicLib");
    var item = crafted(core("Name", IRON));
    var built = item();
    try (var nbt = mockStatic(NBTItem.class);
        var stats = mockStatic(GearStatApplicator.class);
        var models = mockStatic(GearModelResolver.class);
        var skin = mockStatic(GearSkin.class, i -> i.getArgument(1));
        var lore = mockStatic(WeaponLore.class);
        var items =
            mockConstruction(
                LiveMMOItem.class,
                (m, c) -> {
                  var builder = mock(ItemStackBuilder.class);
                  when(m.newBuilder()).thenReturn(builder);
                  when(builder.build()).thenReturn(built);
                })) {
      models
          .when(() -> GearModelResolver.apply(any(), any(), any()))
          .thenAnswer(i -> i.getArgument(0));
      lore.when(() -> WeaponLore.updateItem(any())).thenAnswer(i -> i.getArgument(0));
      var result = GearRefresher.refreshIfOutdated(item, null);
      assertSame(built, result);
      assertEquals(IRON_STAFF, stored(result));
      verify(items.constructed().getFirst())
          .replaceData(
              eq(ItemStats.NAME),
              argThat(d -> d instanceof NameData n && IRON_STAFF.equals(n.getString())));
      assertNull(GearRefresher.refreshIfOutdated(result, null));
      assertEquals(1, items.constructed().size());
    }
  }

  static Object invokeCore(Collection<PartDef> parts) throws Exception {
    return invoke(GearName.class, "core", new Class[] {Collection.class}, parts);
  }

  static Object invokeMaterial(PartDef core) throws Exception {
    return invoke(GearName.class, "material", new Class[] {PartDef.class}, core);
  }
}
