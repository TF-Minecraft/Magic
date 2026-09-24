package net.tfminecraft.magic.gear;

import java.util.LinkedHashMap;
import java.util.Map;

import net.tfminecraft.magic.modifier.ModifierTriple;

public final class GearCache {

    public static String station = "iaf(tfmc:mage_crafting_station)";
    public static int outputSlot = 16;
    public static long confirmMillis = 5000L;

    public static boolean alignmentEnabled = false;

    /** When false, gem socket colour strings omit Common/Rare/Epic/Legendary prefix. */
    public static boolean socketRarityPrefix = true;

    /** Weapon tier band in the spell's element -> bonus. Never a penalty. */
    private static final Map<Integer, ModifierTriple> ALIGNMENT = new LinkedHashMap<>();

    private GearCache() {}

    public static void clearAlignment() {
        ALIGNMENT.clear();
    }

    public static void putAlignment(int band, ModifierTriple triple) {
        if (band > 0 && triple != null) {
            ALIGNMENT.put(band, triple);
        }
    }

    public static ModifierTriple alignment(int band) {
        if (!alignmentEnabled || band <= 0) {
            return ModifierTriple.ZERO;
        }
        ModifierTriple triple = ALIGNMENT.get(band);
        return triple == null ? ModifierTriple.ZERO : triple;
    }

    public static int alignmentBands() {
        return ALIGNMENT.size();
    }
}
