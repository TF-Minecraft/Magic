package net.tfminecraft.magic.artifact;

import java.util.Locale;
import java.util.Set;

import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import net.tfminecraft.magic.artifact.aura.AuraData;
import net.tfminecraft.magic.artifact.aura.AuraVessel;
import net.tfminecraft.magic.artifact.aura.VesselKind;
import net.tfminecraft.magic.charge.ChargeIds;

/**
 * Permanent shrine item holding per-element aura.
 *
 * <p>Storage lives in {@link AuraData}, shared with enchanted charges. What is specific
 * to artifacts is identity, rarity, the care/muffle system, meditation eligibility and
 * the numeric {@code fill / cap} lore block.
 */
public final class Artifact implements AuraVessel {

    private final AuraData aura;
    private String primary = "";

    private Artifact(AuraData aura) {
        this.aura = aura;
    }

    public static Artifact create() {
        return new Artifact(new AuraData());
    }

    /**
     * Charges share the aura PDC shape, so they are rejected here. Every artifact-only
     * system funnels through this method and therefore ignores charges for free.
     *
     * <p>The charge check is deliberately made only after the item is known to carry
     * aura, because it is the expensive half and most items reaching here carry none.
     */
    public static Artifact fromItem(ItemStack stack) {
        if (stack == null || !stack.hasItemMeta()) {
            return null;
        }
        AuraData data = AuraData.fromPersistentData(stack);
        if (!data.isEmpty()) {
            if (ChargeIds.isCharge(stack)) {
                return null;
            }
            Artifact artifact = new Artifact(data);
            artifact.primary = readPrimary(stack);
            return artifact;
        }
        Artifact fromLore = ArtifactLore.readAura(stack);
        if (fromLore != null && !ChargeIds.isCharge(stack)) {
            fromLore.primary = readPrimary(stack);
            fromLore.persistPdc(stack);
            return fromLore;
        }
        return null;
    }

    private static String readPrimary(ItemStack stack) {
        if (stack == null || !stack.hasItemMeta()) {
            return "";
        }
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) {
            return "";
        }
        String stored = meta.getPersistentDataContainer().get(
                ArtifactKeys.artifactPrimary(), PersistentDataType.STRING);
        return stored == null || stored.isBlank() ? "" : stored.trim().toLowerCase(Locale.ROOT);
    }

    @Override
    public VesselKind kind() {
        return VesselKind.ARTIFACT;
    }

    @Override
    public boolean hasStoredAura() {
        return aura.hasStoredAura();
    }

    @Override
    public double totalFill() {
        return aura.totalFill();
    }

    @Override
    public double getCap(String elementId) {
        return aura.getCap(elementId);
    }

    @Override
    public double getFill(String elementId) {
        return aura.getFill(elementId);
    }

    @Override
    public void setCap(String elementId, double value) {
        aura.setCap(elementId, value);
    }

    @Override
    public void setFill(String elementId, double value) {
        aura.setFill(elementId, value);
    }

    @Override
    public Set<String> getCappedElementIds() {
        return aura.getCappedElementIds();
    }

    @Override
    public String primaryElementId() {
        return primary != null && !primary.isBlank() ? primary : aura.highestCapElementId();
    }

    @Override
    public ItemStack write(ItemStack stack) {
        persistPdc(stack);
        return ArtifactLore.updateItem(stack);
    }

    @Override
    public void persistPdc(ItemStack stack) {
        aura.persistPdc(stack);
    }
}
