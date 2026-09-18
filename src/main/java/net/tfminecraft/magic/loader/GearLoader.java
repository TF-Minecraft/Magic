package net.tfminecraft.magic.loader;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.bukkit.Color;
import org.bukkit.Particle;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;

import net.tfminecraft.magic.Magic;
import net.tfminecraft.magic.gear.orb.OrbCache;
import net.tfminecraft.magic.gear.ArchetypeDef;
import net.tfminecraft.magic.gear.ArchetypeRegistry;
import net.tfminecraft.magic.gear.GearModelScheme;
import net.tfminecraft.magic.gear.GearModelSchemeRegistry;
import net.tfminecraft.magic.gear.GearType;
import net.tfminecraft.magic.gear.PartDef;
import net.tfminecraft.magic.gear.PartRegistry;
import net.tfminecraft.magic.gear.PartTypeDef;
import net.tfminecraft.magic.gear.PartTypeRegistry;
import net.tfminecraft.magic.gear.SocketColourRegistry;
import net.tfminecraft.magic.gear.SocketLayout;
import net.tfminecraft.magic.util.MagicText;
import net.tfminecraft.magic.util.RevisionTracker;

public final class GearLoader {

    public boolean loadFolder(File folder) {
        ArchetypeRegistry.clear();
        PartTypeRegistry.clear();
        PartRegistry.clear();
        GearModelSchemeRegistry.clear();
        SocketColourRegistry.clear();
        SocketLayout.clearLabels();
        if (folder == null || !folder.exists()) {
            Magic.plugin.getLogger().warning("[Magic] gear/ folder missing.");
            return false;
        }
        boolean ok = true;
        ok &= loadPartTypes(new File(folder, "part-types.yml"));
        ok &= loadArchetypes(new File(folder, "archetypes.yml"));
        ok &= loadSocketColours(new File(folder, "socket-colours.yml"));
        ok &= loadModelSchemes(new File(folder, "model-schemes.yml"));
        ok &= loadParts(new File(folder, "parts.yml"));
        ok &= loadOrbs(new File(folder, "orbs.yml"));
        Magic.plugin.getLogger().info("[Magic] Loaded " + ArchetypeRegistry.size()
                + " archetype(s), " + PartTypeRegistry.size() + " part type(s), "
                + PartRegistry.size() + " part(s), " + GearModelSchemeRegistry.size()
                + " model scheme(s). Socket colours: "
                + SocketColourRegistry.prefixes()
                + ". Socket labels: " + SocketLayout.labels());
        return ok;
    }

    private static YamlConfiguration read(File file, String label) {
        if (file == null || !file.exists()) {
            Magic.plugin.getLogger().warning("[Magic] " + label + " missing.");
            return null;
        }
        YamlConfiguration config = new YamlConfiguration();
        try {
            config.load(file);
            return config;
        } catch (IOException | InvalidConfigurationException ex) {
            Magic.plugin.getLogger().severe("[Magic] Failed to load " + label + ": " + ex.getMessage());
            return null;
        }
    }

    private static boolean loadPartTypes(File file) {
        YamlConfiguration config = read(file, "gear/part-types.yml");
        if (config == null) {
            return false;
        }
        for (String id : config.getKeys(false)) {
            ConfigurationSection section = config.getConfigurationSection(id);
            int slot = section == null ? config.getInt(id + ".slot", 0) : section.getInt("slot", 0);
            String name = section == null ? config.getString(id + ".name", "") : section.getString("name", "");
            PartTypeRegistry.register(new PartTypeDef(id, slot, name));
        }
        return true;
    }

    private static boolean loadArchetypes(File file) {
        YamlConfiguration config = read(file, "gear/archetypes.yml");
        if (config == null) {
            return false;
        }
        int skipped = 0;
        for (String key : config.getKeys(false)) {
            GearType type = GearType.fromId(key);
            ConfigurationSection section = config.getConfigurationSection(key);
            if (type == null || section == null) {
                skipped++;
                continue;
            }
            Map<String, String> slots = new LinkedHashMap<>();
            ConfigurationSection slotSection = section.getConfigurationSection("slots");
            if (slotSection != null) {
                for (String slotId : slotSection.getKeys(false)) {
                    slots.put(slotId, slotSection.getString(slotId, ""));
                }
            }
            ArchetypeDef def = new ArchetypeDef(
                    type,
                    section.getString("name", type.getDisplayName()),
                    section.getString("template", ""),
                    section.getString("icon", ""),
                    section.getBoolean("melee", type == GearType.SWORD),
                    section.getStringList("required"),
                    slots);
            def.setRevision(Magic.getRevisionTracker().resolveArchetype(
                    type.name(), RevisionTracker.sha256(def.buildRevisionContent())));
            ArchetypeRegistry.register(def);
        }
        if (skipped > 0) {
            Magic.plugin.getLogger().warning("[Magic] gear/archetypes.yml skipped " + skipped);
        }
        return ArchetypeRegistry.size() > 0;
    }

