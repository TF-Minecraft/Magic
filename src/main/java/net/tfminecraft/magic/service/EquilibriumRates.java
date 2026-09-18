package net.tfminecraft.magic.service;

import net.tfminecraft.magic.Cache;
import net.tfminecraft.magic.GuiCache;

public final class EquilibriumRates {

    private EquilibriumRates() {}

    public static double corruptionDriftPerHour(double equilibrium) {
        if (equilibrium >= 0.0) {
            return 0.0;
        }
        double depth = Math.abs(equilibrium) / GuiCache.equilibriumMax;
        return Cache.equilibriumPassiveCorruptPerHour
                * (1.0 + Cache.equilibriumCorruptCompoundStrength * depth);
    }

    public static double tranquilityDecayPerHour(double equilibrium) {
        if (equilibrium <= 0.0) {
            return 0.0;
        }
        return Cache.equilibriumTranquilityDecayPerHour;
    }
}
