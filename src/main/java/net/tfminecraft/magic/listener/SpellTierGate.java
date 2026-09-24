package net.tfminecraft.magic.listener;

/**
 * Per-spell floors: the weapon must hold the element at the spell's band, and the
 * caster's resonance must meet the same band. A high-attuned staff does not block
 * a lower-tier spell.
 */
public final class SpellTierGate {

    public enum Refuse {
        NONE,
        FOREIGN,
        WEAPON,
        SPELL
    }

    private SpellTierGate() {}

    /**
     * @param hasElement {@code false} when the weapon has no imbued fill for the spell's element
     * @param weaponBand {@link net.tfminecraft.magic.charge.TierBands#bandOf} of that fill
     * @param playerBand band of the caster's resonance in that element
     * @param spellTier required band from skills.yml (1-4)
     */
    public static Refuse refuse(boolean hasElement, int weaponBand, int playerBand, int spellTier) {
        if (!hasElement) {
            return Refuse.FOREIGN;
        }
        int need = Math.max(1, spellTier);
        if (weaponBand < need) {
            return Refuse.WEAPON;
        }
        if (playerBand < need) {
            return Refuse.SPELL;
        }
        return Refuse.NONE;
    }
}