    private static boolean loadSocketColours(File file) {
        YamlConfiguration config = read(file, "gear/socket-colours.yml");
        if (config == null) {
            return false;
        }
        ConfigurationSection labels = config.getConfigurationSection("labels");
        if (labels != null) {
            for (String slotId : labels.getKeys(false)) {
                SocketLayout.putLabel(slotId, labels.getString(slotId, ""));
            }
        }
        ConfigurationSection prefix = config.getConfigurationSection("prefix");
        if (prefix == null) {
            return true;
        }
        SocketColourRegistry.setDefaultPrefix(prefix.getString("default", "Common"));
        for (String key : prefix.getKeys(false)) {
            if ("default".equalsIgnoreCase(key)) {
                continue;
            }
            try {
                SocketColourRegistry.put(Integer.parseInt(key.trim()), prefix.getString(key));
            } catch (NumberFormatException ex) {
                Magic.plugin.getLogger().warning("[Magic] socket-colours prefix '" + key + "' is not a number");
            }
        }
        return true;
    }

    private static boolean loadOrbs(File file) {
        OrbCache.clearTiers();
        YamlConfiguration config = read(file, "gear/orbs.yml");
        if (config == null) {
            return false;
        }
        OrbCache.hitRadius = Math.max(0.1, config.getDouble("hit_radius", OrbCache.hitRadius));
        OrbCache.clickRange = Math.max(1.0, config.getDouble("click_range", OrbCache.clickRange));
        OrbCache.orbitRadius = Math.max(0.5, config.getDouble("orbit_radius", OrbCache.orbitRadius));
        OrbCache.orbitHeight = config.getDouble("orbit_height", OrbCache.orbitHeight);
        OrbCache.orbitBob = Math.max(0.0, config.getDouble("orbit_bob", OrbCache.orbitBob));
        OrbCache.orbitPeriodTicks = Math.max(1L, config.getLong("orbit_period_ticks", OrbCache.orbitPeriodTicks));
        OrbCache.introTicks = Math.max(0, config.getInt("intro_ticks", OrbCache.introTicks));
        OrbCache.lifetimeTicks = Math.max(1, config.getInt("lifetime_ticks", OrbCache.lifetimeTicks));
        OrbCache.spawnIntervalTicks = Math.max(1, config.getInt("spawn_interval_ticks", OrbCache.spawnIntervalTicks));
        OrbCache.windowTicks = Math.max(20, config.getInt("window_ticks", OrbCache.windowTicks));
        OrbCache.missPenalty = Math.max(0.0, config.getDouble("miss_penalty", OrbCache.missPenalty));

        OrbCache.goodColor = colorOf(config.getIntegerList("good.dust"), OrbCache.goodColor);
        OrbCache.badColor = colorOf(config.getIntegerList("bad.dust"), OrbCache.badColor);
        OrbCache.goodParticle = particleOf(config.getString("good.particle"), OrbCache.goodParticle);
        OrbCache.badParticle = particleOf(config.getString("bad.particle"), OrbCache.badParticle);

        OrbCache.riftPerBad = Math.max(0, config.getInt("rift.per_bad", OrbCache.riftPerBad));
        OrbCache.riftCap = Math.max(1, config.getInt("rift.cap", OrbCache.riftCap));
        OrbCache.riftPerRecharge = Math.max(0, config.getInt("rift.per_recharge", OrbCache.riftPerRecharge));

        ConfigurationSection tiers = config.getConfigurationSection("tiers");
        if (tiers != null) {
            for (String key : tiers.getKeys(false)) {
                ConfigurationSection row = tiers.getConfigurationSection(key);
                if (row == null) {
                    continue;
                }
                try {
                    OrbCache.putTier(Integer.parseInt(key.trim()), new OrbCache.Tier(
                            row.getInt("live", 5),
                            row.getDouble("good_ratio", 0.65),
                            row.getDouble("speed", 1.0),
                            row.getInt("window_ticks", OrbCache.windowTicks),
                            row.getInt("good_target", 5)));
                } catch (NumberFormatException ex) {
                    Magic.plugin.getLogger().warning("[Magic] orbs.yml tier '" + key + "' is not a number");
                }
            }
        }
        if (OrbCache.tierCount() == 0) {
            Magic.plugin.getLogger().warning("[Magic] gear/orbs.yml has no tiers; using built-in defaults");
        }
        return true;
    }

