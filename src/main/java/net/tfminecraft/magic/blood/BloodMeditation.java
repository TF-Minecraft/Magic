package net.tfminecraft.magic.blood;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import net.tfminecraft.magic.util.ItemRef;

/**
 * Blood meditation, the wild magic way to draw resonance. The owner sits on their altar among its
 * pedestals; the stone's heart beats, and with each beat a drop of blood rises from a charged artefact
 * and drifts to the mage. Catching a drop (a swing at it) draws that artefact's charge into blood
 * resonance: much on the beat, when every drop swells, and little off it, which also costs the mage
 * some of their own blood. Drops left alone fall back. It ends when the mage stands up or walks off,
 * when the artefacts are dry, or when resonance is as high as the artefacts allow, and only then is
 * the record written. Its music and the heartbeat are heard by the mage alone.
 */
public final class BloodMeditation {

    /** What sitting down to meditate came to. */
    public enum Begin {
        STARTED,
        NOT_READY,
        DRY,
        FULL,
        BUSY
    }

    private static final class Drop {
        final String id;
        final ItemDisplay display;
        final Location from;
        final long born;

        Drop(String id, ItemDisplay display, Location from, long born) {
            this.id = id;
            this.display = display;
            this.from = from;
            this.born = born;
        }
    }

    private static final class Session {
        final UUID player;
        final String character;
        final String altarId;
        final long started;
        final List<Drop> drops = new ArrayList<>();
        long nextMusic;

        Session(UUID player, String character, String altarId, long started) {
            this.player = player;
            this.character = character;
            this.altarId = altarId;
            this.started = started;
        }
    }

    private static final double LEAVE_DISTANCE = 3;
    private static final Map<UUID, Session> SESSIONS = new HashMap<>();
    private static long ticks;
    private static BukkitTask task;

    private BloodMeditation() {}

    static void startTicking(Plugin plugin) {
        if (task != null) {
            task.cancel();
        }
        task = plugin.getServer().getScheduler().runTaskTimer(plugin, BloodMeditation::tick, 1L, 1L);
    }

    /** The altar's owner sat down on it: the meditation begins if there is charge to draw. */
    public static Begin start(Player player, BloodAltar altar) {
        BloodRecord record = BloodRecords.get(AltarService.currentCharacter(player));
        if (record == null || BloodArtefacts.ids(record).isEmpty()) {
            return Begin.NOT_READY;
        }
        if (SESSIONS.containsKey(player.getUniqueId())) {
            return Begin.BUSY;
        }
        if (record.getResonance() >= BloodPool.cap(record)) {
            return Begin.FULL;
        }
        if (charged(altar, record).isEmpty()) {
            return Begin.DRY;
        }
        Session session = new Session(player.getUniqueId(), record.getCharacterId(), altar.getId(), ticks);
        SESSIONS.put(player.getUniqueId(), session);
        playMusic(player, session);
        return Begin.STARTED;
    }

    public static boolean isMeditating(UUID player) {
        return SESSIONS.containsKey(player);
    }

    /** The altar's artefacts that belong to this character, hold a place now and are charged. */
    static List<AltarPedestals.Shown> charged(BloodAltar altar, BloodRecord record) {
        List<AltarPedestals.Shown> out = new ArrayList<>();
        List<String> ids = BloodArtefacts.ids(record);
        for (AltarPedestals.Shown shown : AltarPedestals.artefacts(altar)) {
            String id = BloodArtefacts.id(shown.item());
            if (record.getCharacterId().equalsIgnoreCase(BloodItems.owner(shown.item())) && ids.contains(id)
                    && record.getCharge(id) > 0) {
                out.add(shown);
            }
        }
        return out;
    }

