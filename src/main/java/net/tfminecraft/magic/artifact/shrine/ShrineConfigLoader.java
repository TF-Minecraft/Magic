package net.tfminecraft.magic.artifact.shrine;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.logging.Logger;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.Tag;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;

import net.tfminecraft.magic.Cache;
import net.tfminecraft.magic.Magic;
import net.tfminecraft.magic.registry.ElementRegistry;

public final class ShrineConfigLoader {

    private ShrineConfigLoader() {}

    public static boolean load(File file) {
        Logger log = Magic.plugin.getLogger();
        if (file == null || !file.isFile()) {
            log.warning("[Magic] artifacts/shrines.yml missing; shrine scoring disabled");
            return true;
        }
        YamlConfiguration config = new YamlConfiguration();
        try {
            config.load(file);
        } catch (IOException | InvalidConfigurationException ex) {
            log.severe("[Magic] Failed to load shrines.yml: " + ex.getMessage());
            return true;
        }

        ShrineRegistry.setScan(
                config.getInt("radius", 3),
                config.getDouble("min_score", 0.15),
                config.getDouble("full_charge_seconds", 15),
                config.getInt("min_families", 2));
        loadFx(config.getConfigurationSection("fx"), log);

        ConfigurationSection elements = config.getConfigurationSection("elements");
        if (elements == null) {
            log.warning("[Magic] shrines.yml has no elements");
            return true;
        }
        for (String elementId : elements.getKeys(false)) {
            if (!ElementRegistry.contains(elementId)) {
                log.severe("[Magic] Shrine element '" + elementId + "' is not a loaded element");
                continue;
            }
            ConfigurationSection elementSec = elements.getConfigurationSection(elementId);
            if (elementSec == null) {
                continue;
            }
            ConfigurationSection familiesSec = elementSec.getConfigurationSection("families");
            if (familiesSec == null) {
                log.warning("[Magic] Shrine element '" + elementId + "' has no families");
                continue;
            }
            List<ShrineFamily> families = new ArrayList<>();
            for (String familyId : familiesSec.getKeys(false)) {
                ConfigurationSection familySec = familiesSec.getConfigurationSection(familyId);
                if (familySec == null || familyId.isBlank()) {
                    continue;
                }
                Set<Material> materials = new LinkedHashSet<>();
                for (String tagKey : familySec.getStringList("tags")) {
                    addTag(materials, tagKey, elementId, familyId, log);
                }
                for (String blockName : familySec.getStringList("blocks")) {
                    addBlock(materials, blockName, elementId, familyId, log);
                }
                if (materials.isEmpty()) {
                    log.warning("[Magic] Shrine family '" + elementId + "." + familyId + "' has no valid blocks");
                    continue;
                }
                families.add(new ShrineFamily(
                        familyId,
                        familySec.getInt("max_count", 8),
                        familySec.getDouble("weight", 1.0),
                        materials));
            }
            if (families.isEmpty()) {
                continue;
            }
            boolean sceneryCharge = elementSec.getBoolean("scenery_charge", true);
            ShrineRegistry.register(new ShrineElementDef(elementId, sceneryCharge, families));
        }
        log.info("[Magic] Shrines: elements=" + ShrineRegistry.size()
                + " radius=" + ShrineRegistry.getRadius()
                + " min_score=" + ShrineRegistry.getMinScore());
        if (Cache.debug) {
            for (ShrineElementDef def : ShrineRegistry.getAll()) {
                log.info("[Magic] Debug: shrine " + def.getElementId() + " families=" + def.getFamilies().size());
            }
        }
        return true;
    }

    private static void loadFx(ConfigurationSection fx, Logger log) {
        if (fx == null) {
            return;
        }
        ShrineFxDef defaults = readFx(fx.getConfigurationSection("default"), ShrineFxDef.fallback(), log);
        ShrineRegistry.setDefaultFx(defaults);
        for (String key : fx.getKeys(false)) {
            if (key == null || key.isBlank() || "default".equalsIgnoreCase(key)) {
                continue;
            }
            ConfigurationSection section = fx.getConfigurationSection(key);
            if (section == null) {
                continue;
            }
            ShrineRegistry.setElementFx(key, readFx(section, defaults, log));
        }
    }

