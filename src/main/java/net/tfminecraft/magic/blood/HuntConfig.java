package net.tfminecraft.magic.blood;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.logging.Logger;

import org.bukkit.configuration.ConfigurationSection;

/** Character sacrifices (hunt:), closeness (bonds:) and the final sacrifices (finals:). */
public final class HuntConfig {

    /** Ranks up to and including upTo are worth this many hunt points. */
    public record RankPoints(int upTo, int points) {}

    /**
     * What final sacrifices give the path's class: skill points for its own pool (a lifetime total),
     * MMOCore skill slots to open and class skills (spells) to unlock.
     */
    public record ClassReward(int skillPoints, List<Integer> slots, List<String> spells) {
        static final ClassReward NONE = new ClassReward(0, List.of(), List.of());

        ClassReward plus(ClassReward other) {
            List<Integer> allSlots = new ArrayList<>(slots);
            other.slots.stream().filter(slot -> !allSlots.contains(slot)).forEach(allSlots::add);
            List<String> allSpells = new ArrayList<>(spells);
            other.spells.stream().filter(spell -> !allSpells.contains(spell)).forEach(allSpells::add);
            return new ClassReward(skillPoints + other.skillPoints, List.copyOf(allSlots), List.copyOf(allSpells));
        }
    }

    private static double dragRange;
    private static int riteSeconds;
    private static boolean carryEnabled;
    private static double carryDistance;
    private static int carrySlowness;
    private static double carryBreakDistance;
    private static double minPlaytimeHours;
    private static double minAgeDays;
    private static boolean noRepeatPlayer;
    private static int strangerPoints;
    private static int defaultPoints;
    private static final List<RankPoints> RANK_POINTS = new ArrayList<>();

    private static int sampleMinutes;
    private static double nearRadius;
    private static double conversationWeight;
    private static double hourWeight;
    private static double heirBonus;
    private static double heirMinHours;
    private static int heirMinConversations;

    private static int finalCount;
    private static long failCooldownMillis;
    private static long inactiveMillis;
    private static final Map<BloodPath, TreeMap<Integer, Map<String, Double>>> BOONS = new EnumMap<>(BloodPath.class);
    private static final Map<BloodPath, TreeMap<Integer, ClassReward>> REWARDS = new EnumMap<>(BloodPath.class);

    static {
        reset();
    }

    private HuntConfig() {}

    static void reset() {
        dragRange = 8;
        riteSeconds = 10;
        carryEnabled = true;
        carryDistance = 1.2;
        carrySlowness = 1;
        carryBreakDistance = 6;
        minPlaytimeHours = 10;
        minAgeDays = 7;
        noRepeatPlayer = true;
        strangerPoints = 1;
        defaultPoints = 1;
        RANK_POINTS.clear();
        sampleMinutes = 5;
        nearRadius = 16;
        conversationWeight = 3;
        hourWeight = 2;
        heirBonus = 40;
        heirMinHours = 10;
        heirMinConversations = 20;
        finalCount = 5;
        failCooldownMillis = 72L * 3_600_000L;
        inactiveMillis = 21L * 86_400_000L;
        BOONS.clear();
        REWARDS.clear();
    }

    static void load(ConfigurationSection hunt, ConfigurationSection bonds, ConfigurationSection finals, Logger log) {
        reset();
        if (hunt != null) {
            dragRange = Math.max(1, hunt.getDouble("drag_range", 8));
            riteSeconds = Math.max(1, hunt.getInt("rite_seconds", 10));
            carryEnabled = hunt.getBoolean("carry.enabled", true);
            carryDistance = Math.max(0.5, hunt.getDouble("carry.distance", 1.2));
            carrySlowness = Math.max(0, hunt.getInt("carry.slowness", 1));
            carryBreakDistance = Math.max(carryDistance + 1, hunt.getDouble("carry.break_distance", 6));
            minPlaytimeHours = Math.max(0, hunt.getDouble("victims.min_playtime_hours", 10));
            minAgeDays = Math.max(0, hunt.getDouble("victims.min_age_days", 7));
            noRepeatPlayer = hunt.getBoolean("victims.no_repeat_player", true);
            strangerPoints = Math.max(0, hunt.getInt("points.stranger", 1));
            defaultPoints = Math.max(0, hunt.getInt("points.default", 1));
            for (Map<?, ?> raw : hunt.getMapList("points.ranks")) {
                if (raw.get("up_to") instanceof Number upTo && raw.get("points") instanceof Number points) {
                    RANK_POINTS.add(new RankPoints(upTo.intValue(), Math.max(0, points.intValue())));
                } else {
                    log.warning("[Magic] blood magic config hunt.points.ranks entry needs up_to and points");
                }
            }
            RANK_POINTS.sort(Comparator.comparingInt(RankPoints::upTo));
        }
        if (bonds != null) {
            sampleMinutes = Math.max(1, bonds.getInt("sample_minutes", 5));
            nearRadius = Math.max(1, bonds.getDouble("near_radius", 16));
            conversationWeight = Math.max(0, bonds.getDouble("conversation_weight", 3));
            hourWeight = Math.max(0, bonds.getDouble("hour_weight", 2));
            heirBonus = Math.max(0, bonds.getDouble("heir_bonus", 40));
            heirMinHours = Math.max(0, bonds.getDouble("heir_min_hours", 10));
            heirMinConversations = Math.max(0, bonds.getInt("heir_min_conversations", 20));
        }
        if (finals != null) {
            finalCount = Math.max(1, finals.getInt("count", 5));
            failCooldownMillis = (long) (Math.max(0, finals.getDouble("fail_cooldown_hours", 72)) * 3_600_000L);
            inactiveMillis = (long) (Math.max(1, finals.getDouble("inactive_days", 21)) * 86_400_000L);
            for (BloodPath path : List.of(BloodPath.DEVOTEE, BloodPath.TRUE)) {
                ConfigurationSection section = finals.getConfigurationSection(
                        "boons." + path.name().toLowerCase(Locale.ROOT));
                if (section != null) {
                    BOONS.put(path, boons(section, log));
                }
                ConfigurationSection rewards = finals.getConfigurationSection(
                        "class_rewards." + path.name().toLowerCase(Locale.ROOT));
                if (rewards != null) {
                    REWARDS.put(path, rewards(rewards, log));
                }
            }
        }
    }

