package net.tfminecraft.magic.gear;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class PartSlots {

    public static final String CORE = "core";

    private PartSlots() {}

    /**
     * Part categories that are visible and mandatory for this archetype + core.
     * Core is included when the archetype lists it. Extra slots are the intersection
     * of archetype required and the core's part-limit. No limit means the full
     * required list (never extra part-types.yml categories).
     */
    public static List<String> open(ArchetypeDef archetype, PartDef core) {
        if (archetype == null) {
            return List.of();
        }
        List<String> required = new ArrayList<>();
        for (String id : archetype.getRequired()) {
            if (id == null || id.isBlank()) {
                continue;
            }
            String key = id.trim().toLowerCase(Locale.ROOT);
            if (!required.contains(key)) {
                required.add(key);
            }
        }
        if (core == null || !core.hasPartLimit()) {
            return List.copyOf(required);
        }
        List<String> open = new ArrayList<>();
        if (required.contains(CORE)) {
            open.add(CORE);
        }
        for (String id : core.getPartLimit()) {
            if (id == null || id.isBlank()) {
                continue;
            }
            String key = id.trim().toLowerCase(Locale.ROOT);
            if (CORE.equals(key) || open.contains(key) || !required.contains(key)) {
                continue;
            }
            open.add(key);
        }
        return List.copyOf(open);
    }

    public static boolean contains(List<String> open, String category) {
        if (open == null || category == null || category.isBlank()) {
            return false;
        }
        return open.contains(category.trim().toLowerCase(Locale.ROOT));
    }
}
