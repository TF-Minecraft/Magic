package net.tfminecraft.magic.gear;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public final class PartTypeRegistry {

    private static final Map<String, PartTypeDef> BY_ID = new LinkedHashMap<>();

    private PartTypeRegistry() {}

    public static void clear() {
        BY_ID.clear();
    }

    public static void register(PartTypeDef def) {
        if (def == null || def.getId().isEmpty()) {
            return;
        }
        BY_ID.put(def.getId(), def);
    }

    public static PartTypeDef get(String id) {
        if (id == null) {
            return null;
        }
        return BY_ID.get(id.trim().toLowerCase());
    }

    public static Collection<PartTypeDef> getAll() {
        return Collections.unmodifiableCollection(BY_ID.values());
    }

    public static String idForSlot(int slot) {
        for (PartTypeDef def : BY_ID.values()) {
            if (def.getSlot() == slot) {
                return def.getId();
            }
        }
        return null;
    }

    public static int size() {
        return BY_ID.size();
    }
}
