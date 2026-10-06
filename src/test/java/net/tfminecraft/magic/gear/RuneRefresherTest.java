package net.tfminecraft.magic.gear;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import io.lumine.mythic.lib.MythicLib;
import io.lumine.mythic.lib.api.item.NBTItem;
import io.lumine.mythic.lib.skill.trigger.TriggerType;
import java.util.*;
import java.util.logging.Logger;
import net.Indyuce.mmoitems.MMOItems;
import net.Indyuce.mmoitems.api.Type;
import net.Indyuce.mmoitems.api.item.mmoitem.MMOItem;
import net.Indyuce.mmoitems.api.item.template.MMOItemTemplate;
import net.Indyuce.mmoitems.skill.RegisteredSkill;
import net.Indyuce.mmoitems.stat.data.AbilityData;
import net.Indyuce.mmoitems.stat.data.AbilityListData;
import net.Indyuce.mmoitems.stat.data.DoubleData;
import net.Indyuce.mmoitems.stat.data.type.StatData;
import net.Indyuce.mmoitems.stat.type.GemStoneStat;
import net.Indyuce.mmoitems.stat.type.ItemStat;
import net.Indyuce.mmoitems.stat.type.StatHistory;
import net.tfminecraft.magic.Magic;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.junit.jupiter.api.*;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockito.MockedStatic;

class RuneRefresherTest {
  static final UUID A = UUID.fromString("00000000-0000-0000-0000-00000000000a");
  static final UUID B = UUID.fromString("00000000-0000-0000-0000-00000000000b");
  static final UUID C = UUID.fromString("00000000-0000-0000-0000-00000000000c");
  MMOItems oldMmo;
  MythicLib oldLib;
  Logger logger;

  @BeforeEach
  void setup() {
    MockBukkit.mock();
    Magic.plugin = mock(Magic.class, RETURNS_DEEP_STUBS);
    when(Magic.plugin.getName()).thenReturn("Magic");
    when(Magic.plugin.namespace()).thenReturn("magic");
    logger = mock(Logger.class);
    when(Magic.plugin.getLogger()).thenReturn(logger);
    oldMmo = MMOItems.plugin;
    oldLib = MythicLib.plugin;
    MMOItems.plugin = mock(MMOItems.class, RETURNS_DEEP_STUBS);
    when(MMOItems.plugin.namespace()).thenReturn("mmoitems");
    when(MMOItems.plugin.getTypes().get(anyString())).thenReturn(null);
    MythicLib.plugin = mock(MythicLib.class, RETURNS_DEEP_STUBS);
    when(MythicLib.plugin.namespace()).thenReturn("mythiclib");
  }

  @AfterEach
  void cleanup() {
    TYPES.clear();
    MMOItems.plugin = oldMmo;
    MythicLib.plugin = oldLib;
    Magic.plugin = null;
    MockBukkit.unmock();
  }

  static ItemStack weapon() {
    var item = new ItemStack(Material.STICK);
    var meta = item.getItemMeta();
    meta.setDisplayName("Gear");
    item.setItemMeta(meta);
    return item;
  }

  static void stampRaw(ItemStack item, String raw) {
    var meta = item.getItemMeta();
    meta.getPersistentDataContainer().set(GearKeys.runeRevisions(), PersistentDataType.STRING, raw);
    item.setItemMeta(meta);
  }

  static String gem(UUID uuid, String type, String id) {
    return "{\"Name\":\"Rune\",\"History\":\"" + uuid + "\",\"Type\":\"" + type + "\",\"Id\":\"" + id
        + "\",\"Color\":\"Minor Rune\"}";
  }

  static String sockets(String... gems) {
    return "{\"EmptySlots\":[],\"Gemstones\":[" + String.join(",", gems) + "]}";
  }

  static MockedStatic<NBTItem> nbt(String raw) {
    MockedStatic<NBTItem> nbt = mockStatic(NBTItem.class);
    NBTItem item = mock(NBTItem.class);
    when(item.getString(anyString())).thenReturn(raw);
    nbt.when(() -> NBTItem.get(any(ItemStack.class))).thenReturn(item);
    return nbt;
  }

  static final Map<String, Type> TYPES = new HashMap<>();

  static Type template(String typeId, String id, Integer revision) {
    Type type = TYPES.computeIfAbsent(typeId, k -> mock(Type.class));
    when(MMOItems.plugin.getTypes().get(typeId)).thenReturn(type);
    MMOItemTemplate template = null;
    if (revision != null) {
      template = mock(MMOItemTemplate.class);
      when(template.getRevisionId()).thenReturn(revision);
    }
    when(MMOItems.plugin.getTemplates().getTemplate(type, id)).thenReturn(template);
    return type;
  }

