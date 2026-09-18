package net.tfminecraft.magic.gear.orb;

import java.util.LinkedHashMap;
import java.util.Map;

import org.bukkit.Color;
import org.bukkit.Particle;

/**
 * Runtime values from {@code gear/orbs.yml}. Difficulty rows are keyed by charge item
 * tier, so a tier 1 charge always plays the easy game no matter what band it holds.
 */
public final class OrbCache {

    public static double hitRadius = 0.5;
    public static double clickRange = 12.0;

    public static double orbitRadius = 2.4;
    public static double orbitHeight = 1.1;
    public static double orbitBob = 0.25;
    public static long orbitPeriodTicks = 90L;

    public static int introTicks = 10;
    public static int lifetimeTicks = 70;
    public static int spawnIntervalTicks = 14;
    public static int windowTicks = 200;

    public static double missPenalty = 0.08;

    public static Color goodColor = Color.fromRGB(120, 220, 255);
    public static Color badColor = Color.fromRGB(220, 90, 40);
    public static Particle goodParticle = Particle.END_ROD;
    public static Particle badParticle = Particle.SMOKE;

    public static int riftPerBad = 5;
    public static int riftCap = 100;
    public static int riftPerRecharge = 10;

    private static final Map<Integer, Tier> TIERS = new LinkedHashMap<>();

    private OrbCache() {}

    /** One difficulty row. */
    public static final class Tier {
        private final int live;
        private final double goodRatio;
        private final double speed;
        private final int windowTicks;
        private final int goodTarget;

        public Tier(int live, double goodRatio, double speed, int windowTicks, int goodTarget) {
            this.live = Math.max(1, live);
            this.goodRatio = Math.max(0.0, Math.min(1.0, goodRatio));
            this.speed = speed <= 0 ? 1.0 : speed;
            this.windowTicks = Math.max(20, windowTicks);
            this.goodTarget = Math.max(1, goodTarget);
        }

        public int live() {
            return live;
        }

        public double goodRatio() {
            return goodRatio;
        }

        public double speed() {
            return speed;
        }

        public int windowTicks() {
            return windowTicks;
        }

        public int goodTarget() {
            return goodTarget;
        }
    }

    public static void clearTiers() {
        TIERS.clear();
    }

    public static void putTier(int tier, Tier row) {
        if (tier > 0 && row != null) {
            TIERS.put(tier, row);
        }
    }

    public static int tierCount() {
        return TIERS.size();
    }

    /** Nearest configured row at or below the tier, falling back to the lowest one. */
    public static Tier tier(int tier) {
        Tier exact = TIERS.get(tier);
        if (exact != null) {
            return exact;
        }
        Tier best = null;
        int bestKey = Integer.MIN_VALUE;
        for (Map.Entry<Integer, OrbCache.Tier> entry : TIERS.entrySet()) {
            if (entry.getKey() <= tier && entry.getKey() > bestKey) {
                bestKey = entry.getKey();
                best = entry.getValue();
            }
        }
        if (best != null) {
            return best;
        }
        for (Tier row : TIERS.values()) {
            return row;
        }
        return new Tier(4, 0.7, 1.0, windowTicks, 4);
    }
}
