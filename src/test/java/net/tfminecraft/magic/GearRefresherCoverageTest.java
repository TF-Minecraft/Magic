package net.tfminecraft.magic;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import io.lumine.mythic.lib.api.item.NBTItem;
import java.util.*;
import net.Indyuce.mmoitems.ItemStats;
import net.Indyuce.mmoitems.api.item.build.ItemStackBuilder;
import net.Indyuce.mmoitems.api.item.mmoitem.LiveMMOItem;
import net.Indyuce.mmoitems.stat.data.*;
import net.tfminecraft.magic.gear.*;
import org.bukkit.*;
import org.bukkit.inventory.*;
import org.bukkit.persistence.*;
import org.junit.jupiter.api.*;
import org.mockbukkit.mockbukkit.MockBukkit;

class GearRefresherCoverageTest extends GearMmoCoverageSupport {
  ItemStack managed() {
    var p = part("core", 2);
    PartRegistry.register(p);
    var a =
        new ArchetypeDef(
            GearType.STAFF,
            "Staff",
            "v.STICK",
            "v.STICK",
            false,
            List.of(),
            Map.of("spell", "Spell"));
    ArchetypeRegistry.register(a);
    var item = item();
    GearProvenance.stamp(item, GearType.STAFF, List.of(p));
    return item;
  }

  @Test
  void refreshGuardsAndMissingPartProtectionPreserveOriginals() {
    assertFalse(GearRefresher.isManaged(null));
    assertNull(GearRefresher.refresh(null, null, true));
    var empty = item();
    GearProvenance.stamp(empty, GearType.STAFF, null);
    assertFalse(GearRefresher.isManaged(empty));
    var item = managed();
    assertTrue(GearRefresher.isManaged(item));
    assertNull(GearRefresher.refresh(item, null, true));
    MockBukkit.createMockPlugin("MMOItems");
    MockBukkit.createMockPlugin("MythicLib");
    try (var broken = mockStatic(GearBrokenMarker.class)) {
      broken.when(() -> GearBrokenMarker.isBroken(item)).thenReturn(true);
      assertNull(GearRefresher.refresh(item, null, true));
      broken.when(() -> GearBrokenMarker.isBroken(item)).thenReturn(false);
      assertNull(GearRefresher.refreshIfOutdated(item, null));
      PartRegistry.clear();
      var result = GearRefresher.refresh(item, null, true);
      assertNotSame(item, result);
      broken.verify(() -> GearBrokenMarker.mark(result));
      broken.verify(() -> GearBrokenMarker.notifyOnce(null, result, List.of("core")));
    }
  }

  @Test
  void missingMajorityCanRefreshWithoutRebuildingMmoData() {
    var item = managed();
    MockBukkit.createMockPlugin("MMOItems");
    MockBukkit.createMockPlugin("MythicLib");
    tag(item, GearKeys.majorityTier(), PersistentDataType.INTEGER, null);
    try (var lore = mockStatic(WeaponLore.class)) {
      lore.when(() -> WeaponLore.updateItem(any())).thenAnswer(i -> i.getArgument(0));
      var result = GearRefresher.refreshIfOutdated(item, null);
      assertEquals(2, GearProvenance.majorityOf(result));
      assertEquals(0, GearProvenance.majorityOf(item));
    }
    PartRegistry.clear();
    var zero = part("core", 0);
    PartRegistry.register(zero);
    assertNull(GearRefresher.refreshIfOutdated(item, null));
    ArchetypeRegistry.clear();
    assertNull(GearRefresher.refresh(item, null, true));
  }

  @Test
  void rebuildPreservesGearPdcAmountsAndExistingGems() {
    var item = managed();
    item.setAmount(3);
    tag(item, GearKeys.orphans(), PersistentDataType.STRING, "old");
    GearProvenance.lockSockets(item);
    PartRegistry.get("core").setRevision(2);
    MockBukkit.createMockPlugin("MMOItems");
    MockBukkit.createMockPlugin("MythicLib");
    var built = item();
    var good = mock(GemstoneData.class);
    String colour =
        SocketLayout.colours(
                ArchetypeRegistry.get(GearType.STAFF), GearProvenance.resolveParts(item), 0)
            .getFirst();
    when(good.getSocketColor()).thenReturn(colour);
    var oldSockets = new GemSocketsData(List.of());
    oldSockets.add(good);
    try (var nbt = mockStatic(NBTItem.class);
        var stats = mockStatic(GearStatApplicator.class);
        var model = mockStatic(GearModelResolver.class);
        var lore = mockStatic(WeaponLore.class);
        var items =
            mockConstruction(
                LiveMMOItem.class,
                (m, c) -> {
                  when(m.hasData(ItemStats.GEM_SOCKETS)).thenReturn(true);
                  when(m.getData(ItemStats.GEM_SOCKETS)).thenReturn(oldSockets);
                  var builder = mock(ItemStackBuilder.class);
                  when(m.newBuilder()).thenReturn(builder);
                  when(builder.build()).thenReturn(built);
                })) {
      model
          .when(() -> GearModelResolver.apply(any(), any(), any()))
          .thenAnswer(i -> i.getArgument(0));
      lore.when(() -> WeaponLore.updateItem(any())).thenAnswer(i -> i.getArgument(0));
      var result = GearRefresher.refreshIfOutdated(item, null);
      assertSame(built, result);
      assertEquals(3, result.getAmount());
      assertEquals("core:2", GearProvenance.partsRaw(result));
      assertTrue(GearProvenance.socketsLocked(result));
      assertEquals(
          "old",
          result
              .getItemMeta()
              .getPersistentDataContainer()
              .get(GearKeys.orphans(), PersistentDataType.STRING));
      var captured = org.mockito.ArgumentCaptor.forClass(GemSocketsData.class);
      verify(items.constructed().getFirst()).setData(eq(ItemStats.GEM_SOCKETS), captured.capture());
      assertEquals(List.of(good), captured.getValue().getGems());
    }
  }

