package net.tfminecraft.magic.gear;

import java.util.LinkedHashMap;
import java.util.Map;

import net.tfminecraft.magic.modifier.ModifierSet;

public final class GearCache {

    public static String station = "iaf(tfmc:mage_crafting_station)";
    public static int outputSlot = 16;
    public static long confirmMillis = 5000L;

    /** Share of the weapon's part costs a reset of its attunement takes. */
    public static final double DEFAULT_REATTUNE_COST_FRACTION = 0.5;
    public static double reattuneCostFraction = DEFAULT_REATTUNE_COST_FRACTION;

    public static boolean alignmentEnabled = false;

    /** When false, gem socket colour strings omit Common/Rare/Epic/Legendary prefix. */
    public static boolean socketRarityPrefix = false;

    /** Weapon tier band in the spell's element -> bonus. Never a penalty. */
    private static final Map<Integer, ModifierSet> ALIGNMENT = new LinkedHashMap<>();

    private GearCache() {}

    public static void clearAlignment() {
        ALIGNMENT.clear();
    }

    public static void putAlignment(int band, ModifierSet triple) {
        if (band > 0 && triple != null) {
            ALIGNMENT.put(band, triple);
        }
    }

    public static ModifierSet alignment(int band) {
        if (!alignmentEnabled || band <= 0) {
            return ModifierSet.ZERO;
        }
        ModifierSet triple = ALIGNMENT.get(band);
        return triple == null ? ModifierSet.ZERO : triple;
    }

    public static int alignmentBands() {
        return ALIGNMENT.size();
    }
}
