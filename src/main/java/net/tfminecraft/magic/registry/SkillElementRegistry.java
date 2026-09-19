package net.tfminecraft.magic.registry;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

public final class SkillElementRegistry {

    private static final Map<String, String> skillToElement = new LinkedHashMap<>();

    private SkillElementRegistry() {}

    public static void clear() {
        skillToElement.clear();
    }

    public static void register(String skillId, String elementId) {
        if (skillId == null || skillId.isBlank() || elementId == null || elementId.isBlank()) {
            return;
        }
        skillToElement.put(
                skillId.trim().toLowerCase(Locale.ROOT), elementId.trim().toLowerCase(Locale.ROOT));
    }

    public static String elementOf(String skillId) {
        if (skillId == null || skillId.isBlank()) {
            return null;
        }
        return skillToElement.get(skillId.trim().toLowerCase(Locale.ROOT));
    }

    public static Map<String, String> bindings() {
        return Collections.unmodifiableMap(skillToElement);
    }

    public static int size() {
        return skillToElement.size();
    }
}
