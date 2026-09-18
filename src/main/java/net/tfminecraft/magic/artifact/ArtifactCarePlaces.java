package net.tfminecraft.magic.artifact;

import org.bukkit.entity.ItemFrame;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import net.tfminecraft.magic.attunement.ArtifactDisplayIndex;

public final class ArtifactCarePlaces {

    private ArtifactCarePlaces() {}

    public static boolean accepted(Inventory inventory, ItemStack stack) {
        if (inventory == null) {
            return false;
        }
        if (ArtifactDisplayIndex.isDisplayed(ArtifactIds.read(stack))) {
            return true;
        }
        InventoryHolder holder = inventory.getHolder();
        return holder instanceof Player || holder instanceof ItemFrame;
    }
}
