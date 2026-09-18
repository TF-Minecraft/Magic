package net.tfminecraft.magic.artifact.aura;

import org.bukkit.inventory.ItemStack;

import net.tfminecraft.magic.artifact.Artifact;
import net.tfminecraft.magic.charge.Charge;

/**
 * Resolves an item into whichever aura vessel it is.
 *
 * <p>Charges are checked first because a blank charge carries no aura yet and would
 * otherwise fall through as "not a vessel".
 */
public final class AuraVessels {

    private AuraVessels() {}

    public static AuraVessel fromItem(ItemStack stack) {
        if (stack == null || stack.getType().isAir()) {
            return null;
        }
        Charge charge = Charge.fromItem(stack);
        if (charge != null) {
            return charge;
        }
        return Artifact.fromItem(stack);
    }
}
