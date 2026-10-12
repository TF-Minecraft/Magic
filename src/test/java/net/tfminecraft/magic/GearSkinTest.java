package net.tfminecraft.magic;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import io.lumine.mythic.lib.api.item.NBTItem;
import net.tfminecraft.magic.gear.GearSkin;
import net.tfminecraft.magic.util.LegacyModelData;
import net.tfminecraft.tlibs.objects.api.subapi.ItemSkinPreserver;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.junit.jupiter.api.*;
import org.mockito.MockedStatic;

class GearSkinTest {
  MockedStatic<ItemSkinPreserver> preserver;
  MockedStatic<NBTItem> nbt;
  MockedStatic<LegacyModelData> models;

  @BeforeEach
  void mockStatics() {
    preserver = mockStatic(ItemSkinPreserver.class);
    nbt = mockStatic(NBTItem.class);
    models = mockStatic(LegacyModelData.class);
  }

  @AfterEach
  void closeStatics() {
    preserver.close();
    nbt.close();
    models.close();
  }

  /** A weapon with skin data, an {@code amodel} tag and an optional current model. */
  ItemStack weapon(boolean skinData, String amodel, Integer model, boolean hasMeta) {
    var stack = mock(ItemStack.class);
    var meta = hasMeta ? mock(ItemMeta.class) : null;
    when(stack.getItemMeta()).thenReturn(meta);
    preserver.when(() -> ItemSkinPreserver.hasSkinData(same(stack))).thenReturn(skinData);
    var tags = mock(NBTItem.class);
    when(tags.getString("amodel")).thenReturn(amodel);
    nbt.when(() -> NBTItem.get(same(stack))).thenReturn(tags);
    if (meta != null) {
      models.when(() -> LegacyModelData.has(same(meta))).thenReturn(model != null);
      if (model != null) models.when(() -> LegacyModelData.get(same(meta))).thenReturn(model);
    }
    return stack;
  }

  @Test
  void rebuildsKeepAWornSkinAndDropAStaleTag() {
    var rebuilt = mock(ItemStack.class);
    var dressed = mock(ItemStack.class);
    preserver.when(() -> ItemSkinPreserver.apply(any(), same(rebuilt))).thenReturn(dressed);

    assertSame(dressed, GearSkin.keep(weapon(true, "66", 66, true), rebuilt));
    assertSame(dressed, GearSkin.keep(weapon(true, null, null, true), rebuilt));
    assertSame(dressed, GearSkin.keep(weapon(true, "", null, true), rebuilt));

    assertSame(rebuilt, GearSkin.keep(weapon(true, "66", 2, true), rebuilt));
    assertSame(rebuilt, GearSkin.keep(weapon(true, "66", null, true), rebuilt));
    assertSame(rebuilt, GearSkin.keep(weapon(true, "66", null, false), rebuilt));
    assertSame(rebuilt, GearSkin.keep(weapon(false, "66", 66, true), rebuilt));
    assertNull(GearSkin.keep(weapon(true, "66", 66, true), null));
  }
}
