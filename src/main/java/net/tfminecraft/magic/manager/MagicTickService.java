package net.tfminecraft.magic.manager;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import org.bukkit.scheduler.BukkitTask;

import net.tfminecraft.magic.Cache;
import net.tfminecraft.magic.Magic;
import net.tfminecraft.magic.tick.MagicTickContext;
import net.tfminecraft.magic.tick.MagicTickHandler;

/**
 * Single global tick cycle for Magic. One runnable drives elapsed time and dispatches handlers.
 * Handlers use {@link MagicTickContext#every(long)} for minute/hour style intervals.
 */
public final class MagicTickService {

    private static final List<MagicTickHandler> handlers = new CopyOnWriteArrayList<>();

    private static BukkitTask task;
    private static long elapsedSeconds;
    private static long elapsedTicks;
    private static long scheduledIntervalTicks;

    private MagicTickService() {}

    public static void start() {
        stop();
        long intervalTicks = Math.max(1L, Cache.tickIntervalTicks);
        scheduledIntervalTicks = intervalTicks;
        task = Magic.plugin.getServer().getScheduler().runTaskTimer(
                Magic.plugin, MagicTickService::tick, intervalTicks, intervalTicks);
    }

    public static void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
    }

    public static void clearHandlers() {
        handlers.clear();
    }

    public static void register(MagicTickHandler handler) {
        if (handler != null) {
            handlers.add(handler);
        }
    }

    public static void unregister(MagicTickHandler handler) {
        handlers.remove(handler);
    }

    public static long getElapsedSeconds() {
        return elapsedSeconds;
    }

    public static boolean isRunning() {
        return task != null && !task.isCancelled();
    }

    private static void tick() {
        long previousSeconds = elapsedSeconds;
        elapsedTicks += scheduledIntervalTicks;
        elapsedSeconds = elapsedTicks / 20L;
        MagicTickContext context = MagicTickContext.between(previousSeconds, elapsedSeconds);
        for (MagicTickHandler handler : handlers) {
            try {
                handler.onTick(context);
            } catch (Exception ex) {
                Magic.plugin.getLogger().warning("[Magic] Tick handler failed: " + ex.getMessage());
            }
        }
        if (Cache.debug && context.everyMinutes(1)) {
            Magic.plugin.getLogger().info("[Magic] Tick: " + context.seconds() + "s elapsed ("
                    + context.minutes() + "m, " + context.hours() + "h)");
        }
    }
}
