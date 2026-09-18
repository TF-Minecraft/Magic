package net.tfminecraft.magic.listener;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;

import net.tfminecraft.magic.Magic;
import net.tfminecraft.magic.gear.GearCache;

/**
 * Alignment reads the weapon in hand, so swapping weapons has to resync the skill
 * modifiers. The periodic sync would catch it within a tick cycle anyway; this makes it
 * immediate. Runs next tick because the held slot has not moved yet when the event
 * fires. Does nothing while the alignment flag is off.
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
