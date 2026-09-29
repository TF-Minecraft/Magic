package net.tfminecraft.magic;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.*;
import net.tfminecraft.magic.gear.*;
import net.tfminecraft.magic.gear.gui.*;
import net.tfminecraft.magic.util.ItemRef;
import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.event.inventory.*;
import org.bukkit.inventory.*;
import org.bukkit.persistence.PersistentDataType;
import org.junit.jupiter.api.*;
import org.mockito.MockedStatic;

class GearGuiCoverageTest extends GearCoverageSupport {
  org.mockito.MockedStatic<net.tfminecraft.tlibs.TLibs> libs;
  net.tfminecraft.tlibs.objects.api.subapi.BlockChecker checker;
  MockedStatic<GearItemBuilder> builder;
  MockedStatic<ItemRef> refs;
  GearInventoryManager gui;
  Player p;
  int output;

  @BeforeEach
  void setupGui() {
    libs = mockStatic(net.tfminecraft.tlibs.TLibs.class);
    var blockAPI = mock(net.tfminecraft.tlibs.objects.api.BlockAPI.class, RETURNS_DEEP_STUBS);
    checker = blockAPI.getChecker();
    libs.when(net.tfminecraft.tlibs.TLibs::getBlockAPI).thenReturn(blockAPI);
    when(checker.checkBlock(any(), any())).thenReturn(true);
    p = server.addPlayer();
    gui = new GearInventoryManager();
    PartTypeRegistry.clear();
    output = GearCache.outputSlot;
    GearCache.outputSlot = 22;
    builder = mockStatic(GearItemBuilder.class);
    builder
        .when(() -> GearItemBuilder.preview(any(), anyCollection()))
        .thenAnswer(a -> new ItemStack(Material.STICK));
    refs = mockStatic(ItemRef.class);
    refs.when(() -> ItemRef.buildOrFallback(any(), any()))
        .thenAnswer(a -> new ItemStack((Material) a.getArgument(1)));
  }

  @AfterEach
  void cleanupGui() {
    libs.close();
    SelectedPartsManager.clear(p);
    TypeSelectionManager.clear(p);
    OpenStationManager.clear(p);
    PartTypeRegistry.clear();
    GearCache.outputSlot = output;
    refs.close();
    builder.close();
  }

  PartDef part(
      String id, String category, Set<GearType> types, boolean disabled, List<String> limit) {
    var part =
        new PartDef(
            id,
            id,
            category,
            1,
            types,
            "v.stick",
            Map.of("v.stone", 2),
            limit,
            Map.of(),
            Map.of("spell", 1),
            List.of("Existing"),
            null,
            1,
            disabled);
    PartRegistry.register(part);
    return part;
  }

  void config() {
    ArchetypeRegistry.register(
        new ArchetypeDef(
            GearType.STAFF,
            "Staff",
            "SWORD.TEST",
            "v.stick",
            false,
            List.of("core", "head", "grip", "missing", "invalid0", "invalidbig"),
            Map.of()));
    PartTypeRegistry.register(new PartTypeDef("core", 10, "Core"));
    PartTypeRegistry.register(new PartTypeDef("head", 11));
    PartTypeRegistry.register(new PartTypeDef("grip", 12));
    PartTypeRegistry.register(new PartTypeDef("invalid0", 0));
    PartTypeRegistry.register(new PartTypeDef("invalidbig", 99));
    part("base", "core", Set.of(GearType.STAFF), false, List.of());
    part("head", "head", Set.of(GearType.STAFF), false, List.of());
  }

  Inventory top() {
    return p.getOpenInventory().getTopInventory();
  }

  InventoryClickEvent event(Inventory inventory, int slot, ItemStack current) {
    var e = mock(InventoryClickEvent.class);
    when(e.getWhoClicked()).thenReturn(p);
    when(e.getInventory()).thenReturn(inventory);
    when(e.getClickedInventory()).thenReturn(inventory);
    when(e.getSlot()).thenReturn(slot);
    when(e.getCurrentItem()).thenReturn(current);
    return e;
  }

