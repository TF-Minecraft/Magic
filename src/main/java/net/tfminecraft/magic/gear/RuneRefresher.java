package net.tfminecraft.magic.gear;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.StringJoiner;
import java.util.UUID;

import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import io.lumine.mythic.lib.api.item.NBTItem;
import net.Indyuce.mmoitems.ItemStats;
import net.Indyuce.mmoitems.MMOItems;
import net.Indyuce.mmoitems.api.Type;
import net.Indyuce.mmoitems.api.item.mmoitem.MMOItem;
import net.Indyuce.mmoitems.api.item.template.MMOItemTemplate;
import net.Indyuce.mmoitems.stat.data.AbilityData;
import net.Indyuce.mmoitems.stat.data.AbilityListData;
import net.Indyuce.mmoitems.stat.data.type.Mergeable;
import net.Indyuce.mmoitems.stat.data.type.StatData;
import net.Indyuce.mmoitems.stat.type.GemStoneStat;
import net.Indyuce.mmoitems.stat.type.ItemStat;
import net.Indyuce.mmoitems.stat.type.StatHistory;
import net.tfminecraft.magic.Magic;

/**
 * Socketed runes keep a copy of the rune's stats and abilities from the day they were
 * socketed. This swaps that copy for the rune's current MMOItems template, keeping the cast
 * trigger the player picked for each ability, and records the template revision on the
 * weapon so each rune change is applied once.
 */
public final class RuneRefresher {

    record Socketed(UUID uuid, String type, String id) {}

    private RuneRefresher() {}

    /** True when a socketed rune's template changed since the weapon last took its data. */
    public static boolean isOutdated(ItemStack stack) {
        Map<UUID, Integer> stamped = stamps(stack);
        for (Socketed gem : socketed(stack)) {
            int live = liveRevision(gem);
            if (live >= 0 && !Integer.valueOf(live).equals(stamped.get(gem.uuid()))) {
                return true;
            }
        }
        return false;
    }

    /**
     * Brings every outdated rune in {@code mmo} up to its template. {@code stack} is the
     * weapon {@code mmo} was read from. Returns the revisions to stamp on the rebuilt weapon;
     * a rune that could not be rebuilt is left out, so it is tried again next time.
     */
    public static Map<UUID, Integer> refresh(MMOItem mmo, ItemStack stack) {
        Map<UUID, Integer> stamped = stamps(stack);
        Map<UUID, Integer> revisions = new LinkedHashMap<>();
        for (Socketed gem : socketed(stack)) {
            int live = liveRevision(gem);
            if (live < 0) {
                continue;
            }
            if (Integer.valueOf(live).equals(stamped.get(gem.uuid())) || replace(mmo, gem)) {
                revisions.put(gem.uuid(), live);
            }
        }
        return revisions;
    }

    public static void stamp(ItemStack stack, Map<UUID, Integer> revisions) {
        if (stack == null || !stack.hasItemMeta()) {
            return;
        }
        ItemMeta meta = stack.getItemMeta();
        if (revisions.isEmpty()) {
            meta.getPersistentDataContainer().remove(GearKeys.runeRevisions());
        } else {
            StringJoiner raw = new StringJoiner(",");
            revisions.forEach((uuid, revision) -> raw.add(uuid + "@" + revision));
            meta.getPersistentDataContainer().set(GearKeys.runeRevisions(), PersistentDataType.STRING, raw.toString());
        }
        stack.setItemMeta(meta);
    }

    static Map<UUID, Integer> stamps(ItemStack stack) {
        Map<UUID, Integer> stamps = new HashMap<>();
        if (stack == null || !stack.hasItemMeta()) {
            return stamps;
        }
        String raw = stack.getItemMeta().getPersistentDataContainer()
                .get(GearKeys.runeRevisions(), PersistentDataType.STRING);
        if (raw == null || raw.isBlank()) {
            return stamps;
        }
        for (String entry : raw.split(",")) {
            String[] split = entry.split("@", 2);
            try {
                stamps.put(UUID.fromString(split[0].trim()), Integer.parseInt(split[1].trim()));
            } catch (IllegalArgumentException | ArrayIndexOutOfBoundsException ignored) {
                // A damaged entry only means that rune is refreshed once more.
            }
        }
        return stamps;
    }

