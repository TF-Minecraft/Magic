package net.tfminecraft.magic.artifact.sealed;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * One sealed artifact item from artifacts/sealed.yml.
 *
 * <p>A null element means the school is rolled from generator.yml's random_pool.
 */
public final class SealedArtifactDef {

    private final String id;
    private final String itemPath;
    private final String elementId;
    private final Map<String, Double> rarities;

    public SealedArtifactDef(String id, String itemPath, String elementId, Map<String, Double> rarities) {
        this.id = id;
        this.itemPath = itemPath;
        this.elementId = elementId;
        this.rarities = Collections.unmodifiableMap(new LinkedHashMap<>(rarities));
    }

    public String getId() {
        return id;
    }

    public String getItemPath() {
        return itemPath;
    }

    /** The locked school, or null for a random school. */
    public String getElementId() {
        return elementId;
    }

    /** Rarity id to weight, every weight above zero. */
    public Map<String, Double> getRarities() {
        return rarities;
    }
}
