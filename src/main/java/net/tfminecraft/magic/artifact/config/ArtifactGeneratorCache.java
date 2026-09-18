package net.tfminecraft.magic.artifact.config;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

public final class ArtifactGeneratorCache {

    public static String template = "m.artifacts.template_artifact";
    public static List<String> randomPool = List.of();

    private ArtifactGeneratorCache() {}

    public static void reset() {
        template = "m.artifacts.template_artifact";
        randomPool = List.of();
    }

    public static boolean inRandomPool(String elementId) {
        if (randomPool == null || randomPool.isEmpty()) {
            return true;
        }
        if (elementId == null || elementId.isBlank()) {
            return false;
        }
        String wanted = elementId.trim().toLowerCase(Locale.ROOT);
        for (String id : randomPool) {
            if (id != null && id.equalsIgnoreCase(wanted)) {
                return true;
            }
        }
        return false;
    }

    public static void setRandomPool(List<String> ids) {
        if (ids == null || ids.isEmpty()) {
            randomPool = List.of();
            return;
        }
        randomPool = Collections.unmodifiableList(new ArrayList<>(ids));
    }
}
