package net.tfminecraft.magic.artifact.config;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import net.tfminecraft.magic.artifact.config.ArtifactTypeDef;
import net.tfminecraft.magic.artifact.config.ArtifactTypeRegistry;

public final class ArtifactAffinityRegistry {

    private static final Map<String, List<String>> groups = new LinkedHashMap<>();
    private static final List<ExcludeRule> excludes = new ArrayList<>();

    private ArtifactAffinityRegistry() {}

    public static void clear() {
        groups.clear();
        excludes.clear();
    }

    public static void registerGroup(String groupId, List<String> elementIds) {
        if (groupId == null || groupId.isBlank() || elementIds == null) {
            return;
        }
        groups.put(groupId, List.copyOf(elementIds));
    }

    public static void registerExclude(List<String> never, List<String> with) {
        Set<String> campA = normalizeCamp(never);
        Set<String> campB = normalizeCamp(with);
        if (campA.isEmpty() || campB.isEmpty()) {
            return;
        }
        excludes.add(new ExcludeRule(campA, campB));
    }

    public static Map<String, List<String>> getGroups() {
        Map<String, List<String>> copy = new LinkedHashMap<>();
        for (Map.Entry<String, List<String>> entry : groups.entrySet()) {
            copy.put(entry.getKey(), entry.getValue());
        }
        return Collections.unmodifiableMap(copy);
    }

    public static List<String> getGroup(String groupId) {
        if (groupId == null || groupId.isBlank()) {
            return List.of();
        }
        List<String> exact = groups.get(groupId);
        if (exact != null) {
            return exact;
        }
        for (Map.Entry<String, List<String>> entry : groups.entrySet()) {
            if (entry.getKey() != null && entry.getKey().equalsIgnoreCase(groupId)) {
                return entry.getValue();
            }
        }
        return List.of();
    }

    public static int excludeSize() {
        return excludes.size();
    }

    public static boolean compatible(String a, String b) {
        if (a == null || b == null || a.isBlank() || b.isBlank()) {
            return false;
        }
        if (a.equalsIgnoreCase(b)) {
            return true;
        }
        String left = a.trim().toLowerCase(Locale.ROOT);
        String right = b.trim().toLowerCase(Locale.ROOT);
        for (ExcludeRule rule : excludes) {
            if (rule.blocks(left, right)) {
                return false;
            }
        }
        return true;
    }

    public static boolean compatibleWith(Collection<String> selected, String candidate) {
        if (candidate == null || candidate.isBlank()) {
            return false;
        }
        if (selected == null || selected.isEmpty()) {
            return true;
        }
        for (String id : selected) {
            if (id == null || id.isBlank() || id.equalsIgnoreCase(candidate)) {
                continue;
            }
            if (!compatible(id, candidate)) {
                return false;
            }
        }
        return true;
    }

    public static List<String> companions(String primaryId) {
        if (primaryId == null || primaryId.isBlank()) {
            return List.of();
        }
        Set<String> out = new LinkedHashSet<>();
        for (List<String> members : groups.values()) {
            boolean contains = false;
            for (String member : members) {
                if (primaryId.equalsIgnoreCase(member)) {
                    contains = true;
                    break;
                }
            }
            if (!contains) {
                continue;
            }
            for (String member : members) {
                if (primaryId.equalsIgnoreCase(member)) {
                    continue;
                }
                if (compatible(primaryId, member)) {
                    ArtifactTypeDef type = ArtifactTypeRegistry.getById(member);
                    if (type == null) {
                        for (ArtifactTypeDef candidate : ArtifactTypeRegistry.getAll()) {
                            if (candidate.getElementId().equalsIgnoreCase(member)) {
                                type = candidate;
                                break;
                            }
                        }
                    }
                    if (type != null && type.isEnabled()) {
                        out.add(member);
                    }
                }
            }
        }
        return Collections.unmodifiableList(new ArrayList<>(out));
    }

    public static int size() {
        return groups.size();
    }

    private static Set<String> normalizeCamp(List<String> ids) {
        Set<String> out = new LinkedHashSet<>();
        if (ids == null) {
            return out;
        }
        for (String id : ids) {
            if (id == null || id.isBlank()) {
                continue;
            }
            out.add(id.trim().toLowerCase(Locale.ROOT));
        }
        return out;
    }

    private static final class ExcludeRule {
        private final Set<String> campA;
        private final Set<String> campB;

        private ExcludeRule(Set<String> campA, Set<String> campB) {
            this.campA = campA;
            this.campB = campB;
        }

        private boolean blocks(String a, String b) {
            return (campA.contains(a) && campB.contains(b))
                    || (campA.contains(b) && campB.contains(a));
        }
    }
}
