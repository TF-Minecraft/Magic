package net.tfminecraft.magic.charge;

import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import net.tfminecraft.magic.artifact.ArtifactKeys;
import net.tfminecraft.magic.artifact.aura.AuraData;
import net.tfminecraft.magic.artifact.aura.AuraVessel;
import net.tfminecraft.magic.artifact.aura.VesselKind;
import net.tfminecraft.magic.artifact.shrine.ShrineScore;
import net.tfminecraft.magic.artifact.shrine.ShrineElementScore;
import net.tfminecraft.magic.model.ElementVisibility;

/**
 * Crafting consumable that gathers aura at a shrine and is spent at the mage station.
 *
 * <p>Unlike an artifact, a blank charge is still a valid vessel: it has no caps until it
 * is placed on a pedestal and imprints whatever the shrine scores. That is why the
 * resolver has to check charges before artifacts.
 */
public final class Charge implements AuraVessel {

    private final AuraData aura;
    private final int tier;
    private String primary = "";

    private Charge(AuraData aura, int tier) {
        this.aura = aura;
        this.tier = tier;
    }

    /** Resolves a charge by stamped tier first, then by item path. */
    public static Charge fromItem(ItemStack stack) {
        if (stack == null || stack.getType().isAir() || !stack.hasItemMeta()) {
            return null;
        }
        int tier = readTier(stack);
        if (tier <= 0) {
            ChargeDef def = ChargeRegistry.match(stack);
            if (def == null) {
                return null;
            }
            tier = def.getTier();
        }
        Charge charge = new Charge(AuraData.fromPersistentData(stack), tier);
        charge.primary = readPrimary(stack);
        return charge;
    }

    private static int readTier(ItemStack stack) {
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) {
            return 0;
        }
        Integer stored = meta.getPersistentDataContainer().get(
                ChargeKeys.chargeTier(), PersistentDataType.INTEGER);
        return stored == null ? 0 : stored;
    }

    private static String readPrimary(ItemStack stack) {
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) {
            return "";
        }
        String stored = meta.getPersistentDataContainer().get(
                ArtifactKeys.artifactPrimary(), PersistentDataType.STRING);
        return stored == null || stored.isBlank() ? "" : stored.trim().toLowerCase(Locale.ROOT);
    }

    public int getTier() {
        return tier;
    }

    /** Per-element cap this charge writes when it imprints. */
    public double tierCap() {
        ChargeDef def = ChargeRegistry.getByTier(tier);
        return def == null ? 0.0 : def.getAuraCap();
    }

    /** True while the charge has not yet taken on a shrine's elements. */
    public boolean isBlank() {
        return getCappedElementIds().isEmpty();
    }

    /**
     * Takes on every playable element the shrine scores, capped at this charge's tier.
     *
     * @return true when at least one element was imprinted
     */
    public boolean imprint(ItemStack stack, ShrineScore score) {
        if (stack == null || score == null) {
            return false;
        }
        double cap = tierCap();
        if (cap <= 0) {
            return false;
        }
        String best = "";
        double bestScore = 0;
        boolean any = false;
        for (Map.Entry<String, ShrineElementScore> entry : score.getByElement().entrySet()) {
            ShrineElementScore elementScore = entry.getValue();
            if (elementScore == null || elementScore.getMaxAura() <= 0) {
                continue;
            }
            if (!ElementVisibility.shownOnCharge(entry.getKey())) {
                continue;
            }
            setCap(entry.getKey(), cap);
            any = true;
            if (elementScore.getMaxAura() > bestScore) {
                bestScore = elementScore.getMaxAura();
                best = entry.getKey();
            }
        }
        if (!any) {
            return false;
        }
        stampTier(stack);
        if (!best.isEmpty()) {
            setPrimary(stack, best);
        }
        return true;
    }

    /** Imprints a single element, used by admin fill on a blank charge. */
    public boolean imprintElement(ItemStack stack, String elementId) {
        double cap = tierCap();
        if (stack == null || elementId == null || elementId.isBlank() || cap <= 0) {
            return false;
        }
        if (!ElementVisibility.shownOnCharge(elementId)) {
            return false;
        }
        setCap(elementId, cap);
        stampTier(stack);
        if (primary.isBlank()) {
            setPrimary(stack, elementId);
        }
        return true;
    }

    private void stampTier(ItemStack stack) {
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) {
            return;
        }
        meta.getPersistentDataContainer().set(
                ChargeKeys.chargeTier(), PersistentDataType.INTEGER, tier);
        stack.setItemMeta(meta);
    }

    private void setPrimary(ItemStack stack, String elementId) {
        String id = elementId.trim().toLowerCase(Locale.ROOT);
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) {
            return;
        }
        meta.getPersistentDataContainer().set(
                ArtifactKeys.artifactPrimary(), PersistentDataType.STRING, id);
        stack.setItemMeta(meta);
        primary = id;
    }

    @Override
    public VesselKind kind() {
        return VesselKind.CHARGE;
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
        return ChargeLore.updateItem(stack);
    }

    @Override
    public void persistPdc(ItemStack stack) {
        aura.persistPdc(stack);
    }
}
