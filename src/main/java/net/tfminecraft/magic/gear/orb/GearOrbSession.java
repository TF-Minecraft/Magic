package net.tfminecraft.magic.gear.orb;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Player;

import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import net.tfminecraft.magic.Messages;
import net.tfminecraft.magic.model.ElementDef;
import net.tfminecraft.magic.registry.ElementRegistry;
import net.tfminecraft.magic.util.MagicText;

/**
 * One attunement run at a station. The charge is already spent, so this only decides how
 * much of its aura survives and how much Rift the weapon picks up.
 */
public final class GearOrbSession {

    private final Location station;
    private final UUID playerId;
    private final Map<String, Double> snapshot;
    private final String primaryElement;
    private final OrbCache.Tier tier;
    private final Color beamColor;

    private final List<GearOrb> orbs = new ArrayList<>();
    private int goodHits;
    private int misses;
    private int riftDelta;
    private long ticks;
    private long nextSpawnTick;
    private long lastSwingTick = -1;
    private long lastHitTick = -100;

    public GearOrbSession(
            Location station,
            UUID playerId,
            Map<String, Double> snapshot,
            String primaryElement,
            OrbCache.Tier tier) {
        this.station = station.clone();
        this.playerId = playerId;
        this.snapshot = new LinkedHashMap<>(snapshot);
        this.primaryElement = primaryElement == null ? "" : primaryElement;
        this.tier = tier;
        this.beamColor = resolveBeamColor(this.primaryElement);
    }

    public Location getStation() {
        return station;
    }

    public UUID getPlayerId() {
        return playerId;
    }

    /** Orbit centre: one block above the station block's centre. */
    public Location anchor() {
        return station.clone().add(0.5, 1.0, 0.5);
    }

    /** Where the prepared weapon floats, used as the beam target. */
    public Location displayPoint() {
        return station.clone().add(0.5, 1.15, 0.5);
    }

    public boolean isFinished() {
        return ticks >= tier.windowTicks();
    }

    public boolean claimSwing(long nowTick) {
        if (nowTick == lastSwingTick) {
            return false;
        }
        lastSwingTick = nowTick;
        return true;
    }

    public boolean canHit(long nowTick) {
        return nowTick - lastHitTick >= 4L;
    }

    public void markHit(long nowTick) {
        lastHitTick = nowTick;
    }

    public long ticks() {
        return ticks;
    }

    public List<GearOrb> livingOrbs() {
        List<GearOrb> living = new ArrayList<>();
        for (GearOrb orb : orbs) {
            if (!orb.isConsumed()) {
                living.add(orb);
            }
        }
        return living;
    }

    public void tick(Player player) {
        ticks++;
        expire();
        if (!isFinished() && ticks >= nextSpawnTick && livingOrbs().size() < tier.live()) {
            spawn();
            nextSpawnTick = ticks + OrbCache.spawnGap(tier.live(), tier.speed());
        }
        orbit();
        render();
        prune();
        if (player != null && ticks % 5 == 0) {
            actionBar(player);
        }
    }

    public void onHit(Player player, GearOrb orb) {
        orb.setConsumed(true);
        if (orb.isGood()) {
            goodHits++;
        } else {
            riftDelta += OrbCache.riftPerBad;
        }
        playHitFx(player, orb);
        if (player != null) {
            actionBar(player);
        }
    }

    /** Fraction of the charge that survived the run, 0 to 1. */
    public double capturedFraction() {
        double raw = (double) goodHits / tier.goodTarget() - misses * OrbCache.missPenalty;
        return Math.max(0.0, Math.min(1.0, raw));
    }

    /** Per-element aura to merge into the weapon. */
    public Map<String, Double> capturedFill() {
        double fraction = capturedFraction();
        Map<String, Double> captured = new LinkedHashMap<>();
        for (Map.Entry<String, Double> entry : snapshot.entrySet()) {
            Double fill = entry.getValue();
            if (fill == null || fill <= 0) {
                continue;
            }
            captured.put(entry.getKey(), fill * fraction);
        }
        return captured;
    }

