package net.tfminecraft.magic.gear;

import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;

/**
 * Vanilla durability on mage weapons. Spent casts and wand shots wear it, but the last
 * point is never spent: MMOItems deletes a weapon whose vanilla durability runs out. A
 * weapon on its last point is worn out and refuses spells and shots until it is repaired.
 */
public final class WeaponWear {

    private WeaponWear() {}

    /** Durability left, or -1 when the stack has no vanilla durability that can wear. */
    public static int remaining(ItemStack stack) {
        ItemMeta meta = stack == null ? null : stack.getItemMeta();
        if (!(meta instanceof Damageable damageable) || meta.isUnbreakable()) {
            return -1;
        }
        int max = damageable.hasMaxDamage() ? damageable.getMaxDamage() : stack.getType().getMaxDurability();
        if (max <= 0) {
            return -1;
        }
        return Math.max(0, max - damageable.getDamage());
    }

    /** True when only the last point, or none, is left. */
    public static boolean isWorn(ItemStack stack) {
        int left = remaining(stack);
        return left >= 0 && left <= 1;
    }

    /** Spends up to {@code points} durability, stopping at the last point. */
    public static void wear(ItemStack stack, int points) {
        int left = remaining(stack);
        if (points <= 0 || left <= 1) {
            return;
        }
        Damageable meta = (Damageable) stack.getItemMeta();
        meta.setDamage(meta.getDamage() + Math.min(points, left - 1));
        stack.setItemMeta(meta);
    }
}
