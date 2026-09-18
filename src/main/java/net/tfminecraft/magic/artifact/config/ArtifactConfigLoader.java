package net.tfminecraft.magic.artifact.config;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;

import net.tfminecraft.magic.Cache;
import net.tfminecraft.magic.Magic;
import net.tfminecraft.magic.artifact.sacrifice.SacrificeConfigLoader;
import net.tfminecraft.magic.artifact.sacrifice.SacrificeRegistry;
import net.tfminecraft.magic.artifact.shrine.ShrineConfigLoader;
import net.tfminecraft.magic.artifact.shrine.ShrineRegistry;
import net.tfminecraft.magic.registry.ElementRegistry;

public final class ArtifactConfigLoader {

    private ArtifactConfigLoader() {}

    public static boolean loadFolder(File folder) {
        clearAll();
        Logger log = Magic.plugin.getLogger();
        if (folder == null || !folder.exists() || !folder.isDirectory()) {
            log.warning("[Magic] Artifacts folder missing: " + (folder == null ? "?" : folder.getPath()));
            return false;
        }

        boolean ok = true;
        ok &= loadModelSchemes(new File(folder, "model-schemes.yml"));
        ok &= loadNamingSchemes(new File(folder, "naming-schemes.yml"));
        ok &= loadGenerator(new File(folder, "generator.yml"));
        ok &= ArtifactAdjectiveLoader.load(new File(folder, "adjectives.yml"));
        ok &= ShrineConfigLoader.load(new File(folder, "shrines.yml"));
        ok &= SacrificeConfigLoader.load(new File(folder, "sacrifice.yml"));

        StringBuilder groupSizes = new StringBuilder();
        for (Map.Entry<String, List<String>> entry : ArtifactAffinityRegistry.getGroups().entrySet()) {
            if (groupSizes.length() > 0) {
                groupSizes.append(", ");
            }
            groupSizes.append(entry.getKey()).append("=").append(entry.getValue().size());
        }
        StringBuilder rarityWeights = new StringBuilder();
        for (ArtifactRarityDef rarity : ArtifactRarityRegistry.getAll()) {
            if (rarityWeights.length() > 0) {
                rarityWeights.append(", ");
            }
            rarityWeights.append(rarity.getId()).append("=").append(rarity.getWeight());
        }
        log.info("[Magic] Artifact generator: rarities=" + ArtifactRarityRegistry.size()
                + " [" + rarityWeights + "], types=" + ArtifactTypeRegistry.size()
                + ", groups=" + ArtifactAffinityRegistry.size() + " [" + groupSizes + "]"
                + ", exclude=" + ArtifactAffinityRegistry.excludeSize()
                + ", random_pool=" + ArtifactGeneratorCache.randomPool
                + ", model-schemes=" + ArtifactModelSchemeRegistry.size()
                + ", naming-schemes=" + ArtifactNamingSchemeRegistry.size()
                + ", shrines=" + ShrineRegistry.size()
                + ", sacrifice=" + SacrificeRegistry.size());
        if (Cache.debug) {
            log.info("[Magic] Debug: artifact types=" + ArtifactTypeRegistry.getAllIds());
            for (ArtifactTypeDef type : ArtifactTypeRegistry.getAll()) {
                log.info("[Magic] Debug: companions(" + type.getElementId() + ")="
                        + ArtifactAffinityRegistry.companions(type.getElementId()));
            }
        }
        return ok;
    }

    private static void clearAll() {
        ArtifactRarityRegistry.clear();
        ArtifactTypeRegistry.clear();
        ArtifactModelSchemeRegistry.clear();
        ArtifactNamingSchemeRegistry.clear();
        ArtifactAffinityRegistry.clear();
        ArtifactAdjectiveRegistry.clear();
        ArtifactGeneratorCache.reset();
        ShrineRegistry.clear();
        SacrificeRegistry.clear();
    }