  @Test
  void assemblyAndPartMenusUseValidDefaultsAndBoundedSlots() throws Exception {
    assertTrue(gui.collectParts(p, GearType.STAFF).isEmpty());
    gui.openAssembly(p);
    assertEquals(27, top().getSize());
    config();
    gui.openAssembly(p);
    assertEquals(Material.PAPER, top().getItem(10).getType());
    assertEquals(Material.BARRIER, top().getItem(12).getType());
    assertEquals(2, gui.collectParts(p, GearType.STAFF).size());
    for (String selected : List.of("missing", "disabled", "wrongtype", "wrongcategory", "base")) {
      part("disabled", "core", Set.of(GearType.STAFF), true, List.of());
      part("wrongtype", "core", Set.of(GearType.WAND), false, List.of());
      part("wrongcategory", "head", Set.of(GearType.STAFF), false, List.of());
      SelectedPartsManager.set(p, "core", selected);
      gui.openAssembly(p);
      assertEquals("base", top().getItem(10).getItemMeta().getDisplayName());
    }
    gui.openPartSelection(p, "closed");
    assertTrue(top().getHolder() instanceof AssemblyHolder);
    gui.openPartSelection(p, "grip");
    assertEquals(Material.BARRIER, top().getItem(4).getType());
    gui.openPartSelection(p, "head");
    assertTrue(top().getHolder() instanceof PartSelectionHolder);
    assertEquals(
        "head",
        top()
            .getItem(0)
            .getItemMeta()
            .getPersistentDataContainer()
            .get(GearKeys.partPick(), PersistentDataType.STRING));
    for (int i = 0; i < 60; i++) part("many" + i, "head", Set.of(GearType.STAFF), false, List.of());
    gui.openPartSelection(p, "head");
    assertEquals(54, top().getSize());
    assertEquals(Material.ARROW, top().getItem(53).getType());
    gui.openTypeSelection(p);
    assertEquals(9, top().getSize());
    ArchetypeRegistry.register(
        new ArchetypeDef(GearType.WAND, "Wand", "template", "v.stick", true, List.of(), Map.of()));
    gui.openTypeSelection(p);
    assertTrue(
        Arrays.stream(top().getContents())
            .filter(Objects::nonNull)
            .anyMatch(
                i ->
                    i.getItemMeta().hasLore()
                        && i.getItemMeta().getLore().contains("§8Melee weapon")));
  }

  @Test
  void menuClickRoutingHonorsHoldersPartsAndTypeSelection() throws Exception {
    var unrelated = Bukkit.createInventory(null, 9);
    var e = event(unrelated, 0, null);
    when(e.getWhoClicked()).thenReturn(mock(HumanEntity.class));
    gui.onClick(e);
    gui.onClick(event(unrelated, 0, null));
    config();
    gui.openAssembly(p);
    var assembly = top();
    for (ItemStack item :
        Arrays.asList(
            null, new ItemStack(Material.GRAY_STAINED_GLASS_PANE), new ItemStack(Material.BARRIER)))
      gui.onClick(event(assembly, 1, item));
    gui.onClick(event(assembly, 25, item()));
    gui.onClick(event(assembly, 0, item()));
    assertTrue(top().getHolder() instanceof TypeSelectionHolder);
    var selection = top();
    gui.onClick(event(selection, 0, null));
    var bare = mock(ItemStack.class);
    gui.onClick(event(selection, 0, bare));
    var unknown = item();
    gui.onClick(event(selection, 0, unknown));
    gui.onClick(event(selection, 8, selection.getItem(8)));
    assertTrue(top().getHolder() instanceof AssemblyHolder);
    gui.openTypeSelection(p);
    selection = top();
    gui.onClick(event(selection, 0, selection.getItem(0)));
    assertEquals(GearType.STAFF, TypeSelectionManager.get(p));
    gui.openAssembly(p);
    gui.onClick(event(top(), 11, top().getItem(11)));
    assertTrue(top().getHolder() instanceof PartSelectionHolder);
    selection = top();
    gui.onClick(event(selection, 0, null));
    gui.onClick(event(selection, 0, bare));
    gui.onClick(event(selection, 0, item()));
    gui.onClick(event(selection, 0, selection.getItem(0)));
    assertEquals("head", SelectedPartsManager.get(p, "head"));
    gui.openPartSelection(p, "head");
    selection = top();
    gui.onClick(
        event(selection, selection.getSize() - 1, selection.getItem(selection.getSize() - 1)));
    assertTrue(top().getHolder() instanceof AssemblyHolder);
    part("limited", "core", Set.of(GearType.STAFF), false, List.of("head"));
    SelectedPartsManager.set(p, "grip", "removed");
    gui.openPartSelection(p, "core");
    var pick = item();
    tag(pick, GearKeys.partPick(), PersistentDataType.STRING, "limited");
    gui.onClick(event(top(), 0, pick));
    assertNull(SelectedPartsManager.get(p, "grip"));
    assertEquals("limited", SelectedPartsManager.get(p, "core"));
    gui.onClick(event(top(), 12, item()));
  }