  @Test
  void stampsRoundTripAndSkipDamagedEntries() {
    assertTrue(RuneRefresher.stamps(null).isEmpty());
    ItemStack bare = mock(ItemStack.class);
    when(bare.hasItemMeta()).thenReturn(false);
    assertTrue(RuneRefresher.stamps(bare).isEmpty());
    var item = weapon();
    assertTrue(RuneRefresher.stamps(item).isEmpty());
    stampRaw(item, " ");
    assertTrue(RuneRefresher.stamps(item).isEmpty());
    RuneRefresher.stamp(item, new LinkedHashMap<>(Map.of(A, 3)));
    assertEquals(Map.of(A, 3), RuneRefresher.stamps(item));
    stampRaw(item, A + "@3,broken,not-a-uuid@2," + B + "@x");
    assertEquals(Map.of(A, 3), RuneRefresher.stamps(item));
    RuneRefresher.stamp(item, Map.of());
    assertFalse(item.getItemMeta().getPersistentDataContainer().has(GearKeys.runeRevisions()));
    RuneRefresher.stamp(null, Map.of(A, 1));
    RuneRefresher.stamp(bare, Map.of(A, 1));
    verify(bare, never()).getItemMeta();
  }

  @Test
  void socketedReadsGemsAndIgnoresBrokenData() {
    assertTrue(RuneRefresher.socketed(null).isEmpty());
    assertTrue(RuneRefresher.socketed(new ItemStack(Material.AIR)).isEmpty());
    try (var nbt = mockStatic(NBTItem.class)) {
      nbt.when(() -> NBTItem.get(any(ItemStack.class))).thenReturn(null);
      assertTrue(RuneRefresher.socketed(weapon()).isEmpty());
    }
    for (String raw : Arrays.asList(null, " ", "{}", "not json", "[1]", sockets(gem(null, "T", "I")))) {
      try (var nbt = nbt(raw)) {
        assertTrue(RuneRefresher.socketed(weapon()).isEmpty(), String.valueOf(raw));
      }
    }
    String raw =
        sockets(
            gem(A, "CERRITH_RUNES", "RUNE_OF_HEALING_ORB"),
            "{\"History\":\"" + B + "\",\"Type\":\"T\"}",
            "{\"History\":\"" + B + "\",\"Id\":\"I\"}",
            "{\"Type\":\"T\",\"Id\":\"I\"}");
    try (var nbt = nbt(raw)) {
      var gems = RuneRefresher.socketed(weapon());
      assertEquals(List.of(new RuneRefresher.Socketed(A, "CERRITH_RUNES", "RUNE_OF_HEALING_ORB")), gems);
    }
  }

  @Test
  void outdatedComparesTheLiveTemplateRevisionWithTheStamp() {
    template("KNOWN", "RUNE", 3);
    template("NOTEMPLATE", "RUNE", null);
    var item = weapon();
    try (var nbt = nbt(sockets(gem(B, "UNKNOWN", "RUNE"), gem(C, "NOTEMPLATE", "RUNE")))) {
      assertFalse(RuneRefresher.isOutdated(item));
    }
    try (var nbt = nbt(sockets(gem(A, "KNOWN", "RUNE")))) {
      assertTrue(RuneRefresher.isOutdated(item));
      RuneRefresher.stamp(item, Map.of(A, 2));
      assertTrue(RuneRefresher.isOutdated(item));
      RuneRefresher.stamp(item, Map.of(A, 3));
      assertFalse(RuneRefresher.isOutdated(item));
    }
  }

  @Test
  void refreshReplacesOnlyOutdatedRunesAndReportsTheirRevisions() {
    template("KNOWN", "CURRENT", 3);
    template("KNOWN", "STALE", 5);
    template("KNOWN", "BUILDS", 7);
    UUID d = UUID.fromString("00000000-0000-0000-0000-00000000000d");
    var item = weapon();
    RuneRefresher.stamp(item, Map.of(A, 3, B, 4));
    when(MMOItems.plugin.getMMOItem(any(), anyString())).thenReturn(null);
    MMOItem fresh = mock(MMOItem.class);
    when(MMOItems.plugin.getMMOItem(any(), eq("BUILDS"))).thenReturn(fresh);
    MMOItem mmo = mock(MMOItem.class);
    try (var nbt =
        nbt(
            sockets(
                gem(A, "KNOWN", "CURRENT"),
                gem(B, "KNOWN", "STALE"),
                gem(C, "GONE", "X"),
                gem(d, "KNOWN", "BUILDS")))) {
      // B could not be rebuilt, so it gets no stamp and is tried again next time.
      assertEquals(Map.of(A, 3, d, 7), RuneRefresher.refresh(mmo, item));
    }
    verify(MMOItems.plugin, times(1)).getMMOItem(any(), eq("STALE"));
    verify(MMOItems.plugin, never()).getMMOItem(any(), eq("CURRENT"));
    verify(logger).warning(contains("KNOWN.STALE"));
    verify(mmo, never()).mergeData(any(), any(), any());
  }

