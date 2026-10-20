package net.tfminecraft.magic.meditation;

import java.util.Collections;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public final class MeditationSitYield {

    private static final double EPSILON = 0.005;

    private final Map<String, Double> sessionCapByArtifact;
    private final Map<String, Integer> usersByArtifact;
    private final Map<String, Double> shareByElement;

    public MeditationSitYield(Map<String, Double> sessionCapByArtifact, Map<String, Integer> usersByArtifact) {
        this(sessionCapByArtifact, usersByArtifact, null);
    }

    public MeditationSitYield(
            Map<String, Double> sessionCapByArtifact,
            Map<String, Integer> usersByArtifact,
            Map<String, Double> shareByElement) {
        this.sessionCapByArtifact = sessionCapByArtifact != null
                ? Map.copyOf(sessionCapByArtifact)
                : Map.of();
        this.usersByArtifact = usersByArtifact != null
                ? Map.copyOf(usersByArtifact)
                : Map.of();
        Map<String, Double> shares = new HashMap<>();
        if (shareByElement != null) {
            for (Map.Entry<String, Double> entry : shareByElement.entrySet()) {
                if (entry.getKey() != null && entry.getValue() != null) {
                    shares.merge(key(entry.getKey()), entry.getValue(), Double::sum);
                }
            }
        }
        this.shareByElement = Map.copyOf(shares);
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

    /**
     * This sitter's share of the circle's aura in one element: each artifact's usable
     * aura divided by its users, summed. Meditation cannot raise the element above it.
     */
    public double elementShare(String elementId) {
        if (elementId == null || elementId.isBlank()) {
            return 0.0;
        }
        return Math.max(0.0, shareByElement.getOrDefault(key(elementId), 0.0));
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
            double cap = entry.getValue();
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

    private static String key(String elementId) {
        return elementId.trim().toLowerCase(Locale.ROOT);
    }
}
