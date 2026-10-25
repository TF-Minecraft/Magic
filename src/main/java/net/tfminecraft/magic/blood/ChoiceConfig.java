package net.tfminecraft.magic.blood;

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
    private static final Map<BloodPath, List<String>> ARMOUR = new EnumMap<>(BloodPath.class);

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
                ARMOUR.put(path, List.copyOf(armour.getStringList(key)));
            }
        }
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

    /** TLibs item paths of the bound armour a path receives. */
    public static List<String> armourFor(BloodPath path) {
        return ARMOUR.getOrDefault(path, List.of());
    }
}
