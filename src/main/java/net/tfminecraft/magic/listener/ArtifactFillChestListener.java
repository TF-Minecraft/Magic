package net.tfminecraft.magic.listener;

import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;

import net.tfminecraft.magic.Messages;
import net.tfminecraft.magic.artifact.fillchest.ArtifactFillChestService;
import net.tfminecraft.magic.artifact.fillchest.ArtifactFillChestService.Pending;

public final class ArtifactFillChestListener implements Listener {

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        ArtifactFillChestService.clear(event.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        Player player = event.getPlayer();
        Pending held = ArtifactFillChestService.get(player.getUniqueId());
        if (held == null) {
            return;
        }
        Block block = event.getClickedBlock();
        if (!ArtifactFillChestService.isChestBlock(block)) {
            return;
        }
        if (System.currentTimeMillis() > held.expireAtMs()) {
            ArtifactFillChestService.clear(player.getUniqueId());
            player.sendMessage(Messages.get("fillchest.expired"));
            return;
        }
        event.setCancelled(true);
        int placed = ArtifactFillChestService.fill(block, held);
        ArtifactFillChestService.consume(player.getUniqueId());
        player.sendMessage(Messages.get("fillchest.done", "count", Integer.toString(placed)));
    }
}
