package net.tfminecraft.magic.listener;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;

import net.tfminecraft.events.FurnitureBreakEvent;
import net.tfminecraft.events.FurnitureSlotItemAddEvent;
import net.tfminecraft.events.FurnitureSlotItemTakeEvent;
import net.tfminecraft.furniture.Furniture;
import net.tfminecraft.magic.Magic;
import net.tfminecraft.magic.artifact.aura.AuraVessels;
import net.tfminecraft.magic.artifact.ArtifactCarePlaces;
import net.tfminecraft.magic.artifact.ArtifactCareStore;
import net.tfminecraft.magic.artifact.ArtifactIds;
import net.tfminecraft.magic.artifact.aura.VesselLore;
import net.tfminecraft.magic.artifact.sacrifice.SacrificeRiteService;
import net.tfminecraft.magic.artifact.shrine.ShrineChargeService;
import net.tfminecraft.magic.attunement.ArtifactDisplayIndex;
import net.tfminecraft.magic.meditation.MeditationCache;
import net.tfminecraft.magic.meditation.MeditationService;

public final class ArtifactListener implements Listener {

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onCareClick(InventoryClickEvent event) {
        Inventory clicked = event.getClickedInventory();
        applyCare(event.getCurrentItem(), clicked);
        applyCare(event.getCursor(), clicked);
        int hotbar = event.getHotbarButton();
        if (hotbar >= 0 && event.getWhoClicked() instanceof Player player) {
            applyCare(player.getInventory().getItem(hotbar), player.getInventory());
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onCareDrag(InventoryDragEvent event) {
        InventoryView view = event.getView();
        for (var entry : event.getNewItems().entrySet()) {
            applyCare(entry.getValue(), inventoryFor(view, entry.getKey()));
        }
        Inventory cursorPlace = event.getWhoClicked() instanceof Player player
                ? player.getInventory()
                : null;
        applyCare(event.getCursor(), cursorPlace);
    }

    private static Inventory inventoryFor(InventoryView view, int rawSlot) {
        if (view == null) {
            return null;
        }
        return view.getInventory(rawSlot);
    }

    private static void applyCare(ItemStack stack, Inventory inventory) {
        if (!ArtifactIds.hasKey(stack)) {
            return;
        }
        ArtifactCareStore.apply(
                stack,
                ArtifactCarePlaces.accepted(inventory, stack),
                System.currentTimeMillis(),
                ArtifactCareStore.Persist.ALWAYS);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onFurnitureSlotAdd(FurnitureSlotItemAddEvent event) {
        Furniture furniture = event.getFurniture();
        if (furniture == null || event.getSlot() == null) {
            return;
        }
        ItemStack item = event.getItem();
        if (ArtifactDisplayIndex.isDisplayFurniture(furniture)) {
            ArtifactDisplayIndex.add(item);
        }
        if (!MeditationCache.pedestalId.equalsIgnoreCase(furniture.getId())) {
            return;
        }
        if (AuraVessels.fromItem(item) == null) {
            return;
        }
        String slotId = event.getSlot().getId();
        Player player = event.getPlayer();
        Bukkit.getScheduler().runTask(Magic.plugin, () -> ShrineChargeService.tryStart(player, furniture, slotId));
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onFurnitureSlotTake(FurnitureSlotItemTakeEvent event) {
        if (MeditationService.locksFurniture(event.getFurniture())) {
            event.setCancelled(true);
            return;
        }
        if (event.getFurniture() != null && event.getSlot() != null) {
            ShrineChargeService.stop(event.getFurniture().getEntityId(), event.getSlot().getId());
            SacrificeRiteService.stop(event.getFurniture().getEntityId(), event.getSlot().getId());
        }
        event.setItem(VesselLore.updateItem(event.getItem()));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onFurnitureSlotTakeDisplay(FurnitureSlotItemTakeEvent event) {
        if (ArtifactDisplayIndex.isDisplayFurniture(event.getFurniture())) {
            ArtifactDisplayIndex.remove(event.getItem());
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onFurnitureBreakLock(FurnitureBreakEvent event) {
        if (MeditationService.locksFurniture(event.getFurniture())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onFurnitureBreak(FurnitureBreakEvent event) {
        if (event.getFurniture() != null) {
            ArtifactDisplayIndex.removeFurniture(event.getFurniture());
            ShrineChargeService.stopAll(event.getFurniture().getEntityId());
            SacrificeRiteService.stopAll(event.getFurniture().getEntityId());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onChunkLoad(ChunkLoadEvent event) {
        ArtifactDisplayIndex.ensureChunk(event.getChunk());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPickup(EntityPickupItemEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        Bukkit.getScheduler().runTask(Magic.plugin, () -> VesselLore.updateInventory(player));
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        VesselLore.updateInventory(event.getPlayer());
    }
}
