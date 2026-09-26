package net.tfminecraft.magic.listener;

import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import net.tfminecraft.magic.Magic;
import net.tfminecraft.magic.gear.GearRefresher;

/**
 * Lazy refresh. A crafted weapon catches up with the gear config the next time the
 * player touches it, so a reload never has to walk every inventory on the server.
 * Runs next tick because the slot contents have not settled when these events fire.
 */
public final class GearRefreshListener implements Listener {

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent event) {
        Item drop = event.getItemDrop();
        Player player = event.getPlayer();
        later(() -> {
            if (drop.isValid()) {
                ItemStack rebuilt = GearRefresher.refreshIfOutdated(drop.getItemStack(), player);
                if (rebuilt != null) {
                    drop.setItemStack(rebuilt);
                }
            }
        });
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        // Keep the clicked inventory and its own slot index. The open view can change
        // before next tick (a menu closes), so a raw view slot may no longer exist.
        Inventory clicked = event.getClickedInventory();
        int slot = event.getSlot();
        later(() -> {
            if (!player.isOnline()) {
                return;
            }
            if (clicked != null && slot >= 0 && slot < clicked.getSize()) {
                ItemStack rebuilt = GearRefresher.refreshIfOutdated(clicked.getItem(slot), player);
                if (rebuilt != null) {
                    clicked.setItem(slot, rebuilt);
                }
            }
            ItemStack cursor = GearRefresher.refreshIfOutdated(
                    player.getOpenInventory().getCursor(), player);
            if (cursor != null) {
                player.getOpenInventory().setCursor(cursor);
            }
        });
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onItemHeld(PlayerItemHeldEvent event) {
        Player player = event.getPlayer();
        int slot = event.getNewSlot();
        later(() -> {
            if (!player.isOnline()) {
                return;
            }
            ItemStack rebuilt = GearRefresher.refreshIfOutdated(
                    player.getInventory().getItem(slot), player);
            if (rebuilt != null) {
                player.getInventory().setItem(slot, rebuilt);
            }
        });
    }

    private static void later(Runnable task) {
        if (Magic.plugin == null) {
            return;
        }
        Magic.plugin.getServer().getScheduler().runTask(Magic.plugin, task);
    }
}
