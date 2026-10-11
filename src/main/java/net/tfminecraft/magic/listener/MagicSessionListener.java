package net.tfminecraft.magic.listener;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

import net.tfminecraft.magic.Magic;
import net.tfminecraft.magic.artifact.fillchest.ArtifactFillChestService;
import net.tfminecraft.magic.artifact.sacrifice.SacrificeRiteService;
import net.tfminecraft.magic.gear.GearChargeService;
import net.tfminecraft.magic.gear.gui.AssemblyHolder;
import net.tfminecraft.magic.gear.gui.PartSelectionHolder;
import net.tfminecraft.magic.gear.gui.TypeSelectionHolder;
import net.tfminecraft.magic.gear.gui.OpenStationManager;
import net.tfminecraft.magic.gear.gui.SelectedPartsManager;
import net.tfminecraft.magic.gear.gui.TypeSelectionManager;
import net.tfminecraft.magic.gui.ResonanceGuiHolder;
import net.tfminecraft.magic.gear.orb.GearOrbService;
import net.tfminecraft.magic.manager.ResonanceGuiManager;
import net.tfminecraft.magic.meditation.MeditationService;
import net.tfminecraft.magic.profile.MagicProfileService;
import net.tfminecraft.tlibs.character.CharacterSessionEndEvent;
import net.tfminecraft.tlibs.character.CharacterSessionStartEvent;

public final class MagicSessionListener implements Listener {

    private final MagicProfileService profileService;
    private final ResonanceGuiManager guiManager;

    public MagicSessionListener(MagicProfileService profileService, ResonanceGuiManager guiManager) {
        this.profileService = profileService;
        this.guiManager = guiManager;
    }

    @EventHandler
    public void onSessionStart(CharacterSessionStartEvent event) {
        Player player = event.getPlayer();
        clearActivities(player);
        profileService.activate(player, event.getSession());
        Magic.plugin.syncSpellModifiers(player, guiManager.getSessionManager().get(player));
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onSessionEnd(CharacterSessionEndEvent event) {
        clearActivities(event.getPlayer());
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        clearActivities(event.getPlayer());
    }

    private void clearActivities(Player player) {
        Object holder = player.getOpenInventory().getTopInventory().getHolder();
        MeditationService.cancel(player);
        SacrificeRiteService.cancelForPlayer(player.getUniqueId());
        GearOrbService.finishForPlayer(player.getUniqueId());
        GearChargeService.clear(player);
        ArtifactFillChestService.clear(player.getUniqueId());
        Magic.plugin.getArtifactCreateGuiManager().cancel(player);
        if (holder instanceof ResonanceGuiHolder) {
            guiManager.close(player);
        }
        if (holder instanceof AssemblyHolder || holder instanceof PartSelectionHolder
                || holder instanceof TypeSelectionHolder) {
            player.closeInventory();
        }
        OpenStationManager.clear(player);
        SelectedPartsManager.clear(player);
        TypeSelectionManager.clear(player);
        Magic.plugin.clearSpellModifiers(player);
        guiManager.getSessionManager().removeStandalone(player.getUniqueId());
    }
}
