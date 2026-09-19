package net.tfminecraft.magic.gear;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;

public final class SocketColourRegistry {

    private static final Map<Integer, String> PREFIX = new TreeMap<>();
    private static String defaultPrefix = "Common";

    private SocketColourRegistry() {}

    public static void clear() {
        PREFIX.clear();
        defaultPrefix = "Common";
    }

    public static void setDefaultPrefix(String prefix) {
        if (prefix != null && !prefix.isBlank()) {
            defaultPrefix = prefix.trim();
        }
    }

    public static void put(int band, String prefix) {
        if (band <= 0 || prefix == null || prefix.isBlank()) {
            return;
        }
        PREFIX.put(band, prefix.trim());
    }

    public static String defaultPrefix() {
        return defaultPrefix;
    }

    public static String prefix(int band) {
        if (band <= 0) {
            return defaultPrefix;
        }
        return PREFIX.getOrDefault(band, defaultPrefix);
    }

    public static String colour(int band, String suffix) {
        if (suffix == null || suffix.isBlank()) {
            return "";
        }
        return prefix(band) + " " + suffix.trim();
    }

    public static Map<Integer, String> prefixes() {
        Map<Integer, String> copy = new LinkedHashMap<>();
        copy.put(0, defaultPrefix);
        copy.putAll(PREFIX);
        return Collections.unmodifiableMap(copy);
    }
}