    private static boolean loadModelSchemes(File file) {
        YamlConfiguration config = loadYaml(file);
        if (config == null) {
            return false;
        }
        Logger log = Magic.plugin.getLogger();
        boolean ok = true;
        for (String key : config.getKeys(false)) {
            if (key.isBlank() || ArtifactModelSchemeRegistry.contains(key)) {
                log.severe("[Magic] Duplicate or empty model-scheme id '" + key + "' in "
                        + file.getName());
                ok = false;
                continue;
            }
            ConfigurationSection section = config.getConfigurationSection(key);
            if (section == null) {
                log.severe("[Magic] Model-scheme '" + key + "' has no data in " + file.getName());
                ok = false;
                continue;
            }
            List<ArtifactModelEntry> models = new ArrayList<>();
            for (String line : section.getStringList("models")) {
                ArtifactModelEntry entry = ArtifactModelEntry.parse(line);
                if (entry == null) {
                    log.severe("[Magic] Invalid model '" + line + "' in scheme '" + key + "'");
                    ok = false;
                    continue;
                }
                models.add(entry);
            }
            if (models.isEmpty()) {
                log.severe("[Magic] Model-scheme '" + key + "' has no models");
                ok = false;
                continue;
            }
            ArtifactModelSchemeRegistry.register(new ArtifactModelScheme(key, models));
        }
        return ok;
    }

    private static boolean loadNamingSchemes(File file) {
        YamlConfiguration config = loadYaml(file);
        if (config == null) {
            return false;
        }
        Logger log = Magic.plugin.getLogger();
        boolean ok = true;
        for (String key : config.getKeys(false)) {
            if (key.isBlank() || ArtifactNamingSchemeRegistry.contains(key)) {
                log.severe("[Magic] Duplicate or empty naming-scheme id '" + key + "' in "
                        + file.getName());
                ok = false;
                continue;
            }
            ConfigurationSection section = config.getConfigurationSection(key);
            if (section == null) {
                log.severe("[Magic] Naming-scheme '" + key + "' has no data in " + file.getName());
                ok = false;
                continue;
            }
            Map<String, List<String>> byKind = new LinkedHashMap<>();
            for (String kind : section.getKeys(false)) {
                List<String> names = new ArrayList<>();
                for (String name : section.getStringList(kind)) {
                    if (name != null && !name.isBlank()) {
                        names.add(name.trim());
                    }
                }
                if (!names.isEmpty()) {
                    byKind.put(kind, names);
                }
            }
            ArtifactNamingScheme scheme = new ArtifactNamingScheme(key, byKind);
            if (scheme.isEmpty()) {
                log.severe("[Magic] Naming-scheme '" + key + "' has no names");
                ok = false;
                continue;
            }
            ArtifactNamingSchemeRegistry.register(scheme);
        }
        return ok;
    }

    private static boolean loadGenerator(File file) {
        YamlConfiguration config = loadYaml(file);
        if (config == null) {
            return false;
        }
        Logger log = Magic.plugin.getLogger();
        boolean ok = true;

        String template = config.getString("template", "");
        if (template == null || template.isBlank()) {
            log.severe("[Magic] artifacts/generator.yml needs a non-empty template");
            ok = false;
        } else {
            ArtifactGeneratorCache.template = template.trim();
        }

        ConfigurationSection rarities = config.getConfigurationSection("rarities");
        if (rarities == null) {
            log.severe("[Magic] artifacts/generator.yml missing rarities");
            ok = false;
        } else {
            ok &= loadRarities(rarities, file.getName());
        }

        ConfigurationSection groups = config.getConfigurationSection("affinity.groups");
        if (groups != null) {
            ok &= loadGroups(groups, file.getName());
        }
        ok &= loadExcludes(config.getMapList("affinity.exclude"), file.getName());

        ConfigurationSection types = config.getConfigurationSection("types");
        if (types == null) {
            log.severe("[Magic] artifacts/generator.yml missing types");
            ok = false;
        } else {
            ok &= loadTypes(types, file.getName());
        }
        ok &= loadRandomPool(config, file.getName());
        return ok;
    }

