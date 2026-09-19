package net.tfminecraft.magic.model;

import org.bukkit.configuration.ConfigurationSection;

import net.tfminecraft.magic.Cache;
import net.tfminecraft.magic.modifier.KeyframeCurve;

public final class ElementDef {

    private final String id;
    private final String name;
    private final String icon;
    private final String color;
    private final int slot;
    private final double maxResonance;
    private final Double decayPerHourOverride;
    private final double auraDecayPerHour;
    private final KeyframeCurve resonance;

    public ElementDef(String id, ConfigurationSection config) {
        this.id = id;
        this.name = config.getString("name", id);
        this.icon = config.getString("icon", "v.BARRIER");
        this.color = config.getString("color", "#ffffff");
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

    public String getColor() {
        return color;
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
}
