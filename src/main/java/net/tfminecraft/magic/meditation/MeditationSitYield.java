package net.tfminecraft.magic.meditation;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public final class MeditationSitYield {

    private static final double EPSILON = 0.005;

    private final Map<String, Double> sessionCapByArtifact;
    private final Map<String, Integer> usersByArtifact;

    public MeditationSitYield(Map<String, Double> sessionCapByArtifact, Map<String, Integer> usersByArtifact) {
        this.sessionCapByArtifact = sessionCapByArtifact != null
                ? Map.copyOf(sessionCapByArtifact)
                : Map.of();
        this.usersByArtifact = usersByArtifact != null
                ? Map.copyOf(usersByArtifact)
                : Map.of();
    }

    public static MeditationSitYield empty() {
        return new MeditationSitYield(new HashMap<>(), new HashMap<>());
    }

    public double sessionCap(String artifactId) {
        if (artifactId == null) {
            return 0.0;
        }
        return Math.max(0.0, sessionCapByArtifact.getOrDefault(artifactId, 0.0));
    }

    public int users(String artifactId) {
        if (artifactId == null) {
            return 1;
        }
        return Math.max(1, usersByArtifact.getOrDefault(artifactId, 1));
    }

    public boolean hasAnyCap() {
        for (double cap : sessionCapByArtifact.values()) {
            if (cap > EPSILON) {
                return true;
            }
        }
        return false;
    }

    public boolean exhausted(Map<String, Double> attunedByArtifact) {
        for (Map.Entry<String, Double> entry : sessionCapByArtifact.entrySet()) {
            double cap = entry.getValue() != null ? entry.getValue() : 0.0;
            if (cap <= EPSILON) {
                continue;
            }
            double credit = attunedByArtifact == null
                    ? 0.0
                    : attunedByArtifact.getOrDefault(entry.getKey(), 0.0);
            if (credit + EPSILON < cap) {
                return false;
            }
        }
        return true;
    }

    public Map<String, Double> sessionCaps() {
        return Collections.unmodifiableMap(sessionCapByArtifact);
    }
}
