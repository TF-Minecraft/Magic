package net.tfminecraft.magic.gear;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import org.bukkit.inventory.ItemStack;

import net.tfminecraft.magic.artifact.aura.AuraData;
import net.tfminecraft.magic.charge.Charge;
import net.tfminecraft.magic.charge.TierBands;

public final class WeaponRequirement {

    private final AuraData aura;

    private WeaponRequirement(AuraData aura) {
        this.aura = aura;
    }

    public static WeaponRequirement fromItem(ItemStack stack) {
        return new WeaponRequirement(AuraData.fromPersistentData(
                stack, GearKeys.weaponReqCap(), GearKeys.weaponReqFill(), GearKeys.weaponReq()));
    }

    public AuraData aura() {
        return aura;
    }

    public boolean hasStored() {
        return aura.hasStoredAura();
    }

    public void persist(ItemStack stack) {
        aura.persistPdc(stack, GearKeys.weaponReqCap(), GearKeys.weaponReqFill(), GearKeys.weaponReq());
    }

    /**
     * Highest-only merge of the charge's fill into this weapon. Caps are raised so the
     * stored fill is never clamped away.
     */
    public void mergeFrom(Charge charge) {
        if (charge == null) {
            return;
        }
        Map<String, Double> amounts = new LinkedHashMap<>();
        for (String elementId : charge.getCappedElementIds()) {
            amounts.put(elementId, charge.getFill(elementId));
        }
        mergeAmounts(amounts);
    }

    /**
     * Highest-only merge of already scaled per-element amounts, as produced by an orb
     * run capturing a fraction of a charge.
     */
    public void mergeAmounts(Map<String, Double> amounts) {
        if (amounts == null) {
            return;
        }
        for (Map.Entry<String, Double> entry : amounts.entrySet()) {
            Double incoming = entry.getValue();
            if (incoming == null || incoming <= 0) {
                continue;
            }
            String elementId = entry.getKey();
            double next = Math.max(aura.getFill(elementId), incoming);
            if (aura.getCap(elementId) < next) {
                aura.setCap(elementId, next);
            }
            aura.setFill(elementId, next);
        }
    }

    /** Highest band across a set of raw per-element amounts. */
    public static int highestBand(Map<String, Double> amounts) {
        int best = 0;
        if (amounts != null) {
            for (Map.Entry<String, Double> entry : amounts.entrySet()) {
                if (entry.getValue() == null) {
                    continue;
                }
                best = Math.max(best, TierBands.bandOf(entry.getKey(), entry.getValue()));
            }
        }
        return best;
    }

    public int highestBand() {
        int best = 0;
        for (String elementId : aura.getCappedElementIds()) {
            best = Math.max(best, TierBands.bandOf(elementId, aura.getFill(elementId)));
        }
        return best;
    }

    public static int highestBand(Charge charge) {
        if (charge == null) {
            return 0;
        }
        int best = 0;
        Set<String> ids = charge.getCappedElementIds();
        for (String elementId : ids) {
            best = Math.max(best, TierBands.bandOf(elementId, charge.getFill(elementId)));
        }
        return best;
    }
}
