package net.tfminecraft.magic.modifier;

import org.bukkit.configuration.ConfigurationSection;

/**
 * Decimal spell modifiers: 0.25 is +25%. Negative cooldown is a shorter cooldown.
 *
 * <p>Support is heal and shield power. A curve point that does not set it follows its
 * damage, so healers and shielders scale exactly like damage dealers unless a config
 * says otherwise. Duration is off (0) unless a curve point sets it. Which skill
 * parameters each stat reaches is {@link ModifierParameters}.
 */
public final class ModifierSet {

    public static final ModifierSet ZERO = new ModifierSet(0.0, 0.0, 0.0, 0.0, 0.0);

    public static final double MANA_DAMAGE_CLAMP = 0.9;
    public static final double COOLDOWN_MIN = -0.5;

    private final double mana;
    private final double damage;
    private final double cooldown;
    private final double support;
    private final double duration;

    /** Support follows damage; duration is 0. */
    public ModifierSet(double mana, double damage, double cooldown) {
        this(mana, damage, cooldown, damage, 0.0);
    }

    public ModifierSet(double mana, double damage, double cooldown, double support, double duration) {
        this.mana = mana;
        this.damage = damage;
        this.cooldown = cooldown;
        this.support = support;
        this.duration = duration;
    }

    public static ModifierSet from(ConfigurationSection section) {
        if (section == null) {
            return ZERO;
        }
        double damage = section.getDouble("damage", 0.0);
        return new ModifierSet(
                section.getDouble("mana", 0.0),
                damage,
                section.getDouble("cooldown", 0.0),
                section.isSet("support") ? section.getDouble("support", damage) : damage,
                section.getDouble("duration", 0.0));
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

    public double support() {
        return support;
    }

    public double duration() {
        return duration;
    }

    public double get(ModifierStat stat) {
        return switch (stat) {
            case MANA -> mana;
            case COOLDOWN -> cooldown;
            case DAMAGE -> damage;
            case SUPPORT -> support;
            case DURATION -> duration;
        };
    }

    public ModifierSet lerp(ModifierSet other, double t) {
        if (other == null || t <= 0.0) {
            return this;
        }
        if (t >= 1.0) {
            return other;
        }
        return new ModifierSet(
                mana + (other.mana - mana) * t,
                damage + (other.damage - damage) * t,
                cooldown + (other.cooldown - cooldown) * t,
                support + (other.support - support) * t,
                duration + (other.duration - duration) * t);
    }

    public ModifierSet combine(ModifierSet other) {
        if (other == null) {
            return clamp(this);
        }
        return clamp(new ModifierSet(
                multiplicative(mana, other.mana),
                multiplicative(damage, other.damage),
                multiplicative(cooldown, other.cooldown),
                multiplicative(support, other.support),
                multiplicative(duration, other.duration)));
    }

    public boolean isZero() {
        return mana == 0.0 && damage == 0.0 && cooldown == 0.0 && support == 0.0 && duration == 0.0;
    }

    public ModifierSet clamp() {
        return clamp(this);
    }

    private static double multiplicative(double a, double b) {
        return (1.0 + a) * (1.0 + b) - 1.0;
    }

    private static ModifierSet clamp(ModifierSet v) {
        return new ModifierSet(
                clampSigned(v.mana, MANA_DAMAGE_CLAMP),
                clampSigned(v.damage, MANA_DAMAGE_CLAMP),
                Math.max(COOLDOWN_MIN, v.cooldown),
                clampSigned(v.support, MANA_DAMAGE_CLAMP),
                clampSigned(v.duration, MANA_DAMAGE_CLAMP));
    }

    private static double clampSigned(double value, double absMax) {
        return Math.max(-absMax, Math.min(absMax, value));
    }
}
