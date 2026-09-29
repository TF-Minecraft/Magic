package net.tfminecraft.magic;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.nio.file.*;
import java.util.*;
import net.tfminecraft.magic.charge.TierBands;
import net.tfminecraft.magic.gear.*;
import net.tfminecraft.tlibs.TLibs;
import net.tfminecraft.tlibs.objects.api.BlockAPI;
import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.inventory.*;
import org.junit.jupiter.api.*;
import org.mockito.MockedStatic;

abstract class GearStationCoverageSupport extends GearCoverageSupport {
  MockedStatic<org.mockbukkit.mockbukkit.entity.EntityTypesMock> entityTypes;
  MockedStatic<TLibs> libs;
  BlockAPI blocks;
  net.tfminecraft.tlibs.objects.api.subapi.BlockChecker checker;
  String oldStation;

  @SuppressWarnings("unchecked")
  static Map<String, GearStationStore.Occupancy> occupied() throws Exception {
    var field = GearStationStore.class.getDeclaredField("OCCUPIED");
    field.setAccessible(true);
    return (Map<String, GearStationStore.Occupancy>) field.get(null);
  }

  @BeforeEach
  void setupStation() throws Exception {
    occupied().clear();
    ((Map<?, ?>) field("retainedRows")).clear();
    setField("unreadableStore", false);
    entityTypes =
        mockStatic(org.mockbukkit.mockbukkit.entity.EntityTypesMock.class, CALLS_REAL_METHODS);
    entityTypes
        .when(
            () ->
                org.mockbukkit.mockbukkit.entity.EntityTypesMock.createEntity(
                    eq(ItemDisplay.class), same(server)))
        .thenAnswer(
            a -> {
              var display =
                  spy(
                      new org.mockbukkit.mockbukkit.entity.ItemDisplayMock(
                          server, UUID.randomUUID()));
              doNothing().when(display).setBillboard(any(Display.Billboard.class));
              return display;
            });
    oldStation = GearCache.station;
    GearCache.station = "iaf(test:station)";
    libs = mockStatic(TLibs.class);
    blocks = mock(BlockAPI.class, RETURNS_DEEP_STUBS);
    libs.when(TLibs::getBlockAPI).thenReturn(blocks);
    checker = blocks.getChecker();
    when(checker.checkBlock(any(), anyString())).thenReturn(true);
    TierBands.clear();
    TierBands.register("fire", 1, 1);
  }

  @AfterEach
  void cleanupStation() throws Exception {
    occupied().clear();
    GearCache.station = oldStation;
    entityTypes.close();
    libs.close();
    TierBands.clear();
  }

  static Object field(String name) throws Exception {
    var f = GearStationStore.class.getDeclaredField(name);
    f.setAccessible(true);
    return f.get(null);
  }

  static void setField(String name, Object value) throws Exception {
    var f = GearStationStore.class.getDeclaredField(name);
    f.setAccessible(true);
    f.set(null, value);
  }

  Location station() {
    return new Location(
        server.getWorld("world") == null
            ? server.addSimpleWorld("world")
            : server.getWorld("world"),
        1,
        2,
        3);
  }

  ItemStack attuned() {
    var weapon = item();
    var req = WeaponRequirement.fromItem(weapon);
    req.aura().setCap("fire", 10);
    req.aura().setFill("fire", 10);
    req.persist(weapon);
    return weapon;
  }
}
