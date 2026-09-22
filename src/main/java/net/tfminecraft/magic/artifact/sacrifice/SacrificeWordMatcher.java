package net.tfminecraft.magic.artifact.sacrifice;

import java.util.Locale;

import org.bukkit.ChatColor;

public final class SacrificeWordMatcher {

    private SacrificeWordMatcher() {}

    public static String matchElementId(String rawMessage) {
        if (!SacrificeRegistry.isEnabled() || SacrificeRegistry.size() <= 0) {
            return null;
        }
        String text = normalize(rawMessage);
        if (text.isEmpty()) {
            return null;
        }
        boolean ignoreCase = SacrificeRegistry.isCaseInsensitive();
        String haystack = ignoreCase ? text.toLowerCase(Locale.ROOT) : text;
        SacrificeWordMatch mode = SacrificeRegistry.getMatch();
        for (SacrificeElementDef def : SacrificeRegistry.getAll()) {
            if (!def.isEnabled()) {
                continue;
            }
            for (String phrase : def.getWords()) {
                if (phrase == null || phrase.isBlank()) {
                    continue;
                }
                String needle = ignoreCase ? phrase.toLowerCase(Locale.ROOT) : phrase;
                if (matches(haystack, needle, mode)) {
                    return def.getElementId();
                }
            }
        }
        return null;
    }

    private static boolean matches(String haystack, String needle, SacrificeWordMatch mode) {
        if (mode == SacrificeWordMatch.EXACT) {
            return haystack.equals(needle);
        }
        if (mode == SacrificeWordMatch.CONTAINS) {
            return haystack.contains(needle);
        }
        return haystack.startsWith(needle);
    }

    // Keep the existing legacy text representation, formatting, and exact-string comparisons.
    @SuppressWarnings("deprecation")
    private static String normalize(String raw) {
        if (raw == null) {
            return "";
        }
        return ChatColor.stripColor(raw).trim();
    }
}
