package net.tfminecraft.magic.gear.gui;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import net.tfminecraft.magic.Messages;
import net.tfminecraft.magic.gear.ArchetypeDef;
import net.tfminecraft.magic.gear.ArchetypeRegistry;
import net.tfminecraft.magic.gear.GearCache;
import net.tfminecraft.magic.gear.GearCosts;
import net.tfminecraft.magic.gear.GearItemBuilder;
import net.tfminecraft.magic.gear.GearKeys;
import net.tfminecraft.magic.gear.GearStationStore;
import net.tfminecraft.magic.gear.GearType;
import net.tfminecraft.magic.gear.PartDef;
import net.tfminecraft.magic.gear.PartRegistry;
import net.tfminecraft.magic.gear.PartSlots;
import net.tfminecraft.magic.gear.PartTypeDef;
import net.tfminecraft.magic.gear.PartTypeRegistry;
import net.tfminecraft.magic.gear.SocketLayout;
import net.tfminecraft.magic.util.CostFormatter;
import net.tfminecraft.magic.util.ItemRef;

public final class GearInventoryManager implements Listener {

    // Keep the existing legacy text representation, formatting, and exact-string comparisons.
    @SuppressWarnings("deprecation")
    public void openAssembly(Player player) {
        Inventory inv = Bukkit.createInventory(new AssemblyHolder(), 27, "§6Mage Assembly");
        ItemStack filler = pane();
        for (int i = 0; i < inv.getSize(); i++) {
            inv.setItem(i, filler);
        }
        GearType type = TypeSelectionManager.get(player);
        ArchetypeDef archetype = ArchetypeRegistry.get(type);
        PartDef core = selectedOrFirst(player, PartSlots.CORE, type);
        List<String> open = PartSlots.open(archetype, core);
        inv.setItem(0, typeButton(type));
        Collection<PartDef> parts = collectParts(player, type);
        for (String categoryId : open) {
            PartTypeDef category = PartTypeRegistry.get(categoryId);
            if (category == null) {
                continue;
            }
            int slot = category.getSlot();
            if (slot <= 0 || slot >= inv.getSize()) {
                continue;
            }
            PartDef part = selectedOrFirst(player, categoryId, type);
            if (part == null) {
                inv.setItem(slot, barrier("No part"));
            } else {
                inv.setItem(slot, partIcon(part, false));
            }
        }
        inv.setItem(GearCache.outputSlot, GearItemBuilder.preview(type, parts));
        player.openInventory(inv);
    }

    // Keep the existing legacy text representation, formatting, and exact-string comparisons.
    @SuppressWarnings("deprecation")
    public void openTypeSelection(Player player) {
        Inventory inv = Bukkit.createInventory(new TypeSelectionHolder(), 9, "§6Select Archetype");
        int slot = 0;
        for (GearType type : GearType.values()) {
            ArchetypeDef def = ArchetypeRegistry.get(type);
            ItemStack item = archetypeIcon(type, def);
            ItemMeta meta = item.getItemMeta();
            if (meta != null) {
                meta.setDisplayName("§e" + type.getDisplayName());
                List<String> lore = new ArrayList<>();
                lore.add("§7Click to choose");
                if (def != null && def.isMelee()) {
                    lore.add("§8Melee weapon");
                }
                meta.setLore(lore);
                item.setItemMeta(meta);
            }
            inv.setItem(slot++, item);
        }
        ItemStack back = new ItemStack(Material.ARROW);
        ItemMeta meta = back.getItemMeta();
        if (meta != null) {
            meta.setDisplayName("§eBack");
            back.setItemMeta(meta);
        }
        inv.setItem(8, back);
        player.openInventory(inv);
    }

    // Keep the existing legacy text representation, formatting, and exact-string comparisons.
    @SuppressWarnings("deprecation")
    public void openPartSelection(Player player, String categoryId) {
        GearType type = TypeSelectionManager.get(player);
        ArchetypeDef archetype = ArchetypeRegistry.get(type);
        PartDef core = selectedOrFirst(player, PartSlots.CORE, type);
        if (!PartSlots.contains(PartSlots.open(archetype, core), categoryId)) {
            openAssembly(player);
            return;
        }
        List<PartDef> options = PartRegistry.matching(categoryId, type);
        int size = Math.max(9, Math.min(54, ((options.size() + 8) / 9) * 9));
        Inventory inv = Bukkit.createInventory(
                new PartSelectionHolder(categoryId), size, "§6Select " + partTypeName(categoryId));
        if (options.isEmpty()) {
            inv.setItem(size / 2, barrier("No part"));
        } else {
            for (PartDef part : options) {
                inv.addItem(partIcon(part, true));
            }
        }
        ItemStack back = new ItemStack(Material.ARROW);
        ItemMeta meta = back.getItemMeta();
        if (meta != null) {
            meta.setDisplayName("§eBack");
            back.setItemMeta(meta);
        }
        inv.setItem(size - 1, back);
        player.openInventory(inv);
    }

