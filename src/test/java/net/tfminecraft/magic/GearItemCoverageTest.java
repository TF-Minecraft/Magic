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
import net.tfminecraft.magic.util.ItemRef;
import org.bukkit.*;
import org.bukkit.inventory.*;
import org.bukkit.persistence.*;
import org.junit.jupiter.api.*;
import org.mockbukkit.mockbukkit.MockBukkit;

class GearItemCoverageTest extends GearMmoCoverageSupport {
  ArchetypeDef archetype(List<String> required) {
    return new ArchetypeDef(
        GearType.STAFF, "Staff", "v.STICK", "v.STICK", false, required, Map.of("spell", "Spell"));
  }

  @Test
  void plainTemplatesStillReceiveGearIdentity() {
    ArchetypeRegistry.register(archetype(List.of()));
    var part = part("core", 1);
    PartRegistry.register(part);
    try (var refs = mockStatic(ItemRef.class);
        var lore = mockStatic(WeaponLore.class);
        var models = mockStatic(GearModelResolver.class)) {
      refs.when(() -> ItemRef.build("v.STICK")).thenAnswer(i -> new ItemStack(Material.STICK));
      lore.when(() -> WeaponLore.updateItem(any())).thenAnswer(i -> i.getArgument(0));
      models
          .when(() -> GearModelResolver.apply(any(), any(), any()))
          .thenAnswer(i -> i.getArgument(0));
      var result = GearItemBuilder.prepare(GearType.STAFF, List.of(part));
      assertTrue(GearProvenance.isGear(result));
      assertEquals("core:1", GearProvenance.partsRaw(result));
    }
  }

  @Test
  void preparationRejectsUnknownMissingPartsAndTemplates() {
    assertEquals(Material.BARRIER, GearItemBuilder.preview(GearType.STAFF, null).getType());
    ArchetypeRegistry.register(archetype(List.of()));
    assertTrue(
        GearItemBuilder.preview(GearType.STAFF, null)
            .getItemMeta()
            .getDisplayName()
            .contains("Missing parts"));
    assertEquals(Material.BARRIER, GearItemBuilder.prepare(GearType.STAFF, List.of()).getType());
    ArchetypeRegistry.register(archetype(List.of("core", "head")));
    var part = part("core", 1);
    assertEquals(Material.BARRIER, GearItemBuilder.prepare(GearType.STAFF, null).getType());
    assertEquals(
        Material.BARRIER,
        GearItemBuilder.prepare(GearType.STAFF, Arrays.asList(null, part)).getType());
    ArchetypeRegistry.register(archetype(List.of("core")));
    try (var refs = mockStatic(ItemRef.class)) {
      assertTrue(
          GearItemBuilder.preview(GearType.STAFF, Arrays.asList(null, part))
              .getItemMeta()
              .getDisplayName()
              .contains("Missing template"));
      assertEquals(
          Material.BARRIER, GearItemBuilder.prepare(GearType.STAFF, List.of(part)).getType());
      refs.when(() -> ItemRef.build("v.STICK")).thenReturn(new ItemStack(Material.AIR));
      assertEquals(
          Material.BARRIER, GearItemBuilder.preview(GearType.STAFF, List.of(part)).getType());
    }
  }

  @Test
  void previewsKeepTemplateNameAndLoreAndExplainRequiredTierAndDependencies() {
    ArchetypeRegistry.register(archetype(List.of()));
    try (var refs = mockStatic(ItemRef.class)) {
      refs.when(() -> ItemRef.build("v.STICK")).thenAnswer(i -> new ItemStack(Material.STICK));
      var result = GearItemBuilder.preview(GearType.STAFF, List.of(part("core", 2)));
      assertEquals("§6Staff", result.getItemMeta().getDisplayName());
      assertTrue(result.getItemMeta().getLore().contains("§eTier II"));
      assertTrue(result.getItemMeta().getLore().contains("§cMMOItems is not loaded"));
      MockBukkit.createMockPlugin("MMOItems");
      MockBukkit.createMockPlugin("MythicLib");
      var named = item();
      var meta = named.getItemMeta();
      meta.setLore(List.of("Existing"));
      named.setItemMeta(meta);
      refs.when(() -> ItemRef.build("v.STICK")).thenReturn(named);
      result = GearItemBuilder.preview(GearType.STAFF, List.of(part("core", 0)));
      assertEquals("Gear", result.getItemMeta().getDisplayName());
      assertEquals("Existing", result.getItemMeta().getLore().getFirst());
      assertFalse(result.getItemMeta().getLore().contains("§cMMOItems is not loaded"));
    }
  }