    private static Color colorOf(List<Integer> rgb, Color fallback) {
        if (rgb == null || rgb.size() < 3) {
            return fallback;
        }
        int r = Math.max(0, Math.min(255, rgb.get(0)));
        int g = Math.max(0, Math.min(255, rgb.get(1)));
        int b = Math.max(0, Math.min(255, rgb.get(2)));
        return Color.fromRGB(r, g, b);
    }

    private static Particle particleOf(String name, Particle fallback) {
        if (name == null || name.isBlank()) {
            return fallback;
        }
        try {
            return Particle.valueOf(name.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            Magic.plugin.getLogger().warning("[Magic] orbs.yml: unknown particle '" + name + "'");
            return fallback;
        }
    }

    private static boolean loadModelSchemes(File file) {
        YamlConfiguration config = read(file, "gear/model-schemes.yml");
        if (config == null) {
            return false;
        }
        int skipped = 0;
        for (String key : config.getKeys(false)) {
            ConfigurationSection section = config.getConfigurationSection(key);
            if (key == null || key.isBlank() || section == null) {
                skipped++;
                continue;
            }
            if (GearModelSchemeRegistry.contains(key)) {
                Magic.plugin.getLogger().warning("[Magic] Duplicate model-scheme '" + key + "'");
                skipped++;
                continue;
            }
            Map<GearType, String> paths = new LinkedHashMap<>();
            for (String line : section.getStringList("models")) {
                if (line == null || line.isBlank()) {
                    continue;
                }
                String trimmed = line.trim();
                int open = trimmed.indexOf('(');
                int close = trimmed.lastIndexOf(')');
                if (open <= 0 || close <= open) {
                    Magic.plugin.getLogger().warning("[Magic] Invalid model '" + line
                            + "' in scheme '" + key + "'");
                    continue;
                }
                GearType type = GearType.fromId(trimmed.substring(0, open).trim());
                String path = trimmed.substring(open + 1, close).trim();
                if (type == null || path.isEmpty()) {
                    Magic.plugin.getLogger().warning("[Magic] Invalid model '" + line
                            + "' in scheme '" + key + "'");
                    continue;
                }
                paths.put(type, path);
            }
            if (paths.isEmpty()) {
                Magic.plugin.getLogger().warning("[Magic] Model-scheme '" + key + "' has no models");
                skipped++;
                continue;
            }
            GearModelSchemeRegistry.register(new GearModelScheme(key, paths));
        }
        if (skipped > 0) {
            Magic.plugin.getLogger().warning("[Magic] gear/model-schemes.yml skipped " + skipped);
        }
        return GearModelSchemeRegistry.size() > 0;
    }

    private static boolean loadParts(File file) {
        YamlConfiguration config = read(file, "gear/parts.yml");
        if (config == null) {
            return false;
        }
        int skipped = 0;
        for (String id : config.getKeys(false)) {
            ConfigurationSection section = config.getConfigurationSection(id);
            if (section == null) {
                skipped++;
                continue;
            }
            String partType = section.getString("part-type", "");
            if (PartTypeRegistry.get(partType) == null) {
                Magic.plugin.getLogger().warning("[Magic] parts.yml: unknown part-type '" + partType
                        + "' for '" + id + "'");
                skipped++;
                continue;
            }
            Set<GearType> types = EnumSet.noneOf(GearType.class);
            for (String raw : section.getStringList("type")) {
                GearType type = GearType.fromId(raw);
                if (type != null) {
                    types.add(type);
                }
            }
            if (types.isEmpty()) {
                skipped++;
                continue;
            }
            Map<String, Integer> sockets = new LinkedHashMap<>();
            ConfigurationSection socketSection = section.getConfigurationSection("sockets");
            if (socketSection != null) {
                for (String slotId : socketSection.getKeys(false)) {
                    sockets.put(slotId, socketSection.getInt(slotId, 0));
                }
            }
            List<String> partLimit = new ArrayList<>();
            for (String raw : section.getStringList("part-limit")) {
                if (raw == null || raw.isBlank()) {
                    continue;
                }
                String category = raw.trim().toLowerCase(Locale.ROOT);
                if (PartTypeRegistry.get(category) == null) {
                    Magic.plugin.getLogger().warning("[Magic] parts.yml: unknown part-limit '"
                            + raw + "' for '" + id + "'");
                    continue;
                }
                if (!partLimit.contains(category)) {
                    partLimit.add(category);
                }
            }
            List<String> lore = new ArrayList<>();
            for (String line : section.getStringList("lore")) {
                lore.add(MagicText.format(line));
            }
            String schemeId = "";
            int schemeWeight = 1;
            String schemeRaw = section.getString("model-scheme", "");
            if (schemeRaw != null && !schemeRaw.isBlank()) {
                int start = schemeRaw.indexOf('(');
                int end = schemeRaw.indexOf(')');
                if (start > 0 && end > start) {
                    schemeId = schemeRaw.substring(0, start).trim();
                    try {
                        schemeWeight = Integer.parseInt(schemeRaw.substring(start + 1, end).trim());
                    } catch (NumberFormatException ignored) {
                        schemeWeight = 1;
                    }
                } else {
                    schemeId = schemeRaw.trim();
                }
                schemeId = schemeId.toLowerCase(Locale.ROOT);
                if (!GearModelSchemeRegistry.contains(schemeId)) {
                    Magic.plugin.getLogger().warning("[Magic] parts.yml: unknown model-scheme '"
                            + schemeRaw + "' for '" + id + "'");
                    schemeId = "";
                    schemeWeight = 1;
                }
            }
            PartDef def = new PartDef(
                    id,
                    MagicText.format(section.getString("name", id)),
                    partType,
                    section.getInt("tier", 0),
                    types,
                    section.getString("item", "v.stone"),
                    parseCost(section.getStringList("cost")),
                    partLimit,
                    parseStats(section.getStringList("stats")),
                    sockets,
                    lore,
                    schemeId,
                    schemeWeight,
                    section.getBoolean("disabled", false));
            if (def.totalSocketCount() > 4) {
                Magic.plugin.getLogger().warning("[Magic] part '" + id
                        + "' declares more than 4 sockets; extras are dropped at craft");
            }
            def.setRevision(Magic.getRevisionTracker().resolvePart(
                    def.getId(), RevisionTracker.sha256(def.buildRevisionContent())));
            PartRegistry.register(def);
        }
        if (skipped > 0) {
            Magic.plugin.getLogger().warning("[Magic] gear/parts.yml skipped " + skipped);
        }
        return true;
    }

    private static Map<String, Integer> parseCost(List<String> raw) {
        Map<String, Integer> cost = new LinkedHashMap<>();
        if (raw == null) {
            return cost;
        }
        for (String entry : raw) {
            if (entry == null || entry.isBlank()) {
                continue;
            }
            int start = entry.indexOf('(');
            int end = entry.indexOf(')');
            if (start > 0 && end > start) {
                try {
                    cost.put(entry.substring(0, start).trim(), Integer.parseInt(entry.substring(start + 1, end)));
                    continue;
                } catch (NumberFormatException ignored) {
                    // fall through
                }
            }
            cost.put(entry.trim(), 1);
        }
        return cost;
    }

    private static Map<String, Double> parseStats(List<String> raw) {
        Map<String, Double> stats = new LinkedHashMap<>();
        if (raw == null) {
            return stats;
        }
        for (String entry : raw) {
            if (entry == null || entry.isBlank()) {
                continue;
            }
            int start = entry.indexOf('(');
            int end = entry.indexOf(')');
            if (start <= 0 || end <= start) {
                continue;
            }
            try {
                String statId = entry.substring(0, start).trim().toLowerCase(Locale.ROOT);
                if (statId.isEmpty()) {
                    continue;
                }
                stats.put(statId, Double.parseDouble(entry.substring(start + 1, end)));
            } catch (NumberFormatException ignored) {
                // skip malformed values
            }
        }
        return stats;
    }
}
