package net.tfminecraft.magic;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.*;
import net.tfminecraft.magic.gear.*;
import net.tfminecraft.magic.util.ItemRef;
import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.inventory.*;
import org.junit.jupiter.api.*;

class GearCostsCoverageTest extends GearCoverageSupport {
  PartDef cost(String id, int amount) {
    return new PartDef(
        id,
        id,
        "core",
        1,
        Set.of(GearType.STAFF),
        "v.STICK",
        Map.of("v.STONE", amount),
        List.of(),
        Map.of(),
        Map.of(),
        List.of(),
        null,
        1,
        false);
  }

  @Test
  void combinedCostsCannotWrapNegativeAndPermitFreeCrafting() {
    assertEquals(
        Integer.MAX_VALUE,
        GearCosts.total(List.of(cost("a", Integer.MAX_VALUE), cost("b", Integer.MAX_VALUE)))
            .get("v.STONE"));
  }

  @Test
  void inventoryChecksAndConsumptionHandleBypassFailuresAndPartialStacks() {
    assertTrue(GearCosts.total(null).isEmpty());
    assertTrue(
        GearCosts.total(
                Arrays.asList(
                    null,
                    new PartDef(
                        null, null, null, 0, null, null, null, null, null, null, null, null, 0,
                        false)))
            .isEmpty());
    assertFalse(GearCosts.bypasses(null));
    var p = server.addPlayer();
    var parts = List.of(cost("core", 5));
    try (var libs = mockStatic(net.tfminecraft.tlibs.TLibs.class)) {
      var api = mock(net.tfminecraft.tlibs.objects.api.ItemAPI.class, RETURNS_DEEP_STUBS);
      libs.when(net.tfminecraft.tlibs.TLibs::getItemAPI).thenReturn(api);
      when(api.getChecker().checkItemWithPath(any(), any()))
          .thenAnswer(i -> ((ItemStack) i.getArgument(0)).getType() == Material.STONE);
      p.getInventory().setItem(0, new ItemStack(Material.DIRT, 10));
      p.getInventory().setItem(1, new ItemStack(Material.STONE, 2));
      assertFalse(GearCosts.has(p, parts));
      p.getInventory().setItem(2, new ItemStack(Material.STONE, 4));
      assertTrue(GearCosts.has(p, parts));
      GearCosts.take(p, parts);
      assertEquals(1, p.getInventory().getItem(2).getAmount());
      assertEquals(10, p.getInventory().getItem(0).getAmount());
      GearCosts.take(null, parts);
      GearCosts.take(p, List.of(cost("zero", 0)));
      p.setOp(true);
      assertTrue(GearCosts.has(p, parts));
      GearCosts.take(p, parts);
      assertEquals(1, p.getInventory().getItem(2).getAmount());
      p.setOp(false);
      var checker = api.getChecker();
      doThrow(new IllegalArgumentException("bad path"))
          .when(checker)
          .checkItemWithPath(any(), any());
      assertFalse(GearCosts.has(p, parts));
      GearCosts.take(p, parts);
      assertEquals(1, p.getInventory().getItem(2).getAmount());
    }
  }

  @Test
  void refundsSplitStacksAndDropOnlyInventoryOverflow() {
    var p = server.addPlayer();
    GearCosts.refund(null, Map.of(), null);
    GearCosts.refund(p, null, null);
    GearCosts.refund(p, Map.of(), null);
    try (var refs = mockStatic(ItemRef.class)) {
      refs.when(() -> ItemRef.build("stone")).thenAnswer(i -> new ItemStack(Material.STONE));
      GearCosts.refund(p, Map.of("stone", 65), null);
      assertEquals(64, p.getInventory().getItem(0).getAmount());
      assertEquals(1, p.getInventory().getItem(1).getAmount());
      GearCosts.refund(p, Map.of("missing", 1), null);
      refs.when(() -> ItemRef.build("air")).thenReturn(new ItemStack(Material.AIR));
      GearCosts.refund(p, Map.of("air", 1, "stone", 0), null);
      var world = mock(World.class);
      for (int i = 0; i < p.getInventory().getSize(); i++)
        p.getInventory().setItem(i, new ItemStack(Material.DIRT, 64));
      GearCosts.refund(p, Map.of("stone", 2), new Location(world, 1, 2, 3));
      verify(world)
          .dropItem(
              eq(new Location(world, 1.5, 3, 3.5)),
              argThat(it -> it.getType() == Material.STONE && it.getAmount() == 2));
      GearCosts.refund(p, Map.of("stone", 1), new Location(null, 1, 2, 3));
    }
  }
}
