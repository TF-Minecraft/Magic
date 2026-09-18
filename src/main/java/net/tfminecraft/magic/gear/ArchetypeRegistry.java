package net.tfminecraft.magic.gear;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;

public final class ArchetypeRegistry {

    private static final Map<GearType, ArchetypeDef> BY_TYPE = new EnumMap<>(GearType.class);

    private ArchetypeRegistry() {}

    public static void clear() {
        BY_TYPE.clear();
    }

    public static void register(ArchetypeDef def) {
        if (def == null || def.getType() == null) {
            return;
        }
        BY_TYPE.put(def.getType(), def);
    }

    public static ArchetypeDef get(GearType type) {
        return type == null ? null : BY_TYPE.get(type);
    }

    public static Map<GearType, ArchetypeDef> getAll() {
        return Collections.unmodifiableMap(BY_TYPE);
    }

    public static int size() {
        return BY_TYPE.size();
    }
}
