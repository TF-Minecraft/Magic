package net.tfminecraft.magic.gui;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import net.tfminecraft.magic.ArtifactCreateCache;
import net.tfminecraft.magic.GuiCache;
import net.tfminecraft.magic.artifact.create.ArtifactCreateSession;
import net.tfminecraft.magic.artifact.create.ArtifactCreateSession.ElementRole;
import net.tfminecraft.magic.artifact.generate.ArtifactItemBuilder;
import net.tfminecraft.magic.artifact.generate.ArtifactRoll;
import net.tfminecraft.magic.artifact.config.ArtifactRarityDef;
import net.tfminecraft.magic.artifact.config.ArtifactRarityRegistry;
import net.tfminecraft.magic.artifact.config.ArtifactTypeDef;
import net.tfminecraft.magic.artifact.config.ArtifactTypeRegistry;
import net.tfminecraft.magic.model.CastModeDef;
import net.tfminecraft.magic.model.ElementDef;
import net.tfminecraft.magic.registry.ElementRegistry;
import net.tfminecraft.magic.util.GridLayout;
import net.tfminecraft.magic.util.GuiText;
import net.tfminecraft.magic.util.ItemRef;
import net.tfminecraft.magic.util.MagicNumbers;

public final class ArtifactCreateGuiBuilder {

    private ArtifactCreateGuiBuilder() {}

    public static Inventory build(ArtifactCreateGuiHolder holder, ArtifactCreateSession session) {
        Inventory inventory = Bukkit.createInventory(
                holder, GridLayout.SIZE, GuiText.format(ArtifactCreateCache.title));
        populate(inventory, session);
        return inventory;
    }

    public static void populate(Inventory inventory, ArtifactCreateSession session) {
        ItemStack innerFiller = ItemRef.buildOrFallback(GuiCache.filler, Material.PURPLE_STAINED_GLASS_PANE);
        ItemRef.applyBlankDisplay(innerFiller);
        ItemStack borderFiller = ItemRef.buildOrFallback(GuiCache.fillerBorder, Material.BLACK_STAINED_GLASS_PANE);
        ItemRef.applyBlankDisplay(borderFiller);

        Set<Integer> reserved = reservedSlots();
        for (int slotIndex = 0; slotIndex < GridLayout.SIZE; slotIndex++) {
            if (reserved.contains(slotIndex)) {
                continue;
            }
            ItemStack filler = GridLayout.isBorderSlot(slotIndex) ? borderFiller : innerFiller;
            inventory.setItem(slotIndex, filler.clone());
        }

        inventory.setItem(ArtifactCreateCache.previewSlot, buildPreview(session));
        inventory.setItem(ArtifactCreateCache.capMinusSlot, buildButton(ArtifactCreateCache.capMinus));
        inventory.setItem(ArtifactCreateCache.capPlusSlot, buildButton(ArtifactCreateCache.capPlus));
        inventory.setItem(ArtifactCreateCache.confirmSlot, buildButton(ArtifactCreateCache.confirm));
        inventory.setItem(ArtifactCreateCache.cancelSlot, buildButton(ArtifactCreateCache.cancel));

        List<Integer> raritySlots = ArtifactCreateCache.raritySlots;
        List<ArtifactRarityDef> rarities = ArtifactRarityRegistry.getAll();
        String selectedRarityId = session != null ? session.getRarityId() : "";
        for (int i = 0; i < raritySlots.size(); i++) {
            int slot = raritySlots.get(i);
            if (i < rarities.size()) {
                ArtifactRarityDef rarity = rarities.get(i);
                boolean selected = rarity.getId() != null && rarity.getId().equals(selectedRarityId);
                inventory.setItem(slot, buildRarityItem(rarity, selected));
            } else {
                inventory.setItem(slot, buildUnusedRarity());
            }
        }

        for (ElementDef element : ElementRegistry.getAll()) {
            if (element.getSlot() < 0) {
                continue;
            }
            ArtifactTypeDef type = ArtifactTypeRegistry.getById(element.getId());
            if (type == null || !type.isEnabled()) {
                continue;
            }
            inventory.setItem(element.getSlot(), buildElementItem(element, session));
        }
    }

