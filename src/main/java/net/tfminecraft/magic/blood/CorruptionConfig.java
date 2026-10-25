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

/** The corruption: section of bloodmagic.yml. */
public final class CorruptionConfig {

    /** How far an altar of a level reaches and how many blocks it may have changed. */
    public record Reach(int radius, int maxBlocks) {}

    /** A conversion of exposed solid blocks, or a decoration placed into air. */
    public record Rule(int minLevel, Set<Material> from, Placement on, List<Material> to) {}

    public enum Placement {
        CONVERT,
        FLOOR,
        CEILING
    }

    private static final TreeMap<Integer, Reach> LEVELS = new TreeMap<>();
    private static final List<Rule> RULES = new ArrayList<>();
    private static int blocksPerSacrifice;
    private static int ambientEveryMinutes;

    static {
        reset();
    }

    private CorruptionConfig() {}

    static void reset() {
        LEVELS.clear();
        LEVELS.put(0, new Reach(2, 4));
        RULES.clear();
        blocksPerSacrifice = 3;
        ambientEveryMinutes = 30;
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
                    LEVELS.put(level, new Reach(Math.max(1, levels.getInt(key + ".radius", 2)),
                            Math.max(0, levels.getInt(key + ".max_blocks", 4))));
                } catch (NumberFormatException ex) {
                    log.warning("[Magic] bloodmagic.yml corruption.levels key '" + key + "' is not a level number");
                }
            }
            if (LEVELS.isEmpty()) {
                LEVELS.put(0, new Reach(2, 4));
            }
        }
        blocksPerSacrifice = Math.max(0, section.getInt("blocks_per_sacrifice", 3));
        ambientEveryMinutes = Math.max(1, section.getInt("ambient_every_minutes", 30));
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
        Set<Material> from = new HashSet<>(materials(raw.get("from"), log));
        List<Material> to = materials(raw.get("to"), log);
        if (to.isEmpty() || (placement == Placement.CONVERT && from.isEmpty())) {
            log.warning("[Magic] bloodmagic.yml corruption rule without usable blocks skipped");
            return null;
        }
        return new Rule(minLevel, Set.copyOf(from), placement, List.copyOf(to));
    }

    private static List<Material> materials(Object raw, Logger log) {
        List<Material> out = new ArrayList<>();
        if (!(raw instanceof List<?> list)) {
            return out;
        }
        for (Object entry : list) {
            Material material = Material.matchMaterial(String.valueOf(entry).trim().toUpperCase(Locale.ROOT));
            if (material == null || !material.isBlock()) {
                log.warning("[Magic] bloodmagic.yml corruption: '" + entry + "' is not a block");
            } else {
                out.add(material);
            }
        }
        return out;
    }

    /** The reach for a level: the highest configured level at or below it. */
    public static Reach reach(int level) {
        Map.Entry<Integer, Reach> entry = LEVELS.floorEntry(level);
        return entry != null ? entry.getValue() : LEVELS.firstEntry().getValue();
    }

    /** Rules an altar of this level may use. */
    public static List<Rule> rules(int level) {
        List<Rule> out = new ArrayList<>();
        for (Rule rule : RULES) {
            if (level >= rule.minLevel()) {
                out.add(rule);
            }
        }
        return out;
    }

    public static int blocksPerSacrifice() {
        return blocksPerSacrifice;
    }

    public static int ambientEveryMinutes() {
        return ambientEveryMinutes;
    }
}
