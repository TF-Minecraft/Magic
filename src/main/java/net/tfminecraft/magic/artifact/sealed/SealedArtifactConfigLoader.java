package net.tfminecraft.magic.artifact.sealed;

import java.io.File;
import java.io.IOException;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Logger;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;

import net.tfminecraft.magic.Magic;
import net.tfminecraft.magic.artifact.config.ArtifactRarityRegistry;
import net.tfminecraft.magic.artifact.config.ArtifactTypeDef;
import net.tfminecraft.magic.artifact.config.ArtifactTypeRegistry;

/**
 * Loads artifacts/sealed.yml. Runs after generator.yml so rarities and types can be checked.
 *
 * <p>Like shrines.yml and sacrifice.yml, problems here never fail the artifact configs: a bad
 * entry or weight is skipped with a warning, and an unreadable file turns sealed artifacts off.
 */
public final class SealedArtifactConfigLoader {

    static final String RANDOM = "random";

    private final Logger log;

    private SealedArtifactConfigLoader(Logger log) {
        this.log = log;
    }

    public static boolean load(File file) {
        SealedArtifactRegistry.clear();
        Logger log = Magic.plugin.getLogger();
        if (file == null || !file.isFile()) {
            log.info("[Magic] artifacts/sealed.yml missing; sealed artifacts disabled");
            return true;
        }
        YamlConfiguration config = new YamlConfiguration();
        try {
            config.load(file);
        } catch (IOException | InvalidConfigurationException ex) {
            log.severe("[Magic] Failed to load sealed.yml, sealed artifacts disabled: " + ex.getMessage());
            return true;
        }
        new SealedArtifactConfigLoader(log).read(config);
        return true;
    }

    private void read(YamlConfiguration config) {
        SealedArtifactRegistry.setSound(config.getString("sound", ""));
        Map<String, Double> shared = readWeights(config.getConfigurationSection("rarities"), "rarities");
        ConfigurationSection sealed = config.getConfigurationSection("sealed");
        if (sealed == null) {
            return;
        }
        for (String key : sealed.getKeys(false)) {
            readEntry(key, sealed.getConfigurationSection(key), shared);
        }
    }

    private void readEntry(String id, ConfigurationSection section, Map<String, Double> shared) {
        if (section == null) {
            warn("Sealed artifact '" + id + "' has no data");
            return;
        }
        String item = section.getString("item", "").trim();
        if (item.isEmpty()) {
            warn("Sealed artifact '" + id + "' has no item");
            return;
        }
        String element = section.getString("element", RANDOM).trim().toLowerCase(Locale.ROOT);
        ArtifactTypeDef type = null;
        if (!RANDOM.equals(element)) {
            type = ArtifactTypeRegistry.getById(element);
            if (type == null || !type.isEnabled()) {
                warn("Sealed artifact '" + id + "' has unknown or disabled element '" + element + "'");
                return;
            }
        }
        Map<String, Double> weights = section.isConfigurationSection("rarities")
                ? readWeights(section.getConfigurationSection("rarities"), id + ".rarities")
                : new LinkedHashMap<>(shared);
        if (type != null) {
            Iterator<String> rarities = weights.keySet().iterator();
            while (rarities.hasNext()) {
                String rarity = rarities.next();
                if (!type.hasRarity(rarity)) {
                    warn("Sealed artifact '" + id + "': " + element + " has no " + rarity + " bracket");
                    rarities.remove();
                }
            }
        }
        if (weights.isEmpty()) {
            warn("Sealed artifact '" + id + "' has no rarity with a weight above 0");
            return;
        }
        SealedArtifactRegistry.register(new SealedArtifactDef(id, item, type == null ? null : element, weights));
    }

    private Map<String, Double> readWeights(ConfigurationSection section, String where) {
        Map<String, Double> weights = new LinkedHashMap<>();
        if (section == null) {
            return weights;
        }
        for (String key : section.getKeys(false)) {
            String rarity = key.trim().toLowerCase(Locale.ROOT);
            double weight = section.getDouble(key, -1.0);
            if (!ArtifactRarityRegistry.contains(rarity)) {
                warn("Unknown rarity '" + key + "' in sealed.yml " + where);
            } else if (weight < 0.0 || !Double.isFinite(weight)) {
                warn("Rarity '" + key + "' in sealed.yml " + where + " needs a weight of 0 or more");
            } else if (weight > 0.0) {
                weights.put(rarity, weight);
            }
        }
        return weights;
    }

    private void warn(String message) {
        log.warning("[Magic] " + message);
    }
}