  @Test
  void preparationReportsMissingStationsInvalidBuildsCostsAndStaffBypass() throws Exception {
    config();
    gui.openAssembly(p);
    var assembly = top();
    gui.onClick(event(assembly, GearCache.outputSlot, item()));
    var location = new Location(server.addSimpleWorld("world"), 1, 2, 3);
    OpenStationManager.set(p, location);
    try (var stores = mockStatic(GearStationStore.class);
        var costs = mockStatic(GearCosts.class)) {
      stores.when(() -> GearStationStore.isOccupied(location)).thenReturn(true);
      gui.onClick(event(assembly, GearCache.outputSlot, item()));
      stores.when(() -> GearStationStore.isOccupied(location)).thenReturn(false);
      builder
          .when(() -> GearItemBuilder.preview(any(), anyCollection()))
          .thenReturn(new ItemStack(Material.BARRIER));
      gui.onClick(event(assembly, GearCache.outputSlot, item()));
      builder
          .when(() -> GearItemBuilder.preview(any(), anyCollection()))
          .thenAnswer(a -> new ItemStack(Material.STICK));
      gui.onClick(event(assembly, GearCache.outputSlot, item()));
      costs.when(() -> GearCosts.has(eq(p), anyCollection())).thenReturn(true);
      gui.onClick(event(assembly, GearCache.outputSlot, item()));
      builder
          .when(() -> GearItemBuilder.prepare(any(), anyCollection()))
          .thenReturn(new ItemStack(Material.BARRIER));
      gui.onClick(event(assembly, GearCache.outputSlot, item()));
      builder
          .when(() -> GearItemBuilder.prepare(any(), anyCollection()))
          .thenAnswer(a -> new ItemStack(Material.STICK));
      for (boolean bypass : List.of(false, true)) {
        OpenStationManager.set(p, location);
        costs.when(() -> GearCosts.bypasses(p)).thenReturn(bypass);
        costs.when(() -> GearCosts.total(anyCollection())).thenReturn(Map.of("v.stone", 2));
        gui.onClick(event(assembly, GearCache.outputSlot, item()));
        assertNull(OpenStationManager.get(p));
      }
      costs.verify(() -> GearCosts.take(eq(p), anyCollection()), times(2));
      stores.verify(
          () ->
              GearStationStore.occupy(
                  eq(location), any(), eq(p.getUniqueId()), eq(Map.of("v.stone", 2))));
      stores.verify(
          () -> GearStationStore.occupy(eq(location), any(), eq(p.getUniqueId()), eq(Map.of())));
    }
  }

  @Test
  void bottomInventoryClicksCannotTriggerAssemblyActions() {
    config();
    gui.openAssembly(p);
    var e = event(top(), 0, item());
    when(e.getClickedInventory()).thenReturn(p.getInventory());
    gui.onClick(e);
    assertTrue(top().getHolder() instanceof AssemblyHolder);
    verify(e).setCancelled(true);
  }

