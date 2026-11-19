package net.tfminecraft.magic.gear;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import io.lumine.mythic.lib.MythicLib;
import io.lumine.mythic.lib.api.item.NBTItem;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.Indyuce.mmoitems.ItemStats;
import net.Indyuce.mmoitems.MMOItems;
import net.Indyuce.mmoitems.api.item.mmoitem.MMOItem;
import net.Indyuce.mmoitems.api.item.template.MMOItemTemplate;
import net.Indyuce.mmoitems.stat.data.BooleanData;
import net.Indyuce.mmoitems.stat.data.StringListData;
import net.Indyuce.mmoitems.stat.data.random.RandomBooleanData;
import net.Indyuce.mmoitems.stat.data.random.RandomStatData;
import net.Indyuce.mmoitems.stat.type.ItemStat;
import net.Indyuce.mmoitems.stat.type.StatHistory;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import org.mockbukkit.mockbukkit.MockBukkit;

class GearTemplateSyncTest {
  private MMOItems oldMmo;
  private MythicLib oldLib;
  private MockedStatic<NBTItem> nbtStatic;
  private NBTItem nbt;
  private final Map<ItemStat, RandomStatData> base = new HashMap<>();

  @BeforeEach
  void setUp() {
    MockBukkit.mock();
    oldMmo = MMOItems.plugin;
    oldLib = MythicLib.plugin;
    MMOItems.plugin = mock(MMOItems.class, RETURNS_DEEP_STUBS);
    when(MMOItems.plugin.namespace()).thenReturn("mmoitems");
    MythicLib.plugin = mock(MythicLib.class, RETURNS_DEEP_STUBS);
    when(MythicLib.plugin.namespace()).thenReturn("mythiclib");
    MMOItemTemplate template = mock(MMOItemTemplate.class);
    when(template.getBaseItemData()).thenReturn(base);
    nbt = mock(NBTItem.class);
    when(nbt.hasType()).thenReturn(true);
    when(MMOItems.plugin.getTemplates().getTemplate(nbt)).thenReturn(template);
    nbtStatic = mockStatic(NBTItem.class);
    nbtStatic.when(() -> NBTItem.get(any(ItemStack.class))).thenReturn(nbt);
  }

  @AfterEach
  void tearDown() {
    nbtStatic.close();
    MMOItems.plugin = oldMmo;
    MythicLib.plugin = oldLib;
    MockBukkit.unmock();
  }

  private static ItemStack stick() {
    return new ItemStack(Material.STICK);
  }

  private void template(List<String> classes, RandomStatData twoHanded) {
    base.clear();
    if (classes != null) {
      base.put(ItemStats.REQUIRED_CLASS, new StringListData(classes));
    }
    if (twoHanded != null) {
      base.put(ItemStats.TWO_HANDED, twoHanded);
    }
  }

  private void item(String classes, boolean twoHanded) {
    when(nbt.getString(GearTemplateSync.CLASS_TAG)).thenReturn(classes);
    when(nbt.getBoolean(GearTemplateSync.TWO_HANDED_TAG)).thenReturn(twoHanded);
  }

  @Test
  void matchingWeaponsAreNotOutdated() {
    template(List.of("Arcanist"), new RandomBooleanData(true));
    item(" arcanist ,", true);
    assertFalse(GearTemplateSync.isOutdated(stick()));
    template(null, null);
    item(null, false);
    assertFalse(GearTemplateSync.isOutdated(stick()));
  }

  @Test
  void classOrTwoHandedChangesMakeAWeaponOutdated() {
    template(List.of("Arcanist", "Evoker"), new RandomBooleanData(true));
    item("Mage", true);
    assertTrue(GearTemplateSync.isOutdated(stick()));
    item("Evoker, Arcanist", false);
    assertTrue(GearTemplateSync.isOutdated(stick()));
    item("Evoker, Arcanist", true);
    assertFalse(GearTemplateSync.isOutdated(stick()));
  }

