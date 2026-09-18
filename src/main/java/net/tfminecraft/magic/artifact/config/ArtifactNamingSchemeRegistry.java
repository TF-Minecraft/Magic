package net.tfminecraft.magic.artifact.config;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class ArtifactNamingSchemeRegistry {

    private static final Map<String, ArtifactNamingScheme> map = new LinkedHashMap<>();

    private ArtifactNamingSchemeRegistry() {}

    public static void clear() {
        map.clear();
    }

    public static void register(ArtifactNamingScheme scheme) {
        if (scheme == null || scheme.getId() == null || scheme.getId().isBlank()) {
            return;
        }
        map.put(scheme.getId(), scheme);
    }

    public static boolean contains(String id) {
        return map.containsKey(id);
    }

    public static ArtifactNamingScheme getById(String id) {
        return map.get(id);
    }

    public static List<ArtifactNamingScheme> getAll() {
        return Collections.unmodifiableList(new ArrayList<>(map.values()));
    }

    public static int size() {
        return map.size();
    }
}