    /** Every tick: the heart beats, drops rise and drift, and meditations that should end do. */
    static void tick() {
        ticks++;
        for (Session session : List.copyOf(SESSIONS.values())) {
            Player player = Bukkit.getPlayer(session.player);
            BloodAltar altar = BloodAltars.byId(session.altarId);
            BloodRecord record = BloodRecords.get(session.character);
            if (player == null || altar == null || record == null || !stillSitting(player, session, altar)) {
                end(session, player, altar, record, "stood");
                continue;
            }
            long age = ticks - session.started;
            int beat = MeditationConfig.beatTicks();
            if (age % beat == 0) {
                beat(player, session, altar, record);
            } else if (age % beat == 4) {
                scale(session, MeditationConfig.dropScale());
            }
            drift(player, session);
            if (MeditationConfig.music().seconds() > 0 && ticks >= session.nextMusic) {
                playMusic(player, session);
            }
            if (record.getResonance() >= BloodPool.cap(record)) {
                end(session, player, altar, record, "full");
            } else if (session.drops.isEmpty() && age % beat == 1 && charged(altar, record).isEmpty()) {
                end(session, player, altar, record, "dry");
            }
        }
    }

    private static boolean stillSitting(Player player, Session session, BloodAltar altar) {
        Location center = altar.center();
        return player.getVehicle() != null
                && session.character.equalsIgnoreCase(AltarService.currentCharacter(player))
                && center != null && player.getWorld().equals(center.getWorld())
                && player.getLocation().distance(center) <= LEAVE_DISTANCE;
    }

    private static void playMusic(Player player, Session session) {
        MeditationConfig.music().play(player);
        int seconds = MeditationConfig.music().seconds();
        session.nextMusic = seconds > 0 ? ticks + seconds * 20L : Long.MAX_VALUE;
    }

    /** A heartbeat: the drops swell, and a new one rises from a charged artefact if there is room. */
    private static void beat(Player player, Session session, BloodAltar altar, BloodRecord record) {
        MeditationConfig.heartbeat().play(player);
        List<AltarPedestals.Shown> charged = session.drops.size() < MeditationConfig.maxDrops()
                ? charged(altar, record) : List.of();
        if (charged.isEmpty()) {
            scale(session, MeditationConfig.flareScale());
            return;
        }
        AltarPedestals.Shown source = charged.get(ThreadLocalRandom.current().nextInt(charged.size()));
        Location from = source.point();
        ItemDisplay display = from.getWorld().spawn(from, ItemDisplay.class, spawned -> {
            spawned.setItemStack(ItemRef.buildOrFallback(MeditationConfig.dropItem(), Material.REDSTONE));
            spawned.setPersistent(false);
            spawned.setTeleportDuration(1);
        });
        session.drops.add(new Drop(BloodArtefacts.id(source.item()), display, from, ticks));
        scale(session, MeditationConfig.flareScale());
    }

    private static void scale(Session session, float size) {
        for (Drop drop : session.drops) {
            drop.display.setTransformation(new Transformation(new Vector3f(), new AxisAngle4f(),
                    new Vector3f(size, size, size), new AxisAngle4f()));
        }
    }

    /** Where the drops gather: a little in front of the mage's chest. */
    private static Location gather(Player player) {
        Location eye = player.getEyeLocation();
        return eye.clone().add(eye.getDirection().setY(0).normalize().multiply(1.4)).add(0, -0.3, 0);
    }

    /** Drops drift from their artefact to the mage in an arc, wait there, then fall back. */
    private static void drift(Player player, Session session) {
        long travel = (long) MeditationConfig.travelBeats() * MeditationConfig.beatTicks();
        long life = travel + (long) MeditationConfig.lingerBeats() * MeditationConfig.beatTicks();
        Location to = gather(player);
        for (Iterator<Drop> it = session.drops.iterator(); it.hasNext();) {
            Drop drop = it.next();
            long age = ticks - drop.born;
            if (age >= life) {
                it.remove();
                spill(drop.display.getLocation());
                drop.display.remove();
                continue;
            }
            double t = Math.min(1, (double) age / travel);
            Vector path = to.toVector().subtract(drop.from.toVector()).multiply(t);
            Location at = drop.from.clone().add(path).add(0, Math.sin(Math.PI * t) * 0.8, 0);
            drop.display.teleport(at);
        }
    }

