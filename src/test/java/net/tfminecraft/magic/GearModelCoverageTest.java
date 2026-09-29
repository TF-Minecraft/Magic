package net.tfminecraft.magic;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.*;
import net.tfminecraft.magic.gear.*;
import org.bukkit.*;
import org.junit.jupiter.api.*;

class GearModelCoverageTest extends GearCoverageSupport {
  @AfterEach
  void resetModels() {
    GearModelSchemeRegistry.clear();
  }

  PartDef modelPart(String id, String type, String scheme, int weight) {
    return new PartDef(
        id,
        id,
        type,
        1,
        Set.of(GearType.STAFF),
        "v.STICK",
        Map.of(),
        List.of(),
        Map.of(),
        Map.of(),
        List.of(),
        scheme,
        weight,
        false);
  }

  void scheme(String id, String path) {
    GearModelSchemeRegistry.register(new GearModelScheme(id, Map.of(GearType.STAFF, path)));
  }

  @Test
  void largeModelWeightsCannotOverflowIntoLosingVotes() {
    scheme("heavy", "v.STICK");
    scheme("light", "v.BLAZE_ROD");
    var parts =
        List.of(
            modelPart("a", "head", "heavy", Integer.MAX_VALUE),
            modelPart("b", "grip", "heavy", Integer.MAX_VALUE),
            modelPart("c", "core", "light", 1));
    assertEquals("heavy", GearModelResolver.winner(parts).getId());
  }

  @Test
  void malformedOrNonItemModelsCannotDestroyOrPartiallyChangeGear() {
    for (String path : List.of("v.AIR", "v.WATER", "v.DIAMOND.bad-number")) {
      scheme("model", path);
      var item = item();
      var original = item.clone();
      GearModelResolver.apply(item, GearType.STAFF, List.of(modelPart("core", "core", "model", 1)));
      assertEquals(original, item, path);
    }
  }

  @Test
  void invalidCustomModelNumberDoesNotPartiallyChangeItem() {
    scheme("model", "v.DIAMOND.bad-number");
    var item = item();
    var original = item.clone();
    GearModelResolver.apply(item, GearType.STAFF, List.of(modelPart("core", "core", "model", 1)));
    assertEquals(original, item);
  }

  @Test
  void weightedModelsUseCoreToResolveOnlyLeadingTies() {
    assertNull(GearModelResolver.winner(null));
    assertNull(GearModelResolver.path(GearType.STAFF, List.of()));
    assertNull(
        GearModelResolver.winner(
            Arrays.asList(
                null,
                modelPart("none", "head", null, 1),
                modelPart("unknown", "head", "missing", 1))));
    scheme("a", "v.STICK");
    scheme("b", "v.BLAZE_ROD");
    scheme("c", "v.DIAMOND");
    assertEquals(
        "a",
        GearModelResolver.winner(
                List.of(modelPart("a", "head", "a", 2), modelPart("b", "grip", "b", 1)))
            .getId());
    assertEquals(
        "a",
        GearModelResolver.winner(
                List.of(modelPart("a", "head", "a", 1), modelPart("b", "grip", "b", 1)))
            .getId());
    assertEquals(
        "b",
        GearModelResolver.winner(
                List.of(modelPart("a", "head", "a", 1), modelPart("b", "core", "b", 1)))
            .getId());
    assertEquals(
        "a",
        GearModelResolver.winner(
                List.of(
                    modelPart("a", "head", "a", 2),
                    modelPart("b", "grip", "b", 2),
                    modelPart("c", "core", "c", 1)))
            .getId());
    assertEquals(
        "c",
        GearModelResolver.winner(
                List.of(
                    modelPart("a", "head", "a", 1),
                    modelPart("b", "grip", "b", 1),
                    modelPart("c", "core", "c", 2)))
            .getId());
    assertEquals(
        "v.STICK", GearModelResolver.path(GearType.STAFF, List.of(modelPart("a", "core", "a", 1))));
    assertNull(GearModelResolver.path(GearType.WAND, List.of(modelPart("a", "core", "a", 1))));
  }

