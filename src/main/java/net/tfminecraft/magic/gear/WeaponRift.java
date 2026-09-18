package net.tfminecraft.magic.gear;

import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

/**
 * Permanent fumble chance burnt into a weapon by bad orbs, as a whole percentage.
 *
 * <p>This is the stored half of Rift. The temporary mismatch between a caster and a
 * weapon is never written here; it is shown live at cast time.
 */
public final class WeaponRift {

    private WeaponRift() {}

    public static int get(ItemStack stack) {
        if (stack == null || !stack.hasItemMeta()) {
            return 0;
        }
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) {
            return 0;
        }
        Integer stored = meta.getPersistentDataContainer().get(GearKeys.rift(), PersistentDataType.INTEGER);
        return stored == null ? 0 : clamp(stored);
    }

    public static void set(ItemStack stack, int value) {
        if (stack == null || !stack.hasItemMeta()) {
            return;
        }
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) {
            return;
        }
        int clamped = clamp(value);
        if (clamped <= 0) {
            meta.getPersistentDataContainer().remove(GearKeys.rift());
        } else {
            meta.getPersistentDataContainer().set(GearKeys.rift(), PersistentDataType.INTEGER, clamped);
        }
        stack.setItemMeta(meta);
    }

    /** Carries Rift across an MMOItems rebuild, which drops the container. */
    public static void copy(ItemStack from, ItemStack to) {
        int rift = get(from);
        if (rift > 0) {
            set(to, rift);
        }
    }

    private static int clamp(int value) {
        return Math.max(0, Math.min(net.tfminecraft.magic.gear.orb.OrbCache.riftCap, value));
    }
}
