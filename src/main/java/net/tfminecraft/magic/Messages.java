package net.tfminecraft.magic;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;

import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

/**
 * Player-facing strings from messages.yml.
 */
public final class Messages {

    private static FileConfiguration config;
    private static FileConfiguration bundled;

    private Messages() {}

    public static void load(File file) {
        bundled = readBundled();
        FileConfiguration loaded = new YamlConfiguration();
        try {
            loaded.load(file);
            config = loaded;
        } catch (IOException | InvalidConfigurationException ex) {
            Magic.plugin.getLogger().severe("[Magic] Failed to load messages.yml: " + ex.getMessage());
            config = bundled != null ? bundled : new YamlConfiguration();
        }
    }

    public static void loadFromResources() {
        bundled = readBundled();
        if (bundled != null) {
            config = bundled;
        }
    }

    private static FileConfiguration readBundled() {
        try (InputStream in = Magic.plugin.getResource("messages.yml")) {
            if (in == null) {
                return null;
            }
            FileConfiguration loaded = new YamlConfiguration();
            loaded.loadFromString(new String(in.readAllBytes()));
            return loaded;
        } catch (Exception ex) {
            Magic.plugin.getLogger().warning("[Magic] Failed to load bundled messages.yml: " + ex.getMessage());
            return null;
        }
    }

    public static String get(String path) {
        return format(getRaw(path));
    }

    public static String get(String path, String... keyValues) {
        String raw = getRaw(path);
        for (int i = 0; i + 1 < keyValues.length; i += 2) {
            String value = keyValues[i + 1] != null ? keyValues[i + 1] : "";
            raw = raw.replace("{" + keyValues[i] + "}", value);
        }
        return format(raw);
    }

    public static String getRaw(String path) {
        if (config != null && config.contains(path)) {
            String live = config.getString(path);
            if (live != null && !live.isBlank()) {
                return live;
            }
        }
        if (bundled != null && bundled.contains(path)) {
            String fallback = bundled.getString(path);
            if (fallback != null && !fallback.isBlank()) {
                return fallback;
            }
        }
        return path;
    }

    private static String format(String value) {
        return net.tfminecraft.magic.util.MagicText.format(value != null ? value : "");
    }
}
