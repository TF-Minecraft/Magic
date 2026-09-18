package net.tfminecraft.magic.gear.gui;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

public final class PartSelectionHolder implements InventoryHolder {

    private final String categoryId;

    public PartSelectionHolder(String categoryId) {
        this.categoryId = categoryId;
    }

    public String getCategoryId() {
        return categoryId;
    }

    @Override
    public Inventory getInventory() {
        return null;
    }
}
