package net.tfminecraft.magic.api;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import io.lumine.mythic.lib.api.item.NBTItem;
import io.lumine.mythic.lib.skill.trigger.TriggerType;
import net.Indyuce.mmoitems.ItemStats;
import net.Indyuce.mmoitems.MMOItems;
import net.Indyuce.mmoitems.api.Type;
import net.Indyuce.mmoitems.api.item.mmoitem.LiveMMOItem;
import net.Indyuce.mmoitems.api.item.template.MMOItemTemplate;
import net.Indyuce.mmoitems.stat.data.AbilityListData;
import net.Indyuce.mmoitems.stat.data.BooleanData;
import net.Indyuce.mmoitems.stat.data.GemSocketsData;
import net.Indyuce.mmoitems.stat.data.GemstoneData;
import net.Indyuce.mmoitems.stat.data.StringData;
import net.Indyuce.mmoitems.stat.data.StringListData;
import net.Indyuce.mmoitems.stat.data.type.Mergeable;
import net.Indyuce.mmoitems.stat.data.type.StatData;
import net.Indyuce.mmoitems.stat.type.GemStoneStat;
import net.Indyuce.mmoitems.stat.type.ItemStat;
import net.tfminecraft.magic.Magic;
import net.tfminecraft.magic.gear.ArchetypeDef;
import net.tfminecraft.magic.gear.ArchetypeRegistry;
import net.tfminecraft.magic.gear.GearItemBuilder;
import net.tfminecraft.magic.gear.GearKeys;
import net.tfminecraft.magic.gear.GearModelResolver;
import net.tfminecraft.magic.gear.GearName;
import net.tfminecraft.magic.gear.GearProvenance;
import net.tfminecraft.magic.gear.GearStatApplicator;
import net.tfminecraft.magic.gear.GearType;
import net.tfminecraft.magic.gear.PartDef;
import net.tfminecraft.magic.gear.PartRegistry;
import net.tfminecraft.magic.gear.RuneKeybind;
import net.tfminecraft.magic.util.ItemRef;

/**
 * Mage weapons for TrialRooms' dungeons: built finished, with their runes in, and kept as built.
 *
 * <p>They carry their own mark and none of a crafted mage weapon's. So no attunement or resonance
 * gates their spells, casts don't wear them, they never refresh, the station and the Recycler don't
 * take them, and nobody owns them. Their class limit is their MMOItems template's, as on crafted ones.
 */
public final class DungeonWeapons {

    /** The material dungeon gear starts from; power is measured against it. */
    private static final String MATERIAL = "steel";
    private static final Set<String> SCHOOLS =
            Set.of("SEITHR_RUNES", "OSENI_RUNES", "CERRITH_RUNES", "MITLAN_RUNES");
    /** One each, in rune order: players can't rebind a rune that is already in a weapon. */
    private static final List<TriggerType> TRIGGERS = List.of(TriggerType.RIGHT_CLICK,
            TriggerType.SHIFT_RIGHT_CLICK, TriggerType.LEFT_CLICK, TriggerType.SHIFT_LEFT_CLICK);

    private DungeonWeapons() {}

    /** The archetypes a dungeon weapon can be built on. */
    public static List<String> archetypes() {
        return Arrays.stream(GearType.values()).filter(type -> core(type) != null)
                .map(type -> type.name().toLowerCase(Locale.ROOT)).toList();
    }

    /** Item paths of the school runes that fit a socket slot (minor_rune, lesser_rune, ...), sorted. */
    public static List<String> runes(String archetype, String slot) {
        ArchetypeDef def = ArchetypeRegistry.get(GearType.fromId(archetype));
        String colour = def == null ? "" : def.slotSuffix(slot);
        if (colour.isEmpty() || !GearItemBuilder.mmoItemsPresent()) {
            return List.of();
        }
        List<String> paths = new ArrayList<>();
        for (String school : SCHOOLS) {
            Type type = MMOItems.plugin.getTypes().get(school);
            if (type == null) {
                continue;
            }
            for (MMOItemTemplate rune : MMOItems.plugin.getTemplates().getTemplates(type)) {
                if (rune.getBaseItemData().get(ItemStats.GEM_COLOR) instanceof StringData fits
                        && colour.equals(fits.getString())) {
                    paths.add("m." + (school + "." + rune.getId()).toLowerCase(Locale.ROOT));
                }
            }
        }
        paths.sort(null);
        return paths;
    }

