package net.tfminecraft.magic.registry;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

public final class SkillElementRegistry {

    public static final int DEFAULT_TIER = 1;
    public static final int MAX_TIER = 4;

    public record Binding(String elementId, int tier) {}

    private static final Map<String, Binding> skills = new LinkedHashMap<>();

    private SkillElementRegistry() {}

    public static void clear() {
        skills.clear();
    }

    public static void register(String skillId, String elementId) {
        register(skillId, elementId, DEFAULT_TIER);
    }

    public static void register(String skillId, String elementId, int tier) {
        if (skillId == null || skillId.isBlank() || elementId == null || elementId.isBlank()) {
            return;
        }
        skills.put(
                skillId.trim().toLowerCase(Locale.ROOT),
                new Binding(elementId.trim().toLowerCase(Locale.ROOT), clampTier(tier)));
    }

    public static String elementOf(String skillId) {
        Binding binding = bindingOf(skillId);
        return binding == null ? null : binding.elementId();
    }

    public static int tierOf(String skillId) {
        Binding binding = bindingOf(skillId);
        return binding == null ? DEFAULT_TIER : binding.tier();
    }

    public static Map<String, String> bindings() {
        Map<String, String> copy = new LinkedHashMap<>();
        for (Map.Entry<String, Binding> entry : skills.entrySet()) {
            copy.put(entry.getKey(), entry.getValue().elementId());
        }
        return Collections.unmodifiableMap(copy);
    }

    public static int size() {
        return skills.size();
    }

    public static int clampTier(int tier) {
        if (tier < DEFAULT_TIER) {
            return DEFAULT_TIER;
        }
        if (tier > MAX_TIER) {
            return MAX_TIER;
        }
        return tier;
    }

    private static Binding bindingOf(String skillId) {
        if (skillId == null || skillId.isBlank()) {
            return null;
        }
        return skills.get(skillId.trim().toLowerCase(Locale.ROOT));
    }
}
