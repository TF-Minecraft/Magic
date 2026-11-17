package net.tfminecraft.magic.modifier;

import java.util.Locale;

/** The spell stats resonance, balance and weapon alignment can change. */
public enum ModifierStat {
    MANA,
    COOLDOWN,
    DAMAGE,
    /** Heal and shield power. */
    SUPPORT,
    DURATION;

    /** The config key: {@code mana}, {@code cooldown}, {@code damage}, {@code support}, {@code duration}. */
    public String key() {
        return name().toLowerCase(Locale.ROOT);
    }
}
