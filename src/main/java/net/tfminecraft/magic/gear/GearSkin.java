package net.tfminecraft.magic.gear;

import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import io.lumine.mythic.lib.api.item.NBTItem;
import net.tfminecraft.magic.util.LegacyModelData;
import net.tfminecraft.tlibs.objects.api.subapi.ItemSkinPreserver;

/**
 * Keeps an ArmourShop skin when a weapon is rebuilt. A rebuild starts from the MMOItems
 * template and the part model scheme, so without this the skin and its scroll are lost.
 */
public final class GearSkin {

    private GearSkin() {}

    /** The rebuilt weapon wearing the old weapon's skin, or the rebuilt weapon unchanged when there was none. */
    public static ItemStack keep(ItemStack old, ItemStack rebuilt) {
        if (rebuilt == null || !isSkinned(old)) {
            return rebuilt;
        }
        return ItemSkinPreserver.apply(old, rebuilt);
    }

    /**
     * Rebuilds keep the {@code amodel} tag but reset the model, so a weapon that already lost
     * its skin still carries the tag. Only a model that matches the tag counts as worn.
     */
    static boolean isSkinned(ItemStack stack) {
        if (!ItemSkinPreserver.hasSkinData(stack)) {
            return false;
        }
        String amodel = NBTItem.get(stack).getString("amodel");
        if (amodel == null || amodel.isEmpty()) {
            return true;
        }
        ItemMeta meta = stack.getItemMeta();
        return meta != null && LegacyModelData.has(meta)
                && amodel.equals(String.valueOf(LegacyModelData.get(meta)));
    }
}