    private static boolean loadRandomPool(YamlConfiguration config, String fileName) {
        Logger log = Magic.plugin.getLogger();
        List<String> tokens = new ArrayList<>();
        if (config.isList("random_pool")) {
            tokens.addAll(config.getStringList("random_pool"));
        } else {
            String one = config.getString("random_pool", "");
            if (one != null && !one.isBlank()) {
                tokens.add(one.trim());
            }
        }
        LinkedHashSet<String> ids = new LinkedHashSet<>();
        boolean ok = true;
        for (String token : tokens) {
            if (token == null || token.isBlank()) {
                continue;
            }
            String key = token.trim();
            List<String> group = ArtifactAffinityRegistry.getGroup(key);
            if (!group.isEmpty()) {
                ids.addAll(group);
                continue;
            }
            if (ArtifactTypeRegistry.contains(key)) {
                ids.add(key);
                continue;
            }
            ArtifactTypeDef match = null;
            for (ArtifactTypeDef type : ArtifactTypeRegistry.getAll()) {
                if (type.getElementId().equalsIgnoreCase(key)) {
                    match = type;
                    break;
                }
            }
            if (match != null) {
                ids.add(match.getElementId());
                continue;
            }
            log.severe("[Magic] random_pool entry '" + key
                    + "' is not an affinity group or artifact type in " + fileName);
            ok = false;
        }
        if (tokens.isEmpty()) {
            List<String> planar = ArtifactAffinityRegistry.getGroup("planar_four");
            if (!planar.isEmpty()) {
                ids.addAll(planar);
            }
        }
        ArtifactGeneratorCache.setRandomPool(new ArrayList<>(ids));
        return ok;
    }

    private static boolean loadRarities(ConfigurationSection rarities, String fileName) {
        Logger log = Magic.plugin.getLogger();
        boolean ok = true;
        for (String key : rarities.getKeys(false)) {
            if (key.isBlank() || ArtifactRarityRegistry.contains(key)) {
                log.severe("[Magic] Duplicate or empty rarity id '" + key + "' in " + fileName);
                ok = false;
                continue;
            }
            ConfigurationSection section = rarities.getConfigurationSection(key);
            if (section == null) {
                log.severe("[Magic] Rarity '" + key + "' has no data in " + fileName);
                ok = false;
                continue;
            }
            CapRange elements = CapRange.parse(section.getString("elements", ""));
            if (elements == null || elements.getMin() > elements.getMax()
                    || elements.getMin() < 1) {
                log.severe("[Magic] Rarity '" + key + "' has invalid elements range in " + fileName);
                ok = false;
                continue;
            }
            CapRange auraCap = CapRange.parse(section.getString("aura_cap", ""));
            if (auraCap == null || auraCap.getMin() > auraCap.getMax() || auraCap.getMax() <= 0
                    || auraCap.getMin() < 0) {
                log.severe("[Magic] Rarity '" + key + "' has missing or invalid aura_cap in " + fileName);
                ok = false;
                continue;
            }
            double ceiling = Cache.artifactAuraCap > 0 ? Cache.artifactAuraCap : auraCap.getMax();
            double capMin = auraCap.getMin();
            double capMax = auraCap.getMax();
            if (capMax > ceiling) {
                capMax = ceiling;
            }
            if (capMin > capMax) {
                log.severe("[Magic] Rarity '" + key + "' aura_cap min exceeds artifacts.aura_cap in " + fileName);
                ok = false;
                continue;
            }
            ArtifactRarityRegistry.register(new ArtifactRarityDef(
                    key,
                    section.getString("name", key),
                    section.getString("color", "#ffffff"),
                    section.getDouble("weight", 0.0),
                    (int) Math.round(elements.getMin()),
                    (int) Math.round(elements.getMax()),
                    new CapRange(capMin, capMax)));
        }
        return ok;
    }

