package net.tfminecraft.magic.model;

import java.util.ArrayList;
import java.util.List;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

import me.Plugins.TLibs.Objects.API.SubAPI.StringFormatter;
import net.tfminecraft.magic.Cache;
import net.tfminecraft.magic.modifier.KeyframeCurve;

public final class ElementDef {

    private static final String FALLBACK_COLOR = "#ffffff";

    private final String id;
    private final String name;
    private final String icon;
    private final List<String> colorStops;
    private final int slot;
    private final double maxResonance;
    private final Double decayPerHourOverride;
    private final double auraDecayPerHour;
    private final String permission;
    private final KeyframeCurve resonance;

    public ElementDef(String id, ConfigurationSection config) {
        this.id = id;
        this.name = config.getString("name", id);
        this.icon = config.getString("icon", "v.BARRIER");
        this.colorStops = parseColorStops(config);
        this.slot = config.getInt("slot", -1);
        String perm = config.getString("permission", "");
        this.permission = perm == null || perm.isBlank() ? null : perm.trim();
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

    private static List<String> parseColorStops(ConfigurationSection config) {
        List<String> stops = new ArrayList<>();
        if (config.isList("color")) {
            for (String raw : config.getStringList("color")) {
                addStop(stops, raw);
            }
        } else {
            addStop(stops, config.getString("color", ""));
        }
        if (stops.isEmpty()) {
            stops.add(FALLBACK_COLOR);
        }
        return List.copyOf(stops);
    }

    private static void addStop(List<String> stops, String raw) {
        if (raw == null || raw.isBlank()) {
            return;
        }
        stops.add(raw.trim());
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getColoredName() {
        return colorText(name);
    }

    public String colorText(String plain) {
        String text = plain != null ? plain : "";
        return StringFormatter.applyColourGradient(text, colorStops);
    }

    public String getIcon() {
        return icon;
    }

    public String getColor() {
        return colorStops.isEmpty() ? FALLBACK_COLOR : colorStops.get(0);
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

    public String getPermission() {
        return permission;
    }

    public boolean isUnlocked(Player player) {
        if (permission == null || permission.isBlank()) {
            return true;
        }
        if (player == null) {
            return false;
        }
        return player.hasPermission(permission);
    }

    public KeyframeCurve getResonanceCurve() {
        return resonance;
    }
}
