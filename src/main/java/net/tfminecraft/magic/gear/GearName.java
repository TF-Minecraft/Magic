package net.tfminecraft.magic.gear;

import java.util.Collection;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import net.Indyuce.mmoitems.ItemStats;
import net.Indyuce.mmoitems.api.item.mmoitem.MMOItem;
import net.Indyuce.mmoitems.stat.type.NameData;
import net.Indyuce.mmoitems.stat.type.StatHistory;

/**
 * Names a mage weapon after its core, the way AdvancedCrafting names equipment after its
 * majority material: the archetype's item name with {@link #MATERIAL} filled in, all of it
 * in the material's colour.
 */
public final class GearName {

    public static final String MATERIAL = "%material%";

    private static final Pattern LEADING_CODES = Pattern.compile("^(?:§[0-9a-fk-orx])+", Pattern.CASE_INSENSITIVE);
    private static final Pattern CORE_SUFFIX = Pattern.compile("\\s+(?:magical\\s+)?core$", Pattern.CASE_INSENSITIVE);

    private GearName() {}

    /** The name a weapon built from these parts carries, or null when its core gives no material. */
    public static String expected(GearType type, Collection<PartDef> parts) {
        ArchetypeDef archetype = ArchetypeRegistry.get(type);
        if (archetype == null) {
            return null;
        }
        String material = material(core(parts));
        Matcher codes = LEADING_CODES.matcher(material);
        String colour = codes.find() ? codes.group() : "";
        String plain = material.substring(colour.length()).trim();
        if (plain.isEmpty()) {
            return null;
        }
        return colour + archetype.getItemName().replace(MATERIAL, plain);
    }

    /** True when the weapon's name no longer matches its core and archetype. */
    public static boolean isOutdated(ItemStack stack) {
        String expected = expected(GearProvenance.archetypeOf(stack), GearProvenance.resolveParts(stack));
        return expected != null && !expected.equals(stored(stack));
    }

    /** The core's material, or its name without "Magical Core" when it sets none. */
    static String material(PartDef core) {
        if (core == null) {
            return "";
        }
        if (!core.getMaterial().isEmpty()) {
            return core.getMaterial();
        }
        Matcher suffix = CORE_SUFFIX.matcher(core.getName());
        return suffix.find() ? core.getName().substring(0, suffix.start()) : "";
    }

    static PartDef core(Collection<PartDef> parts) {
        if (parts == null) {
            return null;
        }
        for (PartDef part : parts) {
            if (part != null && PartSlots.CORE.equalsIgnoreCase(part.getPartType())) {
                return part;
            }
        }
        return null;
    }

    /** Writes the name to the MMOItems data and its original, so MMOItems updates keep it. */
    public static void write(MMOItem mmo, String name) {
        if (mmo == null || name == null) {
            return;
        }
        NameData data = mmo.getData(ItemStats.NAME) instanceof NameData current ? current : new NameData(name);
        data.setString(name);
        mmo.replaceData(ItemStats.NAME, data);
        StatHistory history = mmo.computeStatHistory(ItemStats.NAME);
        if (history != null && history.getOriginalData() instanceof NameData original) {
            original.setString(name);
            mmo.setStatHistory(ItemStats.NAME, history);
        }
    }

    /** Records the name written on the weapon. */
    public static void stamp(ItemStack stack, String name) {
        if (stack == null || name == null || !stack.hasItemMeta()) {
            return;
        }
        ItemMeta meta = stack.getItemMeta();
        meta.getPersistentDataContainer().set(GearKeys.gearName(), PersistentDataType.STRING, name);
        stack.setItemMeta(meta);
    }

    static String stored(ItemStack stack) {
        if (stack == null || !stack.hasItemMeta()) {
            return null;
        }
        return stack.getItemMeta().getPersistentDataContainer().get(GearKeys.gearName(), PersistentDataType.STRING);
    }
}
