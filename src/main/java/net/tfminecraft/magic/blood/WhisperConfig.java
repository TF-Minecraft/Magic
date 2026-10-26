package net.tfminecraft.magic.blood;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.EntityType;

/** The whisper meter and the two voices, from the whispers: and voices: sections of the wildmagic/bloodmagic config. */
public final class WhisperConfig {

    /** Who speaks. The god until a mage refuses it; after that only the mage's own dark self. */
    public enum Speaker {
        GOD,
        SELF
    }

    /**
     * How a speaker sounds, and its lines per stage. With colours, a line is a gradient that drifts
     * from the start stops to the end ones along the path, style (e.g. italics) after each colour;
     * without, style is the whole prefix.
     */
    public record Voice(String style, String sound, float pitch, Map<BloodStage, List<String>> lines,
            List<String> start, List<String> end) {
        public List<String> lines(BloodStage stage) {
            return lines.getOrDefault(stage, List.of());
        }

        public boolean coloured() {
            return !start.isEmpty() || !end.isEmpty();
        }
    }

    private static double threshold = 30;
    private static double decayPerHour = 1;
    private static Set<String> fastTraits = Set.of();
    private static double fastMultiplier = 3;
    private static double animalKill = 2;
    private static double aloneOrNightMultiplier = 2;
    private static double characterKill = 15;
    private static double corruption = 1;
    private static double corruptionBelow = -40;
    private static Set<EntityType> animals = Set.of();
    private static int minInterval = 15;
    private static int maxInterval = 35;
    private static boolean requireAlone = true;
    private static double aloneRadius = 24;
    private static final Map<Speaker, Voice> VOICES = new EnumMap<>(Speaker.class);

    static {
        reset();
    }

    private WhisperConfig() {}

    static void reset() {
        threshold = 30;
        decayPerHour = 1;
        fastTraits = Set.of();
        fastMultiplier = 3;
        animalKill = 2;
        aloneOrNightMultiplier = 2;
        characterKill = 15;
        corruption = 1;
        corruptionBelow = -40;
        animals = Set.of();
        minInterval = 15;
        maxInterval = 35;
        requireAlone = true;
        aloneRadius = 24;
        VOICES.clear();
        VOICES.put(Speaker.GOD, new Voice("", "", 1f, Map.of(), List.of(), List.of()));
        VOICES.put(Speaker.SELF, new Voice("", "", 1f, Map.of(), List.of(), List.of()));
    }

    static void load(ConfigurationSection whispers, ConfigurationSection voices, Logger log) {
        reset();
        if (whispers != null) {
            threshold = Math.max(1, whispers.getDouble("threshold", 30));
            decayPerHour = Math.max(0, whispers.getDouble("decay_per_hour", 1));
            Set<String> traits = new HashSet<>();
            for (String trait : whispers.getStringList("fast_traits")) {
                traits.add(trait.trim().toLowerCase(Locale.ROOT));
            }
            fastTraits = Set.copyOf(traits);
            fastMultiplier = Math.max(1, whispers.getDouble("fast_multiplier", 3));
            animalKill = Math.max(0, whispers.getDouble("sources.animal_kill", 2));
            aloneOrNightMultiplier = Math.max(1, whispers.getDouble("sources.alone_or_night_multiplier", 2));
            characterKill = Math.max(0, whispers.getDouble("sources.character_kill", 15));
            corruption = Math.max(0, whispers.getDouble("sources.corruption", 1));
            corruptionBelow = whispers.getDouble("sources.corruption_below", -40);
            Set<EntityType> types = new HashSet<>();
            for (String raw : whispers.getStringList("animal_types")) {
                try {
                    types.add(EntityType.valueOf(raw.trim().toUpperCase(Locale.ROOT)));
                } catch (IllegalArgumentException ex) {
                    log.warning("[Magic] blood magic config whispers.animal_types: unknown entity '" + raw + "'");
                }
            }
            animals = Set.copyOf(types);
        }
        if (voices != null) {
            List<Integer> interval = voices.getIntegerList("interval_minutes");
            if (interval.size() == 2) {
                minInterval = Math.max(1, Math.min(interval.get(0), interval.get(1)));
                maxInterval = Math.max(minInterval, Math.max(interval.get(0), interval.get(1)));
            }
            requireAlone = voices.getBoolean("require_alone", true);
            aloneRadius = Math.max(1, voices.getDouble("alone_radius", 24));
            for (Speaker speaker : Speaker.values()) {
                ConfigurationSection section = voices.getConfigurationSection(speaker.name().toLowerCase(Locale.ROOT));
                if (section != null) {
                    VOICES.put(speaker, voice(section, log));
                }
            }
        }
    }

    private static Voice voice(ConfigurationSection section, Logger log) {
        Map<BloodStage, List<String>> lines = new EnumMap<>(BloodStage.class);
        ConfigurationSection stages = section.getConfigurationSection("lines");
        if (stages != null) {
            for (String key : stages.getKeys(false)) {
                BloodStage stage = BloodStage.parse(key);
                if (stage == null) {
                    log.warning("[Magic] blood magic config voice lines: unknown stage '" + key + "'");
                    continue;
                }
                lines.put(stage, List.copyOf(new ArrayList<>(stages.getStringList(key))));
            }
        }
        return new Voice(section.getString("style", ""), section.getString("sound", ""),
                (float) section.getDouble("pitch", 1.0), lines,
                colours(section, "colors.start", log), colours(section, "colors.end", log));
    }

    private static List<String> colours(ConfigurationSection section, String path, Logger log) {
        List<String> out = new ArrayList<>();
        for (String raw : section.getStringList(path)) {
            if (VoiceColours.valid(raw)) {
                out.add(raw.trim());
            } else {
                log.warning("[Magic] blood magic config voice " + path + ": '" + raw + "' is not a #rrggbb colour");
            }
        }
        return List.copyOf(out);
    }

    public static double threshold() {
        return threshold;
    }

    public static double decayPerHour() {
        return decayPerHour;
    }

    public static boolean fastTrait(Set<String> traits) {
        for (String trait : traits) {
            if (trait != null && fastTraits.contains(trait.toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }

    public static double fastMultiplier() {
        return fastMultiplier;
    }

    public static double animalKill() {
        return animalKill;
    }

    public static double aloneOrNightMultiplier() {
        return aloneOrNightMultiplier;
    }

    public static double characterKill() {
        return characterKill;
    }

    public static double corruption() {
        return corruption;
    }

    public static double corruptionBelow() {
        return corruptionBelow;
    }

    public static boolean isAnimal(EntityType type) {
        return animals.contains(type);
    }

    public static int minInterval() {
        return minInterval;
    }

    public static int maxInterval() {
        return maxInterval;
    }

    public static boolean requireAlone() {
        return requireAlone;
    }

    public static double aloneRadius() {
        return aloneRadius;
    }

    public static Voice voice(Speaker speaker) {
        return VOICES.get(speaker);
    }
}
