package net.tfminecraft.magic.gui;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import net.tfminecraft.RPCharacters.Objects.RPCharacter;
import net.tfminecraft.RPCharacters.api.CharacterSkull;
import net.tfminecraft.magic.GuiCache;
import net.tfminecraft.magic.integration.RpCharactersBridge;
import net.tfminecraft.magic.model.CastModeDef;
import net.tfminecraft.magic.model.ElementDef;
import net.tfminecraft.magic.registry.ElementRegistry;
import net.tfminecraft.magic.session.ResonanceSession;
import net.tfminecraft.magic.util.EquilibriumBar;
import net.tfminecraft.magic.util.GridLayout;
import net.tfminecraft.magic.util.GuiText;
import net.tfminecraft.magic.util.ItemRef;
import net.tfminecraft.magic.util.ResonanceBar;
import net.tfminecraft.tfmccore.TFMCCore;
import net.tfminecraft.tfmccore.focus.FocusConfig;

public final class ResonanceGuiBuilder {

    private ResonanceGuiBuilder() {}

    public static Inventory build(Player player, ResonanceGuiHolder holder, ResonanceSession session) {
        String title = GuiText.format(GuiCache.title);
        Inventory inventory = Bukkit.createInventory(holder, GridLayout.SIZE, title);
        populate(inventory, player, session);
        return inventory;
    }

    public static void populate(Inventory inventory, Player player, ResonanceSession session) {
        String castModeId = normalizeCastModeId(session.getCastModeId());

        ItemStack innerFiller = ItemRef.buildOrFallback(GuiCache.filler, Material.PURPLE_STAINED_GLASS_PANE);
        ItemRef.applyBlankDisplay(innerFiller);
        ItemStack borderFiller = ItemRef.buildOrFallback(GuiCache.fillerBorder, Material.BLACK_STAINED_GLASS_PANE);
        ItemRef.applyBlankDisplay(borderFiller);

        for (int slotIndex : GridLayout.borderFillerSlots()) {
            inventory.setItem(slotIndex, borderFiller.clone());
        }
        for (int slotIndex : GridLayout.innerFillerSlots()) {
            inventory.setItem(slotIndex, innerFiller.clone());
        }

        inventory.setItem(GridLayout.characterHeadSlot(), buildCharacterHead(player, session));
        inventory.setItem(
                GridLayout.castModeLeftSlot(),
                buildCastModeItem(GuiCache.castModeLeft, castModeId.equals(GuiCache.castModeLeft.getId()), session));
        inventory.setItem(
                GridLayout.castModeRightSlot(),
                buildCastModeItem(GuiCache.castModeRight, castModeId.equals(GuiCache.castModeRight.getId()), session));

        for (ElementDef element : ElementRegistry.getAll()) {
            if (element.getSlot() >= 0 && element.isUnlocked(player)) {
                inventory.setItem(element.getSlot(), buildElementItem(element, session));
            }
        }
    }

    private static String normalizeCastModeId(String castModeId) {
        if (castModeId == null || castModeId.isBlank()) {
            return GuiCache.castModeLeft.getId();
        }
        String normalized = castModeId.toLowerCase(Locale.ROOT);
        if (normalized.equals(GuiCache.castModeLeft.getId())) {
            return GuiCache.castModeLeft.getId();
        }
        if (normalized.equals(GuiCache.castModeRight.getId())) {
            return GuiCache.castModeRight.getId();
        }
        return GuiCache.castModeLeft.getId();
    }

    @SuppressWarnings("deprecation")
    private static ItemStack buildCharacterHead(Player player, ResonanceSession session) {
        RPCharacter character = RpCharactersBridge.getActiveCharacter(player);
        ItemStack head = character != null
                ? character.getSkull()
                : CharacterSkull.ofOwner(player);

        SkullMeta meta = (SkullMeta) head.getItemMeta();
        if (meta == null) {
            return head;
        }

        if (character == null) {
            meta.setDisplayName(GuiText.text("label_accent", "Resonance"));
        } else {
            String displayTab = RpCharactersBridge.resolveDisplayTab(player);
            meta.setDisplayName(displayTab != null && !displayTab.isBlank()
                    ? displayTab
                    : GuiText.text("label_accent", character.getName()));
        }
        meta.setLore(buildHeadLore(player, session));
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ADDITIONAL_TOOLTIP);
        head.setItemMeta(meta);
        return head;
    }

    private static List<String> buildHeadLore(Player player, ResonanceSession session) {
        String castingMode = formatCastModeLabel(session.getCastModeId());
        List<String> lore = new ArrayList<>();
        lore.add(GuiText.format("{color:label_muted}Casting: {color:label_body}" + castingMode));
        lore.add(formatFocusLore(player));
        lore.add(EquilibriumBar.formatLore(session.getEquilibrium()));
        String driftLore = EquilibriumBar.formatDriftLore(session.getEquilibrium());
        if (driftLore != null) {
            lore.add(driftLore);
        }
        return lore;
    }

    private static String formatFocusLore(Player player) {
        int current = 0;
        int max = 0;
        var focus = TFMCCore.getFocusService();
        if (focus != null) {
            current = focus.getPoints(player);
            max = FocusConfig.max;
        }
        String line = GuiCache.focusLore
                .replace("{current}", String.valueOf(current))
                .replace("{max}", String.valueOf(max));
        return GuiText.format(line);
    }

    private static String formatCastModeLabel(String modeId) {
        if (modeId == null || modeId.isBlank()) {
            return "Surge";
        }
        if (modeId.length() == 1) {
            return modeId.toUpperCase(Locale.ROOT);
        }
        return modeId.substring(0, 1).toUpperCase(Locale.ROOT) + modeId.substring(1).toLowerCase(Locale.ROOT);
    }

    private static ItemStack buildCastModeItem(CastModeDef mode, boolean selected, ResonanceSession session) {
        ItemStack item = ItemRef.buildOrFallback(mode.getIcon(), Material.BARRIER);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return item;
        }
        meta.setDisplayName(GuiText.format(mode.getName()));
        List<String> lore = new ArrayList<>(GuiText.formatLoreLines(mode.getLore()));
        if (mode.getId().equalsIgnoreCase(GuiCache.castModeLeft.getId())) {
            lore.addAll(ModifierLore.linesForSurge(session));
        } else if (mode.getId().equalsIgnoreCase(GuiCache.castModeRight.getId())) {
            lore.addAll(ModifierLore.linesForFlow(session));
        }
        if (selected) {
            lore.add(GuiText.format("{color:resonance_high}§lSelected"));
            meta.addEnchant(Enchantment.UNBREAKING, 1, true);
        } else {
            lore.add(GuiText.text("label_muted", "Click to Select"));
        }
        meta.setLore(lore);
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ADDITIONAL_TOOLTIP, ItemFlag.HIDE_ENCHANTS);
        item.setItemMeta(meta);
        return item;
    }

    private static ItemStack buildElementItem(ElementDef element, ResonanceSession session) {
        ItemStack item = ItemRef.buildOrFallback(element.getIcon(), Material.BARRIER);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return item;
        }
        meta.setDisplayName(element.getColoredName());
        List<String> lore = new ArrayList<>();
        double resonance = session != null ? session.getResonance(element.getId()) : 0.0;
        lore.add(ResonanceBar.formatLore(element, resonance));
        String driftLore = ResonanceBar.formatDriftLore(element, session);
        if (driftLore != null) {
            lore.add(driftLore);
        }
        lore.addAll(ModifierLore.linesForElement(element, session));
        meta.setLore(lore);
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ADDITIONAL_TOOLTIP);
        item.setItemMeta(meta);
        return item;
    }
}
