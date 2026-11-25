package net.tfminecraft.magic.blood;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Logger;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

import net.tfminecraft.magic.util.MagicText;

/**
 * Faint hints for characters with a fast trait (Occultist) whose voices have not woken yet: a short
 * line when a deed feeds their whisper meter, and now and then a riddle when they are alone. Both are
 * the character's own thoughts in the pale colours; they never name the rules or the god.
 * From the hints: section of the wildmagic/bloodmagic config.
 */
public final class OccultistHints {

    /** What fed the meter. Each has its own hint lines (hints.deeds.<name in lower case>). */
    public enum Deed {
        STOLEN_ANIMAL,
        OWN_ANIMAL,
        CHARACTER_KILL,
        ROBBERY,
        CORRUPTION
    }

    private static final long MINUTE = 60_000L;
    /** When each online player may next hear a riddle (by player). */
    private static final Map<UUID, Long> NEXT_RIDDLE = new HashMap<>();
    /** When each online player may next hear a hint for each deed (by player). */
    private static final Map<UUID, Map<Deed, Long>> NEXT_DEED = new HashMap<>();

    private static boolean enabled = true;
    private static String style = "";
    private static List<String> colours = List.of();
    private static String sound = "";
    private static float pitch = 1f;
    private static float volume = 0.4f;
    private static long deedCooldown = 60 * MINUTE;
    private static final Map<Deed, List<String>> DEEDS = new EnumMap<>(Deed.class);
    private static List<String> riddles = List.of();
    private static int[] firstRiddle = {30, 90};
    private static int[] riddleInterval = {120, 240};

    private OccultistHints() {}

    static void reset() {
        enabled = true;
        style = "";
        colours = List.of();
        sound = "";
        pitch = 1f;
        volume = 0.4f;
        deedCooldown = 60 * MINUTE;
        DEEDS.clear();
        riddles = List.of();
        firstRiddle = new int[] {30, 90};
        riddleInterval = new int[] {120, 240};
    }

    static void load(ConfigurationSection section, Logger log) {
        reset();
        if (section == null) {
            return;
        }
        enabled = section.getBoolean("enabled", true);
        style = section.getString("style", "");
        List<String> stops = new ArrayList<>();
        for (String raw : section.getStringList("colors")) {
            if (VoiceColours.valid(raw)) {
                stops.add(raw.trim());
            } else {
                log.warning("[Magic] blood magic config hints.colors: '" + raw + "' is not a #rrggbb colour");
            }
        }
        colours = List.copyOf(stops);
        sound = section.getString("sound", "");
        pitch = (float) section.getDouble("pitch", 1.0);
        volume = (float) Math.max(0, section.getDouble("volume", 0.4));
        deedCooldown = Math.max(0, section.getInt("deed_cooldown_minutes", 60)) * MINUTE;
        for (Deed deed : Deed.values()) {
            List<String> lines = section.getStringList("deeds." + deed.name().toLowerCase(Locale.ROOT));
            if (!lines.isEmpty()) {
                DEEDS.put(deed, List.copyOf(lines));
            }
        }
        riddles = List.copyOf(section.getStringList("riddles"));
        firstRiddle = range(section.getIntegerList("first_riddle_minutes"), firstRiddle);
        riddleInterval = range(section.getIntegerList("riddle_interval_minutes"), riddleInterval);
    }

    private static int[] range(List<Integer> raw, int[] fallback) {
        if (raw.size() != 2) {
            return fallback;
        }
        int min = Math.max(1, Math.min(raw.get(0), raw.get(1)));
        return new int[] {min, Math.max(min, Math.max(raw.get(0), raw.get(1)))};
    }

    /**
     * A deed just fed a fast-trait character's meter (the voices are not awake yet): one line about it,
     * at most once per deed_cooldown_minutes for each kind of deed.
     */
    static void deed(Player player, Deed deed) {
        List<String> lines = DEEDS.getOrDefault(deed, List.of());
        if (!enabled || lines.isEmpty()) {
            return;
        }
        long now = WhisperService.clock.getAsLong();
        Map<Deed, Long> due = NEXT_DEED.computeIfAbsent(player.getUniqueId(), id -> new EnumMap<>(Deed.class));
        if (now < due.getOrDefault(deed, 0L)) {
            return;
        }
        due.put(deed, now + deedCooldown);
        say(player, lines.get(WhisperService.random.nextInt(lines.size())));
    }

    /**
     * Once a minute for each online player with a character. A riddle falls due a while after they
     * join and then every riddle_interval_minutes; it waits until they are alone, and only a fast-trait
     * character whose voices have not woken hears it.
     */
    static void tick(Player player, String character, BloodRecord record) {
        UUID id = player.getUniqueId();
        if (!enabled || riddles.isEmpty() || record != null && record.getStage() != BloodStage.NONE) {
            NEXT_RIDDLE.remove(id);
            return;
        }
        long now = WhisperService.clock.getAsLong();
        Long due = NEXT_RIDDLE.get(id);
        if (due == null) {
            NEXT_RIDDLE.put(id, now + minutes(firstRiddle) * MINUTE);
            return;
        }
        if (now < due || !WhisperService.alone(player)) {
            return;
        }
        // The trait is only looked up when a riddle falls due; without it the next check is an interval away.
        if (WhisperService.fastTraitOf.test(character)) {
            say(player, riddles.get(WhisperService.random.nextInt(riddles.size())));
        }
        NEXT_RIDDLE.put(id, now + minutes(riddleInterval) * MINUTE);
    }

    private static int minutes(int[] range) {
        return range[0] + WhisperService.random.nextInt(range[1] - range[0] + 1);
    }

    /** One line to this player alone, in the hints' colours and sound. */
    static void say(Player player, String line) {
        String prefix = MagicText.format(style);
        player.sendMessage(colours.isEmpty() ? prefix + MagicText.format(line)
                : VoiceColours.gradient(line, colours, prefix));
        if (!sound.isBlank()) {
            player.playSound(player.getLocation(), sound, volume, pitch);
        }
    }

    /** Forget the player's timers, e.g. after a quit or a character switch. */
    static void forget(UUID player) {
        NEXT_RIDDLE.remove(player);
        NEXT_DEED.remove(player);
    }

    static Long nextRiddle(UUID player) {
        return NEXT_RIDDLE.get(player);
    }

    static void clear() {
        NEXT_RIDDLE.clear();
        NEXT_DEED.clear();
    }
}
