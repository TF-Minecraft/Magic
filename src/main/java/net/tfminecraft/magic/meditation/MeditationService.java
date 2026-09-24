package net.tfminecraft.magic.meditation;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.Bukkit;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerAnimationEvent;
import org.bukkit.event.player.PlayerAnimationType;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.block.Action;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import net.tfminecraft.interactiblefurniture.furniture.Furniture;
import net.tfminecraft.magic.Magic;
import net.tfminecraft.magic.Messages;
import net.tfminecraft.magic.profile.MagicProfileService;
import net.tfminecraft.magic.session.ResonanceSession;
import net.tfminecraft.magic.session.ResonanceSessionManager;
import net.tfminecraft.rpcharacters.RPCharacters;
import net.tfminecraft.rpcharacters.focus.FocusService;

public final class MeditationService implements Listener {

    private static volatile MeditationService instance;

    private final ResonanceSessionManager sessionManager;
    private final MagicProfileService profileService;
    private final Map<UUID, MeditationSession> sessions = new ConcurrentHashMap<>();
    private final Set<UUID> sitNotices = new HashSet<>();
    private BukkitTask task;
    private long tickCount;

    public MeditationService(ResonanceSessionManager sessionManager, MagicProfileService profileService) {
        this.sessionManager = sessionManager;
        this.profileService = profileService;
    }

    public void start() {
        stop();
        instance = this;
        task = Bukkit.getScheduler().runTaskTimer(Magic.plugin, this::tick, 1L, 1L);
    }

    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
        for (MeditationSession session : sessions.values()) {
            session.end();
        }
        sessions.clear();
        sitNotices.clear();
        if (instance == this) {
            instance = null;
        }
    }

    public static boolean locksFurniture(Furniture furniture) {
        MeditationService service = instance;
        if (service == null || furniture == null) {
            return false;
        }
        UUID id = furniture.getEntityId();
        if (id == null) {
            return false;
        }
        for (MeditationSession session : service.sessions.values()) {
            MeditationCircle circle = session.getCircle();
            if (circle == null) {
                continue;
            }
            for (Furniture post : circle.getPedestals()) {
                if (post != null && id.equals(post.getEntityId())) {
                    return true;
                }
            }
        }
        return false;
    }

    private void tick() {
        tickCount++;
        for (UUID id : new ArrayList<>(sessions.keySet())) {
            MeditationSession session = sessions.get(id);
            if (session == null) {
                continue;
            }
            Player player = Bukkit.getPlayer(id);
            if (session.isWindingDown()) {
                tickWindDown(id, player, session);
                continue;
            }
            if (player == null || !stillValid(player, session)) {
                if (player != null && !player.isInsideVehicle()) {
                    player.sendMessage(Messages.get("meditation.stop"));
                }
                session.beginWindDown();
                tickWindDown(id, player, session);
                continue;
            }
            ResonanceSession resonance = sessionManager.getOrCreate(player);
            session.tick(player, resonance);
        }
        for (Player player : Bukkit.getOnlinePlayers()) {
            UUID id = player.getUniqueId();
            if (sessions.containsKey(id)) {
                continue;
            }
            if (!player.isInsideVehicle()) {
                sitNotices.remove(id);
                continue;
            }
            tryStart(player);
        }
        sitNotices.removeIf(id -> Bukkit.getPlayer(id) == null);
    }

    private void tickWindDown(UUID playerId, Player player, MeditationSession session) {
        ResonanceSession resonance = player != null ? sessionManager.getOrCreate(player) : null;
        session.tick(player, resonance);
        if (!session.hasActiveOrbs()) {
            finishSession(playerId, player);
        }
    }

    private void finishSession(UUID playerId, Player player) {
        MeditationSession session = sessions.remove(playerId);
        if (session != null) {
            session.end();
        }
        if (player != null && profileService != null) {
            profileService.savePlayer(player);
        }
    }

    private void tryStart(Player player) {
        if (!player.isInsideVehicle()) {
            return;
        }
        MeditationCircle circle = MeditationCircle.detect(player.getLocation());
        if (circle == null) {
            return;
        }
        if (!isOnCenter(player, circle)) {
            return;
        }
        if (circle.getTotalPower() <= 0) {
            notifySit(player, Messages.get("meditation.no_artifacts"));
            return;
        }
        long nowMs = System.currentTimeMillis();
        String characterId = profileService != null ? profileService.characterId(player) : null;
        MeditationSitYield yield = circle.stampAndSnapshot(characterId, nowMs);
        if (!yield.hasAnyCap()) {
            notifySit(player, Messages.get("meditation.nothing"));
            return;
        }
        sessions.put(player.getUniqueId(), new MeditationSession(player, circle, yield));
    }

    private void notifySit(Player player, String message) {
        if (!sitNotices.add(player.getUniqueId())) {
            return;
        }
        player.sendMessage(message);
    }

    private boolean stillValid(Player player, MeditationSession session) {
        if (!player.isInsideVehicle() || !isOnCenter(player, session.getCircle())) {
            return false;
        }
        return session.getCircle().stillIntact();
    }

    private static boolean isOnCenter(Player player, MeditationCircle circle) {
        Block playerBlock = player.getLocation().getBlock();
        Block centerBlock = circle.getCenter().getBlock();
        return playerBlock.getX() == centerBlock.getX()
                && playerBlock.getY() == centerBlock.getY()
                && playerBlock.getZ() == centerBlock.getZ();
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

    private void tryHit(Player player) {
        MeditationSession session = sessions.get(player.getUniqueId());
        if (session == null || session.isWindingDown()
                || !session.claimSwing(tickCount) || !session.canHit(tickCount)) {
            return;
        }
        MeditationOrb orb = hitscan(player, session);
        if (orb == null) {
            return;
        }
        FocusService focus = RPCharacters.getFocusService();
        if (focus == null || !focus.trySpend(player, MeditationCache.mentalCostPerHit)) {
            if (session.claimTiredNotice()) {
                player.sendMessage(Messages.get("meditation.tired"));
            }
            return;
        }
        session.markHit(tickCount);
        ResonanceSession resonance = sessionManager.getOrCreate(player);
        boolean complete = session.onOrbHit(player, orb, resonance, profileService);
        if (complete) {
            session.playComplete(player);
            player.sendMessage(Messages.get("meditation.complete"));
            sitNotices.add(player.getUniqueId());
            session.beginWindDown();
        }
    }

    private MeditationOrb hitscan(Player player, MeditationSession session) {
        Location eye = player.getEyeLocation();
        Vector dir = eye.getDirection().normalize();
        World world = eye.getWorld();
        if (world == null) {
            return null;
        }
        double range = MeditationCache.orbClickRange;
        double radius = MeditationCache.orbHitRadius;
        double radiusSq = radius * radius;
        Vector origin = eye.toVector();

        MeditationOrb best = null;
        double bestT = range + 1;
        for (MeditationOrb orb : session.livingOrbs()) {
            Location loc = orb.getLocation();
            if (loc.getWorld() == null || !loc.getWorld().equals(world)) {
                continue;
            }
            Vector oc = loc.toVector().subtract(origin);
            double t = oc.dot(dir);
            if (t < 0 || t > range) {
                continue;
            }
            double distSq = oc.lengthSquared() - t * t;
            if (distSq > radiusSq) {
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
        if (blockHit != null) {
            double blockDist = eye.distance(blockHit.getHitPosition().toLocation(world));
            if (blockDist < bestT) {
                return null;
            }
        }
        return best;
    }
}
