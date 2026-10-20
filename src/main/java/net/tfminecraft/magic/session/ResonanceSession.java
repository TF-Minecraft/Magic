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
    /** Stored values keep two decimals, so passive drift is applied in hundredths. */
    private static final double DRIFT_STEP = 0.01;

    private String castModeId;
    private double equilibrium;
    private final Map<String, Double> resonance = new HashMap<>();
    private final Map<String, Double> resonanceDrift = new HashMap<>();
    private double equilibriumDrift;
    private long modifierRevision;

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

    public long modifierRevision() {
        return modifierRevision;
    }

    public void setEquilibrium(double value) {
        double next = MagicNumbers.round(
                MagicNumbers.clamp(value, GuiCache.equilibriumMin, GuiCache.equilibriumMax), 2);
        if (next == this.equilibrium) {
            return;
        }
        this.equilibrium = next;
        bumpModifiers();
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
        double previous = amount(element.getId());
        if (target <= EPSILON) {
            if (resonance.remove(element.getId()) != null) {
                bumpModifiers();
            }
            return;
        }
        if (previous == target) {
            return;
        }
        resonance.put(element.getId(), target);
        bumpModifiers();
    }

    public void addResonance(String elementId, double delta) {
        if (Math.abs(delta) <= EPSILON) {
            return;
        }
        setResonance(elementId, getResonance(elementId) + delta);
    }

    /**
     * Passive drift per tick is far below the two stored decimals, so it is carried here
     * until it reaches whole hundredths and applied then. Hitting 0 or the element's max
     * drops the remainder. Only kept in memory: offline time does not drift.
     *
     * @return true when the stored resonance changed
     */
    public boolean driftResonance(String elementId, double delta) {
        ElementDef element = ElementRegistry.getById(elementId);
        if (element == null) {
            return false;
        }
        String id = element.getId();
        double pending = resonanceDrift.getOrDefault(id, 0.0) + delta;
        double step = wholeSteps(pending);
        if (step == 0.0) {
            resonanceDrift.put(id, pending);
            return false;
        }
        double before = getResonance(id);
        setResonance(id, before + step);
        double after = getResonance(id);
        if (Math.abs(after - before - step) > EPSILON) {
            resonanceDrift.remove(id);
        } else {
            resonanceDrift.put(id, pending - step);
        }
        return after != before;
    }

    public void clearResonanceDrift(String elementId) {
        ElementDef element = ElementRegistry.getById(elementId);
        if (element != null) {
            resonanceDrift.remove(element.getId());
        }
    }

    /**
     * Moves equilibrium down by {@code delta} (negative) without passing {@code floor}, carried
     * like {@link #driftResonance} until it reaches whole hundredths.
     */
    public void driftEquilibrium(double delta, double floor) {
        double pending = equilibriumDrift + delta;
        double step = wholeSteps(pending);
        if (step == 0.0) {
            equilibriumDrift = pending;
            return;
        }
        double target = equilibrium + step;
        if (target < floor) {
            setEquilibrium(floor);
            equilibriumDrift = 0.0;
            return;
        }
        setEquilibrium(target);
        equilibriumDrift = pending - step;
    }

    public void clearEquilibriumDrift() {
        equilibriumDrift = 0.0;
    }

    private static double wholeSteps(double pending) {
        double steps = pending / DRIFT_STEP;
        double whole = pending > 0.0 ? Math.floor(steps + 1e-9) : Math.ceil(steps - 1e-9);
        return whole * DRIFT_STEP;
    }

    public void setResonanceMap(Map<String, Double> next) {
        boolean hadValues = !resonance.isEmpty();
        resonance.clear();
        resonanceDrift.clear();
        if (next != null) {
            for (Map.Entry<String, Double> entry : next.entrySet()) {
                if (entry.getKey() == null || entry.getValue() == null) {
                    continue;
                }
                setResonance(entry.getKey(), entry.getValue());
            }
        }
        if (hadValues && resonance.isEmpty()) {
            bumpModifiers();
        }
    }

    public Map<String, Double> copyResonance() {
        return new HashMap<>(resonance);
    }

    private void bumpModifiers() {
        modifierRevision++;
    }

    private double amount(String elementId) {
        String id = elementId.trim().toLowerCase(Locale.ROOT);
        Double value = resonance.get(id);
        return value != null ? value : 0.0;
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
