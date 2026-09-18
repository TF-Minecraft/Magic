package net.tfminecraft.magic.artifact.config;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class ArtifactRarityRegistry {

    private static final Map<String, ArtifactRarityDef> map = new LinkedHashMap<>();

    private ArtifactRarityRegistry() {}

    public static void clear() {
        map.clear();
    }

    public static void register(ArtifactRarityDef rarity) {
        if (rarity == null || rarity.getId() == null || rarity.getId().isBlank()) {
            return;
        }
        map.put(rarity.getId(), rarity);
    }

    public static boolean contains(String id) {
        return map.containsKey(id);
    }

    public static ArtifactRarityDef getById(String id) {
        return map.get(id);
    }

    public static List<ArtifactRarityDef> getAll() {
        return Collections.unmodifiableList(new ArrayList<>(map.values()));
    }

    public static int size() {
        return map.size();
    }
}
