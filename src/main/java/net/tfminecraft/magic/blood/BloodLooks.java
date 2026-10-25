package net.tfminecraft.magic.blood;

import java.util.Optional;

import org.bukkit.Bukkit;
import org.bukkit.inventory.ItemStack;

import de.tr7zw.nbtapi.NBT;
import net.tfminecraft.magic.Magic;
import net.tfminecraft.magic.util.ItemRef;
import net.tfminecraft.tlibs.TLibs;

/** ItemsAdder looks on blood items, kept safe from ItemsAdder rebuilding them later. */
final class BloodLooks {

    private static final String COMPOUND = "itemsadder";

    private BloodLooks() {}

    /** The item wearing the look (a TLibs ia. path); the item itself without one or when it fails. */
    static ItemStack apply(ItemStack item, String look) {
        if (look == null) {
            return item;
        }
        try {
            ItemStack merged = TLibs.getItemAPI().getArmorMerger().merge(item, Optional.empty(), ItemRef.normalize(look));
            return merged == null || merged.getType().isAir() ? item : merged;
        } catch (RuntimeException ex) {
            Magic.plugin.getLogger().warning("[Magic] Failed to apply blood look " + look + ": " + ex.getMessage());
            return item;
        }
    }

    /**
     * ItemsAdder rebuilds an item from its definition when that definition changes, which would wipe
     * a bound piece's stats and lore. It skips items whose itemsadder compound has override_auto_update.
     */
    static void protect(ItemStack item) {
        if (!Bukkit.getPluginManager().isPluginEnabled("NBTAPI")) {
            return;
        }
        try {
            NBT.modify(item, nbt -> {
                if (nbt.hasTag(COMPOUND)) {
                    nbt.getCompound(COMPOUND).setBoolean("override_auto_update", true);
                }
            });
        } catch (RuntimeException | LinkageError ex) {
            // Without NBTAPI there is no opt-out; the item stays as it is.
        }
    }
}
