package net.tfminecraft.magic.blood;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.bukkit.SoundCategory;
import org.bukkit.configuration.ConfigurationSection;

/**
 * The god's question (choice:), the hidden classes (classes:) and the bound armour (armour:). The
 * question is a dialogue: the voices ask, and the mage answers aloud in roleplay chat.
 */
public final class ChoiceConfig {

    /** One line of the question: who says it and what. */
    public record Line(WhisperConfig.Speaker speaker, String text) {}

    /** The words that mean an answer, and what the voices say around it. */
    public record Answer(List<String> words, List<Line> confirm, List<Line> sealed) {}

    private static int devoteeInjuries;
    private static int answerSeconds;
    private static int lineSeconds;
    private static String music;
    private static SoundCategory musicCategory;
    private static float musicVolume;
    private static List<Line> ask;
    private static Line unclear;
    private static Line walkedAway;
    private static final Map<BloodPath, Answer> ANSWERS = new EnumMap<>(BloodPath.class);
    private static final Map<BloodPath, String> CLASSES = new EnumMap<>(BloodPath.class);
    private static final Map<BloodPath, List<Piece>> ARMOUR = new EnumMap<>(BloodPath.class);
    private static final Map<BloodPath, List<String>> PERMISSIONS = new EnumMap<>(BloodPath.class);

    static {
        reset();
    }

    private ChoiceConfig() {}

    static void reset() {
        devoteeInjuries = 2;
        answerSeconds = 90;
        lineSeconds = 4;
        music = "";
        musicCategory = SoundCategory.RECORDS;
        musicVolume = 0.8f;
        ask = List.of();
        unclear = null;
        walkedAway = null;
        ANSWERS.clear();
        ANSWERS.put(BloodPath.DEVOTEE, new Answer(List.of(), List.of(), List.of()));
        ANSWERS.put(BloodPath.TRUE, new Answer(List.of(), List.of(), List.of()));
        CLASSES.clear();
        ARMOUR.clear();
        PERMISSIONS.clear();
    }

    /**
     * One bound armour piece: the item (TLibs path), an optional look (TLibs ia. path) put over it, and an
     * optional name with {victim} for the true path's pieces made from the people they took.
     */
    public record Piece(String item, String look, String name) {
        public Piece(String item, String look) {
            this(item, look, null);
        }
    }

