package net.tfminecraft.magic.artifact.config;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class ArtifactAdjectiveRegistry {

    private static final Map<String, Double> chanceByRarity = new LinkedHashMap<>();
    private static final Map<String, List<String>> global = new LinkedHashMap<>();
    private static final Map<String, Map<String, List<String>>> byElement = new LinkedHashMap<>();

    private ArtifactAdjectiveRegistry() {}

    public static void clear() {
        chanceByRarity.clear();
        global.clear();
        byElement.clear();
    }

    public static void setChance(String rarityId, double chance) {
        if (rarityId == null || rarityId.isBlank()) {
            return;
        }
        chanceByRarity.put(rarityId.trim().toLowerCase(Locale.ROOT), Math.max(0.0, Math.min(1.0, chance)));
    }

    public static void setGlobal(String rarityId, List<String> words) {
        putList(global, rarityId, words);
    }

    public static void setElement(String elementId, String rarityId, List<String> words) {
        if (elementId == null || elementId.isBlank()) {
            return;
        }
        String id = elementId.trim().toLowerCase(Locale.ROOT);
        Map<String, List<String>> byRarity = byElement.computeIfAbsent(id, key -> new LinkedHashMap<>());
        putList(byRarity, rarityId, words);
    }

    public static double chance(String rarityId) {
        if (rarityId == null || rarityId.isBlank()) {
            return 0.0;
        }
        return chanceByRarity.getOrDefault(rarityId.trim().toLowerCase(Locale.ROOT), 0.0);
    }

    public static List<String> pool(String elementId, String rarityId) {
        List<String> out = new ArrayList<>();
        addAll(out, getList(global, rarityId));
        if (elementId != null && !elementId.isBlank()) {
            Map<String, List<String>> byRarity = byElement.get(elementId.trim().toLowerCase(Locale.ROOT));
            if (byRarity != null) {
                addAll(out, getList(byRarity, rarityId));
            }
        }
        return Collections.unmodifiableList(out);
    }

    private static void putList(Map<String, List<String>> map, String rarityId, List<String> words) {
        if (rarityId == null || rarityId.isBlank() || words == null) {
            return;
        }
        List<String> clean = new ArrayList<>();
        for (String word : words) {
            if (word != null && !word.isBlank()) {
                clean.add(word.trim());
            }
        }
        map.put(rarityId.trim().toLowerCase(Locale.ROOT), List.copyOf(clean));
    }

    private static List<String> getList(Map<String, List<String>> map, String rarityId) {
        if (map == null || rarityId == null || rarityId.isBlank()) {
            return List.of();
        }
        List<String> exact = map.get(rarityId.trim().toLowerCase(Locale.ROOT));
        return exact != null ? exact : List.of();
    }

    private static void addAll(List<String> out, List<String> extra) {
        for (String word : extra) {
            if (word != null && !word.isBlank() && !out.contains(word)) {
                out.add(word);
            }
        }
    }
}
