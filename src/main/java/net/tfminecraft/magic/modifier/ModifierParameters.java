package net.tfminecraft.magic.modifier;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;

import org.bukkit.configuration.ConfigurationSection;

/**
 * Which MythicLib skill parameters each modifier stat reaches ({@code modifier_parameters}
 * in config.yml). A stat missing from the config keeps its default list; an empty list
 * turns the stat off. A parameter belongs to one stat only: a later claim is dropped.
 */
public final class ModifierParameters {

    private static final Map<ModifierStat, List<String>> DEFAULTS = defaults();
    private static Map<ModifierStat, List<String>> current = DEFAULTS;

    private ModifierParameters() {}

    public static List<String> of(ModifierStat stat) {
        return current.get(stat);
    }

    public static void reset() {
        current = DEFAULTS;
    }

    public static void load(ConfigurationSection section, Logger logger) {
        if (section == null) {
            reset();
            return;
        }
        Map<ModifierStat, List<String>> loaded = new EnumMap<>(ModifierStat.class);
        Set<String> claimed = new HashSet<>();
        for (ModifierStat stat : ModifierStat.values()) {
            List<String> raw = section.isSet(stat.key()) ? section.getStringList(stat.key()) : DEFAULTS.get(stat);
            List<String> parameters = new ArrayList<>();
            for (String entry : raw) {
                if (entry.isBlank()) {
                    continue;
                }
                String parameter = entry.trim().toLowerCase(Locale.ROOT);
                if (!claimed.add(parameter)) {
                    if (logger != null) {
                        logger.warning("[Magic] modifier_parameters: '" + parameter + "' is already under another stat;"
                                + " ignored under " + stat.key());
                    }
                    continue;
                }
                parameters.add(parameter);
            }
            loaded.put(stat, Collections.unmodifiableList(parameters));
        }
        current = Collections.unmodifiableMap(loaded);
    }

    private static Map<ModifierStat, List<String>> defaults() {
        Map<ModifierStat, List<String>> map = new EnumMap<>(ModifierStat.class);
        map.put(ModifierStat.MANA, List.of("mana"));
        map.put(ModifierStat.COOLDOWN, List.of("cooldown"));
        map.put(ModifierStat.DAMAGE, List.of("damage", "burnt_damage"));
        map.put(ModifierStat.SUPPORT, List.of("heal", "shield_power"));
        map.put(ModifierStat.DURATION, List.of());
        return Collections.unmodifiableMap(map);
    }
}
