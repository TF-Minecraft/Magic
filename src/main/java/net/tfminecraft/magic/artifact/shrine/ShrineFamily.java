package net.tfminecraft.magic.artifact.shrine;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

import org.bukkit.Material;

public final class ShrineFamily {

    private final String id;
    private final int maxCount;
    private final double weight;
    private final Set<Material> materials;

    public ShrineFamily(String id, int maxCount, double weight, Set<Material> materials) {
        this.id = id;
        this.maxCount = Math.max(1, maxCount);
        this.weight = weight > 0 ? weight : 1.0;
        this.materials = Collections.unmodifiableSet(new LinkedHashSet<>(
                materials != null ? materials : Set.of()));
    }

    public String getId() {
        return id;
    }

    public int getMaxCount() {
        return maxCount;
    }

    public double getWeight() {
        return weight;
    }

    public Set<Material> getMaterials() {
        return materials;
    }

    public boolean matches(Material material) {
        return material != null && materials.contains(material);
    }
}
