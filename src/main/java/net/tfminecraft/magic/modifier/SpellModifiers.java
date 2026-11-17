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

    public static ModifierSet resonance(String elementId, double amount) {
        ElementDef element = resolve(elementId);
        if (element == null) {
            return ModifierSet.ZERO;
        }
        return element.getResonanceCurve().sample(amount);
    }

    public static ModifierSet drift(double equilibrium) {
        if (equilibrium < 0.0) {
            return DriftCache.surge.sample(Math.abs(equilibrium));
        }
        if (equilibrium > 0.0) {
            return DriftCache.tranquility.sample(equilibrium);
        }
        return ModifierSet.ZERO;
    }

    /**
     * One-sided bonus for casting an element the held weapon is attuned to, scaled by
     * the weapon's band in that element. A weapon with no attunement in the element
     * contributes nothing rather than a penalty, because a weapon that cannot carry the
     * element refuses the cast outright.
     */
    public static ModifierSet alignment(ItemStack weapon, String elementId) {
        if (weapon == null) {
            return ModifierSet.ZERO;
        }
        return alignment(WeaponRequirement.fromItem(weapon), elementId);
    }

    public static ModifierSet alignment(WeaponRequirement requirement, String elementId) {
        if (requirement == null || !GearCache.alignmentEnabled || resolve(elementId) == null) {
            return ModifierSet.ZERO;
        }
        double fill = requirement.aura().getFill(elementId);
        if (fill <= 0) {
            return ModifierSet.ZERO;
        }
        return GearCache.alignment(TierBands.bandOf(elementId, fill));
    }

    public static ModifierSet combine(ModifierSet a, ModifierSet b) {
        if (a == null) {
            a = ModifierSet.ZERO;
        }
        if (b == null) {
            b = ModifierSet.ZERO;
        }
        return a.combine(b);
    }

    /** Batch 2 lore: resonance curve at 100. */
    public static ModifierSet resonanceAt100(String elementId) {
        return resonance(elementId, 100.0);
    }

    /** Batch 2 lore: surge curve at 60. */
    public static ModifierSet surgeAt60() {
        return DriftCache.surge.sample(60.0);
    }

    /** Batch 2 lore: tranquility curve at 100. */
    public static ModifierSet tranquilityAt100() {
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
