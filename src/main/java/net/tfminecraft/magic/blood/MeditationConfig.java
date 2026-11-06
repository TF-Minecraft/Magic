package net.tfminecraft.magic.blood;


import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

/**
 * Wild magic meditation and charging (settings.yml meditation:): the heartbeat the blood mage
 * meditates to, the drops of blood it draws from the charged artefacts, what a catch on or off the
 * beat gives, how long charging an artefact in the pool takes, and the private sounds and music.
 */
public final class MeditationConfig {

    /**
     * A sound only its player hears: key (vanilla or a resource pack's), volume, pitch, and how long
     * it runs before a loop plays it again (0: it plays once).
     */
    public record Cue(String sound, float volume, float pitch, int seconds) {
        static final Cue NONE = new Cue("", 0, 1, 0);

        boolean isSet() {
            return !sound.isBlank();
        }

        /** Plays the sound to the player alone, where they stand. */
        void play(Player player) {
            if (isSet()) {
                player.playSound(player.getLocation(), sound, volume, pitch);
            }
        }

        void stop(Player player) {
            if (isSet()) {
                player.stopSound(sound);
            }
        }
    }

    private static int beatTicks;
    private static int windowTicks;
    private static int travelBeats;
    private static int lingerBeats;
    private static int maxDrops;
    private static double onBeatDraw;
    private static double offBeatDraw;
    private static double offBeatHealth;
    private static double hitRange;
    private static double hitRadius;
    private static String dropItem;
    private static float dropScale;
    private static float flareScale;
    private static int chargeTicks;
    private static Cue heartbeat;
    private static Cue music;
    private static Cue charge;

    static {
        reset();
    }

    private MeditationConfig() {}

    static void reset() {
        beatTicks = 30;
        windowTicks = 6;
        travelBeats = 2;
        lingerBeats = 2;
        maxDrops = 4;
        onBeatDraw = 3;
        offBeatDraw = 1;
        offBeatHealth = 1;
        hitRange = 6;
        hitRadius = 0.6;
        dropItem = "v.redstone";
        dropScale = 0.3f;
        flareScale = 0.55f;
        chargeTicks = 160;
        heartbeat = Cue.NONE;
        music = Cue.NONE;
        charge = Cue.NONE;
    }

    static void load(ConfigurationSection meditation) {
        reset();
        if (meditation == null) {
            return;
        }
        beatTicks = Math.max(10, meditation.getInt("beat_ticks", 30));
        windowTicks = Math.max(1, Math.min(beatTicks / 2, meditation.getInt("window_ticks", 6)));
        travelBeats = Math.max(1, meditation.getInt("drops.travel_beats", 2));
        lingerBeats = Math.max(1, meditation.getInt("drops.linger_beats", 2));
        maxDrops = Math.max(1, meditation.getInt("drops.max", 4));
        onBeatDraw = Math.max(0, meditation.getDouble("on_beat_draw", 3));
        offBeatDraw = Math.max(0, meditation.getDouble("off_beat_draw", 1));
        offBeatHealth = Math.max(0, meditation.getDouble("off_beat_health", 1));
        hitRange = Math.max(1, meditation.getDouble("hit_range", 6));
        hitRadius = Math.max(0.1, meditation.getDouble("hit_radius", 0.6));
        dropItem = meditation.getString("drops.item", "v.redstone");
        dropScale = (float) Math.max(0.05, meditation.getDouble("drops.scale", 0.3));
        flareScale = (float) Math.max(dropScale, meditation.getDouble("drops.flare_scale", 0.55));
        chargeTicks = Math.max(20, meditation.getInt("charge_ticks", 160));
        heartbeat = cue(meditation.getConfigurationSection("sounds.heartbeat"));
        music = cue(meditation.getConfigurationSection("sounds.music"));
        charge = cue(meditation.getConfigurationSection("sounds.charge"));
    }

    private static Cue cue(ConfigurationSection section) {
        if (section == null) {
            return Cue.NONE;
        }
        return new Cue(section.getString("sound", "").trim(), (float) Math.max(0, section.getDouble("volume", 1)),
                (float) Math.max(0.5, Math.min(2, section.getDouble("pitch", 1))), Math.max(0, section.getInt("seconds", 0)));
    }

    /** Ticks between heartbeats. */
    public static int beatTicks() {
        return beatTicks;
    }

    /** A catch this many ticks from a beat counts as on the beat. */
    public static int windowTicks() {
        return windowTicks;
    }

    /** Beats a drop takes from its artefact to the mage. */
    public static int travelBeats() {
        return travelBeats;
    }

    /** Beats a drop waits in front of the mage before it falls back. */
    public static int lingerBeats() {
        return lingerBeats;
    }

    public static int maxDrops() {
        return maxDrops;
    }

    /** Charge drawn into blood resonance by a catch on the beat. */
    public static double onBeatDraw() {
        return onBeatDraw;
    }

    /** Charge drawn by a catch off the beat, which also costs the mage health. */
    public static double offBeatDraw() {
        return offBeatDraw;
    }

    public static double offBeatHealth() {
        return offBeatHealth;
    }

    public static double hitRange() {
        return hitRange;
    }

    public static double hitRadius() {
        return hitRadius;
    }

    /** The drop's look, a TLibs item path. */
    public static String dropItem() {
        return dropItem;
    }

    public static float dropScale() {
        return dropScale;
    }

    /** How large a drop swells on each beat. */
    public static float flareScale() {
        return flareScale;
    }

    /** How long an artefact takes to charge in the pool. */
    public static int chargeTicks() {
        return chargeTicks;
    }

    public static Cue heartbeat() {
        return heartbeat;
    }

    /** The meditation's music, looped while the mage meditates. */
    public static Cue music() {
        return music;
    }

    /** The cue while an artefact charges in the pool. */
    public static Cue charge() {
        return charge;
    }
}