    public Collection<PartDef> collectParts(Player player, GearType type) {
        List<PartDef> parts = new ArrayList<>();
        ArchetypeDef archetype = ArchetypeRegistry.get(type);
        if (archetype == null) {
            return parts;
        }
        PartDef core = selectedOrFirst(player, PartSlots.CORE, type);
        for (String category : PartSlots.open(archetype, core)) {
            PartDef part = selectedOrFirst(player, category, type);
            if (part != null) {
                parts.add(part);
            }
        }
        return parts;
    }

    private PartDef selectedOrFirst(Player player, String categoryId, GearType type) {
        String selected = SelectedPartsManager.get(player, categoryId);
        if (selected != null) {
            PartDef def = PartRegistry.get(selected);
            if (def != null && !def.isDisabled() && def.supports(type)
                    && def.getPartType().equalsIgnoreCase(categoryId)) {
                return def;
            }
        }
        return PartRegistry.firstMatching(categoryId, type);
    }

    private void pruneClosedSelections(Player player) {
        GearType type = TypeSelectionManager.get(player);
        List<String> open = PartSlots.open(
                ArchetypeRegistry.get(type),
                selectedOrFirst(player, PartSlots.CORE, type));
        for (String category : SelectedPartsManager.categories(player)) {
            if (!PartSlots.contains(open, category)) {
                SelectedPartsManager.remove(player, category);
            }
        }
    }

