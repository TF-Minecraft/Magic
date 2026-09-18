package net.tfminecraft.magic.attunement;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import net.tfminecraft.magic.util.MagicNumbers;

public final class ArtifactClaim {

    private static final double EPSILON = 0.0000001;

    private Map<String, Double> byElement = new HashMap<>();
    private double offDisplaySeconds;
    private boolean waneWarned;
    private String displayName = "";

    public ArtifactClaim() {}

    public ArtifactClaim copy() {
        ArtifactClaim copy = new ArtifactClaim();
        copy.byElement = new HashMap<>();
        if (byElement != null) {
            copy.byElement.putAll(byElement);
        }
        copy.offDisplaySeconds = offDisplaySeconds;
        copy.waneWarned = waneWarned;
        copy.displayName = displayName != null ? displayName : "";
        return copy;
    }

    public Map<String, Double> getByElement() {
        if (byElement == null) {
            byElement = new HashMap<>();
        }
        return byElement;
    }

    public void setByElement(Map<String, Double> byElement) {
        this.byElement = byElement != null ? byElement : new HashMap<>();
    }

    public double getAmount(String elementId) {
        String id = normalize(elementId);
        if (id.isEmpty() || byElement == null) {
            return 0.0;
        }
        Double value = byElement.get(id);
        return value != null ? value : 0.0;
    }

    public void setAmount(String elementId, double value) {
        String id = normalize(elementId);
        if (id.isEmpty()) {
            return;
        }
        if (byElement == null) {
            byElement = new HashMap<>();
        }
        double next = MagicNumbers.round(Math.max(0.0, value), MagicNumbers.CLAIM_DECIMALS);
        if (next <= EPSILON) {
            byElement.remove(id);
            return;
        }
        byElement.put(id, next);
    }

    public boolean hasAnyAmount() {
        if (byElement == null) {
            return false;
        }
        for (Double value : byElement.values()) {
            if (value != null && value > EPSILON) {
                return true;
            }
        }
        return false;
    }

    public boolean isEmpty() {
        return !hasAnyAmount();
    }

    public double getOffDisplaySeconds() {
        return offDisplaySeconds;
    }

    public void setOffDisplaySeconds(double offDisplaySeconds) {
        this.offDisplaySeconds = Math.max(0.0, offDisplaySeconds);
    }

    public boolean isWaneWarned() {
        return waneWarned;
    }

    public void setWaneWarned(boolean waneWarned) {
        this.waneWarned = waneWarned;
    }

    public String getDisplayName() {
        return displayName != null ? displayName : "";
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName != null ? displayName : "";
    }

    private static String normalize(String elementId) {
        if (elementId == null) {
            return "";
        }
        return elementId.trim().toLowerCase(Locale.ROOT);
    }
}