    static void load(ConfigurationSection choice, ConfigurationSection classes, ConfigurationSection armour) {
        reset();
        if (choice != null) {
            devoteeInjuries = Math.max(0, choice.getInt("devotee_injuries", 2));
            answerSeconds = Math.max(5, choice.getInt("answer_seconds", 90));
            lineSeconds = Math.max(1, choice.getInt("line_seconds", 4));
            music = choice.getString("music.sound", "").trim();
            musicVolume = (float) Math.max(0, choice.getDouble("music.volume", 0.8));
            try {
                musicCategory = SoundCategory.valueOf(choice.getString("music.category", "RECORDS").trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException ex) {
                musicCategory = SoundCategory.RECORDS;
            }
            ask = lines(choice.getList("ask", List.of()));
            unclear = first(lines(choice.getList("unclear", List.of())));
            walkedAway = first(lines(choice.getList("walked_away", List.of())));
            for (BloodPath path : List.of(BloodPath.DEVOTEE, BloodPath.TRUE)) {
                String key = path == BloodPath.DEVOTEE ? "kneel" : "refuse";
                List<String> words = new ArrayList<>();
                for (String word : choice.getStringList(key + ".words")) {
                    if (!word.isBlank()) {
                        words.add(QuestionService.normalize(word));
                    }
                }
                ANSWERS.put(path, new Answer(List.copyOf(words), lines(choice.getList(key + ".confirm", List.of())),
                        lines(choice.getList(key + ".sealed", List.of()))));
            }
        }
        for (BloodPath path : List.of(BloodPath.DEVOTEE, BloodPath.TRUE)) {
            String key = path.name().toLowerCase(Locale.ROOT);
            if (classes != null && classes.isString(key) && !classes.getString(key).isBlank()) {
                CLASSES.put(path, classes.getString(key).trim());
            }
            if (armour != null) {
                List<Piece> pieces = new ArrayList<>();
                for (Object entry : armour.getList(key, List.of())) {
                    Piece piece = piece(entry);
                    if (piece != null) {
                        pieces.add(piece);
                    }
                }
                ARMOUR.put(path, List.copyOf(pieces));
            }
        }
    }

    /** A plain item path, or { item: ..., look: ..., name: ... }; null when there is no item. */
    private static Piece piece(Object entry) {
        if (entry instanceof String item && !item.isBlank()) {
            return new Piece(item.trim(), null);
        }
        if (entry instanceof Map<?, ?> map && map.get("item") instanceof String item && !item.isBlank()) {
            return new Piece(item.trim(), map.get("look") instanceof String look && !look.isBlank() ? look.trim() : null,
                    map.get("name") instanceof String name && !name.isBlank() ? name : null);
        }
        return null;
    }

    /** Lines as a list of { god: text } or { self: text }; anything else is skipped. */
    private static List<Line> lines(List<?> raw) {
        List<Line> out = new ArrayList<>();
        for (Object entry : raw) {
            if (entry instanceof Map<?, ?> map && map.size() == 1) {
                Map.Entry<?, ?> only = map.entrySet().iterator().next();
                String who = String.valueOf(only.getKey()).trim().toUpperCase(Locale.ROOT);
                if ((who.equals("GOD") || who.equals("SELF")) && only.getValue() instanceof String text && !text.isBlank()) {
                    out.add(new Line(WhisperConfig.Speaker.valueOf(who), text));
                }
            }
        }
        return List.copyOf(out);
    }

    private static Line first(List<Line> lines) {
        return lines.isEmpty() ? null : lines.get(0);
    }

    /** The question, said in order. */
    public static List<Line> ask() {
        return ask;
    }

    /** What the voices say to an answer they cannot make out; null for nothing. */
    public static Line unclear() {
        return unclear;
    }

    /** What the god says when the mage leaves the chamber or lets the time run out; null for nothing. */
    public static Line walkedAway() {
        return walkedAway;
    }

    public static Answer answer(BloodPath path) {
        return ANSWERS.get(path);
    }

    public static int answerSeconds() {
        return answerSeconds;
    }

    public static int lineSeconds() {
        return lineSeconds;
    }

    /** Played to the mage alone while the question lasts; blank for none. */
    public static String music() {
        return music;
    }

    public static SoundCategory musicCategory() {
        return musicCategory;
    }

    public static float musicVolume() {
        return musicVolume;
    }

    public static int devoteeInjuries() {
        return devoteeInjuries;
    }

    /** The hidden MMOCore class a path unlocks, or null. */
    public static String classFor(BloodPath path) {
        return CLASSES.get(path);
    }

    /** Reads permissions: the permission(s) each path's character holds while played, one or a list. */
    static void loadPermissions(ConfigurationSection permissions) {
        PERMISSIONS.clear();
        if (permissions == null) {
            return;
        }
        for (BloodPath path : List.of(BloodPath.DEVOTEE, BloodPath.TRUE)) {
            String key = path.name().toLowerCase(Locale.ROOT);
            List<String> names = new ArrayList<>();
            List<?> raw = permissions.isList(key) ? permissions.getList(key) : List.of(permissions.get(key, ""));
            for (Object entry : raw) {
                if (entry instanceof String name && !name.isBlank()) {
                    names.add(name.trim());
                }
            }
            if (!names.isEmpty()) {
                PERMISSIONS.put(path, List.copyOf(names));
            }
        }
    }

    /** The permissions a path's character holds while played; empty for none. */
    public static List<String> permissionsFor(BloodPath path) {
        return PERMISSIONS.getOrDefault(path, List.of());
    }

    /** The bound armour pieces a path receives. */
    public static List<Piece> armourFor(BloodPath path) {
        return ARMOUR.getOrDefault(path, List.of());
    }
}
