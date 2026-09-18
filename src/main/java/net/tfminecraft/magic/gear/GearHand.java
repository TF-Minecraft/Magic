package net.tfminecraft.magic.gear;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/**
 * Finds the mage weapon a player is casting with. Main hand wins; offhand is the
 * fallback so an off-hand focus still counts. Armor is deliberately never consulted:
 * armor runes are passive and must not gate anything.
 */
public final class GearHand {

    public enum HeldSlot {
        MAIN_HAND,
        OFF_HAND
    }

    private GearHand() {}

    /** @return the held mage weapon, or null when the player is holding something else */
    public static ItemStack held(Player player) {
        HeldSlot slot = heldSlot(player);
        if (slot == null || player == null) {
            return null;
        }
        return slot == HeldSlot.MAIN_HAND
                ? player.getInventory().getItemInMainHand()
                : player.getInventory().getItemInOffHand();
    }

    /** @return which hand carries the mage weapon, or null when neither hand has gear */
    public static HeldSlot heldSlot(Player player) {
        if (player == null) {
            return null;
        }
        if (GearProvenance.isGear(player.getInventory().getItemInMainHand())) {
            return HeldSlot.MAIN_HAND;
        }
        if (GearProvenance.isGear(player.getInventory().getItemInOffHand())) {
            return HeldSlot.OFF_HAND;
        }
        return null;
    }

    public static void setHeld(Player player, HeldSlot slot, ItemStack stack) {
        if (player == null || slot == null) {
            return;
        }
        if (slot == HeldSlot.MAIN_HAND) {
            player.getInventory().setItemInMainHand(stack);
        } else {
            player.getInventory().setItemInOffHand(stack);
        }
    }

    /**
     * Total staff count in storage and offhand. Armor and ender chest are ignored.
     * Stacks count by amount so two half-stacks still overload.
     */
    public static int staffCount(Player player) {
        if (player == null) {
            return 0;
        }
        int count = 0;
        for (ItemStack stack : player.getInventory().getStorageContents()) {
            count += staffAmount(stack);
        }
        count += staffAmount(player.getInventory().getItemInOffHand());
        return count;
    }

    private static int staffAmount(ItemStack stack) {
        if (stack == null || stack.getType().isAir()) {
            return 0;
        }
        if (GearProvenance.archetypeOf(stack) != GearType.STAFF) {
            return 0;
        }
        return stack.getAmount();
    }
}
