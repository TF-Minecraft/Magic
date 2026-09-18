package net.tfminecraft.magic.util;

import net.tfminecraft.magic.GuiCache;
import net.tfminecraft.magic.service.EquilibriumRates;

public final class EquilibriumBar {

    private EquilibriumBar() {}

    public static String build(double equilibrium) {
        double min = GuiCache.equilibriumMin;
        double max = GuiCache.equilibriumMax;
        double clamped = MagicNumbers.clamp(equilibrium, min, max);
        int segments = GuiCache.equilibriumBarSegments;
        int filled = max <= 0 ? 0 : (int) Math.round(Math.abs(clamped) / max * segments);
        filled = (int) MagicNumbers.clamp(filled, 0, segments);

        String barChar = GuiCache.equilibriumBarChar;
        String bracket = GuiCache.equilibriumBarBracketColor;
        String neutral = GuiCache.equilibriumBarNeutralColor;
        String corruptionColor = GuiCache.equilibriumBarCorruptionColor;
        String tranquilityColor = GuiCache.equilibriumBarTranquilityColor;

        StringBuilder raw = new StringBuilder();
        raw.append(bracket).append("[");
        if (clamped > 0) {
            raw.append(tranquilityColor).append(barChar.repeat(filled));
            if (filled < segments) {
                raw.append(neutral).append(barChar.repeat(segments - filled));
            }
        } else if (clamped < 0) {
            int grayCount = segments - filled;
            if (grayCount > 0) {
                raw.append(neutral).append(barChar.repeat(grayCount));
            }
            raw.append(corruptionColor).append(barChar.repeat(filled));
        } else {
            raw.append(neutral).append(barChar.repeat(segments));
        }
        raw.append(bracket).append("]");
        return MagicText.format(raw.toString());
    }

    public static String formatValue(double equilibrium) {
        double clamped = MagicNumbers.clamp(
                equilibrium, GuiCache.equilibriumMin, GuiCache.equilibriumMax);
        String magnitude = MagicNumbers.format(Math.abs(clamped));
        if (clamped > 0) {
            return MagicText.format("{color:tranquility}" + magnitude);
        }
        if (clamped < 0) {
            return MagicText.format("{color:corruption}" + magnitude);
        }
        return MagicText.format("{color:label_muted}0");
    }

    public static String formatLore(double equilibrium) {
        double clamped = MagicNumbers.clamp(
                equilibrium, GuiCache.equilibriumMin, GuiCache.equilibriumMax);
        String bar = build(equilibrium);
        String valueText = formatValue(equilibrium);
        String template;
        if (clamped < 0) {
            template = GuiCache.equilibriumCorruptionLore;
        } else if (clamped > 0) {
            template = GuiCache.equilibriumTranquilityLore;
        } else {
            template = GuiCache.equilibriumNeutralLore;
        }
        return MagicText.format(template.replace("{bar}", bar).replace("{value}", valueText));
    }

    public static String formatDriftLore(double equilibrium) {
        double clamped = MagicNumbers.clamp(
                equilibrium, GuiCache.equilibriumMin, GuiCache.equilibriumMax);
        if (clamped < 0) {
            String rate = MagicNumbers.format(EquilibriumRates.corruptionDriftPerHour(clamped));
            return MagicText.format(GuiCache.equilibriumDriftLore
                    .replace("{sign}", "+")
                    .replace("{rate}", rate));
        }
        if (clamped > 0) {
            String rate = MagicNumbers.format(EquilibriumRates.tranquilityDecayPerHour(clamped));
            return MagicText.format(GuiCache.equilibriumDriftLore
                    .replace("{sign}", "-")
                    .replace("{rate}", rate));
        }
        return null;
    }
}
