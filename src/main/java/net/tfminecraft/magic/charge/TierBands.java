package net.tfminecraft.magic.charge;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

/**
 * Maps an aura amount to a displayed tier band, per element.
 *
 * <p>Bands are display only. The stored value is always the raw aura, so a charge's
 * tier is derived from what it gathered rather than persisted separately.
 */
public final class TierBands {

    private static final String DEFAULT_KEY = "default";
    private static final String[] NUMERALS = {"I", "II", "III", "IV", "V", "VI", "VII", "VIII"};

    /** element id (or "default") -> band number -> minimum aura for that band. */
    private static final Map<String, TreeMap<Integer, Double>> bands = new LinkedHashMap<>();

    private TierBands() {}

    public static void clear() {
        bands.clear();
    }

    public static void register(String elementId, int band, double minAura) {
        if (band <= 0) {
            return;
        }
        bands.computeIfAbsent(normalize(elementId), key -> new TreeMap<>())
                .put(band, Math.max(0.0, minAura));
    }

    public static boolean isEmpty() {
        return bands.isEmpty();
    }

    public static int size() {
        return bands.size();
    }

    /**
     * Highest band whose threshold the amount meets.
     *
     * @return 0 when no band is reached or none are configured
     */
    public static int bandOf(String elementId, double amount) {
        TreeMap<Integer, Double> table = bands.get(normalize(elementId));
        if (table == null) {
            table = bands.get(DEFAULT_KEY);
        }
        if (table == null || table.isEmpty()) {
            return 0;
        }
        int best = 0;
        for (Map.Entry<Integer, Double> entry : table.entrySet()) {
            if (amount + 0.0001 >= entry.getValue()) {
                best = Math.max(best, entry.getKey());
            }
        }
        return best;
    }

    /** Roman numeral for a band, or an empty string when the band is 0 or unmapped. */
    public static String numeral(int band) {
        if (band <= 0 || band > NUMERALS.length) {
            return "";
        }
        return NUMERALS[band - 1];
    }

    public static String numeralFor(String elementId, double amount) {
        return numeral(bandOf(elementId, amount));
    }

    private static String normalize(String elementId) {
        if (elementId == null || elementId.isBlank()) {
            return DEFAULT_KEY;
        }
        return elementId.trim().toLowerCase(Locale.ROOT);
    }
}
