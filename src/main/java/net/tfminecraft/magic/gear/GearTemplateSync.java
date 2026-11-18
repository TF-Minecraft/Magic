package net.tfminecraft.magic.gear;

import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.bukkit.inventory.ItemStack;

import io.lumine.mythic.lib.api.item.NBTItem;
import net.Indyuce.mmoitems.ItemStats;
import net.Indyuce.mmoitems.MMOItems;
import net.Indyuce.mmoitems.api.item.mmoitem.MMOItem;
import net.Indyuce.mmoitems.api.item.template.MMOItemTemplate;
import net.Indyuce.mmoitems.stat.data.BooleanData;
import net.Indyuce.mmoitems.stat.data.StringListData;
import net.Indyuce.mmoitems.stat.data.random.RandomBooleanData;
import net.Indyuce.mmoitems.stat.type.StatHistory;

/**
 * Keeps a crafted weapon's class list and two-handed flag in line with its MMOItems template.
 * A refresh rebuilds the weapon from its own data, so without this a class added to or removed
 * from a mage weapon template, or a template made two-handed, never reaches weapons that exist.
 * Nothing else on the weapon is taken from the template.
 */
public final class GearTemplateSync {

    static final String CLASS_TAG = "MMOITEMS_REQUIRED_CLASS";
    static final String TWO_HANDED_TAG = "MMOITEMS_TWO_HANDED";

    private GearTemplateSync() {}

    /** True when the weapon's classes (as a set, ignoring case) or two-handed flag differ from its template's. */
    public static boolean isOutdated(ItemStack stack) {
        if (stack == null || stack.getType().isAir()) {
            return false;
        }
        try {
            NBTItem nbt = NBTItem.get(stack);
            MMOItemTemplate template = template(nbt);
            if (template == null) {
                return false;
            }
            String raw = nbt.getString(CLASS_TAG);
            Set<String> classes = lowerCase(Arrays.asList((raw == null ? "" : raw).split(",")));
            return !classes.equals(lowerCase(classes(template))) || nbt.getBoolean(TWO_HANDED_TAG) != twoHanded(template);
        } catch (RuntimeException ex) {
            return false;
        }
    }

    /**
     * Gives {@code mmo} its template's classes and two-handed flag. With a class stat history only
     * its original layer changes, so gem layers stay. {@code nbt} is the item {@code mmo} was read from.
     */
    public static void apply(MMOItem mmo, NBTItem nbt) {
        MMOItemTemplate template = template(nbt);
        if (template == null) {
            return;
        }
        List<String> classes = classes(template);
        if (classes.isEmpty()) {
            mmo.removeData(ItemStats.REQUIRED_CLASS);
        } else {
            StringListData data = new StringListData(classes);
            StatHistory history = mmo.getStatHistory(ItemStats.REQUIRED_CLASS);
            if (history != null) {
                history.setOriginalData(data.clone());
            }
            mmo.setData(ItemStats.REQUIRED_CLASS, data);
        }
        if (twoHanded(template)) {
            mmo.setData(ItemStats.TWO_HANDED, new BooleanData(true));
        } else {
            mmo.removeData(ItemStats.TWO_HANDED);
        }
    }

    private static MMOItemTemplate template(NBTItem nbt) {
        if (MMOItems.plugin == null || nbt == null || !nbt.hasType()) {
            return null;
        }
        return MMOItems.plugin.getTemplates().getTemplate(nbt);
    }

    private static List<String> classes(MMOItemTemplate template) {
        return template.getBaseItemData().get(ItemStats.REQUIRED_CLASS) instanceof StringListData list
                ? list.getList()
                : List.of();
    }

    static boolean twoHanded(MMOItemTemplate template) {
        Object data = template.getBaseItemData().get(ItemStats.TWO_HANDED);
        if (data instanceof RandomBooleanData random) {
            return random.getChance() >= 1;
        }
        return data instanceof BooleanData flag && flag.isEnabled();
    }

    /** Trimmed, non-blank, lower-case names, as MMOItems compares a player's class. */
    static Set<String> lowerCase(Collection<String> names) {
        Set<String> result = new HashSet<>();
        for (String name : names) {
            String trimmed = name.trim();
            if (!trimmed.isEmpty()) {
                result.add(trimmed.toLowerCase(Locale.ROOT));
            }
        }
        return result;
    }
}
