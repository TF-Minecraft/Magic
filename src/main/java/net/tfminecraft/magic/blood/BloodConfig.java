package net.tfminecraft.magic.blood;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.logging.Logger;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;

/**
 * Settings from the wildmagic/bloodmagic folder (settings, items, voices, animals, corruption, choice,
 * armour and fx files, merged; messages.yml is read by Messages). Defaults keep the feature safe when
 * a key is missing.
 */
public final class BloodConfig {

    /** A bound item the voices hand out. */
    public record ItemDef(String path, String name, List<String> lore) {}

    private static boolean enabled;
    private static final Map<BloodItemKind, ItemDef> ITEMS = new EnumMap<>(BloodItemKind.class);
    private static Material altarBlock = Material.BARRIER;
    private static final TreeMap<Integer, String> MODELS = new TreeMap<>();
    private static float displayScale = 1f;
    private static final TreeMap<Integer, Integer> LEVELS = new TreeMap<>();
    private static List<String> worlds = List.of();
    private static boolean requireUnderground = true;
    private static int minCover = 3;
    private static int coverScanHeight = 48;
    private static boolean denyRegions = true;
    private static List<String> allowedRegions = List.of();
    private static int purifyCheckMinutes = 10;
    private static int huntPointsRequired = 7;

    static {
        reset();
    }

    private BloodConfig() {}

    /** The files a source stands for: itself, or a folder's .yml files but messages.yml, by name. */
    static List<File> parts(File source) {
        if (source.isFile()) {
            return List.of(source);
        }
        File[] files = source.listFiles((dir, name) -> name.endsWith(".yml") && !name.equals("messages.yml"));
        List<File> out = new ArrayList<>(files != null ? List.of(files) : List.of());
        out.sort((a, b) -> a.getName().compareTo(b.getName()));
        return out;
    }

    /**
     * Loads the config folder (every .yml in it but messages.yml, merged) or a single file. A missing
     * folder leaves the feature disabled; a broken file does too and returns false.
     */
    public static boolean load(File source, Logger log) {
        reset();
        if (source == null || !source.exists()) {
            log.warning("[Magic] wildmagic/bloodmagic config missing; blood magic disabled");
            return true;
        }
        YamlConfiguration config = new YamlConfiguration();
        for (File part : parts(source)) {
            YamlConfiguration loaded = new YamlConfiguration();
            try {
                loaded.load(part);
            } catch (IOException | InvalidConfigurationException ex) {
                log.severe("[Magic] Failed to load blood magic config " + part.getName() + ": " + ex.getMessage());
                reset();
                return false;
            }
            for (String key : loaded.getKeys(true)) {
                if (!loaded.isConfigurationSection(key)) {
                    config.set(key, loaded.get(key));
                }
            }
        }
        enabled = config.getBoolean("enabled", false);
        ConfigurationSection items = config.getConfigurationSection("items");
        for (BloodItemKind kind : BloodItemKind.values()) {
            ConfigurationSection section = items != null ? items.getConfigurationSection(kind.key()) : null;
            if (section == null) {
                log.warning("[Magic] blood magic config items." + kind.key() + " missing; using a plain default");
                ITEMS.put(kind, new ItemDef(kind.fallbackPath(), "", List.of()));
                continue;
            }
            ITEMS.put(kind, new ItemDef(section.getString("path", kind.fallbackPath()),
                    section.getString("name", ""), List.copyOf(section.getStringList("lore"))));
        }
        boolean ok = true;
        MODELS.clear();
        String block = config.getString("altar.block", "BARRIER");
        Material material = Material.matchMaterial(block);
        if (material == null || !material.isBlock()) {
            log.warning("[Magic] blood magic config altar.block '" + block + "' is not a block; using BARRIER");
            ok = false;
        } else {
            altarBlock = material;
        }
        ok &= readIntMap(config.getConfigurationSection("altar.models"), "altar.models", log,
                (level, value) -> MODELS.put(level, value));
        if (MODELS.isEmpty()) {
            MODELS.put(0, BloodItemKind.ALTAR.fallbackPath());
        }
        displayScale = (float) Math.max(0.1, config.getDouble("altar.display_scale", 1.0));
        ok &= readIntMap(config.getConfigurationSection("altar.levels"), "altar.levels", log, (level, value) -> {
            try {
                LEVELS.put(level, Integer.parseInt(value));
            } catch (NumberFormatException ex) {
                log.warning("[Magic] blood magic config altar.levels." + level + " is not a number");
            }
        });
        worlds = lower(config.getStringList("placement.worlds"));
        requireUnderground = config.getBoolean("placement.require_underground", true);
        minCover = Math.max(0, config.getInt("placement.min_cover", 3));
        coverScanHeight = Math.max(1, config.getInt("placement.cover_scan_height", 48));
        denyRegions = config.getBoolean("placement.deny_regions", true);
        allowedRegions = lower(config.getStringList("placement.allowed_regions"));
        purifyCheckMinutes = Math.max(1, config.getInt("placement.purify_check_minutes", 10));
        huntPointsRequired = Math.max(1, config.getInt("hunt.points_required", 7));
        WhisperConfig.load(config.getConfigurationSection("whispers"), config.getConfigurationSection("voices"), log);
        AnimalConfig.load(config.getConfigurationSection("animal"), log);
        CorruptionConfig.load(config.getConfigurationSection("corruption"), log);
        ChoiceConfig.load(config.getConfigurationSection("choice"), config.getConfigurationSection("classes"),
                config.getConfigurationSection("armour"));
        ChoiceConfig.loadPermissions(config.getConfigurationSection("permissions"));
        HuntConfig.load(config.getConfigurationSection("hunt"), config.getConfigurationSection("bonds"),
                config.getConfigurationSection("finals"), log);
        BloodFx.load(config.getConfigurationSection("fx"), log);
        return ok;
    }