  @Test
  void modelApplicationHandlesVanillaExternalMissingAndUnsupportedPaths() {
    assertNull(GearModelResolver.apply(null, GearType.STAFF, List.of()));
    var item = item();
    assertSame(item, GearModelResolver.apply(item, GearType.STAFF, List.of()));
    var parts = List.of(modelPart("core", "core", "model", 1));
    for (String path : List.of("v", "v.NO_MATERIAL", "other.path")) {
      scheme("model", path);
      assertSame(item, GearModelResolver.apply(item, GearType.STAFF, parts));
      assertEquals(Material.STICK, item.getType());
    }
    scheme("model", "v.DIAMOND");
    assertSame(item, GearModelResolver.apply(item, GearType.STAFF, parts));
    assertEquals(Material.DIAMOND, item.getType());
    scheme("model", "v.DIAMOND.17");
    try (var models = mockStatic(net.tfminecraft.magic.util.LegacyModelData.class)) {
      GearModelResolver.apply(item, GearType.STAFF, parts);
      models.verify(() -> net.tfminecraft.magic.util.LegacyModelData.set(any(), eq(17)));
    }
    scheme("model", "ia.namespace:item");
    try (var libs = mockStatic(net.tfminecraft.tlibs.TLibs.class)) {
      var api = mock(net.tfminecraft.tlibs.objects.api.ItemAPI.class, RETURNS_DEEP_STUBS);
      libs.when(net.tfminecraft.tlibs.TLibs::getItemAPI).thenReturn(api);
      var merged = item();
      when(api.getArmorMerger().merge(eq(item), any(), anyString())).thenReturn(merged);
      assertSame(merged, GearModelResolver.apply(item, GearType.STAFF, parts));
      when(api.getArmorMerger().merge(eq(item), any(), anyString()))
          .thenThrow(new IllegalStateException("external model failure"));
      assertSame(item, GearModelResolver.apply(item, GearType.STAFF, parts));
    }
  }

  @Test
  void schemeRegistryNormalizesAndRejectsEmptyData() {
    GearModelSchemeRegistry.clear();
    GearModelSchemeRegistry.register(null);
    GearModelSchemeRegistry.register(new GearModelScheme(null, null));
    assertEquals(0, GearModelSchemeRegistry.size());
    assertNull(GearModelSchemeRegistry.get(null));
    assertNull(GearModelSchemeRegistry.get(" "));
    var paths = new LinkedHashMap<GearType, String>();
    paths.put(null, "v.STONE");
    paths.put(GearType.STAFF, null);
    paths.put(GearType.WAND, " ");
    var empty = new GearModelScheme(" Empty ", paths);
    assertNull(empty.pathFor(null));
    assertNull(empty.pathFor(GearType.STAFF));
    GearModelSchemeRegistry.register(empty);
    assertEquals(empty, GearModelSchemeRegistry.all().get("empty"));
    assertThrows(UnsupportedOperationException.class, () -> GearModelSchemeRegistry.all().clear());
  }

  @Test
  void emptyExternalModelCannotDeleteCraftedGear() {
    scheme("model", "ia.namespace:item");
    var parts = List.of(modelPart("core", "core", "model", 1));
    var original = item();
    try (var libs = mockStatic(net.tfminecraft.tlibs.TLibs.class)) {
      var api = mock(net.tfminecraft.tlibs.objects.api.ItemAPI.class, RETURNS_DEEP_STUBS);
      libs.when(net.tfminecraft.tlibs.TLibs::getItemAPI).thenReturn(api);
      when(api.getArmorMerger().merge(eq(original), any(), anyString())).thenReturn(null);
      assertSame(original, GearModelResolver.apply(original, GearType.STAFF, parts));
      when(api.getArmorMerger().merge(eq(original), any(), anyString()))
          .thenReturn(new org.bukkit.inventory.ItemStack(Material.AIR));
      assertSame(original, GearModelResolver.apply(original, GearType.STAFF, parts));
    }
  }

  @Test
  void punctuationOnlyModelCannotCrashCrafting() {
    scheme("model", ".");
    var original = item();
    assertSame(
        original,
        GearModelResolver.apply(
            original, GearType.STAFF, List.of(modelPart("core", "core", "model", 1))));
  }
}