    /**
     * A finished, unbreakable weapon with exactly the sockets its runes fill, or null (with a
     * warning) when it can't be built.
     *
     * @param power      how strong it is next to the steel weapon. Its attack damage is multiplied by
     *                   it, and its skill damage gives spells, heals and shields the same increase.
     *                   Skill damage, not magic damage: wand shots and blade hits are magic damage
     *                   too, and would grow twice.
     * @param extraStats MMOItems stat ids to amounts, added on top as they are
     */
    public static ItemStack build(String archetype, List<String> runePaths, double power,
            Map<String, Double> extraStats, String name, List<String> lore) {
        GearType type = GearType.fromId(archetype);
        PartDef core = core(type);
        if (core == null || runePaths.size() > TRIGGERS.size() || !(power > 0) || !GearItemBuilder.mmoItemsPresent()) {
            return refuse(archetype, "unknown archetype, too many runes, bad power or no MMOItems");
        }
        ArchetypeDef def = ArchetypeRegistry.get(type);
        try {
            ItemStack base = ItemRef.build(def.getTemplate());
            if (base == null) {
                return refuse(archetype, "missing template " + def.getTemplate());
            }
            LiveMMOItem weapon = new LiveMMOItem(NBTItem.get(base));
            stats(weapon, core, power, extraStats);
            // The sockets go on before any rune: MMOItems drops data of gems that aren't socketed.
            GemSocketsData sockets = new GemSocketsData(new ArrayList<>());
            weapon.setData(ItemStats.GEM_SOCKETS, sockets);
            for (int i = 0; i < runePaths.size(); i++) {
                ItemStack rune = ItemRef.build(runePaths.get(i));
                NBTItem nbt = rune == null ? null : NBTItem.get(rune);
                String colour = nbt == null ? null : nbt.getString(ItemStats.GEM_COLOR.getNBTPath());
                if (colour == null || !SCHOOLS.contains(nbt.getType()) || !def.getSlots().containsValue(colour)) {
                    return refuse(archetype, "not a rune it can hold: " + runePaths.get(i));
                }
                socket(weapon, sockets, new LiveMMOItem(nbt), colour, TRIGGERS.get(i));
            }
            weapon.setData(ItemStats.UNBREAKABLE, new BooleanData(true));
            weapon.setData(ItemStats.LORE, new StringListData(new ArrayList<>(lore)));
            GearName.write(weapon, name);
            ItemStack built = GearModelResolver.apply(weapon.newBuilder().build(), type, List.of(core));
            ItemMeta meta = built.getItemMeta();
            meta.getPersistentDataContainer().set(GearKeys.dungeonWeapon(), PersistentDataType.BYTE, (byte) 1);
            built.setItemMeta(meta);
            return built;
        } catch (RuntimeException ex) {
            return refuse(archetype, ex.toString());
        }
    }

    public static boolean isDungeonWeapon(ItemStack stack) {
        return GearProvenance.isDungeonWeapon(stack);
    }

    /** The standard core, whose stats and look the weapon starts from; null when it has none. */
    private static PartDef core(GearType type) {
        return ArchetypeRegistry.get(type) == null ? null
                : PartRegistry.get(MATERIAL + "_" + type.name().toLowerCase(Locale.ROOT) + "_core");
    }

    private static void stats(LiveMMOItem weapon, PartDef core, double power, Map<String, Double> extraStats) {
        Map<String, Double> totals = GearStatApplicator.sum(List.of(core));
        totals.computeIfPresent("attack_damage", (stat, value) -> round(value * power));
        totals.put("skill_damage", round((power - 1) * 100));
        extraStats.forEach((stat, value) -> totals.merge(stat.toLowerCase(Locale.ROOT), value, Double::sum));
        Set<String> ids = GearStatApplicator.managedIds();
        ids.addAll(totals.keySet());
        ids.remove("max_item_damage");
        GearStatApplicator.write(weapon, ids, totals);
    }

    /** What MMOItems does when a player sockets a gem, with the rune's spells on their own trigger. */
    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void socket(LiveMMOItem weapon, GemSocketsData sockets, LiveMMOItem rune, String colour, TriggerType trigger) {
        GemstoneData gem = new GemstoneData(rune, colour);
        sockets.add(gem);
        for (ItemStat stat : rune.getStats()) {
            StatData data = rune.getData(stat);
            if (stat instanceof GemStoneStat || !(data instanceof Mergeable)) {
                continue;
            }
            if (data instanceof AbilityListData spells) {
                data = RuneKeybind.rebind(spells, trigger);
            }
            weapon.mergeData(stat, data, gem.getHistoricUUID());
        }
    }

    private static double round(double value) {
        return Math.round(value * 100) / 100.0;
    }

    private static ItemStack refuse(String archetype, String why) {
        Magic.plugin.getLogger().warning("[Magic] Can't build dungeon " + archetype + ": " + why);
        return null;
    }
}
