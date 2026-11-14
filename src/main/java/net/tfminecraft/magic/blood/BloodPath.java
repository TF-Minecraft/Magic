package net.tfminecraft.magic.blood;

import java.util.Locale;

/** The answer to the god's question. NONE until it is asked and answered. */
public enum BloodPath {
    NONE,
    /** Gave themselves to the god: sacrifices feed the god, so less power returns. */
    DEVOTEE,
    /** Refused the god: sacrifices feed the mage alone. */
    TRUE;

    public static BloodPath parse(String raw) {
        if (raw == null) {
            return null;
        }
        try {
            return valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }
}