    private static ShrineFxDef readFx(ConfigurationSection section, ShrineFxDef base, Logger log) {
        ShrineFxDef parent = base != null ? base : ShrineFxDef.fallback();
        if (section == null) {
            return parent;
        }
        return new ShrineFxDef(
                parseSound(section.getString("ambient_sound"), parent.getAmbientSound(), log),
                (float) section.getDouble("ambient_volume", parent.getAmbientVolume()),
                (float) section.getDouble("ambient_pitch", parent.getAmbientPitch()),
                section.getInt("ambient_every_ticks", parent.getAmbientEveryTicks()),
                parseSound(section.getString("start_sound"), parent.getStartSound(), log),
                (float) section.getDouble("start_volume", parent.getStartVolume()),
                (float) section.getDouble("start_pitch", parent.getStartPitch()),
                parseSound(section.getString("complete_sound"), parent.getCompleteSound(), log),
                (float) section.getDouble("complete_volume", parent.getCompleteVolume()),
                (float) section.getDouble("complete_pitch", parent.getCompletePitch()),
                parseSound(section.getString("complete_sound_2"), parent.getCompleteSound2(), log),
                (float) section.getDouble("complete_volume_2", parent.getCompleteVolume2()),
                (float) section.getDouble("complete_pitch_2", parent.getCompletePitch2()),
                parseParticle(section.getString("trail_particle"), parent.getTrailParticle(), log),
                parseParticle(section.getString("emitter_particle"), parent.getEmitterParticle(), log),
                parseParticle(section.getString("burst_particle"), parent.getBurstParticle(), log));
    }

    // Existing configuration accepts legacy enum names and aliases; registry keys are not equivalent.
    @SuppressWarnings({"deprecation", "removal"})
    private static Sound parseSound(String raw, Sound fallback, Logger log) {
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        String key = raw.trim().toUpperCase(Locale.ROOT).replace('.', '_').replace('-', '_');
        try {
            return Sound.valueOf(key);
        } catch (IllegalArgumentException ex) {
            log.warning("[Magic] Unknown shrine fx sound '" + raw + "'");
            return fallback;
        }
    }

    private static Particle parseParticle(String raw, Particle fallback, Logger log) {
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        String key = raw.trim().toUpperCase(Locale.ROOT).replace('.', '_').replace('-', '_');
        try {
            return Particle.valueOf(key);
        } catch (IllegalArgumentException ex) {
            log.warning("[Magic] Unknown shrine fx particle '" + raw + "'");
            return fallback;
        }
    }

    private static void addBlock(
            Set<Material> materials,
            String raw,
            String elementId,
            String familyId,
            Logger log) {
        if (raw == null || raw.isBlank()) {
            return;
        }
        Material material = Material.matchMaterial(raw.trim().toUpperCase(Locale.ROOT));
        if (material == null || !material.isBlock()) {
            log.warning("[Magic] Shrine " + elementId + "." + familyId + " unknown block '" + raw + "'");
            return;
        }
        materials.add(material);
    }

    private static void addTag(
            Set<Material> materials,
            String raw,
            String elementId,
            String familyId,
            Logger log) {
        if (raw == null || raw.isBlank()) {
            return;
        }
        String key = raw.trim().toLowerCase(Locale.ROOT);
        if (key.startsWith("minecraft:")) {
            key = key.substring("minecraft:".length());
        }
        Tag<Material> tag = Bukkit.getTag(Tag.REGISTRY_BLOCKS, NamespacedKey.minecraft(key), Material.class);
        if (tag == null) {
            log.warning("[Magic] Shrine " + elementId + "." + familyId + " unknown tag '" + raw + "'");
            return;
        }
        for (Material material : tag.getValues()) {
            if (material != null && material.isBlock()) {
                materials.add(material);
            }
        }
    }
}
