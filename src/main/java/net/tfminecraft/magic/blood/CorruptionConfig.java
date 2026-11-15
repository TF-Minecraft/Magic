package net.tfminecraft.magic.blood;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.logging.Logger;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;

/** The corruption: section of the wildmagic/bloodmagic config. */
public final class CorruptionConfig {

    /** How far an altar of a level reaches and how many blocks it may have changed. */
    public record Reach(int radius, int maxBlocks) {}

    /** A conversion of exposed solid blocks, or a decoration placed into air, used from minLevel to maxLevel. */
    public record Rule(int minLevel, int maxLevel, Set<Material> from, Placement on, List<Material> to) {}

    public enum Placement {
        CONVERT,
        FLOOR,
        CEILING
    }

    private static final TreeMap<Integer, Reach> LEVELS = new TreeMap<>();
    private static final List<Rule> RULES = new ArrayList<>();
    private static int blocksPerSacrifice;
    private static int ambientEveryMinutes;
    private static int ambientBlocks;

    static {
        reset();
    }

    private CorruptionConfig() {}

    static void reset() {
        LEVELS.clear();
        LEVELS.put(0, new Reach(3, 24));
        RULES.clear();
        blocksPerSacrifice = 15;
        ambientEveryMinutes = 10;
        ambientBlocks = 3;
    }

    static void load(ConfigurationSection section, Logger log) {
        reset();
        if (section == null) {
            return;
        }
        ConfigurationSection levels = section.getConfigurationSection("levels");
        if (levels != null) {
            LEVELS.clear();
            for (String key : levels.getKeys(false)) {
                try {
                    int level = Integer.parseInt(key.trim());
                    LEVELS.put(level, new Reach(Math.max(1, levels.getInt(key + ".radius", 3)),
                            Math.max(0, levels.getInt(key + ".max_blocks", 24))));
                } catch (NumberFormatException ex) {
                    log.warning("[Magic] blood magic config corruption.levels key '" + key + "' is not a level number");
                }
            }
            if (LEVELS.isEmpty()) {
                LEVELS.put(0, new Reach(3, 24));
            }
        }
        blocksPerSacrifice = Math.max(0, section.getInt("blocks_per_sacrifice", 15));
        ambientEveryMinutes = Math.max(1, section.getInt("ambient_every_minutes", 10));
        ambientBlocks = Math.max(1, section.getInt("ambient_blocks", 3));
        for (Map<?, ?> raw : section.getMapList("convert")) {
            Rule rule = rule(raw, Placement.CONVERT, log);
            if (rule != null) {
                RULES.add(rule);
            }
        }
        for (Map<?, ?> raw : section.getMapList("decorate")) {
            Placement placement = "ceiling".equalsIgnoreCase(String.valueOf(raw.get("at")))
                    ? Placement.CEILING : Placement.FLOOR;
            Rule rule = rule(raw, placement, log);
            if (rule != null) {
                RULES.add(rule);
            }
        }
    }

    private static Rule rule(Map<?, ?> raw, Placement placement, Logger log) {
        int minLevel = raw.get("min_level") instanceof Number number ? number.intValue() : 0;
        int maxLevel = raw.get("max_level") instanceof Number number ? number.intValue() : Integer.MAX_VALUE;
        Set<Material> from = new HashSet<>(materials(raw.get("from"), log));
        List<Material> to = materials(raw.get("to"), log);
        if (to.isEmpty() || (placement == Placement.CONVERT && from.isEmpty())) {
            log.warning("[Magic] blood magic config corruption rule without usable blocks skipped");
            return null;
        }
        return new Rule(minLevel, maxLevel, Set.copyOf(from), placement, List.copyOf(to));
    }

    private static List<Material> materials(Object raw, Logger log) {
        List<Material> out = new ArrayList<>();
        if (!(raw instanceof List<?> list)) {
            return out;
        }
        for (Object entry : list) {
            Material material = Material.matchMaterial(String.valueOf(entry).trim().toUpperCase(Locale.ROOT));
            if (material == null || !material.isBlock()) {
                log.warning("[Magic] blood magic config corruption: '" + entry + "' is not a block");
            } else {
                out.add(material);
            }
        }
        return out;
    }

    /** The farthest any level reaches. */
    public static int maxRadius() {
        int max = 0;
        for (Reach reach : LEVELS.values()) {
            max = Math.max(max, reach.radius());
        }
        return max;
    }

    /** The reach for a level: the highest configured level at or below it. */
    public static Reach reach(int level) {
        Map.Entry<Integer, Reach> entry = LEVELS.floorEntry(level);
        return entry != null ? entry.getValue() : LEVELS.firstEntry().getValue();
    }

    /** Rules an altar of this level may use (a rule with a max_level retires above it). */
    public static List<Rule> rules(int level) {
        List<Rule> out = new ArrayList<>();
        for (Rule rule : RULES) {
            if (level >= rule.minLevel() && level <= rule.maxLevel()) {
                out.add(rule);
            }
        }
        return out;
    }

    public static int blocksPerSacrifice() {
        return blocksPerSacrifice;
    }

    /** Blocks the slow spread changes each time. */
    public static int ambientBlocks() {
        return ambientBlocks;
    }

    public static int ambientEveryMinutes() {
        return ambientEveryMinutes;
    }
}
