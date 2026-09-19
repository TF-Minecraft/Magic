package net.tfminecraft.magic.listener;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;

import net.tfminecraft.magic.Magic;
import net.tfminecraft.magic.gear.GearCache;

/**
 * Alignment reads the weapon in hand, so changing the held item has to resync the skill
 * modifiers. Runs next tick because slot contents have not settled when these events fire.
 * Does nothing while the alignment flag is off. Snapshot comparison skips no-op events.
 */
public final class GearAlignmentListener implements Listener {

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onItemHeld(PlayerItemHeldEvent event) {
        resync(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onSwapHands(PlayerSwapHandItemsEvent event) {
        resync(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        if (event.getWhoClicked() instanceof Player player) {
            resync(player);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onInventoryDrag(InventoryDragEvent event) {
        if (event.getWhoClicked() instanceof Player player) {
            resync(player);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent event) {
        resync(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPickup(EntityPickupItemEvent event) {
        if (event.getEntity() instanceof Player player) {
            resync(player);
        }
    }

    private static void resync(Player player) {
        if (!GearCache.alignmentEnabled || player == null || Magic.plugin == null) {
            return;
        }
        Magic.plugin.getServer().getScheduler().runTask(Magic.plugin, () -> {
            if (player.isOnline()) {
                Magic.plugin.syncSpellModifiers(
                        player, Magic.plugin.getResonanceGuiManager().getSessionManager().get(player));
            }
        });
    }
}
