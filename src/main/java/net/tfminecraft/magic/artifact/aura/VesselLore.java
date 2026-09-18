package net.tfminecraft.magic.artifact.aura;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import net.tfminecraft.magic.artifact.ArtifactLore;
import net.tfminecraft.magic.charge.ChargeIds;
import net.tfminecraft.magic.charge.ChargeLore;

/** Sends an item to whichever lore writer owns it. */
public final class VesselLore {

    private VesselLore() {}

    public static ItemStack updateItem(ItemStack stack) {
        if (stack == null || stack.getType().isAir()) {
            return stack;
        }
        if (ChargeIds.isCharge(stack)) {
            return ChargeLore.updateItem(stack);
        }
        return ArtifactLore.updateItem(stack);
    }

    public static void updateInventory(Player player) {
        if (player == null) {
            return;
        }
        PlayerInventory inventory = player.getInventory();
        ItemStack[] storage = inventory.getStorageContents();
        if (storage != null) {
            for (int i = 0; i < storage.length; i++) {
                storage[i] = updateItem(storage[i]);
            }
            inventory.setStorageContents(storage);
        }
        ItemStack[] armor = inventory.getArmorContents();
        if (armor != null) {
            for (int i = 0; i < armor.length; i++) {
                armor[i] = updateItem(armor[i]);
            }
            inventory.setArmorContents(armor);
        }
        ItemStack[] extra = inventory.getExtraContents();
        if (extra != null) {
            for (int i = 0; i < extra.length; i++) {
                extra[i] = updateItem(extra[i]);
            }
            inventory.setExtraContents(extra);
        }
    }
}
