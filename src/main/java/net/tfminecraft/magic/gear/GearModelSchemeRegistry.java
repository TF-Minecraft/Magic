package net.tfminecraft.magic.gear;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

public final class GearModelSchemeRegistry {

    private static final Map<String, GearModelScheme> BY_ID = new LinkedHashMap<>();

    private GearModelSchemeRegistry() {}

    public static void clear() {
        BY_ID.clear();
    }

    public static void register(GearModelScheme scheme) {
        if (scheme == null || scheme.getId().isEmpty()) {
            return;
        }
        BY_ID.put(scheme.getId(), scheme);
    }

    public static boolean contains(String id) {
        return get(id) != null;
    }

    public static GearModelScheme get(String id) {
        if (id == null || id.isBlank()) {
            return null;
        }
        return BY_ID.get(id.trim().toLowerCase(Locale.ROOT));
    }

    public static int size() {
        return BY_ID.size();
    }

    public static Map<String, GearModelScheme> all() {
        return Collections.unmodifiableMap(BY_ID);
    }
}
