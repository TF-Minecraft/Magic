package net.tfminecraft.magic.artifact.path;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public final class ArtifactPathSpec {

    private final String primaryId;
    private final String rarityId;
    private final LinkedHashMap<String, Double> extras;

    public ArtifactPathSpec(String primaryId, String rarityId, LinkedHashMap<String, Double> extras) {
        this.primaryId = primaryId;
        this.rarityId = rarityId;
        this.extras = extras == null ? new LinkedHashMap<>() : new LinkedHashMap<>(extras);
    }

    public String getPrimaryId() {
        return primaryId;
    }

    public String getRarityId() {
        return rarityId;
    }

    public Map<String, Double> getExtras() {
        return Collections.unmodifiableMap(extras);
    }

    public boolean isFullyRandom() {
        return primaryId == null && rarityId == null && extras.isEmpty();
    }
}
