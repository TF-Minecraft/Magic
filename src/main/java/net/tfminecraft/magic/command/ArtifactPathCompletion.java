package net.tfminecraft.magic.command;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import net.tfminecraft.magic.artifact.config.ArtifactRarityRegistry;
import net.tfminecraft.magic.artifact.config.ArtifactTypeDef;
import net.tfminecraft.magic.artifact.config.ArtifactTypeRegistry;

/** Completes the finite portions of the diagnostic's single-token path grammar. */
final class ArtifactPathCompletion {
    private ArtifactPathCompletion() {}

    static List<String> complete(String token) {
        String lower = token.toLowerCase(Locale.ROOT);
        if (!lower.startsWith("magic.(")) {
            return extend("", token, List.of("magic.artifact", "magic.("));
        }
        if (token.indexOf(')') >= 0 || token.chars().anyMatch(Character::isWhitespace)) return List.of();
        int start = token.lastIndexOf(';') + 1;
        if (start == 0) start = "magic.(".length();
        String head = token.substring(0, start);
        String current = token.substring(start);
        Set<String> used = new HashSet<>();
        String primary = null;
        for (String previous : token.substring("magic.(".length(), start).split(";")) {
            if (previous.isEmpty()) continue;
            String[] pair = previous.split("=", 2);
            String key = pair[0].toLowerCase(Locale.ROOT);
            if (key.equals("primary") || key.equals("element")) {
                if (pair.length != 2 || type(pair[1]) == null) return List.of();
                primary = pair[1];
                used.add("primary"); used.add("element");
            } else if (key.equals("rarity")) {
                if (pair.length != 2 || ArtifactRarityRegistry.getAll().stream()
                        .noneMatch(rarity -> rarity.getId().equalsIgnoreCase(pair[1]))) return List.of();
                used.add(key);
            } else {
                if (type(key) == null) return List.of();
                if (pair.length == 2) {
                    try { Double.parseDouble(pair[1]); }
                    catch (NumberFormatException ex) { return List.of(); }
                }
                used.add(key);
            }
        }
        int equals = current.indexOf('=');
        if (equals >= 0) {
            String key = current.substring(0, equals).toLowerCase(Locale.ROOT);
            if (used.contains(key)) return List.of();
            List<String> values = new ArrayList<>();
            if (key.equals("primary") || key.equals("element")) {
                ArtifactTypeRegistry.getAll().forEach(type -> values.add(type.getElementId()));
            } else if (key.equals("rarity")) {
                ArtifactTypeDef selected = type(primary);
                ArtifactRarityRegistry.getAll().stream()
                        .filter(rarity -> selected == null || selected.hasRarity(rarity.getId()))
                        .forEach(rarity -> values.add(rarity.getId()));
            } else {
                return List.of(); // Numeric caps stay manual.
            }
            List<String> endings = new ArrayList<>();
            values.forEach(value -> { endings.add(value + ")"); endings.add(value + ";"); });
            return extend(head + current.substring(0, equals + 1), current.substring(equals + 1), endings);
        }
        List<String> keys = new ArrayList<>();
        for (String key : List.of("primary", "element", "rarity")) {
            if (!used.contains(key)) keys.add(key + "=");
        }
        for (String id : ArtifactTypeRegistry.getAllIds()) {
            if (!used.contains(id.toLowerCase(Locale.ROOT))) {
                keys.add(id + ")"); keys.add(id + ";"); keys.add(id + "=");
            }
        }
        return extend(head, current, keys);
    }

    private static ArtifactTypeDef type(String id) {
        if (id == null) return null;
        return ArtifactTypeRegistry.getAll().stream()
                .filter(type -> type.getElementId().equalsIgnoreCase(id)).findFirst().orElse(null);
    }

    private static List<String> extend(String head, String typed, List<String> candidates) {
        return candidates.stream().filter(candidate -> candidate.regionMatches(true, 0, typed, 0, typed.length()))
                .map(candidate -> head + typed + candidate.substring(typed.length())).distinct().toList();
    }
}