    private static boolean loadGroups(ConfigurationSection groups, String fileName) {
        Logger log = Magic.plugin.getLogger();
        boolean ok = true;
        for (String groupId : groups.getKeys(false)) {
            if (groupId.isBlank() || ArtifactAffinityRegistry.getGroups().containsKey(groupId)) {
                log.severe("[Magic] Duplicate or empty affinity group '" + groupId + "' in " + fileName);
                ok = false;
                continue;
            }
            List<String> members = new ArrayList<>();
            for (String member : groups.getStringList(groupId)) {
                if (member == null || member.isBlank()) {
                    continue;
                }
                String id = member.trim();
                if (!ElementRegistry.contains(id)) {
                    log.warning("[Magic] Affinity group '" + groupId + "' member '" + id
                            + "' is not a loaded element");
                }
                members.add(id);
            }
            ArtifactAffinityRegistry.registerGroup(groupId, members);
        }
        return ok;
    }

    private static boolean loadExcludes(List<Map<?, ?>> rules, String fileName) {
        Logger log = Magic.plugin.getLogger();
        if (rules == null || rules.isEmpty()) {
            return true;
        }
        boolean ok = true;
        for (Map<?, ?> rule : rules) {
            if (rule == null) {
                continue;
            }
            List<String> never = stringList(rule.get("never"));
            List<String> with = stringList(rule.get("with"));
            if (never.isEmpty() || with.isEmpty()) {
                log.severe("[Magic] affinity.exclude entry needs never: and with: lists in " + fileName);
                ok = false;
                continue;
            }
            for (String id : never) {
                if (!ElementRegistry.contains(id)) {
                    log.warning("[Magic] affinity.exclude never member '" + id
                            + "' is not a loaded element");
                }
            }
            for (String id : with) {
                if (!ElementRegistry.contains(id)) {
                    log.warning("[Magic] affinity.exclude with member '" + id
                            + "' is not a loaded element");
                }
            }
            ArtifactAffinityRegistry.registerExclude(never, with);
        }
        return ok;
    }

    private static List<String> stringList(Object raw) {
        if (!(raw instanceof List<?> list)) {
            return List.of();
        }
        List<String> out = new ArrayList<>();
        for (Object value : list) {
            if (value == null) {
                continue;
            }
            String id = value.toString().trim();
            if (!id.isBlank()) {
                out.add(id);
            }
        }
        return out;
    }

    private static boolean loadTypes(ConfigurationSection types, String fileName) {
        Logger log = Magic.plugin.getLogger();
        boolean ok = true;
        for (String key : types.getKeys(false)) {
            if (key.isBlank() || ArtifactTypeRegistry.contains(key)) {
                log.severe("[Magic] Duplicate or empty artifact type '" + key + "' in " + fileName);
                ok = false;
                continue;
            }
            ConfigurationSection section = types.getConfigurationSection(key);
            if (section == null) {
                log.severe("[Magic] Artifact type '" + key + "' has no data in " + fileName);
                ok = false;
                continue;
            }
            if (!ElementRegistry.contains(key)) {
                log.warning("[Magic] Artifact type '" + key + "' is not a loaded element");
            }
            String namingId = section.getString("naming-scheme", "");
            String modelId = section.getString("model-scheme", "");
            if (namingId == null || namingId.isBlank() || !ArtifactNamingSchemeRegistry.contains(namingId)) {
                log.severe("[Magic] Artifact type '" + key + "' has missing naming-scheme '"
                        + namingId + "'");
                ok = false;
                continue;
            }
            if (modelId == null || modelId.isBlank() || !ArtifactModelSchemeRegistry.contains(modelId)) {
                log.severe("[Magic] Artifact type '" + key + "' has missing model-scheme '"
                        + modelId + "'");
                ok = false;
                continue;
            }
            Map<String, CapRange> primary = parseCapMap(section.getConfigurationSection("aura_cap"), fileName, key, "aura_cap");
            Map<String, CapRange> secondary = parseCapMap(section.getConfigurationSection("secondary_cap"), fileName, key, "secondary_cap");
            if (primary == null || secondary == null) {
                ok = false;
                continue;
            }
            Map<String, CapRange> listed = new LinkedHashMap<>();
            ConfigurationSection raritySec = section.getConfigurationSection("rarities");
            if (raritySec != null) {
                for (String rarityId : raritySec.getKeys(false)) {
                    if (rarityId == null || rarityId.isBlank()) {
                        continue;
                    }
                    if (!ArtifactRarityRegistry.contains(rarityId)) {
                        log.warning("[Magic] Artifact type '" + key + "' rarity '" + rarityId
                                + "' is not a loaded rarity");
                    }
                    CapRange aura = lookupIgnoreCase(primary, rarityId);
                    CapRange sec = lookupIgnoreCase(secondary, rarityId);
                    if (aura == null || sec == null) {
                        log.severe("[Magic] Artifact type '" + key + "' rarity '" + rarityId
                                + "' needs aura_cap and secondary_cap in " + fileName);
                        ok = false;
                        listed.clear();
                        break;
                    }
                    listed.put(rarityId, aura);
                }
            }
            if (listed.isEmpty()) {
                log.severe("[Magic] Artifact type '" + key + "' has no valid rarities in " + fileName);
                ok = false;
                continue;
            }
            boolean enabled = !section.getBoolean("disable", false);
            double weight = section.getDouble("weight", 0.0);
            if (weight <= 0) {
                log.severe("[Magic] Artifact type '" + key + "' has missing or invalid weight in " + fileName);
                ok = false;
                continue;
            }
            Map<String, CapRange> secondaryListed = new LinkedHashMap<>();
            for (String rarityId : listed.keySet()) {
                secondaryListed.put(rarityId, lookupIgnoreCase(secondary, rarityId));
            }
            ArtifactTypeRegistry.register(new ArtifactTypeDef(
                    key, namingId, modelId, enabled, weight, listed, secondaryListed));
        }
        return ok;
    }

