package net.tfminecraft.magic.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

import org.bukkit.configuration.ConfigurationSection;

import net.tfminecraft.magic.Cache;
import net.tfminecraft.magic.modifier.KeyframeCurve;

public final class ElementDef {

    private static final Pattern HEX = Pattern.compile("#?[0-9a-fA-F]{6}");

    private final String id;
    private final String name;
    private final String icon;
    private final List<String> colors;
    private final int slot;
    private final double maxResonance;
    private final Double decayPerHourOverride;
    private final double auraDecayPerHour;
    private final KeyframeCurve resonance;

    public ElementDef(String id, ConfigurationSection config) {
        this.id = id;
        this.name = config.getString("name", id);
        this.icon = config.getString("icon", "v.BARRIER");
        this.colors = readColors(config);
        this.slot = config.getInt("slot", -1);
        this.maxResonance = Math.max(1.0, config.getDouble("max_resonance", 100.0));
        this.decayPerHourOverride = config.contains("decay_per_hour")
                ? config.getDouble("decay_per_hour")
                : null;
        this.auraDecayPerHour = config.getDouble("aura_decay_per_hour", 0.0);
        ConfigurationSection resonanceSection = config.getConfigurationSection("resonance");
        KeyframeCurve parsed = KeyframeCurve.fromSection(resonanceSection);
        this.resonance = resonanceSection == null || parsed.size() < 2
                ? KeyframeCurve.defaultResonance()
                : parsed;
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getIcon() {
        return icon;
    }

    /** First colour stop, {@code #rrggbb}. Used for particles and single-colour text. */
    public String getColor() {
        return colors.get(0);
    }

    /** Colour stops in config order. One stop is a solid colour. Several form a gradient. */
    public List<String> getColors() {
        return colors;
    }

    public double getMaxResonance() {
        return maxResonance;
    }

    public double getDecayPerHour() {
        return decayPerHourOverride != null ? decayPerHourOverride : Cache.defaultResonanceDecayPerHour;
    }

    public double getAuraDecayPerHour() {
        return auraDecayPerHour;
    }

    public int getSlot() {
        return slot;
    }

    public KeyframeCurve getResonanceCurve() {
        return resonance;
    }

    /**
     * {@code color} is either one {@code #rrggbb} or a list of stops. Reading the list
     * with {@code getString} would stringify it to {@code [#rrggbb]} and print the brackets.
     */
    private static List<String> readColors(ConfigurationSection config) {
        List<String> parsed = new ArrayList<>();
        if (config.isList("color")) {
            for (String entry : config.getStringList("color")) {
                addColor(parsed, entry);
            }
        } else {
            addColor(parsed, config.getString("color"));
        }
        if (parsed.isEmpty()) {
            parsed.add("#ffffff");
        }
        return Collections.unmodifiableList(parsed);
    }

    private static void addColor(List<String> parsed, String raw) {
        if (raw == null) {
            return;
        }
        String token = raw.trim();
        if (!HEX.matcher(token).matches()) {
            return;
        }
        if (!token.startsWith("#")) {
            token = "#" + token;
        }
        parsed.add(token.toLowerCase(Locale.ROOT));
    }
}
