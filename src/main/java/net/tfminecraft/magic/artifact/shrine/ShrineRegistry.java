package net.tfminecraft.magic.artifact.shrine;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class ShrineRegistry {

    private static int radius = 3;
    private static double minScore = 0.15;
    private static double fullChargeSeconds = 15.0;
    private static int minFamilies = 2;
    private static ShrineFxDef defaultFx = ShrineFxDef.fallback();
    private static final Map<String, ShrineFxDef> fxByElement = new LinkedHashMap<>();
    private static final Map<String, ShrineElementDef> elements = new LinkedHashMap<>();

    private ShrineRegistry() {}

    public static void clear() {
        radius = 3;
        minScore = 0.15;
        fullChargeSeconds = 15.0;
        minFamilies = 2;
        defaultFx = ShrineFxDef.fallback();
        fxByElement.clear();
        elements.clear();
    }

    public static void setScan(int radiusValue, double minScoreValue, double fullChargeSecondsValue, int minFamiliesValue) {
        radius = Math.max(1, radiusValue);
        minScore = Math.max(0.0, minScoreValue);
        fullChargeSeconds = fullChargeSecondsValue > 0 ? fullChargeSecondsValue : 15.0;
        minFamilies = Math.max(1, minFamiliesValue);
    }

    public static void setDefaultFx(ShrineFxDef fx) {
        defaultFx = fx != null ? fx : ShrineFxDef.fallback();
    }

    public static void setElementFx(String elementId, ShrineFxDef fx) {
        if (elementId == null || elementId.isBlank() || fx == null) {
            return;
        }
        fxByElement.put(elementId.trim().toLowerCase(Locale.ROOT), fx);
    }

    public static ShrineFxDef fx(String elementId) {
        if (elementId != null && !elementId.isBlank()) {
            ShrineFxDef exact = fxByElement.get(elementId.trim().toLowerCase(Locale.ROOT));
            if (exact != null) {
                return exact;
            }
        }
        return defaultFx;
    }

    public static void register(ShrineElementDef def) {
        if (def == null || def.getElementId() == null || def.getElementId().isBlank()) {
            return;
        }
        elements.put(def.getElementId(), def);
    }

    public static int getRadius() {
        return radius;
    }

    public static double getMinScore() {
        return minScore;
    }

    public static double getFullChargeSeconds() {
        return fullChargeSeconds;
    }

    public static int getMinFamilies() {
        return minFamilies;
    }

    public static ShrineElementDef getById(String elementId) {
        if (elementId == null || elementId.isBlank()) {
            return null;
        }
        return elements.get(elementId.trim().toLowerCase(Locale.ROOT));
    }

    public static List<ShrineElementDef> getAll() {
        return Collections.unmodifiableList(new ArrayList<>(elements.values()));
    }

    public static int size() {
        return elements.size();
    }
}