    private static Set<Integer> reservedSlots() {
        Set<Integer> reserved = new HashSet<>(ArtifactCreateCache.reservedSlots());
        for (ElementDef element : ElementRegistry.getAll()) {
            if (element.getSlot() >= 0) {
                reserved.add(element.getSlot());
            }
        }
        return reserved;
    }

    private static ItemStack buildPreview(ArtifactCreateSession session) {
        if (session != null) {
            ArtifactRoll roll = session.toRoll();
            if (roll != null) {
                ArtifactItemBuilder builder = new ArtifactItemBuilder();
                session.ensurePreviewLock(builder);
                ItemStack built = builder.build(roll, session.getPreviewBaseName(), session.getPreviewModelPath());
                if (built != null && !built.getType().isAir()) {
                    ItemStack clone = built.clone();
                    clone.setAmount(1);
                    return clone;
                }
            }
        }
        ItemStack item = new ItemStack(Material.BARRIER);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(GuiText.format("{color:label_muted}Preview"));
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ADDITIONAL_TOOLTIP);
            item.setItemMeta(meta);
        }
        return item;
    }

    private static ItemStack buildRarityItem(ArtifactRarityDef rarity, boolean selected) {
        String iconRef = selected ? GuiCache.castModeSelected : GuiCache.castModeUnselected;
        Material fallback = selected ? Material.LIME_STAINED_GLASS_PANE : Material.GRAY_STAINED_GLASS_PANE;
        ItemStack item = ItemRef.buildOrFallback(iconRef, fallback);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return item;
        }
        String color = rarity.getColor() != null && !rarity.getColor().isBlank() ? rarity.getColor() : "#ffffff";
        meta.setDisplayName(GuiText.format(color + rarity.getName()));
        if (selected) {
            meta.setLore(List.of(GuiText.text("label_accent", "Selected")));
            meta.addEnchant(Enchantment.UNBREAKING, 1, true);
        }
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ADDITIONAL_TOOLTIP, ItemFlag.HIDE_ENCHANTS);
        item.setItemMeta(meta);
        return item;
    }

    private static ItemStack buildUnusedRarity() {
        ItemStack item = ItemRef.buildOrFallback(GuiCache.castModeUnselected, Material.GRAY_STAINED_GLASS_PANE);
        ItemRef.applyBlankDisplay(item);
        return item;
    }

    private static ItemStack buildElementItem(ElementDef element, ArtifactCreateSession session) {
        ItemStack item = ItemRef.buildOrFallback(element.getIcon(), Material.BARRIER);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return item;
        }
        meta.setDisplayName(element.getColoredName());
        ElementRole role = session != null ? session.roleOf(element.getId()) : ElementRole.OFF;
        List<String> lore = new ArrayList<>();
        if (role != ElementRole.OFF) {
            lore.add(GuiText.format("{color:label_body}" + MagicNumbers.format(session.getCap(element.getId()))));
        }
        String roleLabel = switch (role) {
            case PRIMARY -> "Primary";
            case SECONDARY -> "Secondary";
            case OFF -> "Off";
        };
        lore.add(GuiText.text("label_muted", roleLabel));
        boolean selected = session != null
                && element.getId() != null
                && element.getId().equals(session.getSelectedElementId());
        if (selected) {
            lore.add(GuiText.text("label_accent", "Selected"));
            meta.addEnchant(Enchantment.UNBREAKING, 1, true);
        }
        meta.setLore(lore);
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ADDITIONAL_TOOLTIP, ItemFlag.HIDE_ENCHANTS);
        item.setItemMeta(meta);
        return item;
    }

    private static ItemStack buildButton(CastModeDef def) {
        ItemStack item = ItemRef.buildOrFallback(def.getIcon(), Material.BARRIER);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return item;
        }
        meta.setDisplayName(GuiText.format(def.getName()));
        meta.setLore(new ArrayList<>(GuiText.formatLoreLines(def.getLore())));
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ADDITIONAL_TOOLTIP, ItemFlag.HIDE_ENCHANTS);
        item.setItemMeta(meta);
        return item;
    }
}
