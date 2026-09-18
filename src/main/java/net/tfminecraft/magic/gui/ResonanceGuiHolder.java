package net.tfminecraft.magic.gui;

import java.util.UUID;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

public final class ResonanceGuiHolder implements InventoryHolder {

    private final UUID playerUuid;
    private final String characterId;
    private Inventory inventory;

    public ResonanceGuiHolder(UUID playerUuid, String characterId) {
        this.playerUuid = playerUuid;
        this.characterId = characterId;
    }

    public UUID getPlayerUuid() {
        return playerUuid;
    }

    public String getCharacterId() {
        return characterId;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    public void setInventory(Inventory inventory) {
        this.inventory = inventory;
    }
}
