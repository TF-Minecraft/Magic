package net.tfminecraft.magic;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import io.lumine.mythic.lib.api.item.NBTItem;
import io.lumine.mythic.lib.skill.trigger.TriggerType;
import java.util.*;
import net.Indyuce.mmoitems.ItemStats;
import net.Indyuce.mmoitems.MMOItems;
import net.Indyuce.mmoitems.api.Type;
import net.Indyuce.mmoitems.api.event.MMOItemReforgeEvent;
import net.Indyuce.mmoitems.api.event.item.UnsocketGemStoneEvent;
import net.Indyuce.mmoitems.api.item.build.ItemStackBuilder;
import net.Indyuce.mmoitems.api.item.mmoitem.LiveMMOItem;
import net.Indyuce.mmoitems.api.item.mmoitem.MMOItem;
import net.Indyuce.mmoitems.api.item.mmoitem.ReadMMOItem;
import net.Indyuce.mmoitems.api.item.template.MMOItemTemplate;
import net.Indyuce.mmoitems.api.util.MMOItemReforger;
import net.Indyuce.mmoitems.skill.RegisteredSkill;
import net.Indyuce.mmoitems.stat.data.*;
import net.Indyuce.mmoitems.stat.data.random.RandomStatData;
import net.Indyuce.mmoitems.stat.type.ItemStat;
import net.tfminecraft.magic.api.DungeonWeapons;
import net.tfminecraft.magic.gear.*;
import net.tfminecraft.magic.listener.*;
import net.tfminecraft.magic.util.ItemRef;
import org.bukkit.Material;
import org.bukkit.event.player.PlayerItemDamageEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.junit.jupiter.api.*;
import org.mockbukkit.mockbukkit.MockBukkit;

class DungeonWeaponsTest extends GearMmoCoverageSupport {
  static final Map<String, String> SLOTS = Map.of("minor_rune", "Minor Rune", "lesser_rune", "Lesser Rune");
  final Map<NBTItem, LiveMMOItem> runes = new HashMap<>();
  final Map<ItemStat, Double> written = new HashMap<>();
  final List<TriggerType> triggers = new ArrayList<>();
  LiveMMOItem weapon;
  ItemStack built;

  @BeforeEach
  void registerWand() {
    archetype(GearType.WAND);
    archetype(GearType.SWORD);
    PartRegistry.register(core("steel_wand_core", GearType.WAND));
  }

  static void archetype(GearType type) {
    ArchetypeRegistry.register(new ArchetypeDef(type, null, "m.mage." + type, "v.STICK", false, List.of(), SLOTS));
  }

  static PartDef core(String id, GearType type) {
    return new PartDef(id, id, "core", 2, Set.of(type), "v.STICK", Map.of(), List.of(),
        Map.of("attack_damage", 5d, "attack_speed", 1.2, "max_item_damage", 1000d), Map.of(), List.of(), null, 1, false);
  }

  static void enableMmo() {
    MockBukkit.createMockPlugin("MMOItems");
    MockBukkit.createMockPlugin("MythicLib");
  }

  static ItemStack dungeonWeapon() {
    var stack = item();
    tag(stack, GearKeys.dungeonWeapon(), PersistentDataType.BYTE, (byte) 1);
    return stack;
  }

  static MMOItemTemplate template(String id, RandomStatData colour) {
    var template = mock(MMOItemTemplate.class);
    when(template.getId()).thenReturn(id);
    Map<ItemStat, RandomStatData> base = new HashMap<>();
    if (colour != null) {
      base.put(ItemStats.GEM_COLOR, colour);
    }
    when(template.getBaseItemData()).thenReturn(base);
    return template;
  }

  @Test
  void archetypesNeedTheStandardCore() {
    assertEquals(List.of("wand"), DungeonWeapons.archetypes());
    PartRegistry.register(core("steel_sword_core", GearType.SWORD));
    assertEquals(List.of("wand", "sword"), DungeonWeapons.archetypes());
  }

