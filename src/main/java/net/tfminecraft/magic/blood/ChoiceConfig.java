package net.tfminecraft.magic.blood;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.bukkit.configuration.ConfigurationSection;

/** The god's question (choice:), the hidden classes (classes:) and the bound armour (armour:). */
public final class ChoiceConfig {

    /** A menu button. */
    public record Button(String item, String name, List<String> lore) {}

    public enum Slot {
        QUESTION("question", "v.paper"),
        DEVOTEE("devotee", "v.red_dye"),
        TRUE("true", "v.iron_sword"),
        CONFIRM("confirm", "v.redstone"),
        BACK("back", "v.barrier");

        private final String key;
        private final String fallback;

        Slot(String key, String fallback) {
            this.key = key;
            this.fallback = fallback;
        }
    }

    private static String title;
    private static final Map<Slot, Button> BUTTONS = new EnumMap<>(Slot.class);
    private static int devoteeInjuries;
    private static final Map<BloodPath, String> CLASSES = new EnumMap<>(BloodPath.class);
    private static final Map<BloodPath, List<Piece>> ARMOUR = new EnumMap<>(BloodPath.class);
    private static final Map<BloodPath, List<String>> PERMISSIONS = new EnumMap<>(BloodPath.class);

    static {
        reset();
    }

    private ChoiceConfig() {}

    static void reset() {
        title = "...";
        BUTTONS.clear();
        for (Slot slot : Slot.values()) {
            BUTTONS.put(slot, new Button(slot.fallback, "", List.of()));
        }
        devoteeInjuries = 2;
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
            title = choice.getString("title", "...");
            devoteeInjuries = Math.max(0, choice.getInt("devotee_injuries", 2));
            for (Slot slot : Slot.values()) {
                if (slot == Slot.QUESTION) {
                    BUTTONS.put(slot, new Button(slot.fallback, "", List.copyOf(choice.getStringList("question"))));
                    continue;
                }
                ConfigurationSection button = choice.getConfigurationSection(slot.key);
                if (button != null) {
                    BUTTONS.put(slot, new Button(button.getString("item", slot.fallback),
                            button.getString("name", ""), List.copyOf(button.getStringList("lore"))));
                }
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

    public static String title() {
        return title;
    }

    public static Button button(Slot slot) {
        return BUTTONS.get(slot);
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
