package net.tfminecraft.magic.artifact.aura;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;

import net.tfminecraft.magic.artifact.ArtifactKeys;
import net.tfminecraft.magic.artifact.ArtifactLore;
import net.tfminecraft.magic.charge.ChargeIds;
import net.tfminecraft.magic.charge.ChargeKeys;
import net.tfminecraft.magic.charge.ChargeLore;
import net.tfminecraft.magic.charge.ChargeRegistry;

/** Sends an item to whichever lore writer owns it. */
public final class VesselLore {

    private VesselLore() {}

    public static ItemStack updateItem(ItemStack stack) {
        if (stack == null || stack.getType().isAir() || !mayHaveVesselLore(stack)) {
            return stack;
        }
        return write(stack);
    }

    private static ItemStack write(ItemStack stack) {
        if (ChargeIds.isCharge(stack)) {
            return ChargeLore.updateItem(stack);
        }
        return ArtifactLore.updateItem(stack);
    }

    /**
     * False only for an item neither lore writer touches, found from one copy of its meta. Both writers skip an item
     * without meta (hasItemMeta). A charge has its tier key or matches a charge item (ChargeIds.isCharge); an artifact
     * has aura data (AuraData.fromPersistentData) or aura lines its lore can be read back from, and each of those has
     * a "/" (ArtifactLore.readAura). For anything else both writers leave the item exactly as it was, so it isn't
     * read again for each of them.
     */
    // Keep the existing legacy text representation, formatting, and exact-string comparisons.
    @SuppressWarnings("deprecation")
    static boolean mayHaveVesselLore(ItemStack stack) {
        ItemMeta meta = stack.getItemMeta();
        if (Bukkit.getItemFactory().equals(meta, null)) {
            return false;
        }
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        if (pdc.has(ArtifactKeys.auraCap()) || pdc.has(ArtifactKeys.auraFill()) || pdc.has(ArtifactKeys.auraData())
                || pdc.has(ChargeKeys.chargeTier()) || ChargeRegistry.match(stack) != null) {
            return true;
        }
        if (!meta.hasLore()) {
            return false;
        }
        for (String line : meta.getLore()) {
            if (line.indexOf('/') >= 0) {
                return true;
            }
        }
        return false;
    }

    /**
     * Rewrites the vessel lore of every item the player carries. Only slots whose item the writers changed are set
     * again; setting an unchanged item gave the same inventory and only resent the slot to the client.
     */
    public static void updateInventory(Player player) {
        if (player == null) {
            return;
        }
        PlayerInventory inventory = player.getInventory();
        ItemStack[] storage = inventory.getStorageContents();
        ItemStack[] armor = inventory.getArmorContents();
        refresh(inventory, storage, 0);
        refresh(inventory, armor, storage.length);
        refresh(inventory, inventory.getExtraContents(), storage.length + armor.length);
    }

    /** Refreshes items, which fill the inventory's slots from first on (storage, then armor, then off hand). */
    private static void refresh(PlayerInventory inventory, ItemStack[] items, int first) {
        for (int i = 0; i < items.length; i++) {
            ItemStack item = items[i];
            if (item == null || item.getType().isAir() || !mayHaveVesselLore(item)) {
                continue;
            }
            // A copy from before: the writers change the item in place, and it may be the inventory's own.
            ItemStack before = item.clone();
            ItemStack after = write(item);
            if (!after.equals(before)) {
                inventory.setItem(first + i, after);
            }
        }
    }
}
