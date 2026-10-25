package net.tfminecraft.magic.blood;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.logging.Logger;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.EntityType;

/** The animal: section of bloodmagic.yml. */
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
                log.warning("[Magic] bloodmagic.yml animal.species: unknown entity '" + raw + "'");
            }
        }
        species = Set.copyOf(types);
        traceModel = section.getString("trace.model", "v.redstone");
        traceMillis = (long) (Math.max(0, section.getDouble("trace.hours", 6)) * 3_600_000L);
        noteDelayMillis = (long) (Math.max(0, section.getDouble("note_delay_minutes", 30)) * 60_000L);
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
