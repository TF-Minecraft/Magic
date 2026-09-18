package net.tfminecraft.magic.gear.orb;

import java.util.ArrayList;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.Bukkit;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerAnimationEvent;
import org.bukkit.event.player.PlayerAnimationType;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import net.tfminecraft.magic.Magic;
import net.tfminecraft.magic.Messages;
import net.tfminecraft.magic.charge.TierBands;
import net.tfminecraft.magic.gear.GearItemBuilder;
import net.tfminecraft.magic.gear.GearProvenance;
import net.tfminecraft.magic.gear.GearStationStore;
import net.tfminecraft.magic.gear.WeaponAttunementChat;
import net.tfminecraft.magic.gear.WeaponLore;
import net.tfminecraft.magic.gear.WeaponRequirement;
import net.tfminecraft.magic.gear.WeaponRift;

/**
 * Drives the orb runs. One session per station, so two players cannot attune the same
 * weapon at once.
 *
 * <p>Runs on its own one tick timer rather than {@code MagicTickService}, which fires
 * once a second and is far too coarse for orbit motion.
 */
public final class GearOrbService implements Listener {

    private static final Map<String, GearOrbSession> SESSIONS = new ConcurrentHashMap<>();
    private static BukkitTask task;
    private static long tickCount;

    public static void start() {
        stop();
        task = Bukkit.getScheduler().runTaskTimer(Magic.plugin, GearOrbService::tick, 1L, 1L);
    }

