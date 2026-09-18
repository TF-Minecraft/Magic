package net.tfminecraft.magic.session;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import net.tfminecraft.magic.Cache;
import net.tfminecraft.magic.GuiCache;
import net.tfminecraft.magic.model.ElementDef;
import net.tfminecraft.magic.registry.ElementRegistry;
import net.tfminecraft.magic.util.MagicNumbers;

public final class ResonanceSession {

    private static final double EPSILON = 0.0001;

    private String castModeId;
    private double equilibrium;
    private final Map<String, Double> resonance = new HashMap<>();

    public ResonanceSession() {
        this.castModeId = normalizeCastModeId(Cache.defaultCastMode);
        this.equilibrium = Cache.defaultEquilibrium;
    }

    public String getCastModeId() {
        return castModeId;
    }

    public void setCastModeId(String castModeId) {
        this.castModeId = normalizeCastModeId(castModeId);
    }

    public double getEquilibrium() {
        return equilibrium;
    }

    public void setEquilibrium(double value) {
        this.equilibrium = MagicNumbers.round(
                MagicNumbers.clamp(value, GuiCache.equilibriumMin, GuiCache.equilibriumMax), 2);
    }

    public double getResonance(String elementId) {
        ElementDef element = ElementRegistry.getById(elementId);
        if (element == null) {
            return 0.0;
        }
        double value = amount(element.getId());
        return MagicNumbers.round(MagicNumbers.clamp(value, 0.0, element.getMaxResonance()), 2);
    }

    public void setResonance(String elementId, double value) {
        ElementDef element = ElementRegistry.getById(elementId);
        if (element == null) {
            return;
        }
        double target = MagicNumbers.round(MagicNumbers.clamp(value, 0.0, element.getMaxResonance()), 2);
        if (target <= EPSILON) {
            resonance.remove(element.getId());
            return;
        }
        resonance.put(element.getId(), target);
    }

    public void addResonance(String elementId, double delta) {
        if (Math.abs(delta) <= EPSILON) {
            return;
        }
        setResonance(elementId, getResonance(elementId) + delta);
    }

    public void setResonanceMap(Map<String, Double> next) {
        resonance.clear();
        if (next == null) {
            return;
        }
        for (Map.Entry<String, Double> entry : next.entrySet()) {
            if (entry.getKey() == null || entry.getValue() == null) {
                continue;
            }
            setResonance(entry.getKey(), entry.getValue());
        }
    }

    public Map<String, Double> copyResonance() {
        Map<String, Double> copy = new HashMap<>();
        for (Map.Entry<String, Double> entry : resonance.entrySet()) {
            if (entry.getKey() == null || entry.getValue() == null || entry.getValue() <= EPSILON) {
                continue;
            }
            copy.put(entry.getKey(), entry.getValue());
        }
        return copy;
    }

    private double amount(String elementId) {
        String id = normalizeElementId(elementId);
        if (id.isEmpty()) {
            return 0.0;
        }
        Double value = resonance.get(id);
        return value != null ? value : 0.0;
    }

    private static String normalizeElementId(String elementId) {
        if (elementId == null) {
            return "";
        }
        return elementId.trim().toLowerCase(Locale.ROOT);
    }

    private static String normalizeCastModeId(String castModeId) {
        if (castModeId == null || castModeId.isBlank()) {
            return GuiCache.castModeLeft.getId();
        }
        String normalized = castModeId.toLowerCase();
        if (normalized.equals(GuiCache.castModeLeft.getId())) {
            return GuiCache.castModeLeft.getId();
        }
        if (normalized.equals(GuiCache.castModeRight.getId())) {
            return GuiCache.castModeRight.getId();
        }
        return GuiCache.castModeLeft.getId();
    }
}