  @Test
  void runesAreTheSchoolRunesOfASlotsColour() {
    assertEquals(List.of(), DungeonWeapons.runes("wand", "minor_rune"));
    enableMmo();
    assertEquals(List.of(), DungeonWeapons.runes("staff", "minor_rune"));
    assertEquals(List.of(), DungeonWeapons.runes("wand", "core"));
    when(MMOItems.plugin.getTypes().get(anyString())).thenReturn(null);
    var oseni = mock(Type.class);
    var seithr = mock(Type.class);
    when(MMOItems.plugin.getTypes().get("OSENI_RUNES")).thenReturn(oseni);
    when(MMOItems.plugin.getTypes().get("SEITHR_RUNES")).thenReturn(seithr);
    var seithrRunes = List.of(template("RUNE_OF_ICE_SHARD", new StringData("Minor Rune")));
    var oseniRunes = List.of(
        template("RUNE_OF_METEOR", new StringData("Minor Rune")),
        template("RUNE_OF_FIRE_BREATH", new StringData("Lesser Rune")),
        template("RUNE_OF_FIRE_SHARD", new StringData("Minor Rune")),
        template("NO_COLOUR", null));
    when(MMOItems.plugin.getTemplates().getTemplates(seithr)).thenReturn(seithrRunes);
    when(MMOItems.plugin.getTemplates().getTemplates(oseni)).thenReturn(oseniRunes);
    assertEquals(
        List.of("m.oseni_runes.rune_of_fire_shard", "m.oseni_runes.rune_of_meteor", "m.seithr_runes.rune_of_ice_shard"),
        DungeonWeapons.runes("wand", "minor_rune"));
    assertEquals(List.of("m.oseni_runes.rune_of_fire_breath"), DungeonWeapons.runes("wand", "lesser_rune"));
  }

  /** Runs a build against mocked item plugins; each rune path is described by its type and colour. */
  ItemStack build(String archetype, Map<String, String[]> items, List<String> paths, double power,
      Map<String, Double> extra) {
    var stats = Map.of("ATTACK_DAMAGE", ItemStats.ATTACK_DAMAGE, "ATTACK_SPEED", ItemStats.ATTACK_SPEED,
        "SKILL_DAMAGE", ItemStats.SKILL_DAMAGE, "MAX_MANA", ItemStats.MAX_MANA);
    when(MMOItems.plugin.getStats().get(anyString())).thenAnswer(call -> stats.get(call.<String>getArgument(0)));
    built = item();
    var weaponNbt = mock(NBTItem.class);
    var base = item();
    try (var refs = mockStatic(ItemRef.class);
        var nbts = mockStatic(NBTItem.class);
        var abilities = mockConstruction(AbilityData.class, (m, c) -> triggers.add((TriggerType) c.arguments().get(1)));
        var gems = mockConstruction(GemstoneData.class, (m, c) -> {
          when(m.getHistoricUUID()).thenReturn(UUID.randomUUID());
          when(m.getSocketColor()).thenReturn((String) c.arguments().get(1));
        });
        var live = mockConstruction(LiveMMOItem.class, (m, c) -> {
          if (c.arguments().get(0) == weaponNbt) {
            weapon = m;
            doAnswer(call -> {
              if (call.getArgument(1) instanceof DoubleData number) {
                written.put(call.getArgument(0), number.getValue());
              }
              return null;
            }).when(m).setData(any(), any());
            var builder = mock(ItemStackBuilder.class);
            when(m.newBuilder()).thenReturn(builder);
            when(builder.build()).thenAnswer(call -> built);
            return;
          }
          var spell = mock(AbilityData.class);
          when(spell.getAbility()).thenReturn(mock(RegisteredSkill.class));
          when(spell.getModifiers()).thenReturn(Set.of());
          when(m.getStats()).thenReturn(new LinkedHashSet<>(
              List.of(ItemStats.ABILITIES, ItemStats.LORE, ItemStats.GEM_COLOR, ItemStats.UNBREAKABLE, ItemStats.MAX_MANA)));
          when(m.getData(ItemStats.ABILITIES)).thenReturn(new AbilityListData(spell));
          when(m.getData(ItemStats.LORE)).thenReturn(new StringListData(List.of("Type: Oseni")));
          when(m.getData(ItemStats.MAX_MANA)).thenReturn(new DoubleData(3));
        })) {
      refs.when(() -> ItemRef.build("m.mage.WAND")).thenReturn(base);
      nbts.when(() -> NBTItem.get(base)).thenReturn(weaponNbt);
      items.forEach((path, described) -> {
        var stack = new ItemStack(Material.ECHO_SHARD, 1 + items.size());
        var meta = stack.getItemMeta();
        meta.setDisplayName(path);
        stack.setItemMeta(meta);
        var nbt = mock(NBTItem.class);
        when(nbt.getType()).thenReturn(described[0]);
        when(nbt.getString(anyString())).thenReturn(described[1]);
        refs.when(() -> ItemRef.build(path)).thenReturn(stack);
        nbts.when(() -> NBTItem.get(stack)).thenReturn(nbt);
      });
      return DungeonWeapons.build(archetype, paths, power, extra, "Ember Wand", List.of("Dungeon gear"));
    }
  }

