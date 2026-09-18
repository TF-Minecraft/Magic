package net.tfminecraft.magic.charge;

import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

/**
 * Cheap "is this a charge" check.
 *
 * <p>Artifact-only code paths call this to bail out, which is what keeps charges out of
 * meditation, care/muffle and the artifact lore block without each of those needing to
 * know charges exist.
 */
public final class ChargeIds {

    private ChargeIds() {}

    public static boolean isCharge(ItemStack stack) {
        if (stack == null || stack.getType().isAir() || !stack.hasItemMeta()) {
            return false;
        }
        return hasKey(stack) || ChargeRegistry.match(stack) != null;
    }

    /** True once the charge has imprinted and stamped its tier. */
    public static boolean hasKey(ItemStack stack) {
        if (stack == null || !stack.hasItemMeta()) {
            return false;
        }
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) {
            return false;
        }
        Integer tier = meta.getPersistentDataContainer().get(
                ChargeKeys.chargeTier(), PersistentDataType.INTEGER);
        return tier != null && tier > 0;
    }
}
