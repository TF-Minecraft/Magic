package net.tfminecraft.magic.loader;

import java.io.File;
import java.io.IOException;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import me.Plugins.TLibs.Interface.LoaderInterface;
import net.tfminecraft.magic.Cache;
import net.tfminecraft.magic.GuiCache;
import net.tfminecraft.magic.Magic;
import net.tfminecraft.magic.attunement.ArtifactCareCache;
import net.tfminecraft.magic.meditation.MeditationCache;
import net.tfminecraft.magic.modifier.DriftCache;
import net.tfminecraft.magic.modifier.KeyframeCurve;
import net.tfminecraft.magic.modifier.ModifierTriple;

public final class ConfigLoader implements LoaderInterface {

    @Override
    public void load(File configFile) {
        loadSafe(configFile);
    }

    public boolean loadSafe(File configFile) {
        FileConfiguration config = new YamlConfiguration();
        try {
            config.load(configFile);
        } catch (IOException | InvalidConfigurationException ex) {
            Magic.plugin.getLogger().severe("[Magic] Failed to load config.yml: " + ex.getMessage());
            return false;
        }

        Cache.debug = config.getBoolean("debug", Cache.debug);
        Cache.loggingEnabled = config.getBoolean("logging", true);
        Cache.wipeLog = config.getBoolean("wipe-log", true);
        Cache.defaultCastMode = config.getString("default_cast_mode", Cache.defaultCastMode);
        Cache.defaultResonance = config.getDouble("default_resonance", Cache.defaultResonance);

        ConfigurationSection resonance = config.getConfigurationSection("resonance");
        if (resonance != null) {
            Cache.defaultResonanceDecayPerHour = resonance.getDouble(
                    "decay_per_hour", Cache.defaultResonanceDecayPerHour);
        }

        ConfigurationSection equilibrium = config.getConfigurationSection("equilibrium");
        if (equilibrium != null) {
            Cache.defaultEquilibrium = equilibrium.getDouble("default", Cache.defaultEquilibrium);
            GuiCache.equilibriumMin = equilibrium.getDouble("min", GuiCache.equilibriumMin);
            GuiCache.equilibriumMax = equilibrium.getDouble("max", GuiCache.equilibriumMax);
            Cache.equilibriumPassiveCorruptPerHour = equilibrium.getDouble(
                    "passive_corrupt_per_hour", Cache.equilibriumPassiveCorruptPerHour);
            Cache.equilibriumCorruptCompoundStrength = equilibrium.getDouble(
                    "corrupt_compound_strength", Cache.equilibriumCorruptCompoundStrength);
            Cache.equilibriumTranquilityDecayPerHour = equilibrium.getDouble(
                    "tranquility_decay_per_hour", Cache.equilibriumTranquilityDecayPerHour);
            DriftCache.surge = KeyframeCurve.fromSection(equilibrium.getConfigurationSection("surge"));
            if (DriftCache.surge.size() <= 1) {
                DriftCache.surge = KeyframeCurve.defaultSurge();
            }
            DriftCache.tranquility = KeyframeCurve.fromSection(
                    equilibrium.getConfigurationSection("tranquility"));
            if (DriftCache.tranquility.size() <= 1) {
                DriftCache.tranquility = KeyframeCurve.defaultTranquility();
            }
        } else if (config.contains("default_corruption_tranquility")) {
            Cache.defaultEquilibrium = config.getDouble(
                    "default_corruption_tranquility", Cache.defaultEquilibrium);
        }

        long intervalTicks = config.getLong("tick.interval_ticks", Cache.tickIntervalTicks);
        Cache.tickIntervalTicks = intervalTicks > 0 ? intervalTicks : Cache.tickIntervalTicks;
        double secondsPerHour = config.getDouble("tick.seconds_per_hour", Cache.secondsPerHour);
        Cache.secondsPerHour = secondsPerHour > 0 ? secondsPerHour : 3600.0;

        ConfigurationSection castDrift = config.getConfigurationSection("cast_drift");
        if (castDrift != null) {
            double divisor = castDrift.getDouble("mana_divisor", Cache.castDriftManaDivisor);
            Cache.castDriftManaDivisor = divisor > 0 ? divisor : Cache.castDriftManaDivisor;
            double min = castDrift.getDouble("min", Cache.castDriftMin);
            Cache.castDriftMin = min > 0 ? min : Cache.castDriftMin;
        }

        ConfigurationSection cast = config.getConfigurationSection("cast");
        if (cast != null) {
            long seconds = cast.getLong("refuse_chat_seconds", 30L);
            Cache.refuseChatMillis = Math.max(0L, seconds) * 1000L;
        }

        ConfigurationSection artifacts = config.getConfigurationSection("artifacts");
        if (artifacts != null) {
            Cache.artifactGlint = artifacts.getBoolean("glint", Cache.artifactGlint);
            double auraCap = artifacts.getDouble("aura_cap", Cache.artifactAuraCap);
            if (auraCap > 0) {
                Cache.artifactAuraCap = auraCap;
            }
        } else {
            Cache.artifactGlint = config.getBoolean("artifacts.glint", Cache.artifactGlint);
            double auraCap = config.getDouble("artifacts.aura_cap", Cache.artifactAuraCap);
            if (auraCap > 0) {
                Cache.artifactAuraCap = auraCap;
            }
        }

        loadMeditation(config.getConfigurationSection("meditation"));
        loadAttunement(config.getConfigurationSection("attunement"));
        loadGear(config.getConfigurationSection("gear"));
        return true;
    }

