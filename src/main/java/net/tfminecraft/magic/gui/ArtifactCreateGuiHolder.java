package net.tfminecraft.magic.gui;

import java.util.UUID;
import net.tfminecraft.magic.artifact.create.ArtifactCreateSession;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

public final class ArtifactCreateGuiHolder implements InventoryHolder {

    private final UUID playerUuid;
    private final ArtifactCreateSession session;
    private Inventory inventory;

    public ArtifactCreateGuiHolder(UUID playerUuid, ArtifactCreateSession session) {
        this.playerUuid = playerUuid;
        this.session = session;
    }

    public ArtifactCreateSession getSession() {
        return session;
    }

    public UUID getPlayerUuid() {
        return playerUuid;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    public void setInventory(Inventory inventory) {
        this.inventory = inventory;
    }
}