    private static Map<String, CapRange> parseCapMap(
            ConfigurationSection section,
            String fileName,
            String typeId,
            String field) {
        Logger log = Magic.plugin.getLogger();
        if (section == null) {
            log.severe("[Magic] Artifact type '" + typeId + "' missing " + field + " in " + fileName);
            return null;
        }
        Map<String, CapRange> out = new LinkedHashMap<>();
        boolean ok = true;
        for (String rarityId : section.getKeys(false)) {
            if (rarityId == null || rarityId.isBlank()) {
                continue;
            }
            CapRange parsed = CapRange.parse(section.getString(rarityId, ""));
            if (parsed == null || parsed.getMin() > parsed.getMax() || parsed.getMax() <= 0
                    || parsed.getMin() < 0) {
                log.severe("[Magic] Artifact type '" + typeId + "' has invalid " + field
                        + " for '" + rarityId + "' in " + fileName);
                ok = false;
                continue;
            }
            double ceiling = Cache.artifactAuraCap > 0 ? Cache.artifactAuraCap : parsed.getMax();
            double min = parsed.getMin();
            double max = parsed.getMax();
            if (max > ceiling) {
                max = ceiling;
            }
            if (min > max) {
                log.severe("[Magic] Artifact type '" + typeId + "' " + field + " min exceeds ceiling for '"
                        + rarityId + "' in " + fileName);
                ok = false;
                continue;
            }
            out.put(rarityId, new CapRange(min, max));
        }
        return ok ? out : null;
    }

    private static CapRange lookupIgnoreCase(Map<String, CapRange> map, String rarityId) {
        if (map == null || rarityId == null) {
            return null;
        }
        CapRange exact = map.get(rarityId);
        if (exact != null) {
            return exact;
        }
        for (Map.Entry<String, CapRange> entry : map.entrySet()) {
            if (entry.getKey() != null && entry.getKey().equalsIgnoreCase(rarityId)) {
                return entry.getValue();
            }
        }
        return null;
    }

    private static YamlConfiguration loadYaml(File file) {
        if (file == null || !file.isFile()) {
            Magic.plugin.getLogger().severe("[Magic] Missing artifact config: "
                    + (file == null ? "?" : file.getPath()));
            return null;
        }
        YamlConfiguration config = new YamlConfiguration();
        try {
            config.load(file);
            return config;
        } catch (IOException | InvalidConfigurationException ex) {
            Magic.plugin.getLogger().severe("[Magic] Failed to load " + file.getName() + ": "
                    + ex.getMessage());
            return null;
        }
    }
}
