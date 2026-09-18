package net.tfminecraft.magic.artifact.sacrifice;

import java.util.Locale;

public enum SacrificeWordMatch {
    EXACT,
    STARTS_WITH,
    CONTAINS;

    public static SacrificeWordMatch fromConfig(String raw) {
        if (raw == null || raw.isBlank()) {
            return STARTS_WITH;
        }
        String key = raw.trim().toLowerCase(Locale.ROOT).replace('-', '_');
        return switch (key) {
            case "exact" -> EXACT;
            case "contains" -> CONTAINS;
            default -> STARTS_WITH;
        };
    }
}
