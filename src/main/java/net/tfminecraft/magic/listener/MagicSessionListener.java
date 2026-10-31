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
        var captured = net.tfminecraft.magic.gear.ownership.GearCharacterBridge.token(event.getSession());
        clearActivities(player, null, false);
        if (net.tfminecraft.magic.gear.GearStationStore.property().identity().current(captured, player)) clearStaleGear(player);
        profileService.activate(player, event.getSession());
        net.tfminecraft.magic.gear.ownership.GearEffects.deliver(player, captured);
        Magic.plugin.syncSpellModifiers(player, guiManager.getSessionManager().get(player));
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onSessionEnd(CharacterSessionEndEvent event) {
        clearActivities(event.getPlayer(), event.getSession());
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        clearActivities(event.getPlayer());
    }

    private void clearActivities(Player player) {
        clearActivities(player, null);
    }
    private void clearActivities(Player player, net.tfminecraft.tlibs.character.CharacterSession outgoing) {
        clearActivities(player, outgoing, true);
    }
    private void clearActivities(Player player, net.tfminecraft.tlibs.character.CharacterSession outgoing, boolean clearGear) {
        Object holder = player.getOpenInventory().getTopInventory().getHolder(false);
        MeditationService.cancel(player);
        SacrificeRiteService.cancelForPlayer(player.getUniqueId());
        var captured = outgoing == null ? null : net.tfminecraft.magic.gear.ownership.GearCharacterBridge.token(outgoing);
        if (clearGear) {
            if (outgoing == null) { GearOrbService.finishForPlayer(player.getUniqueId()); GearChargeService.clear(player); net.tfminecraft.magic.gear.GearReattuneService.clear(player); }
            else { GearOrbService.finishForSession(player.getUniqueId(), outgoing); GearChargeService.clear(player, captured); net.tfminecraft.magic.gear.GearReattuneService.clear(player); }
        }
        ArtifactFillChestService.clear(player.getUniqueId());
        if (holder instanceof ResonanceGuiHolder) {
            guiManager.close(player);
        }
        boolean ownMenu = clearGear && holder instanceof net.tfminecraft.magic.gear.gui.GearMenuHolder gear
                && (captured == null || captured.equals(gear.token()));
        if (ownMenu) {
            player.closeInventory();
        }
        if (clearGear && (captured == null || OpenStationManager.matches(player, captured))) {
            if (captured == null) OpenStationManager.clear(player); else OpenStationManager.clear(player, captured);
            SelectedPartsManager.clear(player); TypeSelectionManager.clear(player);
        }
        Magic.plugin.clearSpellModifiers(player);
        guiManager.getSessionManager().removeStandalone(player.getUniqueId());
    }
    private void clearStaleGear(Player player) {
        GearOrbService.finishStaleForPlayer(player.getUniqueId()); GearChargeService.clearStale(player);
        if (player.getOpenInventory().getTopInventory().getHolder(false) instanceof net.tfminecraft.magic.gear.gui.GearMenuHolder gear && !gear.current(player)) player.closeInventory();
        if (OpenStationManager.get(player) == null) {
            OpenStationManager.clear(player); SelectedPartsManager.clear(player); TypeSelectionManager.clear(player);
        }
    }
}
