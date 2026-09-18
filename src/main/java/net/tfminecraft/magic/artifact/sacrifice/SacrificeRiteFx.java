package net.tfminecraft.magic.artifact.sacrifice;

import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import net.tfminecraft.furniture.Furniture;
import net.tfminecraft.magic.Magic;
import net.tfminecraft.magic.artifact.shrine.ShrineChargeFx;

public final class SacrificeRiteFx {

    private static BukkitTask task;
    private static long fxTick;

    private SacrificeRiteFx() {}

    public static void ensureRunning() {
        if (task != null && !task.isCancelled()) {
            return;
        }
        if (Magic.plugin == null) {
            return;
        }
        fxTick = 0;
        task = Bukkit.getScheduler().runTaskTimer(Magic.plugin, SacrificeRiteFx::tick, 1L, 2L);
    }

    public static void stopIfIdle() {
        if (SacrificeRiteService.hasSessions()) {
            return;
        }
        if (task != null) {
            task.cancel();
            task = null;
        }
    }

    public static void resolve(SacrificeRiteSession session, Location victimLocation) {
        if (session == null || !session.isResolved()) {
            return;
        }
        SacrificeFxDef fx = SacrificeRegistry.getFx();
        if (fx == null || !fx.surges(session.getResolvedTierId())) {
            return;
        }
        Color color = ShrineChargeFx.elementColor(session.getElementId());
        Particle.DustOptions dust = new Particle.DustOptions(color, 1.35f);
        Location pedestal = SacrificeTargeting.originCenter(session.getFurniture());
        if (pedestal != null) {
            burst(pedestal, dust);
            playSounds(pedestal, fx);
        }
        if (victimLocation != null && victimLocation.getWorld() != null) {
            burst(victimLocation.clone().add(0, 1.0, 0), dust);
        }
    }

    private static void burst(Location at, Particle.DustOptions dust) {
        World world = at.getWorld();
        if (world == null) {
            return;
        }
        world.spawnParticle(Particle.DUST, at, 36, 0.45, 0.55, 0.45, dust);
        world.spawnParticle(Particle.ENCHANT, at, 28, 0.5, 0.6, 0.5, 0.55);
        spawnOptional(world, at, "SOUL", 10, 0.35, 0.45, 0.35, 0.02);
        spawnOptional(world, at, "SCULK_SOUL", 8, 0.3, 0.4, 0.3, 0.02);
    }

    private static void spawnOptional(
            World world,
            Location at,
            String particleName,
            int count,
            double ox,
            double oy,
            double oz,
            double extra) {
        try {
            Particle particle = Particle.valueOf(particleName);
            world.spawnParticle(particle, at, count, ox, oy, oz, extra);
        } catch (IllegalArgumentException ignored) {
            // Particle not on this API
        }
    }

    private static void playSounds(Location at, SacrificeFxDef fx) {
        World world = at.getWorld();
        if (world == null || fx == null) {
            return;
        }
        Sound surge = fx.getSurgeSound();
        if (surge != null) {
            world.playSound(at, surge, fx.getSurgeVolume(), fx.getSurgePitch());
        }
        Sound boom = fx.getBoomSound();
        if (boom != null) {
            world.playSound(at, boom, fx.getBoomVolume(), fx.getBoomPitch());
        }
    }

    private static void tick() {
        fxTick++;
        if (!SacrificeRiteService.hasSessions()) {
            stopIfIdle();
            return;
        }
        for (SacrificeRiteSession session : SacrificeRiteService.sessions()) {
            spawnPull(session);
        }
    }

    private static void spawnPull(SacrificeRiteSession session) {
        if (session == null) {
            return;
        }
        Player victim = Bukkit.getPlayer(session.getVictimId());
        Furniture furniture = session.getFurniture();
        if (victim == null || !victim.isOnline() || furniture == null) {
            return;
        }
        Location from = victim.getLocation().add(0, 1.2, 0);
        Location to = SacrificeTargeting.originCenter(furniture);
        if (from.getWorld() == null || to == null || from.getWorld() != to.getWorld()) {
            return;
        }
        World world = from.getWorld();
        Color color = ShrineChargeFx.elementColor(session.getElementId());
        Particle.DustOptions dust = new Particle.DustOptions(color, 0.9f);
        int stagger = Math.abs(session.getCasterId().hashCode());
        for (int i = 0; i < 5; i++) {
            double t = ((fxTick + stagger + i * 2) % 10) / 10.0;
            Location point = lerp(from, to, t);
            world.spawnParticle(Particle.DUST, point, 1, 0.0, 0.0, 0.0, dust);
            if (i == 0 || i == 3) {
                world.spawnParticle(Particle.ENCHANT, from, 2, 0.12, 0.12, 0.12, 0.35);
            }
        }
    }

    private static Location lerp(Location from, Location to, double t) {
        return new Location(
                from.getWorld(),
                from.getX() + (to.getX() - from.getX()) * t,
                from.getY() + (to.getY() - from.getY()) * t,
                from.getZ() + (to.getZ() - from.getZ()) * t);
    }
}
