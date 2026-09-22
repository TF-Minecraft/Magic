package net.tfminecraft.magic.manager;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;

import net.tfminecraft.rpcharacters.objects.RPCharacter;
import net.tfminecraft.magic.GuiCache;
import net.tfminecraft.magic.Messages;
import net.tfminecraft.magic.gui.ResonanceGuiBuilder;
import net.tfminecraft.magic.gui.ResonanceGuiHolder;
import net.tfminecraft.magic.integration.RpCharactersBridge;
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
                && RpCharactersBridge.isAvailable()
                && RpCharactersBridge.getActiveCharacter(player) == null) {
            player.sendMessage(Messages.get("open.no_character"));
            return false;
        }
        open(player);
        return true;
    }

    public void open(Player player) {
        RPCharacter character = RpCharactersBridge.getActiveCharacter(player);
        String characterId = character != null ? character.getId() : null;
        ResonanceSession session = sessionManager.getOrCreate(player);
        ResonanceGuiHolder holder = new ResonanceGuiHolder(player.getUniqueId(), characterId);
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
            ResonanceSession session = sessionManager.getOrCreate(player);
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

        ResonanceSession session = sessionManager.getOrCreate(player);
        String previousMode = session.getCastModeId();
        if (rawSlot == GridLayout.castModeLeftSlot()) {
            session.setCastModeId(GuiCache.castModeLeft.getId());
        } else if (rawSlot == GridLayout.castModeRightSlot()) {
            session.setCastModeId(GuiCache.castModeRight.getId());
        } else {
            return;
        }

        if (!previousMode.equals(session.getCastModeId())) {
            refreshGui(player, holder, session);
        }
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof ResonanceGuiHolder)) {
            return;
        }
        event.setCancelled(true);
    }

    public void closeIfCharacterChanged(Player player, String characterId) {
        Inventory top = player.getOpenInventory().getTopInventory();
        if (!(top.getHolder() instanceof ResonanceGuiHolder holder)) {
            return;
        }
        String openId = holder.getCharacterId();
        if (openId == null || !openId.equals(characterId)) {
            player.closeInventory();
        }
    }

    private void refreshGui(Player player, ResonanceGuiHolder holder, ResonanceSession session) {
        ResonanceGuiBuilder.populate(holder.getInventory(), player, session);
    }
}