  @Test
  void selectionManagersHandleAbsentPlayersAndSnapshotCategories() {
    assertNull(SelectedPartsManager.get(null, "core"));
    assertNull(SelectedPartsManager.get(p, null));
    SelectedPartsManager.set(null, "core", "id");
    SelectedPartsManager.set(p, null, "id");
    SelectedPartsManager.set(p, "core", null);
    SelectedPartsManager.remove(null, "core");
    SelectedPartsManager.remove(p, null);
    SelectedPartsManager.remove(p, "core");
    assertTrue(SelectedPartsManager.categories(null).isEmpty());
    assertTrue(SelectedPartsManager.categories(p).isEmpty());
    SelectedPartsManager.set(p, "core", "id");
    var snapshot = SelectedPartsManager.categories(p);
    SelectedPartsManager.remove(p, "core");
    assertEquals(Set.of("core"), snapshot);
    assertTrue(SelectedPartsManager.categories(p).isEmpty());
    SelectedPartsManager.clear(null);
    TypeSelectionManager.clear(null);
    TypeSelectionManager.set(null, GearType.WAND);
    TypeSelectionManager.set(p, null);
    assertEquals(GearType.STAFF, TypeSelectionManager.get(null));
    TypeSelectionManager.set(p, GearType.WAND);
    assertEquals(GearType.WAND, TypeSelectionManager.get(p));
    assertNull(new AssemblyHolder().getInventory());
    assertNull(new TypeSelectionHolder().getInventory());
    assertNull(new PartSelectionHolder("core").getInventory());
  }

  @Test
  void bottomAndOutsideClicksCannotChangeAnyMenu() {
    config();
    for (int mode = 0; mode < 3; mode++) {
      if (mode == 0) gui.openAssembly(p);
      if (mode == 1) gui.openTypeSelection(p);
      if (mode == 2) gui.openPartSelection(p, "head");
      var top = top();
      for (Inventory clicked : Arrays.asList(p.getInventory(), null)) {
        var e = event(top, 0, item());
        when(e.getClickedInventory()).thenReturn(clicked);
        gui.onClick(e);
        assertSame(top, top());
        verify(e).setCancelled(true);
      }
    }
  }

  @Test
  void iconsWithoutMetadataAndPartsWithoutSocketsOrCostsRemainUsable() throws Exception {
    assertEquals(
        "Missing Type",
        invoke(
            GearInventoryManager.class,
            "partTypeName",
            new Class[] {String.class},
            "missing_type"));
    var free =
        new PartDef(
            "free",
            "Free",
            "head",
            1,
            Set.of(GearType.STAFF),
            "v.stick",
            Map.of(),
            List.of(),
            Map.of(),
            Map.of(),
            List.of(),
            null,
            1,
            false);
    var icon =
        (ItemStack)
            invoke(
                GearInventoryManager.class,
                "partIcon",
                new Class[] {PartDef.class, boolean.class},
                free,
                false);
    assertEquals("Free", icon.getItemMeta().getDisplayName());
    assertThrows(IllegalArgumentException.class, () -> new ItemStack(Material.CAVE_AIR));
  }

  @Test
  void removedStationCannotConsumeMaterialsOrPrepareAnItem() {
    config();
    gui.openAssembly(p);
    var location = new Location(p.getWorld(), 1, 2, 3);
    OpenStationManager.set(p, location);
    builder.when(() -> GearItemBuilder.prepare(any(), anyCollection())).thenReturn(item());
    try (var costs = mockStatic(GearCosts.class);
        var store = mockStatic(GearStationStore.class)) {
      costs.when(() -> GearCosts.has(eq(p), anyCollection())).thenReturn(true);
      when(checker.checkBlock(any(), any())).thenReturn(false);
      gui.onClick(event(top(), GearCache.outputSlot, item()));
      costs.verify(() -> GearCosts.take(eq(p), anyCollection()), never());
      store.verify(() -> GearStationStore.occupy(any(), any(), any(), any()), never());
      when(checker.checkBlock(any(), any())).thenThrow(new IllegalStateException("unavailable"));
      gui.onClick(event(top(), GearCache.outputSlot, item()));
      costs.verify(() -> GearCosts.take(eq(p), anyCollection()), never());
    }
  }
}