    private static TreeMap<Integer, ClassReward> rewards(ConfigurationSection section, Logger log) {
        TreeMap<Integer, ClassReward> out = new TreeMap<>();
        for (String key : section.getKeys(false)) {
            ConfigurationSection reward = section.getConfigurationSection(key);
            int index = index(key);
            if (index < 1 || reward == null) {
                log.warning("[Magic] blood magic config finals.class_rewards entry '" + key + "' skipped");
                continue;
            }
            List<String> spells = new ArrayList<>();
            for (String spell : reward.getStringList("spells")) {
                if (!spell.isBlank()) {
                    spells.add(spell.trim().toUpperCase(Locale.ROOT));
                }
            }
            List<Integer> slots = new ArrayList<>();
            for (Integer slot : reward.getIntegerList("slots")) {
                if (slot > 0) {
                    slots.add(slot);
                }
            }
            out.put(index, new ClassReward(Math.max(0, reward.getInt("skill_points", 0)), List.copyOf(slots),
                    List.copyOf(spells)));
        }
        return out;
    }

    private static int index(String key) {
        try {
            return Integer.parseInt(key.trim());
        } catch (NumberFormatException ex) {
            return -1;
        }
    }

    private static TreeMap<Integer, Map<String, Double>> boons(ConfigurationSection section, Logger log) {
        TreeMap<Integer, Map<String, Double>> out = new TreeMap<>();
        for (String key : section.getKeys(false)) {
            ConfigurationSection stats = section.getConfigurationSection(key);
            int index = index(key);
            if (index < 1 || stats == null) {
                log.warning("[Magic] blood magic config finals.boons entry '" + key + "' skipped");
                continue;
            }
            Map<String, Double> values = new LinkedHashMap<>();
            for (String stat : stats.getKeys(false)) {
                values.put(stat, stats.getDouble(stat));
            }
            out.put(index, values);
        }
        return out;
    }

    public static double dragRange() {
        return dragRange;
    }

    public static boolean carryEnabled() {
        return carryEnabled;
    }

    /** Blocks the dragged body trails behind the mage. */
    public static double carryDistance() {
        return carryDistance;
    }

    /** Slowness level while dragging; 0 for none. */
    public static int carrySlowness() {
        return carrySlowness;
    }

    /** The drag breaks once the body is further away than this (teleports, pearls). */
    public static double carryBreakDistance() {
        return carryBreakDistance;
    }

    public static int riteSeconds() {
        return riteSeconds;
    }

    public static double minPlaytimeHours() {
        return minPlaytimeHours;
    }

    public static double minAgeDays() {
        return minAgeDays;
    }

    public static boolean noRepeatPlayer() {
        return noRepeatPlayer;
    }

    /**
     * Hunt points for a victim at this closeness rank (1 = closest), or 0 for someone the mage
     * never got to know. Ranks inside the final count are not huntable at all.
     */
    public static int points(int rank) {
        if (rank <= 0) {
            return strangerPoints;
        }
        for (RankPoints tier : RANK_POINTS) {
            if (rank <= tier.upTo()) {
                return tier.points();
            }
        }
        return defaultPoints;
    }

    public static int sampleMinutes() {
        return sampleMinutes;
    }

    public static double nearRadius() {
        return nearRadius;
    }

    public static double conversationWeight() {
        return conversationWeight;
    }

    public static double hourWeight() {
        return hourWeight;
    }

    public static double heirBonus() {
        return heirBonus;
    }

    public static double heirMinHours() {
        return heirMinHours;
    }

    public static int heirMinConversations() {
        return heirMinConversations;
    }

    public static int finalCount() {
        return finalCount;
    }

    public static long failCooldownMillis() {
        return failCooldownMillis;
    }

    public static long inactiveMillis() {
        return inactiveMillis;
    }

    /** All boons earned after this many final sacrifices on a path, summed per stat. */
    public static Map<String, Double> boonsUpTo(BloodPath path, int finalsDone) {
        Map<String, Double> total = new LinkedHashMap<>();
        TreeMap<Integer, Map<String, Double>> table = BOONS.get(path);
        if (table == null) {
            return total;
        }
        for (Map<String, Double> step : table.headMap(finalsDone, true).values()) {
            step.forEach((stat, value) -> total.merge(stat, value, Double::sum));
        }
        return total;
    }

    /** Everything the path's class has earned after this many final sacrifices, added up. */
    public static ClassReward classRewardsUpTo(BloodPath path, int finalsDone) {
        ClassReward total = ClassReward.NONE;
        TreeMap<Integer, ClassReward> table = REWARDS.get(path);
        if (table != null) {
            for (ClassReward step : table.headMap(finalsDone, true).values()) {
                total = total.plus(step);
            }
        }
        return total;
    }
}