    private interface IntEntry {
        void accept(int level, String value);
    }

    private static boolean readIntMap(ConfigurationSection section, String path, Logger log, IntEntry sink) {
        if (section == null) {
            return true;
        }
        boolean ok = true;
        for (String key : section.getKeys(false)) {
            try {
                sink.accept(Integer.parseInt(key.trim()), section.getString(key, ""));
            } catch (NumberFormatException ex) {
                log.warning("[Magic] blood magic config " + path + " key '" + key + "' is not a level number");
                ok = false;
            }
        }
        return ok;
    }

    private static List<String> lower(List<String> raw) {
        List<String> out = new ArrayList<>();
        for (String value : raw) {
            if (!value.isBlank()) {
                out.add(value.trim().toLowerCase(Locale.ROOT));
            }
        }
        return Collections.unmodifiableList(out);
    }

    /** Clears everything back to the disabled defaults. */
    public static void reset() {
        enabled = false;
        ITEMS.clear();
        for (BloodItemKind kind : BloodItemKind.values()) {
            ITEMS.put(kind, new ItemDef(kind.fallbackPath(), "", List.of()));
        }
        altarBlock = Material.BARRIER;
        MODELS.clear();
        MODELS.put(0, BloodItemKind.ALTAR.fallbackPath());
        displayScale = 1f;
        LEVELS.clear();
        worlds = List.of();
        requireUnderground = true;
        minCover = 3;
        coverScanHeight = 48;
        denyRegions = true;
        allowedRegions = List.of();
        purifyCheckMinutes = 10;
        huntPointsRequired = 7;
        WhisperConfig.reset();
        AnimalConfig.reset();
        CorruptionConfig.reset();
        ChoiceConfig.reset();
        HuntConfig.reset();
        BloodFx.reset();
    }

    public static boolean isEnabled() {
        return enabled;
    }

    public static void setEnabled(boolean value) {
        enabled = value;
    }

    public static ItemDef item(BloodItemKind kind) {
        return ITEMS.get(kind);
    }

    public static Material altarBlock() {
        return altarBlock;
    }

    /** The model for a level: the highest configured level at or below it. */
    public static String model(int level) {
        Map.Entry<Integer, String> entry = MODELS.floorEntry(level);
        return entry != null ? entry.getValue() : MODELS.firstEntry().getValue();
    }

    public static float displayScale() {
        return displayScale;
    }

    /** The altar level reached with this much progress. */
    public static int level(int progress) {
        int level = 0;
        for (Map.Entry<Integer, Integer> entry : LEVELS.entrySet()) {
            if (progress >= entry.getValue()) {
                level = Math.max(level, entry.getKey());
            }
        }
        return level;
    }

    public static boolean worldAllowed(String world) {
        return worlds.isEmpty() || (world != null && worlds.contains(world.toLowerCase(Locale.ROOT)));
    }

    public static boolean requireUnderground() {
        return requireUnderground;
    }

    public static int minCover() {
        return minCover;
    }

    public static int coverScanHeight() {
        return coverScanHeight;
    }

    public static boolean denyRegions() {
        return denyRegions;
    }

    public static boolean regionAllowed(String regionId) {
        return regionId != null && allowedRegions.contains(regionId.toLowerCase(Locale.ROOT));
    }

    public static int purifyCheckMinutes() {
        return purifyCheckMinutes;
    }

    public static int huntPointsRequired() {
        return huntPointsRequired;
    }
}
