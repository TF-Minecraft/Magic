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

    /**
     * Lays a module's own message file over messages.yml: its live keys win over the main file, and its
     * bundled copy (resourcePath in the jar) backs them up the same way messages.yml's does.
     */
    public static void overlay(File file, String resourcePath) {
        FileConfiguration part = readResource(resourcePath);
        if (part != null) {
            bundled = copyInto(part, bundled);
        }
        if (file == null || !file.isFile()) {
            return;
        }
        FileConfiguration live = new YamlConfiguration();
        try {
            live.load(file);
            config = copyInto(live, config);
        } catch (IOException | InvalidConfigurationException ex) {
            Magic.plugin.getLogger().severe("[Magic] Failed to load " + file.getName() + ": " + ex.getMessage());
        }
    }

    private static FileConfiguration copyInto(FileConfiguration from, FileConfiguration to) {
        FileConfiguration target = to != null ? to : new YamlConfiguration();
        for (String key : from.getKeys(true)) {
            if (!from.isConfigurationSection(key)) {
                target.set(key, from.get(key));
            }
        }
        return target;
    }

    private static FileConfiguration readBundled() {
        return readResource("messages.yml");
    }

    private static FileConfiguration readResource(String name) {
        try {
            InputStream resource = Magic.plugin.getResource(name);
            if (resource == null) {
                return null;
            }
            try (InputStream in = resource) {
                FileConfiguration loaded = new YamlConfiguration();
                loaded.loadFromString(new String(in.readAllBytes()));
                return loaded;
            }
        } catch (Exception ex) {
            Magic.plugin.getLogger().warning("[Magic] Failed to load bundled " + name + ": " + ex.getMessage());
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
            if (!live.isBlank()) {
                return live;
            }
        }
        if (bundled != null && bundled.contains(path)) {
            String fallback = bundled.getString(path);
            if (!fallback.isBlank()) {
                return fallback;
            }
        }
        return path;
    }

    private static String format(String value) {
        return net.tfminecraft.magic.util.MagicText.format(value != null ? value : "");
    }
}
