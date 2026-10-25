package net.tfminecraft.magic.blood;

import java.util.ArrayList;
import java.util.List;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import net.tfminecraft.magic.Magic;
import net.tfminecraft.magic.util.ItemRef;
import net.tfminecraft.magic.util.MagicText;

/** Builds and reads the bound blood items. The kind and owner live in the item's PDC. */
public final class BloodItems {

    private BloodItems() {}

    public static NamespacedKey kindKey() {
        return new NamespacedKey(Magic.plugin, "blood_item");
    }

    public static NamespacedKey ownerKey() {
        return new NamespacedKey(Magic.plugin, "blood_owner");
    }

    /** A new item of this kind bound to the character. */
    @SuppressWarnings("deprecation")
    public static ItemStack create(BloodItemKind kind, String characterId) {
        BloodConfig.ItemDef def = BloodConfig.item(kind);
        ItemStack item = ItemRef.buildOrFallback(def.path(), Material.matchMaterial(kind.fallbackPath().substring(2)));
        item.setAmount(1);
        ItemMeta meta = item.getItemMeta();
        if (!def.name().isBlank()) {
            meta.setDisplayName(MagicText.format(def.name()));
        }
        if (!def.lore().isEmpty()) {
            List<String> lore = new ArrayList<>();
            for (String line : def.lore()) {
                lore.add(MagicText.format(line));
            }
            meta.setLore(lore);
        }
        if (kind != BloodItemKind.ALTAR) {
            meta.setUnbreakable(true);
        }
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        pdc.set(kindKey(), PersistentDataType.STRING, kind.key());
        pdc.set(ownerKey(), PersistentDataType.STRING, characterId);
        item.setItemMeta(meta);
        BloodLooks.protect(item);
        return item;
    }

    /** The blood item kind, or null for any other item. */
    public static BloodItemKind kind(ItemStack item) {
        String raw = read(item, kindKey());
        return raw != null ? BloodItemKind.parse(raw) : null;
    }

    /** The character the item is bound to, or null. */
    public static String owner(ItemStack item) {
        return read(item, ownerKey());
    }

    public static boolean is(ItemStack item, BloodItemKind kind) {
        return kind != null && kind == kind(item);
    }

    private static String read(ItemStack item, NamespacedKey key) {
        if (item == null || item.getType().isAir() || !item.hasItemMeta()) {
            return null;
        }
        return item.getItemMeta().getPersistentDataContainer().get(key, PersistentDataType.STRING);
    }
}
