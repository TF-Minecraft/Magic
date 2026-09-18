package net.tfminecraft.magic.artifact.shrine;

import java.util.List;

import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;

import net.tfminecraft.furniture.Furniture;
import net.tfminecraft.furniture.PlacedSlot;
import net.tfminecraft.magic.Magic;
import net.tfminecraft.magic.artifact.aura.AuraVessel;
import net.tfminecraft.magic.artifact.aura.AuraVessels;
import net.tfminecraft.magic.model.ElementDef;
import net.tfminecraft.magic.registry.ElementRegistry;
import net.tfminecraft.magic.util.MagicText;
import net.tfminecraft.magic.util.PedestalFx;

public final class ShrineChargeFx {

    private static final int MAX_STREAM_BLOCKS = 14;
    private static final double EPSILON = 0.0001;
    private static final Color FALLBACK = Color.WHITE;

    private static BukkitTask task;
    private static long fxTick;

    private ShrineChargeFx() {}

    public static void ensureRunning() {
        if (task != null && !task.isCancelled()) {
            return;
        }
        if (Magic.plugin == null) {
            return;
        }
        fxTick = 0;
        task = Bukkit.getScheduler().runTaskTimer(Magic.plugin, ShrineChargeFx::tick, 1L, 2L);
    }

    public static void stopIfIdle() {
        if (ShrineChargeService.hasSessions()) {
            return;
        }
        if (task != null) {
            task.cancel();
            task = null;
        }
    }

    public static void tick() {
        if (!ShrineChargeService.hasSessions()) {
            stopIfIdle();
            return;
        }
        fxTick++;
        for (ShrineChargeSession session : ShrineChargeService.sessions()) {
            spawnPulls(session);
        }
    }

    public static void complete(Furniture furniture, AuraVessel artifact, ItemStack item, ShrineScore score) {
        complete(furniture, ShrineChargeService.chargeFxElement(item, artifact, score, true));
    }

    public static void complete(Furniture furniture, String elementId) {
        if (furniture == null || furniture.getLoc() == null || furniture.getLoc().getWorld() == null) {
            return;
        }
        World world = furniture.getLoc().getWorld();
        Location at = target(furniture);
        if (world == null || at == null) {
            return;
        }
        ShrineFxDef fx = ShrineRegistry.fx(elementId);
        Color color = elementColor(elementId);
        Particle.DustOptions dust = new Particle.DustOptions(color, 1.2f);
        world.spawnParticle(Particle.DUST, at, 24, 0.35, 0.45, 0.35, dust);
        spawn(world, fx.getTrailParticle(), at, 18, 0.4, 0.5, 0.4, 0.4);
        spawn(world, fx.getBurstParticle(), at, 8, 0.2, 0.3, 0.2, 0.02);
        play(world, at, fx.getCompleteSound(), fx.getCompleteVolume(), fx.getCompletePitch());
        play(world, at, fx.getCompleteSound2(), fx.getCompleteVolume2(), fx.getCompletePitch2());
    }

    public static void start(Furniture furniture, String elementId) {
        if (furniture == null || furniture.getLoc() == null || furniture.getLoc().getWorld() == null) {
            return;
        }
        Location at = target(furniture);
        if (at == null) {
            return;
        }
        ShrineFxDef fx = ShrineRegistry.fx(elementId);
        Color color = elementColor(elementId);
        World world = at.getWorld();
        world.spawnParticle(Particle.DUST, at, 12, 0.25, 0.35, 0.25, new Particle.DustOptions(color, 1.0f));
        spawn(world, fx.getEmitterParticle(), at, 8, 0.3, 0.4, 0.3, 0.02);
        play(world, at, Sound.BLOCK_BEACON_ACTIVATE, 1.0f, 1.4f);
    }

    public static void sacrificeReady(Furniture furniture, String elementId) {
        if (furniture == null || furniture.getLoc() == null || furniture.getLoc().getWorld() == null) {
            return;
        }
        Location at = target(furniture);
        if (at == null) {
            return;
        }
        ShrineFxDef fx = ShrineRegistry.fx(elementId);
        Color color = elementColor(elementId);
        World world = at.getWorld();
        world.spawnParticle(Particle.DUST, at, 10, 0.22, 0.3, 0.22, new Particle.DustOptions(color, 1.0f));
        spawn(world, fx.getEmitterParticle(), at, 6, 0.25, 0.35, 0.25, 0.02);
        spawn(world, fx.getBurstParticle(), at, 5, 0.18, 0.22, 0.18, 0.02);
        play(world, at, fx.getStartSound(), fx.getStartVolume(), fx.getStartPitch());
    }

