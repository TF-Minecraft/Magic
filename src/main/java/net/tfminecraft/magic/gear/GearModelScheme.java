package net.tfminecraft.magic.gear;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;

public final class GearModelScheme {

    private final String id;
    private final Map<GearType, String> paths;

    public GearModelScheme(String id, Map<GearType, String> paths) {
        this.id = id == null ? "" : id.trim().toLowerCase(Locale.ROOT);
        EnumMap<GearType, String> copy = new EnumMap<>(GearType.class);
        if (paths != null) {
            for (Map.Entry<GearType, String> entry : paths.entrySet()) {
                if (entry.getKey() == null || entry.getValue() == null || entry.getValue().isBlank()) {
                    continue;
                }
                copy.put(entry.getKey(), entry.getValue().trim());
            }
        }
        this.paths = Collections.unmodifiableMap(copy);
    }

    public String getId() {
        return id;
    }

    public String pathFor(GearType type) {
        if (type == null) {
            return null;
        }
        String path = paths.get(type);
        return path == null || path.isBlank() ? null : path;
    }
}
