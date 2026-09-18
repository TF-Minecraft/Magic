package net.tfminecraft.magic.util;

import net.tfminecraft.magic.GuiCache;
import net.tfminecraft.magic.model.ElementDef;
import net.tfminecraft.magic.session.ResonanceSession;

public final class ResonanceBar {

    private ResonanceBar() {}

    public static String build(ElementDef element, double current, int segments) {
        double max = element.getMaxResonance();
        double clampedCurrent = MagicNumbers.clamp(current, 0.0, max);
        int filled = max <= 0 ? 0 : (int) Math.round(clampedCurrent / max * segments);
        filled = (int) MagicNumbers.clamp(filled, 0, segments);

        String barChar = GuiCache.resonanceBarChar;
        String bracket = GuiCache.resonanceBarBracketColor;
        int emptyCount = segments - filled;

        String filledPart = element.getColor() + barChar.repeat(filled);
        String emptyPart = GuiCache.resonanceBarEmptyColor + barChar.repeat(emptyCount);
        return MagicText.format(bracket + "[" + filledPart + emptyPart + bracket + "]");
    }

    public static String formatLore(ElementDef element, double current) {
        String bar = build(element, current, GuiCache.resonanceBarSegments);
        double clamped = MagicNumbers.clamp(current, 0.0, element.getMaxResonance());
        String template = GuiCache.resonanceLoreTemplate
                .replace("{bar}", bar)
                .replace("{current}", MagicNumbers.format(clamped))
                .replace("{max}", MagicNumbers.format(element.getMaxResonance()));
        return MagicText.format(template);
    }

    public static String formatDriftLore(ElementDef element) {
        return formatDriftLore(element, null);
    }

    public static String formatDriftLore(ElementDef element, ResonanceSession session) {
        if (element == null) {
            return null;
        }
        return formatRateLine(GuiCache.resonanceDriftLore, element.getDecayPerHour());
    }

    private static String formatRateLine(String template, double rate) {
        if (template == null || rate == 0.0) {
            return null;
        }
        String sign = rate > 0 ? "+" : "-";
        String magnitude = MagicNumbers.format(Math.abs(rate));
        String line = template
                .replace("{sign}", sign)
                .replace("{rate}", magnitude);
        return MagicText.format(line);
    }
}
