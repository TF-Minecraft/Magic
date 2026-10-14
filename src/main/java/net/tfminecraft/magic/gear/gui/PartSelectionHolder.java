package net.tfminecraft.magic.gear.gui;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

public final class PartSelectionHolder extends GearMenuHolder {

    private final String categoryId;

    public PartSelectionHolder(String categoryId) { this(categoryId, null); }
    public PartSelectionHolder(String categoryId, org.bukkit.entity.Player player) {
        super(player);
        this.categoryId = categoryId;
    }

    public String getCategoryId() {
        return categoryId;
    }


}
