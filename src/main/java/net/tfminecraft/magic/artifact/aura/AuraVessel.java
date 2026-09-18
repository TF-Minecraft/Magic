package net.tfminecraft.magic.artifact.aura;

import java.util.Set;

import org.bukkit.inventory.ItemStack;

/**
 * An item that stores per-element aura and can be filled at a shrine, by a sacrifice
 * rite, or by admin command.
 *
 * <p>Implemented by both artifacts and enchanted charges so the fill services do not
 * need to know which they are holding.
 */
public interface AuraVessel {

    VesselKind kind();

    double getCap(String elementId);

    double getFill(String elementId);

    void setCap(String elementId, double value);

    void setFill(String elementId, double value);

    Set<String> getCappedElementIds();

    boolean hasStoredAura();

    double totalFill();

    /** Element this vessel leads with, used for FX colour and lore ordering. */
    String primaryElementId();

    /** Persists aura and refreshes this vessel's lore block. */
    ItemStack write(ItemStack stack);

    void persistPdc(ItemStack stack);

    default boolean isArtifact() {
        return kind() == VesselKind.ARTIFACT;
    }

    default boolean isCharge() {
        return kind() == VesselKind.CHARGE;
    }
}
