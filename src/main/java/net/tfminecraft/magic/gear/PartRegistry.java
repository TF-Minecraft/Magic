package net.tfminecraft.magic.gear;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class PartRegistry {

    private static final Map<String, PartDef> BY_ID = new LinkedHashMap<>();

    private PartRegistry() {}

    public static void clear() {
        BY_ID.clear();
    }

    public static void register(PartDef def) {
        if (def == null || def.getId().isEmpty()) {
            return;
        }
        BY_ID.put(def.getId(), def);
    }

    public static PartDef get(String id) {
        if (id == null) {
            return null;
        }
        return BY_ID.get(id.trim().toLowerCase(Locale.ROOT));
    }

    public static Collection<PartDef> getAll() {
        return Collections.unmodifiableCollection(BY_ID.values());
    }

    public static List<PartDef> matching(String partType, GearType type) {
        List<PartDef> out = new ArrayList<>();
        for (PartDef def : BY_ID.values()) {
            if (def.isDisabled()) {
                continue;
            }
            if (partType != null && !def.getPartType().equalsIgnoreCase(partType)) {
                continue;
            }
            if (type != null && !def.supports(type)) {
                continue;
            }
            out.add(def);
        }
        return out;
    }

    public static PartDef firstMatching(String partType, GearType type) {
        List<PartDef> matching = matching(partType, type);
        return matching.isEmpty() ? null : matching.get(0);
    }

    public static int size() {
        return BY_ID.size();
    }
}
