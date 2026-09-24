package net.tfminecraft.magic.util;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.bukkit.Color;

import net.md_5.bungee.api.ChatColor;
import net.tfminecraft.tlibs.objects.api.subapi.StringFormatter;
import net.tfminecraft.magic.GuiCache;
import net.tfminecraft.magic.model.ElementDef;

/**
 * Formats all player-facing strings from config via TLibs {@link StringFormatter#formatHex}.
 */
public final class MagicText {

    private static final String FALLBACK_COLOR = "#ffffff";
    private static final Pattern COLOR_TOKEN = Pattern.compile("\\{color:([a-zA-Z0-9_]+)}");

    private MagicText() {}

    public static String format(String template) {
        return StringFormatter.formatHex(substituteColorTokens(template != null ? template : ""));
    }

    public static String color(String key) {
        return GuiCache.color(key, FALLBACK_COLOR);
    }

    public static String text(String colorKey, String plain) {
        return format(color(colorKey) + (plain != null ? plain : ""));
    }

    /**
     * Element name in its configured colour. A single stop is solid. Several stops
     * run as a gradient. The name's own hex prefix is not repeated.
     */
    public static String elementName(ElementDef element) {
        if (element == null) {
            return "";
        }
        return elementText(element, visibleName(element));
    }

    /** Any text in the element's colour, solid or gradient like {@link #elementName}. */
    public static String elementText(ElementDef element, String plain) {
        String text = plain != null ? plain : "";
        if (element == null) {
            return format(FALLBACK_COLOR + text);
        }
        List<String> colors = element.getColors();
        if (colors.size() > 1) {
            return StringFormatter.applyColourGradient(text, colors);
        }
        return format(element.getColor() + text);
    }

    private static String visibleName(ElementDef element) {
        String raw = element.getName();
        if (raw == null || raw.isBlank()) {
            raw = element.getId();
        }
        @SuppressWarnings("deprecation")
        String stripped = ChatColor.stripColor(raw);
        if (stripped == null) {
            stripped = raw;
        }
        stripped = stripped.replaceAll("(?i)#[0-9a-f]{6}", "").trim();
        return stripped.isEmpty() ? element.getId() : stripped;
    }

    /**
     * Piecewise RGB: 0 dark red, 0.5 yellow, 1 light green. Returns {@code #rrggbb}.
     */
    public static String gradientHex(double t) {
        double clamped = MagicNumbers.clamp(t, 0.0, 1.0);
        int r;
        int g;
        int b;
        if (clamped <= 0.5) {
            double u = clamped * 2.0;
            r = lerpChannel(0x7a, 0xe8, u);
            g = lerpChannel(0x15, 0xc5, u);
            b = lerpChannel(0x15, 0x47, u);
        } else {
            double u = (clamped - 0.5) * 2.0;
            r = lerpChannel(0xe8, 0x8f, u);
            g = lerpChannel(0xc5, 0xd9, u);
            b = lerpChannel(0x47, 0x8a, u);
        }
        return String.format("#%02x%02x%02x", r, g, b);
    }

    private static int lerpChannel(int from, int to, double t) {
        return (int) Math.round(from + (to - from) * t);
    }

    public static Color bukkitColor(String hex, Color fallback) {
        if (hex == null || hex.isBlank()) {
            return fallback;
        }
        String digits = hex.trim();
        if (digits.startsWith("#")) {
            digits = digits.substring(1);
        }
        if (digits.length() != 6) {
            return fallback;
        }
        try {
            return Color.fromRGB(Integer.parseInt(digits, 16));
        } catch (NumberFormatException ex) {
            return fallback;
        }
    }

    private static String substituteColorTokens(String raw) {
        if (raw.isEmpty()) {
            return raw;
        }
        Matcher matcher = COLOR_TOKEN.matcher(raw);
        StringBuffer out = new StringBuffer(raw.length() + 16);
        while (matcher.find()) {
            String token = matcher.group(1);
            String hex = GuiCache.color(token, FALLBACK_COLOR);
            matcher.appendReplacement(out, Matcher.quoteReplacement(hex));
        }
        matcher.appendTail(out);
        return out.toString();
    }
}