  @Test
  @SuppressWarnings({"rawtypes", "unchecked"})
  void replaceMovesGemDataToTheTemplateAndKeepsTriggers() {
    Type type = template("KNOWN", "STALE", 5);
    MMOItem mmo = mock(MMOItem.class);
    when(mmo.getUpgradeLevel()).thenReturn(2);
    AbilityData oldAbility = mock(AbilityData.class);
    when(oldAbility.getTrigger()).thenReturn(TriggerType.LEFT_CLICK);
    ItemStat abilityStat = mock(ItemStat.class);
    ItemStat manaStat = mock(ItemStat.class);
    StatHistory abilityHist = mock(StatHistory.class);
    StatHistory manaHist = mock(StatHistory.class);
    StatHistory otherHist = mock(StatHistory.class);
    when(abilityHist.getGemstoneData(B)).thenReturn(new AbilityListData(List.of(oldAbility)));
    when(manaHist.getGemstoneData(B)).thenReturn(new DoubleData(1));
    when(abilityHist.getItemStat()).thenReturn(abilityStat);
    when(manaHist.getItemStat()).thenReturn(manaStat);
    StatData recalculated = mock(StatData.class);
    when(abilityHist.recalculate(2)).thenReturn(recalculated);
    when(manaHist.recalculate(2)).thenReturn(recalculated);
    when(mmo.getStatHistories()).thenReturn(new ArrayList<>(List.of(abilityHist, manaHist, otherHist)));

    MMOItem fresh = mock(MMOItem.class);
    ItemStat gemOnly = mock(ItemStat.class, withSettings().extraInterfaces(GemStoneStat.class));
    ItemStat plain = mock(ItemStat.class);
    DoubleData mana = new DoubleData(2);
    AbilityData freshAbility = mock(AbilityData.class);
    when(freshAbility.getAbility()).thenReturn(mock(RegisteredSkill.class));
    when(freshAbility.getModifiers()).thenReturn(Set.of("heal"));
    when(freshAbility.getParameter("heal")).thenReturn(13.0);
    when(fresh.getStats())
        .thenReturn(new LinkedHashSet<>(List.of(gemOnly, plain, manaStat, abilityStat)));
    when(fresh.getData(gemOnly)).thenReturn(new DoubleData(9));
    when(fresh.getData(plain)).thenReturn(mock(StatData.class));
    when(fresh.getData(manaStat)).thenReturn(mana);
    when(fresh.getData(abilityStat)).thenReturn(new AbilityListData(List.of(freshAbility)));
    when(MMOItems.plugin.getMMOItem(type, "STALE")).thenReturn(fresh);

    try (var abilities = mockConstruction(AbilityData.class)) {
      assertTrue(RuneRefresher.replace(mmo, new RuneRefresher.Socketed(B, "KNOWN", "STALE")));
      verify(abilityHist).removeGemData(B);
      verify(manaHist).removeGemData(B);
      verify(otherHist, never()).removeGemData(any());
      verify(mmo).setData(abilityStat, recalculated);
      verify(mmo).setData(manaStat, recalculated);
      verify(mmo).mergeData(manaStat, mana, B);
      var merged = org.mockito.ArgumentCaptor.forClass(StatData.class);
      verify(mmo).mergeData(eq(abilityStat), merged.capture(), eq(B));
      var copy = abilities.constructed().getFirst();
      assertEquals(List.of(copy), ((AbilityListData) merged.getValue()).getAbilities());
      verify(copy).setModifier("heal", 13.0);
      verify(mmo, never()).mergeData(eq(gemOnly), any(), any());
      verify(mmo, never()).mergeData(eq(plain), any(), any());
    }
  }

  @Test
  void keepTriggersCopiesEachTriggerByPositionAndKeepsExtraAbilities() {
    AbilityData first = mock(AbilityData.class);
    AbilityData second = mock(AbilityData.class);
    RegisteredSkill skill = mock(RegisteredSkill.class);
    when(first.getAbility()).thenReturn(skill);
    when(first.getModifiers()).thenReturn(Set.of());
    AbilityListData fresh = new AbilityListData(List.of(first, second));
    assertSame(fresh, RuneRefresher.keepTriggers(fresh, null));
    AbilityData old = mock(AbilityData.class);
    when(old.getTrigger()).thenReturn(TriggerType.LEFT_CLICK);
    List<List<?>> arguments = new ArrayList<>();
    try (var abilities =
        mockConstruction(AbilityData.class, (m, context) -> arguments.add(context.arguments()))) {
      var kept = RuneRefresher.keepTriggers(fresh, new AbilityListData(List.of(old)));
      assertEquals(List.of(abilities.constructed().getFirst(), second), kept.getAbilities());
      assertEquals(List.of(skill, TriggerType.LEFT_CLICK), arguments.getFirst());
    }
  }
}
