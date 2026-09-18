package net.tfminecraft.magic.artifact.config;

import java.util.List;
import java.util.Locale;

public final class ArtifactModelEntry {

    private final String kind;
    private final String path;
    private final String minRarityId;
    private final String maxRarityId;

    public ArtifactModelEntry(String kind, String path, String minRarityId, String maxRarityId) {
        this.kind = kind;
        this.path = path;
        this.minRarityId = minRarityId;
        this.maxRarityId = maxRarityId;
    }

    public String getKind() {
        return kind;
    }

    public String getPath() {
        return path;
    }

    public String getMinRarityId() {
        return minRarityId;
    }

    public String getMaxRarityId() {
        return maxRarityId;
    }

    public boolean eligible(String rarityId) {
        if (rarityId == null || rarityId.isBlank()) {
            return false;
        }
        if (minRarityId == null && maxRarityId == null) {
            return ArtifactRarityRegistry.contains(rarityId);
        }
        int current = rarityIndex(rarityId);
        int min = rarityIndex(minRarityId);
        int max = rarityIndex(maxRarityId);
        if (current < 0 || min < 0 || max < 0) {
            return false;
        }
        if (min > max) {
            int swap = min;
            min = max;
            max = swap;
        }
        return current >= min && current <= max;
    }

    private static int rarityIndex(String id) {
        if (id == null || id.isBlank()) {
            return -1;
        }
        List<ArtifactRarityDef> all = ArtifactRarityRegistry.getAll();
        for (int i = 0; i < all.size(); i++) {
            if (id.equalsIgnoreCase(all.get(i).getId())) {
                return i;
            }
        }
        return -1;
    }

    public static ArtifactModelEntry parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String trimmed = raw.trim();
        int open = trimmed.indexOf('(');
        int close = trimmed.lastIndexOf(')');
        if (open <= 0 || close <= open) {
            return null;
        }
        String kind = trimmed.substring(0, open).trim();
        String path = trimmed.substring(open + 1, close).trim();
        if (kind.isEmpty() || path.isEmpty()) {
            return null;
        }
        String suffix = close + 1 < trimmed.length() ? trimmed.substring(close + 1).trim() : "";
        String min = null;
        String max = null;
        if (!suffix.isEmpty() && !suffix.equalsIgnoreCase("all")) {
            int dash = suffix.indexOf('-');
            if (dash <= 0 || dash >= suffix.length() - 1) {
                min = suffix.toLowerCase(Locale.ROOT);
                max = min;
            } else {
                min = suffix.substring(0, dash).trim().toLowerCase(Locale.ROOT);
                max = suffix.substring(dash + 1).trim().toLowerCase(Locale.ROOT);
                if (min.isEmpty() || max.isEmpty()) {
                    return null;
                }
            }
        }
        return new ArtifactModelEntry(kind, path, min, max);
    }
}