    private static void loadGear(ConfigurationSection section) {
        if (section == null) {
            return;
        }
        String station = section.getString("station", net.tfminecraft.magic.gear.GearCache.station);
        if (station != null && !station.isBlank()) {
            net.tfminecraft.magic.gear.GearCache.station = station.trim();
        }
        int slot = section.getInt("output-slot", net.tfminecraft.magic.gear.GearCache.outputSlot);
        if (slot >= 0 && slot < 27) {
            net.tfminecraft.magic.gear.GearCache.outputSlot = slot;
        }
        long confirm = section.getLong("confirm_seconds", 5L);
        net.tfminecraft.magic.gear.GearCache.confirmMillis = Math.max(1L, confirm) * 1000L;
        loadAlignment(section.getConfigurationSection("alignment"));
    }

    private static void loadAlignment(ConfigurationSection section) {
        net.tfminecraft.magic.gear.GearCache.clearAlignment();
        if (section == null) {
            net.tfminecraft.magic.gear.GearCache.alignmentEnabled = false;
            return;
        }
        net.tfminecraft.magic.gear.GearCache.alignmentEnabled = section.getBoolean("enabled", false);
        for (String key : section.getKeys(false)) {
            if ("enabled".equalsIgnoreCase(key)) {
                continue;
            }
            ConfigurationSection row = section.getConfigurationSection(key);
            if (row == null) {
                continue;
            }
            try {
                net.tfminecraft.magic.gear.GearCache.putAlignment(
                        Integer.parseInt(key.trim()), ModifierTriple.from(row));
            } catch (NumberFormatException ex) {
                Magic.plugin.getLogger().warning("[Magic] gear.alignment band '" + key + "' is not a number");
            }
        }
    }

    private static void loadAttunement(ConfigurationSection section) {
        if (section == null) {
            ArtifactCareCache.muffledEnabled = false;
            return;
        }
        String display = section.getString("display_furniture", ArtifactCareCache.displayFurnitureId);
        if (display != null && !display.isBlank()) {
            ArtifactCareCache.displayFurnitureId = display.trim();
        }
        loadUsers(section.getConfigurationSection("users"));
        loadMuffled(section.getConfigurationSection("muffled"));
    }

    private static void loadUsers(ConfigurationSection section) {
        if (section == null) {
            return;
        }
        int days = section.getInt("ttl_days", ArtifactCareCache.usersTtlDays);
        ArtifactCareCache.usersTtlDays = Math.max(1, days);
    }

    private static void loadMuffled(ConfigurationSection section) {
        ArtifactCareCache.muffledEnabled = section != null;
        if (section == null) {
            return;
        }
        if (section.contains("off_per_hour")) {
            ArtifactCareCache.muffledRecoverPerHour = Math.max(0.0, section.getDouble(
                    "recover_per_hour", ArtifactCareCache.DEFAULT_RECOVER_PER_HOUR));
            ArtifactCareCache.muffledOffPerHour = Math.max(0.0, section.getDouble(
                    "off_per_hour", ArtifactCareCache.DEFAULT_OFF_PER_HOUR));
            return;
        }
        ArtifactCareCache.muffledRecoverPerHour = ArtifactCareCache.DEFAULT_RECOVER_PER_HOUR;
        ArtifactCareCache.muffledOffPerHour = ArtifactCareCache.DEFAULT_OFF_PER_HOUR;
    }

    private static void loadMeditation(ConfigurationSection section) {
        if (section == null) {
            return;
        }
        MeditationCache.pedestalId = section.getString("pedestal", MeditationCache.pedestalId);
        MeditationCache.pedestalSlot = section.getString("pedestal_slot", MeditationCache.pedestalSlot);
        MeditationCache.cardinalOffset = section.getInt("cardinal_offset", MeditationCache.cardinalOffset);
        MeditationCache.diagonalOffset = section.getInt("diagonal_offset", MeditationCache.diagonalOffset);
        MeditationCache.startOrbRange = section.getDouble("start_orb_range", MeditationCache.startOrbRange);
        MeditationCache.orbHitRadius = section.getDouble("orb_hit_radius", MeditationCache.orbHitRadius);
        MeditationCache.orbClickRange = section.getDouble("orb_click_range", MeditationCache.orbClickRange);
        MeditationCache.mentalCostPerHit = section.getInt("mental_cost_per_hit", MeditationCache.mentalCostPerHit);
        MeditationCache.surgeLockSeconds = section.getInt("surge_lock_seconds", MeditationCache.surgeLockSeconds);
        MeditationCache.maxLiveOrbs = section.getInt("max_live_orbs", MeditationCache.maxLiveOrbs);
        MeditationCache.spawnIntervalTicks = section.getLong("spawn_interval_ticks", MeditationCache.spawnIntervalTicks);
        MeditationCache.orbitPeriodTicks = section.getLong("orbit_period_ticks", MeditationCache.orbitPeriodTicks);
        MeditationCache.flowEquilibriumMin = section.getDouble("flow_equilibrium_min", MeditationCache.flowEquilibriumMin);
        MeditationCache.flowEquilibriumMax = section.getDouble("flow_equilibrium_max", MeditationCache.flowEquilibriumMax);
        MeditationCache.surgeEquilibriumMin = section.getDouble("surge_equilibrium_min", MeditationCache.surgeEquilibriumMin);
        MeditationCache.surgeEquilibriumMax = section.getDouble("surge_equilibrium_max", MeditationCache.surgeEquilibriumMax);
        MeditationCache.resonancePerHit = section.getDouble("resonance_per_hit", MeditationCache.resonancePerHit);
        MeditationCache.orbitRadius = section.getDouble("orbit_radius", MeditationCache.orbitRadius);
    }

}
