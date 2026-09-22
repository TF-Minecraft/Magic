package net.tfminecraft.magic.listener;

import java.util.Locale;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import net.tfminecraft.rpcharacters.chat.CharacterChatEvent;
import net.tfminecraft.magic.artifact.sacrifice.SacrificeRegistry;
import net.tfminecraft.magic.artifact.sacrifice.SacrificeRiteService;
import net.tfminecraft.magic.artifact.sacrifice.SacrificeWordMatcher;

public final class SacrificeListener implements Listener {

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onCharacterChat(CharacterChatEvent event) {
        if (!SacrificeRegistry.isEnabled() || SacrificeRegistry.size() <= 0) {
            return;
        }
        if (!matchesRpChannel(event.getChannel())) {
            return;
        }
        String elementId = SacrificeWordMatcher.matchElementId(event.getMessage());
        if (elementId == null) {
            return;
        }
        if (SacrificeRegistry.isHideIncantation()) {
            event.setCancelled(true);
        }
        SacrificeRiteService.tryStart(event.getSender(), elementId);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onDeath(PlayerDeathEvent event) {
        if (event.getEntity() != null) {
            SacrificeRiteService.handleDeath(event.getEntity());
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        if (event.getPlayer() != null) {
            SacrificeRiteService.cancelForPlayer(event.getPlayer().getUniqueId());
        }
    }

    private static boolean matchesRpChannel(String channel) {
        if (channel == null || channel.isBlank()) {
            return false;
        }
        return SacrificeRegistry.getRpChannel().equals(channel.trim().toLowerCase(Locale.ROOT));
    }
}
