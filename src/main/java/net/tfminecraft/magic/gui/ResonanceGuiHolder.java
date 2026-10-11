package net.tfminecraft.magic.gui;

import java.util.UUID;
import net.tfminecraft.tlibs.character.CharacterSession;
import net.tfminecraft.magic.session.ResonanceSession;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

public final class ResonanceGuiHolder implements InventoryHolder {

    private final UUID playerUuid;
    private final CharacterSession session;
    private final ResonanceSession resonance;
    private Inventory inventory;

    public ResonanceGuiHolder(UUID playerUuid, CharacterSession session, ResonanceSession resonance) {
        this.playerUuid = playerUuid;
        this.session = session;
        this.resonance = resonance;
    }

    public UUID getPlayerUuid() {
        return playerUuid;
    }

    public ResonanceSession getResonance() {
        return resonance;
    }

    public CharacterSession getSession() {
        return session;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    public void setInventory(Inventory inventory) {
        this.inventory = inventory;
    }
}
