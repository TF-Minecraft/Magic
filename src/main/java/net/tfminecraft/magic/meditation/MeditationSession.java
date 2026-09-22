package net.tfminecraft.magic.meditation;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
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

import net.tfminecraft.interactiblefurniture.furniture.Furniture;
import net.tfminecraft.magic.GuiCache;
import net.tfminecraft.magic.attunement.AttunementCaptureService;
import net.tfminecraft.magic.profile.MagicProfileService;
import net.tfminecraft.magic.session.ResonanceSession;
import net.tfminecraft.magic.util.MagicText;
import net.tfminecraft.magic.util.PedestalFx;

public final class MeditationSession {

    public enum Phase {
        START_ORB,
        RUNNING
    }

    private static final Color FLOW_FALLBACK = Color.fromRGB(0x5bc4d4);
    private static final Color SURGE_FALLBACK = Color.fromRGB(0xe85d4a);

    private final MeditationCircle circle;
    private final MeditationSitYield yield;
    private Phase phase = Phase.START_ORB;
    private long surgeLockUntilMs;
    private boolean surgeLockActive;
    private long nextSpawnTick;
    private long tickCount;
    private long lastHitTick;
    private long lastSwingTick;
    private boolean tiredNotified;
    private final List<MeditationOrb> orbs = new ArrayList<>();
    private final Map<String, Double> attunedByArtifact = new HashMap<>();
    private boolean windingDown;

    public MeditationSession(Player player, MeditationCircle circle, MeditationSitYield yield) {
        this.circle = circle;
        this.yield = yield != null ? yield : MeditationSitYield.empty();
        spawnStarter(player);
    }

    public MeditationCircle getCircle() {
        return circle;
    }

