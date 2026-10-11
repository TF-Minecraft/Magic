package net.tfminecraft.magic.manager;

import java.util.Locale;

import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;

import net.tfminecraft.tlibs.character.Characters;
import net.tfminecraft.magic.GuiCache;
import net.tfminecraft.magic.Messages;
import net.tfminecraft.magic.gui.ResonanceGuiBuilder;
import net.tfminecraft.magic.gui.ResonanceGuiHolder;
import net.tfminecraft.magic.session.ResonanceSession;
import net.tfminecraft.magic.session.ResonanceSessionManager;
import net.tfminecraft.magic.util.GridLayout;

public final class ResonanceGuiManager implements Listener {

    private final ResonanceSessionManager sessionManager = new ResonanceSessionManager();

    public ResonanceSessionManager getSessionManager() {
        return sessionManager;
    }

    public boolean tryOpen(Player player, boolean bypassCharacterCheck) {
        if (!bypassCharacterCheck
                && Characters.available()
                && sessionManager.get(player) == null) {
            player.sendMessage(Messages.get("open.no_character"));
            return false;
        }
        open(player);
        return true;
    }

    public void open(Player player) {
        ResonanceSession session = sessionManager.getOrCreate(player);
        if (session == null) {
            session = new ResonanceSession();
        }
        ResonanceGuiHolder holder = new ResonanceGuiHolder(player.getUniqueId(),
                Characters.currentSession(player).orElse(null), session);
        var inventory = ResonanceGuiBuilder.build(player, holder, session);
        holder.setInventory(inventory);
        player.openInventory(inventory);
    }

    public void refreshOpenInventories() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            Inventory top = player.getOpenInventory().getTopInventory();
            if (!(top.getHolder() instanceof ResonanceGuiHolder holder)) {
                continue;
            }
            ResonanceSession session = currentSession(player, holder);
            if (session == null) {
                continue;
            }
            ResonanceGuiBuilder.populate(top, player, session);
            holder.setInventory(top);
        }
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        if (!(event.getView().getTopInventory().getHolder() instanceof ResonanceGuiHolder holder)) {
            return;
        }
        event.setCancelled(true);

        int rawSlot = event.getRawSlot();
        if (rawSlot >= event.getView().getTopInventory().getSize()) {
            return;
        }

        ResonanceSession session = currentSession(player, holder);
        if (session == null) {
            return;
        }
        String previousMode = session.getCastModeId();
        if (rawSlot == GridLayout.castModeLeftSlot()) {
            session.setCastModeId(GuiCache.castModeLeft.getId());
        } else if (rawSlot == GridLayout.castModeRightSlot()) {
            session.setCastModeId(GuiCache.castModeRight.getId());
        } else {
            return;
        }

        if (!previousMode.equals(session.getCastModeId())) {
            notifyCastModeChanged(player, session.getCastModeId());
            refreshGui(player, holder, session);
        }
    }

    private static void notifyCastModeChanged(Player player, String modeId) {
        player.sendMessage(Messages.get("cast_mode.switched", "mode", castModeChatLabel(modeId)));
        boolean flow = GuiCache.castModeRight.getId().equalsIgnoreCase(modeId);
        player.playSound(
                player.getLocation(),
                Sound.BLOCK_AMETHYST_BLOCK_CHIME,
                SoundCategory.PLAYERS,
                0.85f,
                flow ? 1.5f : 0.75f);
    }

    private static String castModeChatLabel(String modeId) {
        if (modeId != null && modeId.equalsIgnoreCase(GuiCache.castModeRight.getId())) {
            return "Flow";
        }
        if (modeId != null && modeId.equalsIgnoreCase(GuiCache.castModeLeft.getId())) {
            return "Surge";
        }
        if (modeId == null || modeId.isBlank()) {
            return "Surge";
        }
        return modeId.substring(0, 1).toUpperCase(Locale.ROOT)
                + modeId.substring(1).toLowerCase(Locale.ROOT);
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof ResonanceGuiHolder)) {
            return;
        }
        event.setCancelled(true);
    }

    public void close(Player player) {
        if (player.getOpenInventory().getTopInventory().getHolder() instanceof ResonanceGuiHolder) {
            player.closeInventory();
        }
    }

    private ResonanceSession currentSession(Player player, ResonanceGuiHolder holder) {
        boolean current = holder.getSession() == null
                ? Characters.currentSession(player).isEmpty() : Characters.isCurrent(holder.getSession());
        if (!player.getUniqueId().equals(holder.getPlayerUuid()) || !current) {
            close(player);
            return null;
        }
        return holder.getResonance();
    }

    private void refreshGui(Player player, ResonanceGuiHolder holder, ResonanceSession session) {
        ResonanceGuiBuilder.populate(holder.getInventory(), player, session);
    }
}
