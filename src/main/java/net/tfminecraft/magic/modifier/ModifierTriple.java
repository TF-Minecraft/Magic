package net.tfminecraft.magic.modifier;

import org.bukkit.configuration.ConfigurationSection;

/**
 * Decimal modifiers. Negative cooldown is a shorter cooldown.
 */
public final class ModifierTriple {

    public static final ModifierTriple ZERO = new ModifierTriple(0.0, 0.0, 0.0);

    public static final double MANA_DAMAGE_CLAMP = 0.9;
    public static final double COOLDOWN_MIN = -0.5;

    private final double mana;
    private final double damage;
    private final double cooldown;

    public ModifierTriple(double mana, double damage, double cooldown) {
        this.mana = mana;
        this.damage = damage;
        this.cooldown = cooldown;
    }

    public static ModifierTriple from(ConfigurationSection section) {
        if (section == null) {
            return ZERO;
        }
        return new ModifierTriple(
                section.getDouble("mana", 0.0),
                section.getDouble("damage", 0.0),
                section.getDouble("cooldown", 0.0));
    }

    public double mana() {
        return mana;
    }

    public double damage() {
        return damage;
    }

    public double cooldown() {
        return cooldown;
    }

    public ModifierTriple lerp(ModifierTriple other, double t) {
        if (other == null || t <= 0.0) {
            return this;
        }
        if (t >= 1.0) {
            return other;
        }
        return new ModifierTriple(
                mana + (other.mana - mana) * t,
                damage + (other.damage - damage) * t,
                cooldown + (other.cooldown - cooldown) * t);
    }

    public ModifierTriple combine(ModifierTriple other) {
        if (other == null) {
            return clamp(this);
        }
        return clamp(new ModifierTriple(
                multiplicative(mana, other.mana),
                multiplicative(damage, other.damage),
                multiplicative(cooldown, other.cooldown)));
    }

    public boolean isZero() {
        return mana == 0.0 && damage == 0.0 && cooldown == 0.0;
    }

    public ModifierTriple clamp() {
        return clamp(this);
    }

    private static double multiplicative(double a, double b) {
        return (1.0 + a) * (1.0 + b) - 1.0;
    }

    private static ModifierTriple clamp(ModifierTriple v) {
        return new ModifierTriple(
                clampSigned(v.mana, MANA_DAMAGE_CLAMP),
                clampSigned(v.damage, MANA_DAMAGE_CLAMP),
                Math.max(COOLDOWN_MIN, v.cooldown));
    }

    private static double clampSigned(double value, double absMax) {
        return Math.max(-absMax, Math.min(absMax, value));
    }
}
