package net.tfminecraft.magic.artifact.sacrifice;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;

import org.bukkit.Sound;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;

import net.tfminecraft.magic.Cache;
import net.tfminecraft.magic.Magic;
import net.tfminecraft.magic.registry.ElementRegistry;

public final class SacrificeConfigLoader {

    private SacrificeConfigLoader() {}

    public static boolean load(File file) {
        Logger log = Magic.plugin.getLogger();
        if (file == null || !file.isFile()) {
            log.warning("[Magic] artifacts/sacrifice.yml missing; sacrifice scenery gate disabled");
            return true;
        }
        YamlConfiguration config = new YamlConfiguration();
        try {
            config.load(file);
        } catch (IOException | InvalidConfigurationException ex) {
            log.severe("[Magic] Failed to load sacrifice.yml: " + ex.getMessage());
            return true;
        }

        boolean enabled = config.getBoolean("enabled", true);
        boolean sceneryCharge = config.getBoolean("scenery_charge", false);
        SacrificeRegistry.setEnabled(enabled);
        SacrificeRegistry.setSceneryCharge(sceneryCharge);
        SacrificeRegistry.setHideIncantation(config.getBoolean("hide_incantation", false));
        SacrificeRegistry.setRequireRpCharacters(config.getBoolean("require_rpcharacters", true));
        SacrificeRegistry.setRpChannel(config.getString("rp_channel", "rp"));

        String daggerPath = config.getString("dagger");
        ConfigurationSection daggerSec = config.getConfigurationSection("dagger");
        if (daggerPath == null || daggerPath.isBlank()) {
            if (daggerSec != null) {
                daggerPath = daggerSec.getString("path", "");
                if (daggerPath == null || daggerPath.isBlank()) {
                    daggerPath = legacyDaggerPath(daggerSec);
                }
            }
        }
        if (daggerPath == null || daggerPath.isBlank()) {
            daggerPath = SacrificeDaggerDef.DEFAULT_PATH;
            log.warning("[Magic] sacrifice.yml dagger is empty; using " + daggerPath);
        }

        SacrificeRegistry.setGlobals(
                config.getDouble("charge_seconds", 10.0),
                config.getDouble("victim_range", 4.0),
                SacrificeWordMatch.fromConfig(config.getString("match", "starts_with")),
                config.getBoolean("case_insensitive", true),
                config.getBoolean("require_min_score", true),
                config.getDouble("min_scenery_aura", 50.0),
                config.getDouble("hint_min_aura", 20.0),
                config.getBoolean("require_dagger_in_hand", true),
                config.getBoolean("one_rite_per_caster", true),
                config.getBoolean("refuse_new_words_while_active", true),
                config.getString("pedestal_id", "pedestal"),
                config.getString("pedestal_slot", "*"),
                new SacrificeDaggerDef(daggerPath));

        loadTiers(config.getConfigurationSection("tiers"), log);
        loadFx(config.getConfigurationSection("fx"), log);

        ConfigurationSection elements = config.getConfigurationSection("elements");
        if (elements == null) {
            log.warning("[Magic] sacrifice.yml has no elements");
        } else {
            boolean caseInsensitive = SacrificeRegistry.isCaseInsensitive();
            Map<String, String> wordOwners = new HashMap<>();
            for (String elementId : elements.getKeys(false)) {
                if (!ElementRegistry.contains(elementId)) {
                    log.warning("[Magic] Sacrifice element '" + elementId + "' is not a loaded element");
                    continue;
                }
                ConfigurationSection elementSec = elements.getConfigurationSection(elementId);
                if (elementSec == null) {
                    continue;
                }
                List<String> words = new ArrayList<>();
                for (String raw : elementSec.getStringList("words")) {
                    if (raw == null || raw.isBlank()) {
                        continue;
                    }
                    String word = raw.trim();
                    String key = caseInsensitive ? word.toLowerCase(Locale.ROOT) : word;
                    String previous = wordOwners.put(key, elementId);
                    if (previous != null && !previous.equalsIgnoreCase(elementId)) {
                        log.warning("[Magic] Sacrifice phrase '" + word + "' used by '"
                                + previous + "' and '" + elementId + "'");
                    }
                    words.add(word);
                }
                ConfigurationSection lore = elementSec.getConfigurationSection("lore");
                String lorePain = lore != null ? lore.getString("pain", "") : "";
                String loreScreams = lore != null ? lore.getString("screams", "") : "";
                String loreSoul = lore != null ? lore.getString("soul", "") : "";
                if (loreSoul == null || loreSoul.isBlank()) {
                    loreSoul = lore != null ? lore.getString("death", "") : "";
                }
                double elementMin = elementSec.contains("min_scenery_aura")
                        ? elementSec.getDouble("min_scenery_aura")
                        : -1;
                boolean elementEnabled = elementSec.getBoolean("enabled", true);
                SacrificeRegistry.register(new SacrificeElementDef(
                        elementId,
                        elementEnabled,
                        words,
                        lorePain,
                        loreScreams,
                        loreSoul,
                        elementMin));
            }
        }

        log.info("[Magic] Sacrifice: elements=" + SacrificeRegistry.size()
                + " enabled=" + SacrificeRegistry.isEnabled()
                + " scenery_charge=" + SacrificeRegistry.isSceneryCharge());
        if (Cache.debug) {
            for (SacrificeElementDef def : SacrificeRegistry.getAll()) {
                log.info("[Magic] Debug: sacrifice " + def.getElementId()
                        + " words=" + def.getWords().size());
            }
        }
        return true;
    }