  @Test
  void weaponsWithoutAReadableTemplateAreLeftAlone() {
    assertFalse(GearTemplateSync.isOutdated(null));
    assertFalse(GearTemplateSync.isOutdated(new ItemStack(Material.AIR)));
    when(nbt.hasType()).thenReturn(false);
    assertFalse(GearTemplateSync.isOutdated(stick()));
    when(nbt.hasType()).thenReturn(true);
    when(MMOItems.plugin.getTemplates().getTemplate(nbt)).thenReturn(null);
    assertFalse(GearTemplateSync.isOutdated(stick()));
    nbtStatic.when(() -> NBTItem.get(any(ItemStack.class))).thenReturn(null);
    assertFalse(GearTemplateSync.isOutdated(stick()));
    nbtStatic.when(() -> NBTItem.get(any(ItemStack.class))).thenThrow(new IllegalStateException());
    assertFalse(GearTemplateSync.isOutdated(stick()));
    MMOItems.plugin = null;
    nbtStatic.when(() -> NBTItem.get(any(ItemStack.class))).thenReturn(nbt);
    assertFalse(GearTemplateSync.isOutdated(stick()));
  }

  @Test
  void applyCopiesClassesAndTwoHandedIntoTheOriginalLayer() {
    template(List.of("Battlemage"), new RandomBooleanData(true));
    MMOItem mmo = mock(MMOItem.class);
    StatHistory history = mock(StatHistory.class);
    when(mmo.getStatHistory(ItemStats.REQUIRED_CLASS)).thenReturn(history);
    GearTemplateSync.apply(mmo, nbt);
    var classes = ArgumentCaptor.forClass(StringListData.class);
    verify(mmo).setData(eq(ItemStats.REQUIRED_CLASS), classes.capture());
    assertEquals(List.of("Battlemage"), classes.getValue().getList());
    verify(history).setOriginalData(any(StringListData.class));
    var flag = ArgumentCaptor.forClass(BooleanData.class);
    verify(mmo).setData(eq(ItemStats.TWO_HANDED), flag.capture());
    assertTrue(flag.getValue().isEnabled());

    MMOItem plain = mock(MMOItem.class);
    GearTemplateSync.apply(plain, nbt);
    verify(plain).setData(eq(ItemStats.REQUIRED_CLASS), any());
  }

  @Test
  void applyRemovesWhatTheTemplateNoLongerHas() {
    template(List.of(), null);
    MMOItem mmo = mock(MMOItem.class);
    GearTemplateSync.apply(mmo, nbt);
    verify(mmo).removeData(ItemStats.REQUIRED_CLASS);
    verify(mmo).removeData(ItemStats.TWO_HANDED);
    verify(mmo, never()).setData(any(), any());
  }

  @Test
  void applySkipsWeaponsWithoutATemplate() {
    MMOItem mmo = mock(MMOItem.class);
    GearTemplateSync.apply(mmo, null);
    when(nbt.hasType()).thenReturn(false);
    GearTemplateSync.apply(mmo, nbt);
    verifyNoInteractions(mmo);
  }

  @Test
  void twoHandedReadsEveryTemplateShape() {
    MMOItemTemplate template = mock(MMOItemTemplate.class);
    when(template.getBaseItemData()).thenReturn(base);
    template(null, new RandomBooleanData(1.0));
    assertTrue(GearTemplateSync.twoHanded(template));
    template(null, new RandomBooleanData(0.5));
    assertFalse(GearTemplateSync.twoHanded(template));
    base.put(ItemStats.TWO_HANDED, new BooleanRandom());
    assertTrue(GearTemplateSync.twoHanded(template));
    base.put(ItemStats.TWO_HANDED, new BooleanRandom(false));
    assertFalse(GearTemplateSync.twoHanded(template));
    base.put(ItemStats.TWO_HANDED, mock(RandomStatData.class));
    assertFalse(GearTemplateSync.twoHanded(template));
    base.put(ItemStats.REQUIRED_CLASS, mock(RandomStatData.class));
    item("Mage", false);
    assertTrue(GearTemplateSync.isOutdated(stick()));
  }

  @Test
  void lowerCaseDropsBlanks() {
    assertEquals(Set.of("mage", "evoker"), GearTemplateSync.lowerCase(List.of(" Mage", "", "EVOKER ", "  ")));
  }

  /** A stored boolean that is also template data, as an older MMOItems template could hold. */
  static class BooleanRandom extends BooleanData implements RandomStatData<BooleanData> {
    BooleanRandom() {
      super(true);
    }

    BooleanRandom(boolean state) {
      super(state);
    }

    @Override
    public BooleanData randomize(net.Indyuce.mmoitems.api.item.build.MMOItemBuilder builder) {
      return this;
    }
  }
}