  @Test
  void outdatedRunesRebuildCurrentGearAndStampTheirRevisions() {
    var item = managed();
    MockBukkit.createMockPlugin("MMOItems");
    MockBukkit.createMockPlugin("MythicLib");
    var built = item();
    var revisions = Map.of(UUID.randomUUID(), 3);
    try (var nbt = mockStatic(NBTItem.class);
        var stats = mockStatic(GearStatApplicator.class);
        var model = mockStatic(GearModelResolver.class);
        var lore = mockStatic(WeaponLore.class);
        var runes = mockStatic(RuneRefresher.class);
        var items =
            mockConstruction(
                LiveMMOItem.class,
                (m, c) -> {
                  var builder = mock(ItemStackBuilder.class);
                  when(m.newBuilder()).thenReturn(builder);
                  when(builder.build()).thenReturn(built);
                })) {
      model
          .when(() -> GearModelResolver.apply(any(), any(), any()))
          .thenAnswer(i -> i.getArgument(0));
      lore.when(() -> WeaponLore.updateItem(any())).thenAnswer(i -> i.getArgument(0));
      runes.when(() -> RuneRefresher.isOutdated(item)).thenReturn(false);
      assertNull(GearRefresher.refreshIfOutdated(item, null));
      runes.when(() -> RuneRefresher.isOutdated(item)).thenReturn(true);
      runes.when(() -> RuneRefresher.refresh(any(), eq(item))).thenReturn(revisions);
      assertSame(built, GearRefresher.refreshIfOutdated(item, null));
      runes.verify(() -> RuneRefresher.stamp(built, revisions));
    }
  }

  @Test
  void incompatibleGemsAreHeldAndFailuresNeverReplaceOriginals() {
    MockBukkit.createMockPlugin("MMOItems");
    MockBukkit.createMockPlugin("MythicLib");
    for (int mode = 0; mode < 7; mode++) {
      var item = managed();
      int scenario = mode;
      var built = item();
      var orphan = mock(GemstoneData.class);
      when(orphan.getSocketColor()).thenReturn(mode == 0 ? null : "unmatched");
      when(orphan.getMMOItemType()).thenReturn("RUNE");
      when(orphan.getMMOItemID()).thenReturn("TEST");
      var sockets = new GemSocketsData(List.of());
      sockets.add(orphan);
      try (var nbt = mockStatic(NBTItem.class);
          var stats = mockStatic(GearStatApplicator.class);
          var model = mockStatic(GearModelResolver.class);
          var lore = mockStatic(WeaponLore.class);
          var broken = mockStatic(GearBrokenMarker.class);
          var items =
              mockConstruction(
                  LiveMMOItem.class,
                  (m, c) -> {
                    when(m.hasData(ItemStats.GEM_SOCKETS)).thenReturn(scenario != 2);
                    when(m.getData(ItemStats.GEM_SOCKETS))
                        .thenReturn(scenario == 3 ? new StringData("unexpected") : sockets);
                    var builder = mock(ItemStackBuilder.class);
                    when(m.newBuilder()).thenReturn(builder);
                    if (scenario == 4) when(builder.build()).thenReturn(null);
                    else if (scenario == 5)
                      when(builder.build()).thenReturn(new ItemStack(Material.AIR));
                    else if (scenario == 6)
                      when(builder.build()).thenThrow(new IllegalStateException("bad"));
                    else when(builder.build()).thenReturn(built);
                  })) {
        model
            .when(() -> GearModelResolver.apply(any(), any(), any()))
            .thenAnswer(i -> i.getArgument(0));
        lore.when(() -> WeaponLore.updateItem(any())).thenAnswer(i -> i.getArgument(0));
        var result = GearRefresher.refresh(item, null, true);
        if (mode >= 4) assertNull(result);
        else {
          assertSame(built, result);
          if (mode < 2) {
            broken.verify(() -> GearBrokenMarker.mark(built));
            broken.verify(() -> GearBrokenMarker.addOrphans(eq(built), anyList()));
          } else broken.verify(() -> GearBrokenMarker.mark(any()), never());
        }
      }
    }
  }

  @Test
  void metadataCopyHandlesAbsentOptionalTagsAndInvalidStacks() throws Exception {
    var source = item();
    var destination = item();
    for (ItemStack[] pair :
        new ItemStack[][] {
          {null, destination},
          {source, null},
          {new ItemStack(Material.AIR), destination},
          {source, new ItemStack(Material.AIR)},
          {source, destination}
        })
      invoke(
          GearRefresher.class,
          "copyGearPdc",
          new Class[] {ItemStack.class, ItemStack.class},
          pair[0],
          pair[1]);
    assertEquals("Gear", destination.getItemMeta().getDisplayName());
  }
}
