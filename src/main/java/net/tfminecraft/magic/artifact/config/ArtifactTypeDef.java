package net.tfminecraft.magic.artifact.config;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

public final class ArtifactTypeDef {

    private final String elementId;
    private final String namingSchemeId;
    private final String modelSchemeId;
    private final boolean enabled;
    private final double weight;
    private final Map<String, CapRange> primaryByRarity;
    private final Map<String, CapRange> secondaryByRarity;

    public ArtifactTypeDef(
            String elementId,
            String namingSchemeId,
            String modelSchemeId,
            boolean enabled,
            double weight,
            Map<String, CapRange> primaryByRarity,
            Map<String, CapRange> secondaryByRarity) {
        this.elementId = elementId;
        this.namingSchemeId = namingSchemeId;
        this.modelSchemeId = modelSchemeId;
        this.enabled = enabled;
        this.weight = weight;
        this.primaryByRarity = Collections.unmodifiableMap(new LinkedHashMap<>(primaryByRarity));
        this.secondaryByRarity = Collections.unmodifiableMap(new LinkedHashMap<>(secondaryByRarity));
    }

    public String getElementId() {
        return elementId;
    }

    public String getNamingSchemeId() {
        return namingSchemeId;
    }

    public String getModelSchemeId() {
        return modelSchemeId;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public double getWeight() {
        return weight;
    }

    public CapRange getPrimaryCap(String rarityId) {
        return lookup(primaryByRarity, rarityId);
    }

    public CapRange getSecondaryCap(String rarityId) {
        return lookup(secondaryByRarity, rarityId);
    }

    public CapRange getCap(String rarityId) {
        return getPrimaryCap(rarityId);
    }

    public boolean hasRarity(String rarityId) {
        return getPrimaryCap(rarityId) != null;
    }

    public Set<String> getRarityIds() {
        return primaryByRarity.keySet();
    }

    public Map<String, CapRange> getCapsByRarity() {
        return primaryByRarity;
    }

    public CapRange highestCap() {
        CapRange best = null;
        for (CapRange range : primaryByRarity.values()) {
            if (best == null || range.getMax() > best.getMax()) {
                best = range;
            }
        }
        return best;
    }

    private static CapRange lookup(Map<String, CapRange> map, String rarityId) {
        if (rarityId == null || rarityId.isBlank()) {
            return null;
        }
        CapRange exact = map.get(rarityId);
        if (exact != null) {
            return exact;
        }
        for (Map.Entry<String, CapRange> entry : map.entrySet()) {
            if (entry.getKey() != null && entry.getKey().equalsIgnoreCase(rarityId.trim())) {
                return entry.getValue();
            }
        }
        return null;
    }
}