    public Phase getPhase() {
        return phase;
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

    public boolean claimTiredNotice() {
        if (tiredNotified) {
            return false;
        }
        tiredNotified = true;
        return true;
    }

    public List<MeditationOrb> livingOrbs() {
        List<MeditationOrb> living = new ArrayList<>();
        for (MeditationOrb orb : orbs) {
            if (!orb.isConsumed() && !orb.isReturning()) {
                living.add(orb);
            }
        }
        return living;
    }

    public boolean onOrbHit(Player player, MeditationOrb orb, ResonanceSession session, MagicProfileService profiles) {
        if (orb.isStarter()) {
            activatePedestals();
            despawnOrb(orb);
            phase = Phase.RUNNING;
            nextSpawnTick = tickCount + 5;
            return false;
        }
        if (!orb.isFlow()) {
            boolean startedLock = !locked();
            surgeLockUntilMs = System.currentTimeMillis() + MeditationCache.surgeLockSeconds * 1000L;
            retintAllSurge();
            if (startedLock) {
                player.sendMessage(MagicText.format("{color:corruption}§oYou lose focus, anger grips you"));
            }
        }
        despawnOrb(orb);
        applyRewards(player, session, orb, profiles);
        playHitFx(player, orb);
        return yield.exhausted(attunedByArtifact);
    }

    public void tick(Player player, ResonanceSession session) {
        tickCount++;
        if (windingDown) {
            tickReturns();
            renderOrbs();
            pruneDead();
            return;
        }
        boolean nowLocked = locked();
        if (surgeLockActive && !nowLocked && player != null) {
            player.sendMessage(MagicText.format("{color:mode_flow}§oYou regain focus"));
        }
        surgeLockActive = nowLocked;
        if (phase == Phase.RUNNING) {
            expireOrbitOrbs();
            if (tickCount >= nextSpawnTick) {
                List<Furniture> eligible = eligibleSpawnPedestals();
                if (!eligible.isEmpty()) {
                    spawnOrbitOrb(session, eligible);
                    if (!locked() && unfinishedPedestalCount() == 1) {
                        Boolean missing = missingMixType();
                        List<Furniture> again = eligibleSpawnPedestals();
                        if (missing != null && !again.isEmpty()) {
                            spawnOrbitOrb(session, again);
                        }
                    }
                    int pace = Math.max(1, unfinishedPedestalCount());
                    nextSpawnTick = tickCount + Math.max(5L, MeditationCache.spawnIntervalTicks / pace);
                }
            }
            orbit(player);
        } else {
            keepStarterInFront(player);
        }
        renderOrbs();
        pruneDead();
    }

    public boolean isWindingDown() {
        return windingDown;
    }

    public boolean hasActiveOrbs() {
        for (MeditationOrb orb : orbs) {
            if (!orb.isConsumed()) {
                return true;
            }
        }
        return false;
    }

    public void beginWindDown() {
        if (windingDown) {
            return;
        }
        windingDown = true;
        for (MeditationOrb orb : orbs) {
            if (orb.isConsumed()) {
                continue;
            }
            if (orb.isStarter()) {
                despawnOrb(orb);
                continue;
            }
            if (!orb.isReturning()) {
                orb.beginReturn();
            }
        }
    }

    public void end() {
        for (MeditationOrb orb : new ArrayList<>(orbs)) {
            despawnOrb(orb);
        }
        orbs.clear();
        windingDown = false;
    }

    public void playComplete(Player player) {
        World world = circle.getCenter().getWorld();
        if (world == null) {
            return;
        }
        Location center = circle.getCenter().clone().add(0, 1.0, 0);
        world.playSound(center, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1.0f, 1.15f);
        world.playSound(center, Sound.BLOCK_ENCHANTMENT_TABLE_USE, 1.0f, 1.35f);
        spawnCompleteBurst(center, Particle.END_ROD, 10, 0.28);
        spawnCompleteBurst(center, Particle.ENCHANT, 14, 0.4);
        for (Furniture furniture : circle.getPedestals()) {
            Location loc = PedestalFx.artifactPoint(furniture);
            if (loc != null) {
                spawnCompleteBurst(loc, Particle.END_ROD, 4, 0.12);
            }
        }
    }

    private static void spawnCompleteBurst(Location center, Particle particle, int count, double radius) {
        if (center.getWorld() == null) {
            return;
        }
        center.getWorld().spawnParticle(particle, center, count, radius, 0.7, radius, 0.02);
    }

    private boolean isPedestalFull(UUID furnitureId) {
        Furniture furniture = furniture(furnitureId);
        String artifactId = circle.artifactIdOn(furniture);
        if (artifactId == null) {
            return true;
        }
        double cap = yield.sessionCap(artifactId);
        if (cap <= 0) {
            return true;
        }
        return attunedByArtifact.getOrDefault(artifactId, 0.0) + 0.005 >= cap;
    }

    private int unfinishedPedestalCount() {
        int count = 0;
        for (Furniture furniture : circle.getArtifactPedestals()) {
            if (!isPedestalFull(furniture.getEntityId())) {
                count++;
            }
        }
        return count;
    }

    private int maxLiveOrbsFor(UUID furnitureId) {
        return unfinishedPedestalCount() == 1 ? 2 : 1;
    }

    private int liveOrbCountFrom(UUID furnitureId) {
        if (furnitureId == null) {
            return 0;
        }
        int count = 0;
        for (MeditationOrb orb : orbs) {
            if (orb.isConsumed() || orb.isStarter()) {
                continue;
            }
            if (furnitureId.equals(orb.getSourceId())) {
                count++;
            }
        }
        return count;
    }

    private Boolean missingMixType() {
        boolean hasFlow = false;
        boolean hasSurge = false;
        for (MeditationOrb orb : livingOrbs()) {
            if (orb.isFlow()) {
                hasFlow = true;
            } else {
                hasSurge = true;
            }
        }
        if (!hasFlow) {
            return Boolean.TRUE;
        }
        if (!hasSurge) {
            return Boolean.FALSE;
        }
        return null;
    }

    private List<Furniture> eligibleSpawnPedestals() {
        List<Furniture> eligible = new ArrayList<>();
        for (Furniture furniture : circle.getArtifactPedestals()) {
            UUID id = furniture.getEntityId();
            if (isPedestalFull(id) || liveOrbCountFrom(id) >= maxLiveOrbsFor(id)) {
                continue;
            }
            eligible.add(furniture);
        }
        return eligible;
    }

    private boolean locked() {
        return System.currentTimeMillis() < surgeLockUntilMs;
    }

    private void expireOrbitOrbs() {
        for (MeditationOrb orb : orbs) {
            if (orb.isStarter() || orb.isConsumed()) {
                continue;
            }
            if (orb.tickLife()) {
                orb.beginReturn();
            }
        }
    }

    private void spawnStarter(Player player) {
        Location loc = starterLocation(player);
        orbs.add(new MeditationOrb(loc, true, true, 0));
    }

    private void keepStarterInFront(Player player) {
        Location loc = starterLocation(player);
        for (MeditationOrb orb : orbs) {
            if (!orb.isStarter() || orb.isConsumed()) {
                continue;
            }
            orb.setLocation(loc);
        }
    }

    private static Location starterLocation(Player player) {
        return player.getEyeLocation().add(player.getEyeLocation().getDirection().normalize()
                .multiply(MeditationCache.startOrbRange));
    }

    private void spawnOrbitOrb(ResonanceSession session, List<Furniture> eligible) {
        if (eligible.isEmpty()) {
            return;
        }
        Furniture source = eligible.get(ThreadLocalRandom.current().nextInt(eligible.size()));
        Location spawn = PedestalFx.artifactPoint(source);
        if (spawn == null) {
            return;
        }
        boolean flow;
        if (locked()) {
            flow = false;
        } else {
            Boolean missing = missingMixType();
            flow = missing != null ? missing : ThreadLocalRandom.current().nextDouble() < whiteChance(session);
        }
        ThreadLocalRandom random = ThreadLocalRandom.current();
        double angle = random.nextDouble() * Math.PI * 2;
        double radius = randomRange(random, MeditationCache.orbitRadiusMin, MeditationCache.orbitRadiusMax);
        double heightBias = randomRange(random, MeditationCache.orbitHeightMin, MeditationCache.orbitHeightMax);
        double jitter = randomRange(random, MeditationCache.orbitSpeedJitterMin, MeditationCache.orbitSpeedJitterMax);
        double angleSpeed = (Math.PI * 2.0) / Math.max(1L, MeditationCache.orbitPeriodTicks) * jitter;
        double bobPhase = random.nextDouble() * Math.PI * 2;
        orbs.add(new MeditationOrb(
                spawn,
                flow,
                false,
                angle,
                spawn,
                MeditationCache.orbIntroTicks,
                radius,
                heightBias,
                angleSpeed,
                bobPhase,
                MeditationCache.orbLifetimeTicks,
                source.getEntityId()));
    }

    private static double whiteChance(ResonanceSession session) {
        double max = Math.max(1.0, net.tfminecraft.magic.GuiCache.equilibriumMax);
        double eq = session.getEquilibrium();
        return Math.max(0.0, Math.min(1.0, 0.5 * (1.0 + eq / max)));
    }

    private void tickReturns() {
        for (MeditationOrb orb : orbs) {
            if (orb.isStarter() || orb.isConsumed() || !orb.isReturning()) {
                continue;
            }
            Location from = orb.getReturnFrom();
            Location to = orb.getSpawnLocation();
            World world = to.getWorld() != null ? to.getWorld() : (from.getWorld() != null ? from.getWorld() : null);
            if (world == null) {
                despawnOrb(orb);
                continue;
            }
            int max = orb.getReturnMax();
            double t = (double) (max - orb.getReturnRemaining() + 1) / max;
            t = Math.max(0.0, Math.min(1.0, t));
            orb.setLocation(new Location(
                    world,
                    from.getX() + (to.getX() - from.getX()) * t,
                    from.getY() + (to.getY() - from.getY()) * t,
                    from.getZ() + (to.getZ() - from.getZ()) * t));
            if (orb.tickReturn()) {
                despawnOrb(orb);
            }
        }
    }

    private void orbit(Player player) {
        tickReturns();
        Location eye = player.getEyeLocation();
        World world = eye.getWorld();
        if (world == null) {
            return;
        }
        for (MeditationOrb orb : orbs) {
            if (orb.isStarter() || orb.isConsumed() || orb.isReturning()) {
                continue;
            }
            orb.setAngle(orb.getAngle() + orb.getAngleSpeed());
            orb.addBobPhase(0.1);
            double bob = MeditationCache.orbitBob * Math.sin(orb.getBobPhase());
            Location target = new Location(
                    world,
                    eye.getX() + Math.cos(orb.getAngle()) * orb.getRadius(),
                    eye.getY() + orb.getHeightBias() + bob,
                    eye.getZ() + Math.sin(orb.getAngle()) * orb.getRadius());
            if (orb.getIntroRemaining() > 0 && orb.getIntroMax() > 0) {
                double t = 1.0 - (double) orb.getIntroRemaining() / orb.getIntroMax();
                Location spawn = orb.getSpawnLocation();
                orb.setLocation(new Location(
                        world,
                        spawn.getX() + (target.getX() - spawn.getX()) * t,
                        spawn.getY() + (target.getY() - spawn.getY()) * t,
                        spawn.getZ() + (target.getZ() - spawn.getZ()) * t));
                orb.tickIntro();
            } else {
                orb.setLocation(target);
            }
        }
    }

    private void retintAllSurge() {
        for (MeditationOrb orb : orbs) {
            if (orb.isStarter() || orb.isConsumed()) {
                continue;
            }
            orb.setFlow(false);
        }
    }

    private void activatePedestals() {
        World world = circle.getCenter().getWorld();
        if (world == null) {
            return;
        }
        world.playSound(circle.getCenter(), Sound.BLOCK_BEACON_ACTIVATE, 1.0f, 1.4f);
        for (Furniture furniture : circle.getPedestals()) {
            Location loc = PedestalFx.artifactPoint(furniture);
            if (loc == null) {
                continue;
            }
            world.spawnParticle(Particle.DUST, loc, 18, 0.25, 0.4, 0.25, new Particle.DustOptions(Color.WHITE, 1.2f));
            world.playSound(loc, Sound.BLOCK_AMETHYST_BLOCK_RESONATE, 1.0f, 1.2f);
        }
    }

    private void applyRewards(
            Player player,
            ResonanceSession session,
            MeditationOrb orb,
            MagicProfileService profiles) {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        double eqDelta;
        if (orb.isFlow()) {
            eqDelta = randomRange(random, MeditationCache.flowEquilibriumMin, MeditationCache.flowEquilibriumMax);
        } else {
            eqDelta = -randomRange(random, MeditationCache.surgeEquilibriumMin, MeditationCache.surgeEquilibriumMax);
        }
        session.setEquilibrium(session.getEquilibrium() + eqDelta);

        UUID sourceId = orb.getSourceId();
        MeditationCache.ArtifactDef artifact = sourceId == null ? null : circle.artifactFor(sourceId);
        if (artifact == null) {
            return;
        }
        Furniture furniture = furniture(sourceId);
        String artifactId = circle.artifactIdOn(furniture);
        if (artifactId == null) {
            return;
        }
        double cap = yield.sessionCap(artifactId);
        double credit = attunedByArtifact.getOrDefault(artifactId, 0.0);
        int n = yield.users(artifactId);
        double remaining = Math.max(0.0, cap - credit);
        double gain = Math.min(MeditationCache.resonancePerHit / n, remaining);
        if (gain <= 0) {
            return;
        }
        attunedByArtifact.put(artifactId, credit + gain);
        AttunementCaptureService.credit(player, session, artifactId, artifact.elementId, gain);
        if (profiles != null) {
            profiles.savePlayer(player);
        }
    }

    private Furniture furniture(UUID furnitureId) {
        if (furnitureId == null) {
            return null;
        }
        for (Furniture candidate : circle.getPedestals()) {
            if (furnitureId.equals(candidate.getEntityId())) {
                return candidate;
            }
        }
        return null;
    }

    private void playHitFx(Player player, MeditationOrb orb) {
        World world = player.getWorld();
        if (world == null) {
            return;
        }
        boolean flowLook = orb.isFlow();
        Color dustColor = MagicText.bukkitColor(
                GuiCache.color(flowLook ? "mode_flow" : "mode_surge", flowLook ? "#5bc4d4" : "#e85d4a"),
                flowLook ? FLOW_FALLBACK : SURGE_FALLBACK);
        world.playSound(player.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1.0f, flowLook ? 1.6f : 0.7f);
        world.spawnParticle(Particle.END_ROD, player.getEyeLocation(), 4, 0.1, 0.1, 0.1, 0.01);

        Location from = orb.getLocation();
        Location to = orb.getSpawnLocation();
        if (from.getWorld() == null || to.getWorld() == null) {
            return;
        }
        Particle.DustOptions dust = new Particle.DustOptions(dustColor, 0.8f);
        for (int i = 0; i <= 8; i++) {
            double t = i / 8.0;
            Location point = new Location(
                    world,
                    from.getX() + (to.getX() - from.getX()) * t,
                    from.getY() + (to.getY() - from.getY()) * t,
                    from.getZ() + (to.getZ() - from.getZ()) * t);
            world.spawnParticle(Particle.DUST, point, 1, 0.0, 0.0, 0.0, dust);
        }
        world.spawnParticle(Particle.END_ROD, to, 6, 0.12, 0.2, 0.12, 0.02);
        world.spawnParticle(Particle.FIREWORK, to, 3, 0.08, 0.08, 0.08, 0.02);
        world.playSound(to, Sound.BLOCK_AMETHYST_BLOCK_RESONATE, 1.0f, flowLook ? 1.4f : 0.8f);
    }

    private static double randomRange(ThreadLocalRandom random, double min, double max) {
        if (max <= min) {
            return min;
        }
        return min + random.nextDouble() * (max - min);
    }

    private void renderOrbs() {
        for (MeditationOrb orb : orbs) {
            if (orb.isConsumed()) {
                continue;
            }
            Location loc = orb.getLocation();
            World world = loc.getWorld();
            if (world == null) {
                continue;
            }
            boolean flowLook = orb.isStarter() || orb.isFlow();
            Color dustColor = orb.isStarter()
                    ? Color.WHITE
                    : MagicText.bukkitColor(
                            GuiCache.color(flowLook ? "mode_flow" : "mode_surge", flowLook ? "#5bc4d4" : "#e85d4a"),
                            flowLook ? FLOW_FALLBACK : SURGE_FALLBACK);
            world.spawnParticle(Particle.DUST, loc, 2, 0.02, 0.02, 0.02, new Particle.DustOptions(dustColor, 0.6f));
            if (tickCount % 10 == 0) {
                world.spawnParticle(Particle.FIREWORK, loc, 1, 0.02, 0.02, 0.02, 0.03);
            }
        }
    }

    private void despawnOrb(MeditationOrb orb) {
        orb.setConsumed(true);
    }

    private void pruneDead() {
        Iterator<MeditationOrb> iterator = orbs.iterator();
        while (iterator.hasNext()) {
            MeditationOrb orb = iterator.next();
            if (orb.isConsumed()) {
                iterator.remove();
            }
        }
    }
}