  @Test
  void mmoRewritesUseBuiltItemOrSafelyKeepTemplateOnFailure() throws Exception {
    MockBukkit.createMockPlugin("MMOItems");
    MockBukkit.createMockPlugin("MythicLib");
    ArchetypeRegistry.register(archetype(List.of()));
    var p = part("core", 1);
    PartRegistry.register(p);
    for (int scenario = 0; scenario < 4; scenario++) {
      var base = item();
      var built = item();
      int mode = scenario;
      try (var refs = mockStatic(ItemRef.class);
          var nbt = mockStatic(NBTItem.class);
          var stats = mockStatic(GearStatApplicator.class);
          var models = mockStatic(GearModelResolver.class);
          var lore = mockStatic(WeaponLore.class);
          var constructors =
              mockConstruction(
                  LiveMMOItem.class,
                  (m, c) -> {
                    var builder = mock(ItemStackBuilder.class);
                    when(m.newBuilder()).thenReturn(builder);
                    if (mode == 0) when(builder.build()).thenReturn(built);
                    if (mode == 1) when(builder.build()).thenReturn(null);
                    if (mode == 2) when(builder.build()).thenReturn(new ItemStack(Material.AIR));
                    if (mode == 3)
                      when(builder.build()).thenThrow(new IllegalStateException("failed"));
                  })) {
        refs.when(() -> ItemRef.build("v.STICK")).thenReturn(base);
        models
            .when(() -> GearModelResolver.apply(any(), any(), any()))
            .thenAnswer(i -> i.getArgument(0));
        lore.when(() -> WeaponLore.updateItem(any())).thenAnswer(i -> i.getArgument(0));
        var result = GearItemBuilder.prepare(GearType.STAFF, List.of(p));
        assertSame(mode == 0 ? built : base, result);
        assertTrue(GearProvenance.isGear(result));
        verify(constructors.constructed().getFirst())
            .setData(eq(ItemStats.GEM_SOCKETS), any(GemSocketsData.class));
      }
    }
    try (var nbt = mockStatic(NBTItem.class);
        var stats = mockStatic(GearStatApplicator.class);
        var constructors =
            mockConstruction(
                LiveMMOItem.class,
                (m, c) -> {
                  when(m.newBuilder()).thenReturn(mock(ItemStackBuilder.class));
                })) {
      var stack = item();
      assertNull(
          invoke(
              GearItemBuilder.class,
              "applyMmoData",
              new Class[] {ItemStack.class, List.class, Collection.class},
              null,
              List.of(),
              List.of()));
      for (List<String> colours : Arrays.asList(null, List.<String>of()))
        assertSame(
            stack,
            invoke(
                GearItemBuilder.class,
                "applyMmoData",
                new Class[] {ItemStack.class, List.class, Collection.class},
                stack,
                colours,
                List.of()));
    }
  }

  @Test
  void socketRewriteKeepsIdentityAndLocksFirstAttunement() throws Exception {
    assertNull(GearItemBuilder.rewriteSockets(null, 1));
    var raw = item();
    assertSame(raw, GearItemBuilder.rewriteSockets(raw, 1));
    ArchetypeRegistry.register(archetype(List.of()));
    var p = part("core", 2);
    PartRegistry.register(p);
    var stack = item();
    GearProvenance.stamp(stack, GearType.STAFF, List.of(p));
    GearProvenance.lockSockets(stack);
    assertSame(stack, GearItemBuilder.rewriteSockets(stack, 1));
    tag(stack, GearKeys.socketsLocked(), PersistentDataType.BYTE, null);
    try (var models = mockStatic(GearModelResolver.class);
        var lore = mockStatic(WeaponLore.class)) {
      models
          .when(() -> GearModelResolver.apply(any(), any(), any()))
          .thenAnswer(i -> i.getArgument(0));
      lore.when(() -> WeaponLore.updateItem(any())).thenAnswer(i -> i.getArgument(0));
      assertSame(stack, GearItemBuilder.rewriteSockets(stack, 1));
      assertTrue(GearProvenance.socketsLocked(stack));
      assertEquals("core:1", GearProvenance.partsRaw(stack));
    }
    var minimal = item();
    tag(minimal, GearKeys.archetype(), PersistentDataType.STRING, "staff");
    var dest = item();
    invoke(
        GearItemBuilder.class,
        "copyGearPdc",
        new Class[] {ItemStack.class, ItemStack.class},
        minimal,
        dest);
    assertEquals(GearType.STAFF, GearProvenance.archetypeOf(dest));
    for (ItemStack[] pair :
        new ItemStack[][] {
          {null, dest},
          {minimal, null},
          {new ItemStack(Material.AIR), dest},
          {minimal, new ItemStack(Material.AIR)},
          {raw, dest}
        })
      invoke(
          GearItemBuilder.class,
          "copyGearPdc",
          new Class[] {ItemStack.class, ItemStack.class},
          pair[0],
          pair[1]);
  }

  @Test
  void nonCorePartsCannotSatisfyRequiredCore() {
    ArchetypeRegistry.register(archetype(List.of("core")));
    var head =
        new PartDef(
            "head",
            "Head",
            "head",
            1,
            Set.of(GearType.STAFF),
            "v.STICK",
            Map.of(),
            List.of(),
            Map.of(),
            Map.of(),
            List.of(),
            null,
            1,
            false);
    assertTrue(
        GearItemBuilder.prepare(GearType.STAFF, List.of(head))
            .getItemMeta()
            .getDisplayName()
            .contains("Missing parts"));
  }
}
