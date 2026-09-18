package net.tfminecraft.magic.artifact;

import java.util.ArrayList;
import java.util.List;

import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemFrame;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.ItemStack;

import net.tfminecraft.magic.tick.MagicTickContext;
import net.tfminecraft.magic.tick.MagicTickHandler;

public final class ArtifactFrameCareTicker implements MagicTickHandler, Listener {

    private static final double RANGE = 48.0;

    private int roundRobin;

    @Override
    public void onTick(MagicTickContext context) {
        List<? extends Player> players = new ArrayList<>(Bukkit.getOnlinePlayers());
        if (players.isEmpty()) {
            roundRobin = 0;
            return;
        }
        if (roundRobin >= players.size()) {
            roundRobin = 0;
        }
        Player player = players.get(roundRobin);
        roundRobin++;
        tickNearbyFrames(player);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onFrameInteract(PlayerInteractEntityEvent event) {
        Entity clicked = event.getRightClicked();
        if (clicked instanceof ItemFrame frame) {
            tickFrame(frame, ArtifactCareStore.Persist.ALWAYS);
        }
    }

    private static void tickNearbyFrames(Player player) {
        for (Entity entity : player.getNearbyEntities(RANGE, RANGE, RANGE)) {
            if (entity instanceof ItemFrame frame) {
                tickFrame(frame, ArtifactCareStore.Persist.IF_VISIBLE);
            }
        }
    }

    static void tickFrame(ItemFrame frame, ArtifactCareStore.Persist persist) {
        if (frame == null || !frame.isValid()) {
            return;
        }
        ItemStack stack = frame.getItem();
        if (!ArtifactIds.hasKey(stack)) {
            return;
        }
        boolean wrote = ArtifactCareStore.apply(
                stack, true, System.currentTimeMillis(), persist);
        if (wrote) {
            frame.setItem(stack, false);
        }
    }
}