  ItemStack build(List<String> paths, double power) {
    return build("wand", Map.of(
        "minor", new String[] {"OSENI_RUNES", "Minor Rune"},
        "lesser", new String[] {"SEITHR_RUNES", "Lesser Rune"},
        "greater", new String[] {"SEITHR_RUNES", "Greater Rune"},
        "blood", new String[] {"BLOOD_RUNES", "Minor Rune"},
        "colourless", new String[] {"OSENI_RUNES", null}), paths, power, Map.of());
  }

  @Test
  void buildsAFinishedWeaponWithItsRunesIn() {
    enableMmo();
    var result = build("wand", Map.of("minor", new String[] {"OSENI_RUNES", "Minor Rune"},
        "lesser", new String[] {"SEITHR_RUNES", "Lesser Rune"}), List.of("minor", "lesser"), 1.15 * 1.55,
        Map.of("MAX_MANA", 4d, "skill_damage", 5d));
    assertSame(built, result);
    assertTrue(DungeonWeapons.isDungeonWeapon(result));
    assertFalse(GearProvenance.isGear(result));
    assertNull(GearProvenance.readInputs(result));
    assertFalse(WeaponRequirement.fromItem(result).hasStored());
    assertEquals(Map.of(ItemStats.ATTACK_DAMAGE, 8.91, ItemStats.ATTACK_SPEED, 1.2,
        ItemStats.SKILL_DAMAGE, 83.25, ItemStats.MAX_MANA, 4d), written);
    verify(MMOItems.plugin.getStats(), never()).get("MAX_ITEM_DAMAGE");
    var sockets = org.mockito.ArgumentCaptor.forClass(GemSocketsData.class);
    verify(weapon).setData(eq(ItemStats.GEM_SOCKETS), sockets.capture());
    assertEquals(List.of(), sockets.getValue().getEmptySlots());
    assertEquals(List.of("Minor Rune", "Lesser Rune"),
        sockets.getValue().getGems().stream().map(GemstoneData::getSocketColor).toList());
    assertEquals(List.of(TriggerType.RIGHT_CLICK, TriggerType.SHIFT_RIGHT_CLICK), triggers);
    for (GemstoneData gem : sockets.getValue().getGems()) {
      UUID uuid = gem.getHistoricUUID();
      verify(weapon).mergeData(eq(ItemStats.ABILITIES), any(AbilityListData.class), eq(uuid));
      verify(weapon).mergeData(eq(ItemStats.MAX_MANA), any(DoubleData.class), eq(uuid));
    }
    verify(weapon, times(4)).mergeData(any(), any(), any());
    verify(weapon).setData(eq(ItemStats.UNBREAKABLE), argThat(data -> ((BooleanData) data).isEnabled()));
    verify(weapon).setData(eq(ItemStats.LORE),
        argThat(data -> ((StringListData) data).getList().equals(List.of("Dungeon gear"))));
  }

