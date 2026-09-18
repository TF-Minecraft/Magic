package net.tfminecraft.magic.artifact.generate;

public final class ArtifactAuraSlot {

    private final String elementId;
    private final double cap;

    public ArtifactAuraSlot(String elementId, double cap) {
        this.elementId = elementId;
        this.cap = cap;
    }

    public String getElementId() {
        return elementId;
    }

    public double getCap() {
        return cap;
    }
}
