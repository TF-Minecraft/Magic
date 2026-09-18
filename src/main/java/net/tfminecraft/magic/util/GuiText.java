package net.tfminecraft.magic.util;

import java.util.ArrayList;
import java.util.List;

/**
 * GUI text styling. Delegates to {@link MagicText} for TLibs hex formatting.
 */
public final class GuiText {

    private GuiText() {}

    public static String format(String template) {
        return MagicText.format(template);
    }

    public static String color(String key) {
        return MagicText.color(key);
    }

    public static String text(String colorKey, String plain) {
        return MagicText.text(colorKey, plain);
    }

    public static List<String> formatLoreLines(List<String> lines) {
        if (lines == null || lines.isEmpty()) {
            return List.of();
        }
        List<String> out = new ArrayList<>(lines.size());
        for (String line : lines) {
            out.add(format(line != null ? line : ""));
        }
        return out;
    }
}
