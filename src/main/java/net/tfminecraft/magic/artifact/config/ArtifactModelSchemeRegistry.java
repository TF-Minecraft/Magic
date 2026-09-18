package net.tfminecraft.magic.artifact.config;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class ArtifactModelSchemeRegistry {

    private static final Map<String, ArtifactModelScheme> map = new LinkedHashMap<>();

    private ArtifactModelSchemeRegistry() {}

    public static void clear() {
        map.clear();
    }

    public static void register(ArtifactModelScheme scheme) {
        if (scheme == null || scheme.getId() == null || scheme.getId().isBlank()) {
            return;
        }
        map.put(scheme.getId(), scheme);
    }

    public static boolean contains(String id) {
        return map.containsKey(id);
    }

    public static ArtifactModelScheme getById(String id) {
        return map.get(id);
    }

    public static List<ArtifactModelScheme> getAll() {
        return Collections.unmodifiableList(new ArrayList<>(map.values()));
    }

    public static int size() {
        return map.size();
    }
}