    /** Finishes every open run with whatever was captured. The charge is already spent. */
    public static void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
        for (String key : new ArrayList<>(SESSIONS.keySet())) {
            GearOrbSession session = SESSIONS.get(key);
            if (session != null) {
                complete(session, false);
            }
        }
        SESSIONS.clear();
    }

    public static boolean isActive(Location station) {
        return station != null && SESSIONS.containsKey(GearStationStore.key(station));
    }

    public static UUID sessionOwner(Location station) {
        if (station == null) {
            return null;
        }
        GearOrbSession session = SESSIONS.get(GearStationStore.key(station));
        return session == null ? null : session.getPlayerId();
    }

    /**
     * Stops the run without writing captured aura or rift. The charge stays spent.
     */
    public static void abort(Location station) {
        if (station == null) {
            return;
        }
        GearOrbSession session = SESSIONS.remove(GearStationStore.key(station));
        if (session != null) {
            session.end();
        }
        GearStationStore.Occupancy occupancy = GearStationStore.get(station);
        if (occupancy != null) {
            occupancy.setOrbSessionActive(false);
        }
    }

    /**
     * Begins a run. The caller has already consumed the charge and flagged the station,
     * so this never fails silently: a station that is already running is rejected first.
     */
    public static boolean begin(
            Player player,
            Location station,
            Map<String, Double> snapshot,
            String primaryElement,
            int chargeTier) {
        String key = GearStationStore.key(station);
        if (key.isEmpty() || SESSIONS.containsKey(key)) {
            return false;
        }
        GearOrbSession session = new GearOrbSession(
                station, player.getUniqueId(), snapshot, primaryElement, OrbCache.tier(chargeTier));
        SESSIONS.put(key, session);
        player.sendMessage(Messages.get("gear.orbs.start", "target", String.valueOf(session.goodTarget())));
        World world = station.getWorld();
        if (world != null) {
            world.playSound(session.displayPoint(), Sound.BLOCK_BEACON_ACTIVATE, 1.0f, 1.4f);
        }
        return true;
    }

    private static void tick() {
        tickCount++;
        for (String key : new ArrayList<>(SESSIONS.keySet())) {
            GearOrbSession session = SESSIONS.get(key);
            if (session == null) {
                continue;
            }
            Player player = Bukkit.getPlayer(session.getPlayerId());
            if (player == null || !player.isOnline()) {
                complete(session, false);
                continue;
            }
            try {
                session.tick(player);
            } catch (Exception ex) {
                Magic.plugin.getLogger().warning("[Magic] Orb session failed: " + ex.getMessage());
                complete(session, false);
                continue;
            }
            if (session.isFinished()) {
                complete(session, true);
            }
        }
    }

    private static void complete(GearOrbSession session, boolean announce) {
        SESSIONS.remove(GearStationStore.key(session.getStation()));
        session.end();
        Player player = Bukkit.getPlayer(session.getPlayerId());
        GearStationStore.Occupancy occupancy = GearStationStore.get(session.getStation());
        if (occupancy == null) {
            return;
        }
        occupancy.setOrbSessionActive(false);
        ItemStack weapon = occupancy.getItem();
        if (weapon == null) {
            return;
        }
        Map<String, Double> captured = session.capturedFill();
        WeaponRequirement requirement = WeaponRequirement.fromItem(weapon);
        requirement.mergeAmounts(captured);
        requirement.persist(weapon);

        int oldRift = WeaponRift.get(weapon);
        // Only a follow-up charge scrubs Rift; the first one has nothing to clean up.
        int scrub = oldRift > 0 ? OrbCache.riftPerRecharge : 0;
        int newRift = Math.max(0, Math.min(OrbCache.riftCap, oldRift + session.riftDelta() - scrub));
        WeaponRift.set(weapon, newRift);

        int capturedBand = WeaponRequirement.highestBand(captured);
        if (capturedBand > 0 && !GearProvenance.socketsLocked(weapon)) {
            weapon = GearItemBuilder.rewriteSockets(weapon, capturedBand);
        } else {
            weapon = WeaponLore.updateItem(weapon);
        }
        GearStationStore.update(session.getStation(), weapon);

        if (!announce) {
            return;
        }
        session.playComplete();
        if (player == null) {
            return;
        }
        if (capturedBand <= 0) {
            player.sendMessage(Messages.get("gear.orbs.failed"));
        } else {
            player.sendMessage(Messages.get(
                    "gear.orbs.complete",
                    "percent", String.valueOf((int) Math.round(session.capturedFraction() * 100)),
                    "band", TierBands.numeral(capturedBand)));
        }
        WeaponAttunementChat.sendPostChargeSummary(player, weapon);
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.LEFT_CLICK_AIR && event.getAction() != Action.LEFT_CLICK_BLOCK) {
            return;
        }
        tryHit(event.getPlayer());
    }

    @EventHandler
    public void onSwing(PlayerAnimationEvent event) {
        if (event.getAnimationType() != PlayerAnimationType.ARM_SWING) {
            return;
        }
        tryHit(event.getPlayer());
    }

    private static void tryHit(Player player) {
        if (player.isSneaking()) {
            return;
        }
        GearOrbSession session = sessionOf(player.getUniqueId());
        if (session == null || !session.claimSwing(tickCount) || !session.canHit(tickCount)) {
            return;
        }
        GearOrb orb = hitscan(player, session);
        if (orb == null) {
            return;
        }
        session.markHit(tickCount);
        session.onHit(player, orb);
    }

    private static GearOrbSession sessionOf(UUID playerId) {
        for (GearOrbSession session : SESSIONS.values()) {
            if (playerId.equals(session.getPlayerId())) {
                return session;
            }
        }
        return null;
    }

    private static GearOrb hitscan(Player player, GearOrbSession session) {
        Location eye = player.getEyeLocation();
        World world = eye.getWorld();
        if (world == null) {
            return null;
        }
        Vector dir = eye.getDirection().normalize();
        Vector origin = eye.toVector();
        double range = OrbCache.clickRange;
        double radiusSq = OrbCache.hitRadius * OrbCache.hitRadius;

        GearOrb best = null;
        double bestT = range + 1;
        for (GearOrb orb : session.livingOrbs()) {
            Location loc = orb.getLocation();
            if (loc.getWorld() == null || !loc.getWorld().equals(world)) {
                continue;
            }
            Vector oc = loc.toVector().subtract(origin);
            double t = oc.dot(dir);
            if (t < 0 || t > range) {
                continue;
            }
            if (oc.lengthSquared() - t * t > radiusSq) {
                continue;
            }
            if (t < bestT) {
                bestT = t;
                best = orb;
            }
        }
        if (best == null) {
            return null;
        }
        RayTraceResult blockHit = world.rayTraceBlocks(eye, dir, range, FluidCollisionMode.NEVER, true);
        if (blockHit != null && eye.distance(blockHit.getHitPosition().toLocation(world)) < bestT) {
            return null;
        }
        return best;
    }
}
