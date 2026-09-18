package net.tfminecraft.magic.artifact.config;

public final class ArtifactRarityDef {

    private final String id;
    private final String name;
    private final String color;
    private final double weight;
    private final int minElements;
    private final int maxElements;
    private final CapRange auraCap;

    public ArtifactRarityDef(
            String id,
            String name,
            String color,
            double weight,
            int minElements,
            int maxElements,
            CapRange auraCap) {
        this.id = id;
        this.name = name;
        this.color = color;
        this.weight = weight;
        this.minElements = minElements;
        this.maxElements = maxElements;
        this.auraCap = auraCap;
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getColor() {
        return color;
    }

    public double getWeight() {
        return weight;
    }

    public int getMinElements() {
        return minElements;
    }

    public int getMaxElements() {
        return maxElements;
    }

    public CapRange getAuraCapRange() {
        return auraCap;
    }

    public double getAuraCapMax() {
        return auraCap != null ? auraCap.getMax() : 0.0;
    }
}
