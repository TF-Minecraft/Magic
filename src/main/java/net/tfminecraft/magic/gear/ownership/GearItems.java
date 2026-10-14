package net.tfminecraft.magic.gear.ownership;

import org.bukkit.inventory.ItemStack;

/** Exact Paper item bytes, including third-party metadata. */
public final class GearItems {
    private GearItems() {}
    public static byte[] bytes(ItemStack item) { return item.serializeAsBytes(); }
    public static ItemStack item(byte[] bytes) { return ItemStack.deserializeBytes(bytes); }
}
