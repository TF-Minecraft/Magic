package net.tfminecraft.magic.blood;

import java.util.Locale;

/** Bound items of the blood path. */
public enum BloodItemKind {
    ALTAR("altar", "v.chiseled_stone_bricks"),
    RITUAL_KNIFE("ritual_knife", "v.iron_sword"),
    BLOOD_KNIFE("blood_knife", "v.netherite_sword");

    private final String key;
    private final String fallbackPath;

    BloodItemKind(String key, String fallbackPath) {
        this.key = key;
        this.fallbackPath = fallbackPath;
    }

    public String key() {
        return key;
    }

    public String fallbackPath() {
        return fallbackPath;
    }

    public static BloodItemKind parse(String raw) {
        if (raw == null) {
            return null;
        }
        String wanted = raw.trim().toLowerCase(Locale.ROOT);
        for (BloodItemKind kind : values()) {
            if (kind.key.equals(wanted)) {
                return kind;
            }
        }
        return null;
    }
}
