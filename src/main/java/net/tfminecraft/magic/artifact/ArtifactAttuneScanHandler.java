package net.tfminecraft.magic.artifact;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.entity.Player;

import net.tfminecraft.tfmccore.itemscan.ItemScanHandler;

public final class ArtifactAttuneScanHandler implements ItemScanHandler {

    @Override
    public boolean matches(ItemStack stack) {
        return ArtifactIds.hasKey(stack);
    }

    @Override
    public void update(Player player, Inventory inventory, int slot, ItemStack stack) {
        ArtifactCareStore.Persist persist = inventory == null
                ? ArtifactCareStore.Persist.ALWAYS
                : ArtifactCareStore.Persist.IF_VISIBLE;
        boolean wrote = ArtifactCareStore.apply(
                stack,
                ArtifactCarePlaces.accepted(inventory, stack),
                System.currentTimeMillis(),
                persist);
        // Chest (and other BlockState) inventories often hand out snapshots. Mutating
        // the stack in place does not refresh the open GUI until the item is moved.
        if (wrote && inventory != null && slot >= 0) {
            inventory.setItem(slot, stack);
        }
    }
}
