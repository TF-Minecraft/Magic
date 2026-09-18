package net.tfminecraft.magic.manager;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;

import net.tfminecraft.magic.ArtifactCreateCache;
import net.tfminecraft.magic.Messages;
import net.tfminecraft.magic.artifact.config.ArtifactRarityDef;
import net.tfminecraft.magic.artifact.config.ArtifactRarityRegistry;
import net.tfminecraft.magic.artifact.create.ArtifactCreateSession;
import net.tfminecraft.magic.artifact.generate.ArtifactItemBuilder;
import net.tfminecraft.magic.artifact.generate.ArtifactRoll;
import net.tfminecraft.magic.gui.ArtifactCreateGuiBuilder;
import net.tfminecraft.magic.gui.ArtifactCreateGuiHolder;
import net.tfminecraft.magic.model.ElementDef;
import net.tfminecraft.magic.registry.ElementRegistry;

public final class ArtifactCreateGuiManager implements Listener {

    private final Map<UUID, ArtifactCreateSession> sessions = new ConcurrentHashMap<>();

    public void open(Player player) {
        ArtifactCreateSession session = new ArtifactCreateSession();
        ArtifactCreateGuiHolder holder = new ArtifactCreateGuiHolder(player.getUniqueId());
        var inventory = ArtifactCreateGuiBuilder.build(holder, session);
        holder.setInventory(inventory);
        player.openInventory(inventory);
        sessions.put(player.getUniqueId(), session);
    }

    public ArtifactCreateSession getSession(Player player) {
        if (player == null) {
            return null;
        }
        return getSession(player.getUniqueId());
    }

    public ArtifactCreateSession getSession(UUID playerUuid) {
        if (playerUuid == null) {
            return null;
        }
        return sessions.get(playerUuid);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        if (!(event.getView().getTopInventory().getHolder() instanceof ArtifactCreateGuiHolder holder)) {
            return;
        }
        event.setCancelled(true);

        int rawSlot = event.getRawSlot();
        if (rawSlot >= event.getView().getTopInventory().getSize()) {
            return;
        }

        ArtifactCreateSession session = getSession(player);
        if (session == null) {
            return;
        }

        List<Integer> raritySlots = ArtifactCreateCache.raritySlots;
        List<ArtifactRarityDef> rarities = ArtifactRarityRegistry.getAll();
        for (int i = 0; i < raritySlots.size() && i < rarities.size(); i++) {
            if (rawSlot != raritySlots.get(i)) {
                continue;
            }
            String nextId = rarities.get(i).getId();
            String previous = session.getRarityId();
            if (nextId == null || nextId.equals(previous)) {
                return;
            }
            session.setRarityId(nextId);
            ArtifactCreateGuiBuilder.populate(holder.getInventory(), session);
            return;
        }

        for (ElementDef element : ElementRegistry.getAll()) {
            if (element.getSlot() < 0 || rawSlot != element.getSlot()) {
                continue;
            }
            session.cycleElement(element.getId());
            ArtifactCreateGuiBuilder.populate(holder.getInventory(), session);
            return;
        }

        if (rawSlot == ArtifactCreateCache.capMinusSlot || rawSlot == ArtifactCreateCache.capPlusSlot) {
            double step = event.isShiftClick() ? 5.0 : 1.0;
            if (rawSlot == ArtifactCreateCache.capMinusSlot) {
                step = -step;
            }
            if (session.adjustSelectedCap(step)) {
                ArtifactCreateGuiBuilder.populate(holder.getInventory(), session);
            }
            return;
        }

        if (rawSlot == ArtifactCreateCache.cancelSlot) {
            player.closeInventory();
            return;
        }

        if (rawSlot == ArtifactCreateCache.confirmSlot) {
            confirm(player, session);
        }
    }

    private static void confirm(Player player, ArtifactCreateSession session) {
        ArtifactItemBuilder builder = new ArtifactItemBuilder();
        session.ensurePreviewLock(builder);
        ArtifactRoll roll = session.toRoll();
        if (roll == null) {
            player.sendMessage(Messages.get("artifact.create.need_primary"));
            return;
        }
        ItemStack built = builder.build(
                roll, session.getPreviewBaseName(), session.getPreviewModelPath(), true);
        if (built == null || built.getType().isAir()) {
            player.sendMessage(Messages.get("artifact.create.failed"));
            return;
        }
        ItemStack stack = built.clone();
        stack.setAmount(1);
        giveOrDrop(player, stack);
        player.sendMessage(Messages.get("artifact.create.ok", "player", player.getName()));
        player.closeInventory();
    }

    private static void giveOrDrop(Player player, ItemStack stack) {
        Map<Integer, ItemStack> leftover = player.getInventory().addItem(stack);
        if (leftover.isEmpty() || player.getWorld() == null) {
            return;
        }
        for (ItemStack extra : leftover.values()) {
            if (extra != null && !extra.getType().isAir()) {
                player.getWorld().dropItemNaturally(player.getLocation(), extra);
            }
        }
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof ArtifactCreateGuiHolder)) {
            return;
        }
        event.setCancelled(true);
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) {
            return;
        }
        if (!(event.getView().getTopInventory().getHolder() instanceof ArtifactCreateGuiHolder)) {
            return;
        }
        sessions.remove(player.getUniqueId());
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        sessions.remove(event.getPlayer().getUniqueId());
    }
}
