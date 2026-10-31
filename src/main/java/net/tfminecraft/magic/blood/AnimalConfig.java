package net.tfminecraft.magic.blood;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.logging.Logger;

import org.bukkit.SoundCategory;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.EntityType;

/** The animal: section of the wildmagic/bloodmagic config. */
public final class AnimalConfig {

    private static int speciesRequired;
    private static long cooldownMillis;
    private static double bindRange;
    private static int bindSeconds;
    private static boolean requireStolen;
    private static boolean clearDrops;
    private static Set<EntityType> species;
    private static String traceModel;
    private static long traceMillis;
    private static long noteDelayMillis;
    private static int bleedSeconds;
    private static double stayRange;
    private static Set<Integer> heartbeats;
    private static String music;
    private static SoundCategory musicCategory;
    private static float musicVolume;

    static {
        reset();
    }

    private AnimalConfig() {}

    static void reset() {
        speciesRequired = 7;
        cooldownMillis = 24L * 3_600_000L;
        bindRange = 4;
        bindSeconds = 20;
        requireStolen = true;
        clearDrops = true;
        species = Set.of();
        traceModel = "v.redstone";
        traceMillis = 6L * 3_600_000L;
        noteDelayMillis = 30L * 60_000L;
        bleedSeconds = 15;
        stayRange = 4;
        heartbeats = Set.of(0, 1, 2, 3, 5, 7, 10, 13);
        music = "";
        musicCategory = SoundCategory.RECORDS;
        musicVolume = 0.8f;
    }

    static void load(ConfigurationSection section, Logger log) {
        reset();
        if (section == null) {
            return;
        }
        speciesRequired = Math.max(1, section.getInt("species_required", 7));
        cooldownMillis = (long) (Math.max(0, section.getDouble("cooldown_hours", 24)) * 3_600_000L);
        bindRange = Math.max(1, section.getDouble("bind_range", 4));
        bindSeconds = Math.max(1, section.getInt("bind_seconds", 20));
        requireStolen = section.getBoolean("require_stolen", true);
        clearDrops = section.getBoolean("clear_drops", true);
        Set<EntityType> types = new HashSet<>();
        for (String raw : section.getStringList("species")) {
            try {
                types.add(EntityType.valueOf(raw.trim().toUpperCase(Locale.ROOT)));
            } catch (IllegalArgumentException ex) {
                log.warning("[Magic] blood magic config animal.species: unknown entity '" + raw + "'");
            }
        }
        species = Set.copyOf(types);
        traceModel = section.getString("trace.model", "v.redstone");
        traceMillis = (long) (Math.max(0, section.getDouble("trace.hours", 6)) * 3_600_000L);
        noteDelayMillis = (long) (Math.max(0, section.getDouble("note_delay_minutes", 30)) * 60_000L);
        bleedSeconds = Math.max(1, section.getInt("rite.bleed_seconds", 15));
        stayRange = Math.max(1, section.getDouble("rite.stay_range", 4));
        if (section.isList("rite.heartbeats")) {
            heartbeats = Set.copyOf(section.getIntegerList("rite.heartbeats"));
        }
        music = section.getString("rite.music.sound", "").trim();
        musicVolume = (float) Math.max(0, section.getDouble("rite.music.volume", 0.8));
        String category = section.getString("rite.music.category", "RECORDS");
        try {
            musicCategory = SoundCategory.valueOf(category.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            log.warning("[Magic] blood magic config animal.rite.music.category: unknown '" + category + "'");
        }
    }

    /** The music played to the mage alone while the animal bleeds; blank for none. */
    public static String music() {
        return music;
    }

    public static SoundCategory musicCategory() {
        return musicCategory;
    }

    public static float musicVolume() {
        return musicVolume;
    }

    /** Seconds a cut animal bleeds out on the stone before the sacrifice counts. */
    public static int bleedSeconds() {
        return bleedSeconds;
    }

    /** How far from the altar the mage may stand while it bleeds; further spills it. */
    public static double stayRange() {
        return stayRange;
    }

    /** Seconds into the bleeding when its heart beats, slowing toward the end. */
    public static Set<Integer> heartbeats() {
        return heartbeats;
    }

    public static int speciesRequired() {
        return speciesRequired;
    }

    public static long cooldownMillis() {
        return cooldownMillis;
    }

    public static double bindRange() {
        return bindRange;
    }

    public static int bindSeconds() {
        return bindSeconds;
    }

    public static boolean requireStolen() {
        return requireStolen;
    }

    public static boolean clearDrops() {
        return clearDrops;
    }

    public static boolean isSpecies(EntityType type) {
        return species.contains(type);
    }

    public static String traceModel() {
        return traceModel;
    }

    public static long traceMillis() {
        return traceMillis;
    }

    public static long noteDelayMillis() {
        return noteDelayMillis;
    }
}
