package net.tfminecraft.magic.artifact.config;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class ArtifactNamingScheme {

    private final String id;
    private final Map<String, List<String>> namesByKind;

    public ArtifactNamingScheme(String id, Map<String, List<String>> namesByKind) {
        this.id = id;
        LinkedHashMap<String, List<String>> copy = new LinkedHashMap<>();
        if (namesByKind != null) {
            for (Map.Entry<String, List<String>> entry : namesByKind.entrySet()) {
                if (entry.getKey() == null || entry.getKey().isBlank() || entry.getValue() == null) {
                    continue;
                }
                copy.put(entry.getKey().toLowerCase(Locale.ROOT), List.copyOf(entry.getValue()));
            }
        }
        this.namesByKind = Collections.unmodifiableMap(copy);
    }

    public String getId() {
        return id;
    }

    public boolean isEmpty() {
        for (List<String> names : namesByKind.values()) {
            if (names != null && !names.isEmpty()) {
                return false;
            }
        }
        return true;
    }

    public List<String> namesFor(String kind) {
        if (kind == null || kind.isBlank()) {
            return List.of();
        }
        List<String> names = namesByKind.get(kind.toLowerCase(Locale.ROOT));
        return names != null ? names : List.of();
    }

    public List<String> firstNonEmptyKindNames() {
        for (List<String> names : namesByKind.values()) {
            if (names != null && !names.isEmpty()) {
                return names;
            }
        }
        return List.of();
    }
}
