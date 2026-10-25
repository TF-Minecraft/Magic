package net.tfminecraft.magic.blood;

import java.util.Locale;

/** Where a character stands on the blood path. Order matters: later stages compare greater. */
public enum BloodStage {
    /** No voices yet. The whisper meter may still be filling. */
    NONE,
    /** The voices speak and teach; they end by handing over the knife and the altar. */
    WHISPERED,
    /** Animal sacrifices at the altar. */
    ANIMAL,
    /** The blood knife is given and the god waits for an answer. */
    CHOICE,
    /** True path only: character sacrifices for hunt points. */
    HUNT,
    /** Named sacrifices of the closest characters, both paths. */
    FINAL,
    /** Every final sacrifice is done. */
    ASCENDED;

    public boolean atLeast(BloodStage other) {
        return compareTo(other) >= 0;
    }

    /** Case-insensitive lookup; null when the name is unknown. */
    public static BloodStage parse(String raw) {
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