    private static void spawnPulls(ShrineChargeSession session) {
        Furniture furniture = session.getFurniture();
        if (furniture == null || furniture.getLoc() == null || furniture.getLoc().getWorld() == null) {
            return;
        }
        PlacedSlot slot = furniture.getActiveSlot(session.getSlotId()).orElse(null);
        ItemStack item = ShrineChargeService.itemFromSlot(slot);
        AuraVessel artifact = AuraVessels.fromItem(item);
        if (artifact == null) {
            return;
        }
        Location to = target(furniture);
        if (to == null) {
            return;
        }
        World world = to.getWorld();
        int stagger = Math.abs(ShrineChargeService.key(session.getFurnitureId(), session.getSlotId()).hashCode());
        String ambientElement = session.isAdminForced()
                ? session.getForcedElement()
                : ShrineChargeService.chargeFxElement(item, artifact, session.getScore(), false);
        Iterable<String> elements = session.isAdminForced() && session.getForcedElement() != null
                ? List.of(session.getForcedElement())
                : ShrineChargeService.allowedElements(item, artifact);
        for (String elementId : elements) {
            if (!session.isAdminForced() && ShrineChargeService.blocksVanillaCharge(elementId)) {
                continue;
            }
            ShrineElementScore score = session.getScore().get(elementId);
            if (!session.isAdminForced()) {
                if (score == null || score.getMaxAura() <= 0 || score.getAuraPerSecond() <= 0) {
                    continue;
                }
            } else if (score == null) {
                score = new ShrineElementScore(elementId, 0, 0, List.of(), List.of());
            }
            double cap = artifact.getCap(elementId);
            if (cap <= 0) {
                continue;
            }
            double clamp = session.isAdminForced()
                    ? Math.min(cap, session.getTargetFill())
                    : Math.min(cap, score.getMaxAura());
            if (artifact.getFill(elementId) + EPSILON >= clamp) {
                continue;
            }
            Color color = elementColor(elementId);
            ShrineFxDef fx = ShrineRegistry.fx(elementId);
            Particle.DustOptions dust = new Particle.DustOptions(color, 0.8f);
            List<Location> blocks = score.getContributingBlocks();
            int limit = Math.min(MAX_STREAM_BLOCKS, blocks.size());
            for (int i = 0; i < limit; i++) {
                Location from = blocks.get(i);
                if (from == null || from.getWorld() == null) {
                    continue;
                }
                Location start = from.clone().add(0.5, 0.5, 0.5);
                int phase = (int) ((fxTick + stagger + i * 3) % 8);
                double t = phase / 8.0;
                Location point = lerp(start, to, t);
                world.spawnParticle(Particle.DUST, point, 1, 0.0, 0.0, 0.0, dust);
                if (phase == 0) {
                    spawn(world, fx.getEmitterParticle(), start, 2, 0.12, 0.18, 0.12, 0.01);
                }
                if (phase == 0 || phase == 4) {
                    spawn(world, fx.getTrailParticle(), start, 2, 0.12, 0.12, 0.12, 0.35);
                } else {
                    spawn(world, fx.getTrailParticle(), point, 1, 0.04, 0.04, 0.04, 0.15);
                }
            }
        }
        if (ambientElement != null) {
            ShrineFxDef fx = ShrineRegistry.fx(ambientElement);
            int period = fx.getAmbientEveryTicks();
            if ((fxTick + stagger) % period == 0) {
                play(world, to, fx.getAmbientSound(), fx.getAmbientVolume(), fx.getAmbientPitch());
            }
        }
    }

    private static void play(World world, Location at, Sound sound, float volume, float pitch) {
        if (world == null || at == null || sound == null) {
            return;
        }
        world.playSound(at, sound, SoundCategory.MASTER, Math.max(1.0f, volume), pitch);
    }

    private static void spawn(
            World world,
            Particle particle,
            Location at,
            int count,
            double ox,
            double oy,
            double oz,
            double extra) {
        if (world == null || at == null || particle == null || count <= 0) {
            return;
        }
        try {
            world.spawnParticle(particle, at, count, ox, oy, oz, extra);
        } catch (Exception ignored) {
            // Particle may need extra data on this server version.
        }
    }

    private static Location target(Furniture furniture) {
        return PedestalFx.artifactPoint(furniture);
    }

    private static Location lerp(Location from, Location to, double t) {
        return new Location(
                from.getWorld(),
                from.getX() + (to.getX() - from.getX()) * t,
                from.getY() + (to.getY() - from.getY()) * t,
                from.getZ() + (to.getZ() - from.getZ()) * t);
    }

    public static Color elementColor(String elementId) {
        ElementDef element = ElementRegistry.getById(elementId);
        String hex = element != null ? element.getColor() : null;
        return MagicText.bukkitColor(hex, FALLBACK);
    }
}
