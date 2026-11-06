package net.tfminecraft.magic.blood;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import net.md_5.bungee.api.ChatColor;

/**
 * The voices' colours drift as the path goes on: each voice has a gradient at the start and one at
 * the end, and a line is drawn in the blend between them for how far the character has come.
 */
final class VoiceColours {

    private static final Pattern HEX = Pattern.compile("#[0-9a-fA-F]{6}");

    private VoiceColours() {}

    static boolean valid(String hex) {
        return hex != null && HEX.matcher(hex.trim()).matches();
    }

    /** The gradient stops at t (0 = start, 1 = end); both ends are resampled to the same stop count. */
    static List<String> at(List<String> start, List<String> end, double t) {
        if (start.isEmpty() || end.isEmpty()) {
            return start.isEmpty() ? end : start;
        }
        double clamped = Math.max(0.0, Math.min(1.0, t));
        int stops = Math.max(start.size(), end.size());
        List<String> out = new ArrayList<>(stops);
        for (int i = 0; i < stops; i++) {
            double position = stops == 1 ? 0.0 : (double) i / (stops - 1);
            out.add(hex(lerp(sample(start, position), sample(end, position), clamped)));
        }
        return out;
    }

    /** The text with a colour per character along the stops, the style codes repeated after each colour. */
    static String gradient(String text, List<String> stops, String style) {
        StringBuilder out = new StringBuilder(text.length() * 20);
        int length = text.length();
        for (int i = 0; i < length; i++) {
            char ch = text.charAt(i);
            if (Character.isWhitespace(ch)) {
                out.append(ch);
                continue;
            }
            double position = length == 1 ? 0.0 : (double) i / (length - 1);
            out.append(ChatColor.of(hex(sample(stops, position)))).append(style).append(ch);
        }
        return out.toString();
    }

    static int[] sample(List<String> stops, double position) {
        if (stops.size() == 1) {
            return rgb(stops.get(0));
        }
        double scaled = position * (stops.size() - 1);
        int segment = Math.min((int) Math.floor(scaled), stops.size() - 2);
        return lerp(rgb(stops.get(segment)), rgb(stops.get(segment + 1)), scaled - segment);
    }

    private static int[] lerp(int[] from, int[] to, double t) {
        return new int[] {
            (int) Math.round(from[0] + (to[0] - from[0]) * t),
            (int) Math.round(from[1] + (to[1] - from[1]) * t),
            (int) Math.round(from[2] + (to[2] - from[2]) * t)
        };
    }

    private static int[] rgb(String hex) {
        int value = Integer.parseInt(hex.trim().substring(1), 16);
        return new int[] {(value >> 16) & 0xFF, (value >> 8) & 0xFF, value & 0xFF};
    }

    static String hex(int[] rgb) {
        return String.format("#%02x%02x%02x", rgb[0], rgb[1], rgb[2]);
    }
}