    private static String legacyDaggerPath(ConfigurationSection daggerSec) {
        String ia = daggerSec.getString("itemsadder", "");
        if (ia != null && !ia.isBlank()) {
            String id = ia.trim();
            return id.regionMatches(true, 0, "ia.", 0, 3) ? id : "ia." + id;
        }
        String material = daggerSec.getString("material", "GOLDEN_SWORD");
        String type = material != null && !material.isBlank()
                ? material.trim().toLowerCase(Locale.ROOT)
                : "golden_sword";
        int cmd = daggerSec.getInt("custom_model_data", -1);
        if (cmd >= 0) {
            return "modeled.(type=" + type + ";model=" + cmd + ")";
        }
        return "v." + type;
    }

    private static void loadTiers(ConfigurationSection tiers, Logger log) {
        if (tiers == null) {
            log.warning("[Magic] sacrifice.yml has no tiers");
            return;
        }
        for (String id : tiers.getKeys(false)) {
            ConfigurationSection section = tiers.getConfigurationSection(id);
            if (section == null) {
                continue;
            }
            SacrificeRegistry.registerTier(new SacrificeTierDef(
                    id,
                    section.getDouble("min", 0.0),
                    section.getDouble("below", 0.0),
                    section.getDouble("aura_fraction", 0.0),
                    section.getString("injury", "none"),
                    section.getBoolean("permadeath", false)));
        }
    }

    private static void loadFx(ConfigurationSection fx, Logger log) {
        if (fx == null) {
            SacrificeRegistry.setFx(SacrificeFxDef.empty());
            return;
        }
        Set<String> tiers = new LinkedHashSet<>();
        for (String id : fx.getStringList("surge_tiers")) {
            if (id != null && !id.isBlank()) {
                tiers.add(id.trim().toLowerCase(Locale.ROOT));
            }
        }
        SacrificeRegistry.setFx(new SacrificeFxDef(
                tiers,
                parseSound(fx.getString("surge_sound"), log),
                (float) fx.getDouble("surge_volume", 0.85),
                (float) fx.getDouble("surge_pitch", 0.7),
                parseSound(fx.getString("boom_sound"), log),
                (float) fx.getDouble("boom_volume", 0.45),
                (float) fx.getDouble("boom_pitch", 0.85)));
    }

    // Existing configuration accepts legacy enum names and aliases; registry keys are not equivalent.
    @SuppressWarnings({"deprecation", "removal"})
    private static Sound parseSound(String raw, Logger log) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String key = raw.trim().toUpperCase(Locale.ROOT).replace('.', '_').replace('-', '_');
        try {
            return Sound.valueOf(key);
        } catch (IllegalArgumentException ex) {
            log.warning("[Magic] Unknown sacrifice fx sound '" + raw + "'");
            return null;
        }
    }
}
