package net.tfminecraft.magic.artifact.sacrifice;

import java.util.Locale;

public final class SacrificeTierDef {

    private final String id;
    private final double min;
    private final double below;
    private final double auraFraction;
    private final String injury;
    private final boolean permadeath;

    public SacrificeTierDef(
            String id,
            double min,
            double below,
            double auraFraction,
            String injury,
            boolean permadeath) {
        this.id = id == null ? "" : id.trim().toLowerCase(Locale.ROOT);
        this.min = min;
        this.below = below;
        this.auraFraction = auraFraction;
        this.injury = injury == null || injury.isBlank() ? "none" : injury.trim().toLowerCase(Locale.ROOT);
        this.permadeath = permadeath;
    }

    public String getId() {
        return id;
    }

    public double getMin() {
        return min;
    }

    public double getBelow() {
        return below;
    }

    public double getAuraFraction() {
        return auraFraction;
    }

    public String getInjury() {
        return injury;
    }

    public boolean isPermadeath() {
        return permadeath;
    }

    public boolean matches(double charge) {
        if (charge + 1e-9 < min) {
            return false;
        }
        if (below > 0 && charge + 1e-9 >= below) {
            return false;
        }
        return true;
    }
}
