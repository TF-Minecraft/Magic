package net.tfminecraft.magic.artifact.config;

public final class CapRange {

    private final double min;
    private final double max;

    public CapRange(double min, double max) {
        this.min = min;
        this.max = max;
    }

    public double getMin() {
        return min;
    }

    public double getMax() {
        return max;
    }

    public static CapRange parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String[] parts = raw.trim().split("-", 2);
        if (parts.length != 2) {
            return null;
        }
        try {
            double min = Double.parseDouble(parts[0].trim());
            double max = Double.parseDouble(parts[1].trim());
            return new CapRange(min, max);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    @Override
    public String toString() {
        return min + "-" + max;
    }
}