  @Test
  void refusesWhatItCantBuild() {
    assertNull(build(List.of(), 1));
    enableMmo();
    assertNotNull(build(List.of(), 1));
    assertEquals(0d, written.get(ItemStats.SKILL_DAMAGE));
    assertEquals(5d, written.get(ItemStats.ATTACK_DAMAGE));
    assertNull(build("nothing", Map.of(), List.of(), 1, Map.of()));
    assertNull(build("sword", Map.of(), List.of(), 1, Map.of()));
    assertNull(build(Collections.nCopies(5, "minor"), 1));
    assertNull(build(List.of(), 0));
    assertNull(build(List.of(), Double.NaN));
    for (String path : List.of("missing", "greater", "blood", "colourless")) {
      assertNull(build(List.of("minor", path), 1), path);
    }
    assertNull(build("wand", Map.of(), List.of(), 1, Collections.singletonMap(null, 1d)));
    archetype(GearType.STAFF);
    PartRegistry.register(core("steel_staff_core", GearType.STAFF));
    assertNull(build("staff", Map.of(), List.of(), 1, Map.of()));
  }

  @Test
  void aDungeonWeaponIsNeverTreatedAsCraftedGear() {
    enableMmo();
    var staff = dungeonWeapon();
    assertFalse(DungeonWeapons.isDungeonWeapon(null));
    assertFalse(DungeonWeapons.isDungeonWeapon(new ItemStack(Material.STICK)));
    assertFalse(DungeonWeapons.isDungeonWeapon(item()));
    assertTrue(DungeonWeapons.isDungeonWeapon(staff));
    assertNull(GearRefresher.refresh(staff, null, true));
    assertFalse(GearProvenance.stationCanCreate(staff));
    var lore = staff.clone();
    assertEquals(staff, WeaponLore.updateItem(lore));

    var player = server.addPlayer();
    var crafted = item();
    GearProvenance.stamp(crafted, GearType.STAFF, null);
    player.getInventory().setItemInOffHand(crafted);
    assertEquals(GearHand.HeldSlot.OFF_HAND, GearHand.heldSlot(player));
    player.getInventory().setItemInMainHand(staff);
    assertNull(GearHand.heldSlot(player));
    assertEquals(1, GearHand.staffCount(player));

    var wear = new PlayerItemDamageEvent(player, staff, 1);
    new WeaponWearListener().onItemDamage(wear);
    assertTrue(wear.isCancelled());
  }

  @Test
  void itsRunesStayInAndItIsNeverReforged() {
    var listener = new DungeonWeaponListener();
    var unsocket = mock(UnsocketGemStoneEvent.class);
    when(unsocket.getTargetItem()).thenReturn(mock(MMOItem.class));
    listener.onUnsocket(unsocket);
    var target = mock(ReadMMOItem.class, RETURNS_DEEP_STUBS);
    when(unsocket.getTargetItem()).thenReturn(target);
    when(target.getNBT().getItem()).thenReturn(item());
    listener.onUnsocket(unsocket);
    verify(unsocket, never()).setCancelled(anyBoolean());
    var weapon = dungeonWeapon();
    when(target.getNBT().getItem()).thenReturn(weapon);
    listener.onUnsocket(unsocket);
    verify(unsocket).setCancelled(true);

    var reforge = mock(MMOItemReforgeEvent.class);
    var reforger = mock(MMOItemReforger.class);
    when(reforge.getReforger()).thenReturn(reforger);
    when(reforger.getStack()).thenReturn(item());
    listener.onReforge(reforge);
    verify(reforge, never()).setCancelled(anyBoolean());
    when(reforger.getStack()).thenReturn(weapon);
    listener.onReforge(reforge);
    verify(reforge).setCancelled(true);
  }
}