    public int riftDelta() {
        return riftDelta;
    }

    public int goodHits() {
        return goodHits;
    }

    public int misses() {
        return misses;
    }

    public int goodTarget() {
        return tier.goodTarget();
    }

    public void end() {
        orbs.clear();
    }

    public void playComplete() {
        World world = station.getWorld();
        if (world == null) {
            return;
        }
        Location at = displayPoint();
        world.playSound(at, Sound.BLOCK_ENCHANTMENT_TABLE_USE, 1.0f, 1.2f);
        world.playSound(at, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1.0f, 0.9f);
        world.spawnParticle(Particle.END_ROD, at, 14, 0.3, 0.35, 0.3, 0.03);
        world.spawnParticle(Particle.DUST, at, 20, 0.35, 0.4, 0.35,
                new Particle.DustOptions(beamColor, 1.0f));
    }

    private void expire() {
        for (GearOrb orb : orbs) {
            if (orb.isConsumed()) {
                continue;
            }
            if (orb.tickLife()) {
                orb.setConsumed(true);
                if (orb.isGood()) {
                    misses++;
                }
                fadeFx(orb);
            }
        }
    }

    private void spawn() {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        boolean good = random.nextDouble() < tier.goodRatio();
        double angle = random.nextDouble() * Math.PI * 2;
        double radius = OrbCache.orbitRadius * randomRange(random, 0.85, 1.15);
        double heightBias = OrbCache.orbitHeight + randomRange(random, -0.35, 0.35);
        double base = (Math.PI * 2.0) / Math.max(1L, OrbCache.orbitPeriodTicks) * tier.speed();
        // Bad orbs are quicker and swing wider so they read as wrong before the colour does.
        double angleSpeed = base * (good ? randomRange(random, 0.9, 1.1) : randomRange(random, 1.5, 1.9));
        if (random.nextBoolean()) {
            angleSpeed = -angleSpeed;
        }
        orbs.add(new GearOrb(
                anchor(),
                good,
                angle,
                radius,
                heightBias,
                angleSpeed,
                random.nextDouble() * Math.PI * 2,
                OrbCache.introTicks,
                OrbCache.lifetimeTicks));
    }

    private void orbit() {
        World world = station.getWorld();
        if (world == null) {
            return;
        }
        Location anchor = anchor();
        for (GearOrb orb : orbs) {
            if (orb.isConsumed()) {
                continue;
            }
            orb.setAngle(orb.getAngle() + orb.getAngleSpeed());
            orb.addBobPhase(orb.isGood() ? 0.1 : 0.32);
            double bob = OrbCache.orbitBob * (orb.isGood() ? 1.0 : 1.8) * Math.sin(orb.getBobPhase());
            Location target = new Location(
                    world,
                    anchor.getX() + Math.cos(orb.getAngle()) * orb.getRadius(),
                    anchor.getY() + orb.getHeightBias() + bob,
                    anchor.getZ() + Math.sin(orb.getAngle()) * orb.getRadius());
            if (orb.getIntroRemaining() > 0 && orb.getIntroMax() > 0) {
                double t = 1.0 - (double) orb.getIntroRemaining() / orb.getIntroMax();
                Location from = orb.getAnchor();
                orb.setLocation(new Location(
                        world,
                        from.getX() + (target.getX() - from.getX()) * t,
                        from.getY() + (target.getY() - from.getY()) * t,
                        from.getZ() + (target.getZ() - from.getZ()) * t));
                orb.tickIntro();
            } else {
                orb.setLocation(target);
            }
        }
    }

