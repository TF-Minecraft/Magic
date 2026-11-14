package net.tfminecraft.magic.blood;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.function.BiConsumer;

import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

/**
 * The flames on the altar's candles are vanilla's own candle particles (fx.yml candles:): every few
 * ticks a small flame on each wick of the altar's model, now and then a wisp of smoke, only while a
 * player is near. The wick positions are in the model's pixels and follow the altar's facing and scale.
 */
public final class AltarCandles {

    /** A wick top in model pixels, and the highest altar level that still has that candle. */
    record Wick(double x, double y, double z, int maxLevel) {}

    private static boolean enabled;
    private static int everyTicks;
    private static double smokeChance;
    private static double viewRange;
    private static float yawOffset;
    private static Particle flame;
    private static List<Wick> wicks = List.of();
    private static BukkitTask task;
    private static long ticks;
    static Random random = new Random();

    static {
        reset();
    }

    private AltarCandles() {}

    static void reset() {
        enabled = false;
        everyTicks = 8;
        smokeChance = 0.1;
        viewRange = 24;
        yawOffset = 180;
        flame = Particle.SMALL_FLAME;
        wicks = List.of();
    }

    static void load(ConfigurationSection section, java.util.logging.Logger log) {
        reset();
        if (section == null) {
            return;
        }
        enabled = section.getBoolean("enabled", true);
        everyTicks = Math.max(1, section.getInt("every_ticks", 8));
        smokeChance = Math.max(0, Math.min(1, section.getDouble("smoke_chance", 0.1)));
        viewRange = Math.max(1, section.getDouble("view_range", 24));
        yawOffset = (float) section.getDouble("yaw_offset", 180);
        String name = section.getString("particle", "SMALL_FLAME");
        try {
            flame = Particle.valueOf(name.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            log.warning("[Magic] blood magic config fx.candles.particle: unknown particle '" + name + "'");
        }
        List<Wick> out = new ArrayList<>();
        for (Object raw : section.getList("wicks", List.of())) {
            if (raw instanceof List<?> list && list.size() >= 3 && list.get(0) instanceof Number x
                    && list.get(1) instanceof Number y && list.get(2) instanceof Number z) {
                int max = list.size() > 3 && list.get(3) instanceof Number m ? m.intValue() : Integer.MAX_VALUE;
                out.add(new Wick(x.doubleValue(), y.doubleValue(), z.doubleValue(), max));
            } else {
                log.warning("[Magic] blood magic config fx.candles.wicks: '" + raw + "' is not [x, y, z, max_level]");
            }
        }
        wicks = List.copyOf(out);
    }

    static void startTicking(Plugin plugin) {
        stop();
        task = plugin.getServer().getScheduler().runTaskTimer(plugin, AltarCandles::tick, 20L, 1L);
    }

    static void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
    }

    /** Every tick; flames go up every every_ticks for altars with a player in view. */
    static void tick() {
        if (!enabled || wicks.isEmpty() || ++ticks % everyTicks != 0) {
            return;
        }
        for (BloodAltar altar : BloodAltars.all()) {
            Location center = altar.center();
            if (center == null || !center.getWorld().isChunkLoaded(altar.getX() >> 4, altar.getZ() >> 4)
                    || !watched(center)) {
                continue;
            }
            for (Wick wick : wicks) {
                if (altar.getLevel() <= wick.maxLevel()) {
                    light(position(center, altar.getYaw(), wick));
                }
            }
        }
    }

    private static void light(Location at) {
        spawner.accept(flame, at);
        if (random.nextDouble() < smokeChance) {
            spawner.accept(Particle.SMOKE, at.clone().add(0, 0.05, 0));
        }
    }

    /** Puts one particle at a spot for the players around it. */
    static BiConsumer<Particle, Location> spawner = (particle, at) -> at.getWorld().spawnParticle(particle, at, 1, 0, 0, 0, 0);

    private static boolean watched(Location center) {
        for (Player player : center.getWorld().getPlayers()) {
            if (player.getLocation().distanceSquared(center) <= viewRange * viewRange) {
                return true;
            }
        }
        return false;
    }

    /**
     * Where a wick of the model is in the world: the model is centred on the altar's block, scaled by
     * altar.display_scale and turned by the altar's yaw (plus yaw_offset for how item displays face).
     */
    static Location position(Location center, float yaw, Wick wick) {
        double scale = BloodConfig.displayScale();
        double x = (wick.x() - 8) / 16 * scale;
        double y = (wick.y() - 8) / 16 * scale;
        double z = (wick.z() - 8) / 16 * scale;
        double angle = Math.toRadians(-(yaw + yawOffset));
        double cos = Math.cos(angle);
        double sin = Math.sin(angle);
        return center.clone().add(x * cos + z * sin, y, -x * sin + z * cos);
    }
}