    private static void spill(Location at) {
        at.getWorld().spawnParticle(Particle.DUST, at, 8, 0.1, 0.1, 0.1, 0,
                new Particle.DustOptions(Color.fromRGB(0x5c0202), 1.0f));
    }

    /**
     * The meditating mage swung: the drop they aimed at, if any, bursts and gives up its artefact's
     * charge, more on the beat; off the beat it also takes some of the mage's health (never the last
     * point). False when the player is not meditating.
     */
    public static boolean swing(Player player) {
        Session session = SESSIONS.get(player.getUniqueId());
        if (session == null) {
            return false;
        }
        Drop drop = aimedAt(player, session);
        BloodRecord record = BloodRecords.get(session.character);
        if (drop == null || record == null) {
            return true;
        }
        long phase = (ticks - session.started) % MeditationConfig.beatTicks();
        boolean onBeat = Math.min(phase, MeditationConfig.beatTicks() - phase) <= MeditationConfig.windowTicks();
        double want = onBeat ? MeditationConfig.onBeatDraw() : MeditationConfig.offBeatDraw();
        double drawn = Math.max(0, Math.min(want, Math.min(record.getCharge(drop.id),
                BloodPool.cap(record) - record.getResonance())));
        record.setCharge(drop.id, record.getCharge(drop.id) - drawn);
        record.setResonance(record.getResonance() + drawn);
        session.drops.remove(drop);
        Location at = drop.display.getLocation();
        drop.display.remove();
        at.getWorld().spawnParticle(Particle.DUST, at, onBeat ? 18 : 8, 0.15, 0.15, 0.15, 0,
                new Particle.DustOptions(Color.fromRGB(onBeat ? 0xd10202 : 0x5c0202), onBeat ? 1.6f : 1.0f));
        if (onBeat) {
            BloodFx.play("meditate_catch", at, player);
        } else {
            player.setHealth(Math.max(1, player.getHealth() - MeditationConfig.offBeatHealth()));
            BloodFx.play("meditate_miss", at, player);
        }
        return true;
    }

    /** The drop nearest the line the player looks along, within reach and close to that line. */
    private static Drop aimedAt(Player player, Session session) {
        Location eye = player.getEyeLocation();
        Vector origin = eye.toVector();
        Vector direction = eye.getDirection().normalize();
        Drop best = null;
        double bestDistance = Double.MAX_VALUE;
        for (Drop drop : session.drops) {
            Vector to = drop.display.getLocation().toVector().subtract(origin);
            double along = to.dot(direction);
            if (along < 0 || along > MeditationConfig.hitRange()) {
                continue;
            }
            double off = to.clone().subtract(direction.clone().multiply(along)).length();
            if (off <= MeditationConfig.hitRadius() && off < bestDistance) {
                best = drop;
                bestDistance = off;
            }
        }
        return best;
    }

    /** Stops the player's meditation, e.g. on quit or a character switch. */
    public static void stop(UUID player) {
        Session session = SESSIONS.get(player);
        if (session != null) {
            end(session, Bukkit.getPlayer(player), BloodAltars.byId(session.altarId),
                    BloodRecords.get(session.character), null);
        }
    }

    private static void end(Session session, Player player, BloodAltar altar, BloodRecord record, String why) {
        SESSIONS.remove(session.player);
        for (Drop drop : session.drops) {
            drop.display.remove();
        }
        session.drops.clear();
        if (record != null) {
            BloodRecords.save(record);
        }
        if (altar != null && record != null) {
            AltarPedestals.restyle(altar, item -> BloodArtefacts.styled(record, item));
        }
        if (player != null) {
            MeditationConfig.music().stop(player);
            if (why != null) {
                WhisperService.tell(player, "blood.meditate.end." + why);
            }
        }
    }

    /** Ends every meditation, e.g. on shutdown. */
    static void clear() {
        for (Session session : List.copyOf(SESSIONS.values())) {
            stop(session.player);
        }
        if (task != null) {
            task.cancel();
            task = null;
        }
    }
}