    // Keep the existing legacy text representation, formatting, and exact-string comparisons.
    @SuppressWarnings("deprecation")
    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        if (event.getInventory().getHolder() instanceof AssemblyHolder) {
            event.setCancelled(true);
            ItemStack current = event.getCurrentItem();
            if (current == null || current.getType() == Material.GRAY_STAINED_GLASS_PANE) {
                return;
            }
            if (event.getSlot() == GearCache.outputSlot) {
                tryPrepare(player);
                return;
            }
            if (current.getType() == Material.BARRIER) {
                return;
            }
            if (event.getSlot() == 0) {
                openTypeSelection(player);
                clickSound(player);
                return;
            }
            String category = PartTypeRegistry.idForSlot(event.getSlot());
            if (category != null) {
                GearType type = TypeSelectionManager.get(player);
                PartDef core = selectedOrFirst(player, PartSlots.CORE, type);
                if (!PartSlots.contains(PartSlots.open(ArchetypeRegistry.get(type), core), category)) {
                    return;
                }
                openPartSelection(player, category);
                clickSound(player);
            }
            return;
        }
        if (event.getInventory().getHolder() instanceof TypeSelectionHolder) {
            event.setCancelled(true);
            ItemStack clicked = event.getCurrentItem();
            if (clicked == null || !clicked.hasItemMeta()) {
                return;
            }
            String name = clicked.getItemMeta().getDisplayName();
            if ("§eBack".equals(name)) {
                openAssembly(player);
                clickSound(player);
                return;
            }
            String clean = name == null ? "" : name.replace("§e", "").trim();
            for (GearType type : GearType.values()) {
                if (type.getDisplayName().equalsIgnoreCase(clean)) {
                    TypeSelectionManager.set(player, type);
                    SelectedPartsManager.clear(player);
                    openAssembly(player);
                    clickSound(player);
                    return;
                }
            }
            return;
        }
        if (event.getInventory().getHolder() instanceof PartSelectionHolder holder) {
            event.setCancelled(true);
            ItemStack clicked = event.getCurrentItem();
            if (clicked == null || !clicked.hasItemMeta()) {
                return;
            }
            if ("§eBack".equals(clicked.getItemMeta().getDisplayName())) {
                openAssembly(player);
                clickSound(player);
                return;
            }
            String partId = clicked.getItemMeta().getPersistentDataContainer().get(
                    GearKeys.partPick(), PersistentDataType.STRING);
            if (partId == null) {
                return;
            }
            SelectedPartsManager.set(player, holder.getCategoryId(), partId);
            if (PartSlots.CORE.equalsIgnoreCase(holder.getCategoryId())) {
                pruneClosedSelections(player);
            }
            openAssembly(player);
            clickSound(player);
        }
    }

    private void tryPrepare(Player player) {
        org.bukkit.Location station = OpenStationManager.get(player);
        if (station == null) {
            player.sendMessage(Messages.get("gear.station.gone"));
            return;
        }
        if (GearStationStore.isOccupied(station)) {
            player.sendMessage(Messages.get("gear.station.occupied"));
            return;
        }
        GearType type = TypeSelectionManager.get(player);
        Collection<PartDef> parts = collectParts(player, type);
        ItemStack preview = GearItemBuilder.preview(type, parts);
        if (preview.getType() == Material.BARRIER) {
            player.sendMessage(Messages.get("gear.craft.invalid"));
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
            return;
        }
        if (!GearCosts.has(player, parts)) {
            player.sendMessage(Messages.get("gear.craft.lacking"));
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
            return;
        }
        ItemStack prepared = GearItemBuilder.prepare(type, parts);
        if (prepared == null || prepared.getType() == Material.BARRIER) {
            player.sendMessage(Messages.get("gear.craft.invalid"));
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
            return;
        }
        GearCosts.take(player, parts);
        GearStationStore.occupy(station, prepared);
        OpenStationManager.clear(player);
        player.closeInventory();
        player.sendMessage(Messages.get("gear.craft.prepared"));
        player.playSound(player.getLocation(), Sound.BLOCK_ANVIL_USE, 1f, 1.2f);
    }

    private static void clickSound(Player player) {
        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BIT, 1f, 1f);
    }

    private static String partTypeName(String categoryId) {
        PartTypeDef def = PartTypeRegistry.get(categoryId);
        if (def != null && def.getName() != null && !def.getName().isBlank()) {
            return def.getName();
        }
        return SocketLayout.prettyId(categoryId);
    }

    // Keep the existing legacy text representation, formatting, and exact-string comparisons.
    @SuppressWarnings("deprecation")
    private static ItemStack pane() {
        ItemStack item = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName("§o");
            item.setItemMeta(meta);
        }
        return item;
    }

    // Keep the existing legacy text representation, formatting, and exact-string comparisons.
    @SuppressWarnings("deprecation")
    private static ItemStack barrier(String name) {
        ItemStack item = new ItemStack(Material.BARRIER);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName("§c" + name);
            item.setItemMeta(meta);
        }
        return item;
    }

    private static ItemStack archetypeIcon(GearType type, ArchetypeDef def) {
        if (def == null) {
            return new ItemStack(type.getIcon());
        }
        return ItemRef.buildOrFallback(def.getIcon(), type.getIcon());
    }

    // Keep the existing legacy text representation, formatting, and exact-string comparisons.
    @SuppressWarnings("deprecation")
    private static ItemStack typeButton(GearType type) {
        ItemStack item = archetypeIcon(type, ArchetypeRegistry.get(type));
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName("§6Archetype: §e" + type.getDisplayName());
            meta.setLore(List.of("§7Click to change"));
            item.setItemMeta(meta);
        }
        return item;
    }

    // Keep the existing legacy text representation, formatting, and exact-string comparisons.
    @SuppressWarnings("deprecation")
    private static ItemStack partIcon(PartDef part, boolean picker) {
        ItemStack item = ItemRef.buildOrFallback(part.getItemPath(), Material.PAPER);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return item;
        }
        meta.setDisplayName(part.getName());
        List<String> lore = new ArrayList<>(part.getLore());
        if (!part.getSockets().isEmpty()) {
            lore.add("§8Sockets");
            for (var entry : part.getSockets().entrySet()) {
                lore.add("§7- " + SocketLayout.label(entry.getKey()) + " §fx" + entry.getValue());
            }
        }
        if (part.hasCost()) {
            CostFormatter.appendInput(lore, part.getCost());
        }
        if (picker) {
            lore.add("§eClick to select");
            meta.getPersistentDataContainer().set(
                    GearKeys.partPick(), PersistentDataType.STRING, part.getId());
        } else {
            lore.add("§7Click to change");
        }
        meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
    }
}