    private void render() {
        for (GearOrb orb : orbs) {
            if (orb.isConsumed()) {
                continue;
            }
            Location loc = orb.getLocation();
            World world = loc.getWorld();
            if (world == null) {
                continue;
            }
            Color dust = orb.isGood() ? OrbCache.goodColor : OrbCache.badColor;
            world.spawnParticle(Particle.DUST, loc, 2, 0.02, 0.02, 0.02,
                    new Particle.DustOptions(dust, orb.isGood() ? 0.7f : 0.9f));
            if (orb.isGood()) {
                if (ticks % 4 == 0) {
                    world.spawnParticle(OrbCache.goodParticle, loc, 1, 0.02, 0.02, 0.02, 0.0);
                }
            } else {
                world.spawnParticle(OrbCache.badParticle, loc, 2, 0.08, 0.08, 0.08, 0.01);
            }
        }
    }

    private void prune() {
        Iterator<GearOrb> iterator = orbs.iterator();
        while (iterator.hasNext()) {
            if (iterator.next().isConsumed()) {
                iterator.remove();
            }
        }
    }

    private void fadeFx(GearOrb orb) {
        Location loc = orb.getLocation();
        World world = loc.getWorld();
        if (world == null) {
            return;
        }
        world.spawnParticle(Particle.SMOKE, loc, 4, 0.06, 0.06, 0.06, 0.01);
    }

    private void playHitFx(Player player, GearOrb orb) {
        World world = station.getWorld();
        if (world == null) {
            return;
        }
        boolean good = orb.isGood();
        Location from = orb.getLocation();
        Location to = displayPoint();
        if (player != null) {
            player.playSound(
                    player.getLocation(),
                    good ? Sound.BLOCK_AMETHYST_BLOCK_CHIME : Sound.BLOCK_GLASS_BREAK,
                    1.0f,
                    good ? 1.5f : 0.7f);
        }
        Particle.DustOptions dust = new Particle.DustOptions(good ? beamColor : OrbCache.badColor, 0.8f);
        for (int i = 0; i <= 10; i++) {
            double t = i / 10.0;
            Location point = new Location(
                    world,
                    from.getX() + (to.getX() - from.getX()) * t,
                    from.getY() + (to.getY() - from.getY()) * t,
                    from.getZ() + (to.getZ() - from.getZ()) * t);
            world.spawnParticle(Particle.DUST, point, 1, 0.0, 0.0, 0.0, dust);
        }
        world.spawnParticle(
                good ? OrbCache.goodParticle : OrbCache.badParticle, to, good ? 6 : 8, 0.12, 0.18, 0.12, 0.02);
        world.playSound(to, good ? Sound.BLOCK_AMETHYST_BLOCK_RESONATE : Sound.BLOCK_SCULK_SHRIEKER_SHRIEK,
                1.0f, good ? 1.3f : 0.8f);
    }

    // Keep the existing legacy text representation, formatting, and exact-string comparisons.
    @SuppressWarnings("deprecation")
    private void actionBar(Player player) {
        int remaining = (int) Math.max(0, (tier.windowTicks() - ticks) / 20);
        String message = Messages.get(
                "gear.orbs.progress",
                "hits", String.valueOf(Math.min(goodHits, tier.goodTarget())),
                "target", String.valueOf(tier.goodTarget()),
                "rift", String.valueOf(riftDelta),
                "seconds", String.valueOf(remaining));
        player.spigot().sendMessage(ChatMessageType.ACTION_BAR, TextComponent.fromLegacyText(message));
    }

    private static Color resolveBeamColor(String elementId) {
        ElementDef element = elementId == null || elementId.isBlank()
                ? null
                : ElementRegistry.getById(elementId);
        if (element == null) {
            return Color.WHITE;
        }
        return MagicText.bukkitColor(element.getColor(), Color.WHITE);
    }

    private static double randomRange(ThreadLocalRandom random, double min, double max) {
        if (max <= min) {
            return min;
        }
        return min + random.nextDouble() * (max - min);
    }
}
