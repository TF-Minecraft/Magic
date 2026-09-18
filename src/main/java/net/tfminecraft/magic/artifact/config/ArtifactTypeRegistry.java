package net.tfminecraft.magic.artifact.config;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class ArtifactTypeRegistry {

    private static final Map<String, ArtifactTypeDef> map = new LinkedHashMap<>();

    private ArtifactTypeRegistry() {}

    public static void clear() {
        map.clear();
    }

    public static void register(ArtifactTypeDef type) {
        if (type == null || type.getElementId() == null || type.getElementId().isBlank()) {
            return;
        }
        map.put(type.getElementId(), type);
    }

    public static boolean contains(String elementId) {
        return map.containsKey(elementId);
    }

    public static ArtifactTypeDef getById(String elementId) {
        return map.get(elementId);
    }

    public static List<ArtifactTypeDef> getAll() {
        return Collections.unmodifiableList(new ArrayList<>(map.values()));
    }

    public static List<String> getAllIds() {
        return Collections.unmodifiableList(new ArrayList<>(map.keySet()));
    }

    public static int size() {
        return map.size();
    }
}
