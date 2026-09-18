package net.tfminecraft.magic.listener;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

import net.tfminecraft.RPCharacters.lifecycle.CharacterActivatedEvent;
import net.tfminecraft.magic.Magic;
import net.tfminecraft.magic.manager.ResonanceGuiManager;
import net.tfminecraft.magic.profile.MagicProfileService;

public final class MagicSessionListener implements Listener {

    private final MagicProfileService profileService;
    private final ResonanceGuiManager guiManager;

    public MagicSessionListener(MagicProfileService profileService, ResonanceGuiManager guiManager) {
        this.profileService = profileService;
        this.guiManager = guiManager;
    }

    @EventHandler
    public void onCharacterActivated(CharacterActivatedEvent event) {
        Player owner = event.getOwner();
        if (owner == null || event.getCharacter() == null) {
            return;
        }
        if (event.getPrevious() != null) {
            profileService.savePrevious(owner, event.getPrevious());
        }
        profileService.activate(owner, event.getCharacter());
        Magic.plugin.syncSpellModifiers(owner, guiManager.getSessionManager().get(owner));
        guiManager.closeIfCharacterChanged(owner, event.getCharacter().getId());
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Magic.plugin.clearSpellModifiers(event.getPlayer());
        profileService.deactivate(event.getPlayer());
    }
}
