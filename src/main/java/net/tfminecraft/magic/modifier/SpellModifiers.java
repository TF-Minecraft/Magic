package net.tfminecraft.magic.modifier;

import java.util.Locale;

import org.bukkit.inventory.ItemStack;

import net.tfminecraft.magic.charge.TierBands;
import net.tfminecraft.magic.gear.GearCache;
import net.tfminecraft.magic.gear.WeaponRequirement;
import net.tfminecraft.magic.model.ElementDef;
import net.tfminecraft.magic.registry.ElementRegistry;

public final class SpellModifiers {

    private SpellModifiers() {}

    public static ModifierTriple resonance(String elementId, double amount) {
        ElementDef element = resolve(elementId);
        if (element == null) {
            return ModifierTriple.ZERO;
        }
        return element.getResonanceCurve().sample(amount);
    }

    public static ModifierTriple drift(double equilibrium) {
        if (equilibrium < 0.0) {
            return DriftCache.surge.sample(Math.abs(equilibrium));
        }
        if (equilibrium > 0.0) {
            return DriftCache.tranquility.sample(equilibrium);
        }
        return ModifierTriple.ZERO;
    }

    /**
     * One-sided bonus for casting an element the held weapon is attuned to, scaled by
     * the weapon's band in that element. A weapon with no attunement in the element
     * contributes nothing rather than a penalty, because a weapon that cannot carry the
     * element refuses the cast outright.
     */
    public static ModifierTriple alignment(ItemStack weapon, String elementId) {
        if (weapon == null || !GearCache.alignmentEnabled || resolve(elementId) == null) {
            return ModifierTriple.ZERO;
        }
        double fill = WeaponRequirement.fromItem(weapon).aura().getFill(elementId);
        if (fill <= 0) {
            return ModifierTriple.ZERO;
        }
        return GearCache.alignment(TierBands.bandOf(elementId, fill));
    }

    public static ModifierTriple combine(ModifierTriple a, ModifierTriple b) {
        if (a == null) {
            a = ModifierTriple.ZERO;
        }
        if (b == null) {
            b = ModifierTriple.ZERO;
        }
        return a.combine(b);
    }

    /** Batch 2 lore: resonance curve at 100. */
    public static ModifierTriple resonanceAt100(String elementId) {
        return resonance(elementId, 100.0);
    }

    /** Batch 2 lore: surge curve at 60. */
    public static ModifierTriple surgeAt60() {
        return DriftCache.surge.sample(60.0);
    }

    /** Batch 2 lore: tranquility curve at 100. */
    public static ModifierTriple tranquilityAt100() {
        return DriftCache.tranquility.sample(100.0);
    }

    private static ElementDef resolve(String elementId) {
        if (elementId == null || elementId.isBlank()) {
            return null;
        }
        ElementDef exact = ElementRegistry.getById(elementId);
        if (exact != null) {
            return exact;
        }
        return ElementRegistry.getById(elementId.trim().toLowerCase(Locale.ROOT));
    }
}