    /** Socketed gems, read from MMOItems' socket NBT without building the whole item. */
    static List<Socketed> socketed(ItemStack stack) {
        List<Socketed> gems = new ArrayList<>();
        if (stack == null || stack.getType().isAir()) {
            return gems;
        }
        try {
            NBTItem nbt = NBTItem.get(stack);
            String raw = nbt == null ? null : nbt.getString(ItemStats.GEM_SOCKETS.getNBTPath());
            if (raw == null || raw.isBlank()) {
                return gems;
            }
            JsonObject root = JsonParser.parseString(raw).getAsJsonObject();
            if (!root.has("Gemstones")) {
                return gems;
            }
            for (JsonElement element : root.getAsJsonArray("Gemstones")) {
                JsonObject gem = element.getAsJsonObject();
                if (gem.has("History") && gem.has("Type") && gem.has("Id")) {
                    gems.add(new Socketed(UUID.fromString(gem.get("History").getAsString()),
                            gem.get("Type").getAsString(), gem.get("Id").getAsString()));
                }
            }
        } catch (RuntimeException ex) {
            return new ArrayList<>();
        }
        return gems;
    }

    /** The rune's current template revision, or -1 when MMOItems no longer has it. */
    static int liveRevision(Socketed gem) {
        Type type = MMOItems.plugin.getTypes().get(gem.type());
        if (type == null) {
            return -1;
        }
        MMOItemTemplate template = MMOItems.plugin.getTemplates().getTemplate(type, gem.id());
        return template == null ? -1 : template.getRevisionId();
    }

    /** Same steps as MMOItems socketing a gem, but over the gem's existing history id. False when the template could not be built. */
    @SuppressWarnings({"rawtypes", "unchecked"})
    static boolean replace(MMOItem mmo, Socketed gem) {
        MMOItem fresh = MMOItems.plugin.getMMOItem(MMOItems.plugin.getTypes().get(gem.type()), gem.id());
        if (fresh == null) {
            Magic.plugin.getLogger().warning("[Magic] Rune " + gem.type() + "." + gem.id() + " could not be rebuilt; kept as socketed.");
            return false;
        }
        AbilityListData oldAbilities = null;
        for (StatHistory hist : new ArrayList<>(mmo.getStatHistories())) {
            StatData old = hist.getGemstoneData(gem.uuid());
            if (old == null) {
                continue;
            }
            if (old instanceof AbilityListData list) {
                oldAbilities = list;
            }
            hist.removeGemData(gem.uuid());
            mmo.setData(hist.getItemStat(), hist.recalculate(mmo.getUpgradeLevel()));
        }
        for (ItemStat stat : fresh.getStats()) {
            StatData data = fresh.getData(stat);
            if (stat instanceof GemStoneStat || !(data instanceof Mergeable)) {
                continue;
            }
            if (data instanceof AbilityListData list) {
                data = keepTriggers(list, oldAbilities);
            }
            mmo.mergeData(stat, data, gem.uuid());
        }
        return true;
    }

    /** The template's abilities with the trigger each one had in the weapon, by position. */
    static AbilityListData keepTriggers(AbilityListData fresh, AbilityListData old) {
        if (old == null) {
            return fresh;
        }
        List<AbilityData> was = old.getAbilities();
        List<AbilityData> copies = new ArrayList<>();
        int index = 0;
        for (AbilityData ability : fresh.getAbilities()) {
            if (index >= was.size()) {
                copies.add(ability);
            } else {
                AbilityData copy = new AbilityData(ability.getAbility(), was.get(index).getTrigger());
                for (String modifier : ability.getModifiers()) {
                    copy.setModifier(modifier, ability.getParameter(modifier));
                }
                copies.add(copy);
            }
            index++;
        }
        return new AbilityListData(copies);
    }
}
