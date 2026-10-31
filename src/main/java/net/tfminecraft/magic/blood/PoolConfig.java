package net.tfminecraft.magic.blood;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.logging.Logger;

import org.bukkit.configuration.ConfigurationSection;

/**
 * The pool of blood (settings.yml pool:): how much the altar's pool holds, what charging artefacts in
 * it costs once the path is complete, how fast blood resonance fades then, what sacrifices pour back,
 * what the path's artefacts are worth, and the blood resonance each spell tier needs.
 */
public final class PoolConfig {

    private record RankAmount(int upTo, double amount) {}

    private static double capacity;
    private static double decayPercent;
    private static double bloodPerPoint;
    private static double floor;
    private static double strangerRefill;
    private static double defaultRefill;
    private static final List<RankAmount> RANK_REFILLS = new ArrayList<>();
    private static final Map<BloodPath, TreeMap<Integer, Double>> WORTH = new EnumMap<>(BloodPath.class);
    private static final TreeMap<Integer, Double> TIERS = new TreeMap<>();
    private static final Map<String, Integer> SPELLS = new HashMap<>();

    static {
        reset();
    }

    private PoolConfig() {}

    static void reset() {
        capacity = 100;
        decayPercent = 1;
        bloodPerPoint = 2;
        floor = 5;
        strangerRefill = 40;
        defaultRefill = 40;
        RANK_REFILLS.clear();
        WORTH.clear();
        TIERS.clear();
        SPELLS.clear();
    }

    static void load(ConfigurationSection pool, Logger log) {
        reset();
        if (pool == null) {
            return;
        }
        capacity = Math.max(1, pool.getDouble("capacity", 100));
        decayPercent = Math.max(0, pool.getDouble("decay_percent_per_hour", 1));
        bloodPerPoint = Math.max(0, pool.getDouble("blood_per_point", 2));
        floor = Math.max(0, pool.getDouble("floor", 5));
        strangerRefill = Math.max(0, pool.getDouble("refill.people.stranger", 40));
        defaultRefill = Math.max(0, pool.getDouble("refill.people.default", 40));
        for (Map<?, ?> raw : pool.getMapList("refill.people.ranks")) {
            if (raw.get("up_to") instanceof Number upTo && raw.get("amount") instanceof Number amount) {
                RANK_REFILLS.add(new RankAmount(upTo.intValue(), Math.max(0, amount.doubleValue())));
            } else {
                log.warning("[Magic] blood magic config pool.refill.people.ranks entry needs up_to and amount");
            }
        }
        RANK_REFILLS.sort(Comparator.comparingInt(RankAmount::upTo));
        for (BloodPath path : List.of(BloodPath.DEVOTEE, BloodPath.TRUE)) {
            ConfigurationSection worth = pool.getConfigurationSection("worth." + path.name().toLowerCase(Locale.ROOT));
            if (worth != null) {
                WORTH.put(path, numbers(worth, "pool.worth." + path.name().toLowerCase(Locale.ROOT), log));
            }
        }
        TIERS.putAll(numbers(pool.getConfigurationSection("tiers"), "pool.tiers", log));
        ConfigurationSection spells = pool.getConfigurationSection("spells");
        if (spells != null) {
            for (String key : spells.getKeys(false)) {
                int tier = index(key);
                if (tier < 1) {
                    log.warning("[Magic] blood magic config pool.spells entry '" + key + "' skipped");
                    continue;
                }
                for (String spell : spells.getStringList(key)) {
                    if (!spell.isBlank()) {
                        SPELLS.put(spell.trim().toUpperCase(Locale.ROOT), tier);
                    }
                }
            }
        }
    }

    private static TreeMap<Integer, Double> numbers(ConfigurationSection section, String path, Logger log) {
        TreeMap<Integer, Double> out = new TreeMap<>();
        if (section == null) {
            return out;
        }
        for (String key : section.getKeys(false)) {
            int index = index(key);
            if (index < 0 || !section.isDouble(key) && !section.isInt(key)) {
                log.warning("[Magic] blood magic config " + path + " entry '" + key + "' skipped");
                continue;
            }
            out.put(index, Math.max(0, section.getDouble(key)));
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

    public static double capacity() {
        return capacity;
    }

    /** Share (percent) of their blood resonance a mage loses per hour played, once the path is complete. */
    public static double decayPercent() {
        return decayPercent;
    }

    /** Pool blood one point of artefact charge costs, once the path is complete. */
    public static double bloodPerPoint() {
        return bloodPerPoint;
    }

    /** Blood resonance never sinks below this. */
    public static double floor() {
        return floor;
    }

    /** Blood a person pours back, by their closeness rank to the mage (0 = a stranger). */
    public static double refill(int rank) {
        if (rank <= 0) {
            return strangerRefill;
        }
        for (RankAmount tier : RANK_REFILLS) {
            if (rank <= tier.upTo()) {
                return tier.amount();
            }
        }
        return defaultRefill;
    }

    /**
     * Blood resonance the path's artefacts give when every one is in the pool: the highest entry at
     * or below the final sacrifices done; 0 for an unknown path.
     */
    public static double worth(BloodPath path, int finalsDone) {
        TreeMap<Integer, Double> table = WORTH.get(path);
        Map.Entry<Integer, Double> entry = table != null ? table.floorEntry(Math.max(0, finalsDone)) : null;
        return entry != null ? entry.getValue() : 0;
    }

    /** Blood resonance a spell tier needs; 0 for a tier with no entry. */
    public static double need(int tier) {
        return TIERS.getOrDefault(tier, 0.0);
    }

    /** The highest tier this much resonance reaches; 0 below every tier. */
    public static int band(double resonance) {
        int band = 0;
        for (Map.Entry<Integer, Double> tier : TIERS.entrySet()) {
            if (resonance >= tier.getValue()) {
                band = Math.max(band, tier.getKey());
            }
        }
        return band;
    }

    /** The spell's tier, 0 when it is not a blood spell. */
    public static int tierOf(String spell) {
        return spell == null ? 0 : SPELLS.getOrDefault(spell.trim().toUpperCase(Locale.ROOT), 0);
    }
}
