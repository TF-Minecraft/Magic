package net.tfminecraft.magic.gear;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.Bukkit;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import net.Indyuce.mmoitems.MMOItems;
import net.tfminecraft.magic.Magic;

/**
 * Marks a crafted weapon as damaged, either because a stamped part id no longer exists
 * or because a revision left a socketed rune with nowhere to sit. Orphaned runes are
 * held on the item until the player reclaims them at a station, never dropped.
 */
public final class GearBrokenMarker {

    private static final Set<String> WARNED = ConcurrentHashMap.newKeySet();

    private GearBrokenMarker() {}

    public record Orphan(String type, String id) {

        @Override
        public String toString() {
            return type + ":" + id;
        }
    }

    public static boolean isBroken(ItemStack stack) {
        if (stack == null || !stack.hasItemMeta()) {
            return false;
        }
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) {
            return false;
        }
        Byte broken = meta.getPersistentDataContainer().get(GearKeys.broken(), PersistentDataType.BYTE);
        return broken != null && broken != 0;
    }

    /** Flags the item and re-renders lore. Existing orphans are kept. */
    public static void mark(ItemStack stack) {
        if (stack == null || !stack.hasItemMeta()) {
            return;
        }
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) {
            return;
        }
        meta.getPersistentDataContainer().set(GearKeys.broken(), PersistentDataType.BYTE, (byte) 1);
        stack.setItemMeta(meta);
        WeaponLore.apply(stack);
    }

    public static void clear(ItemStack stack) {
        if (stack == null || !stack.hasItemMeta()) {
            return;
        }
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) {
            return;
        }
        meta.getPersistentDataContainer().remove(GearKeys.broken());
        meta.getPersistentDataContainer().remove(GearKeys.orphans());
        stack.setItemMeta(meta);
    }

    public static List<Orphan> orphans(ItemStack stack) {
        List<Orphan> orphans = new ArrayList<>();
        if (stack == null || !stack.hasItemMeta()) {
            return orphans;
        }
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) {
            return orphans;
        }
        String raw = meta.getPersistentDataContainer().get(GearKeys.orphans(), PersistentDataType.STRING);
        if (raw == null || raw.isBlank()) {
            return orphans;
        }
        for (String token : raw.split(",")) {
            int colon = token.indexOf(':');
            if (colon <= 0 || colon >= token.length() - 1) {
                continue;
            }
            orphans.add(new Orphan(token.substring(0, colon).trim(), token.substring(colon + 1).trim()));
        }
        return orphans;
    }

    /** Appends to whatever is already held, so repeated refreshes never lose a rune. */
    public static void addOrphans(ItemStack stack, List<Orphan> added) {
        if (stack == null || added == null || added.isEmpty() || !stack.hasItemMeta()) {
            return;
        }
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) {
            return;
        }
        List<Orphan> all = new ArrayList<>(orphans(stack));
        all.addAll(added);
        List<String> tokens = new ArrayList<>();
        for (Orphan orphan : all) {
            tokens.add(orphan.toString());
        }
        meta.getPersistentDataContainer().set(
                GearKeys.orphans(), PersistentDataType.STRING, String.join(",", tokens));
        stack.setItemMeta(meta);
    }

    /** Rebuilds the rune item stacks, dropping entries MMOItems can no longer resolve. */
    public static List<ItemStack> buildOrphanItems(ItemStack stack) {
        List<ItemStack> items = new ArrayList<>();
        if (!Bukkit.getPluginManager().isPluginEnabled("MMOItems")) {
            return items;
        }
        for (Orphan orphan : orphans(stack)) {
            try {
                ItemStack rune = MMOItems.plugin.getItem(orphan.type(), orphan.id());
                if (rune != null && !rune.getType().isAir()) {
                    items.add(rune);
                    continue;
                }
            } catch (Exception ignored) {
                // fall through to the warning below
            }
            Magic.plugin.getLogger().warning("[Magic] Cannot rebuild orphaned rune "
                    + orphan.type() + ":" + orphan.id() + "; it is no longer an MMOItem");
        }
        return items;
    }

    /**
     * Console log plus one chat line per weapon per server session, so a player carrying
     * a broken weapon is told once rather than every interaction.
     */
    public static void notifyOnce(org.bukkit.entity.Player player, ItemStack stack, List<String> missingIds) {
        String fingerprint = GearProvenance.partsRaw(stack);
        if (!WARNED.add(fingerprint)) {
            return;
        }
        String missing = missingIds == null || missingIds.isEmpty() ? "none" : String.join(", ", missingIds);
        Magic.plugin.getLogger().warning("[Magic] Damaged mage weapon (" + fingerprint
                + "), missing parts: " + missing
                + (player != null ? " (holder: " + player.getName() + ")" : ""));
    }

    public static void clearWarningSession() {
        WARNED.clear();
    }
}
