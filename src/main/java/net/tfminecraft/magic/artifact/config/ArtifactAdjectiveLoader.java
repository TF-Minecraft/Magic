package net.tfminecraft.magic.artifact.config;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;

import net.tfminecraft.magic.Magic;

public final class ArtifactAdjectiveLoader {

    private ArtifactAdjectiveLoader() {}

    public static boolean load(File file) {
        if (file == null || !file.isFile()) {
            Magic.plugin.getLogger().warning("[Magic] artifacts/adjectives.yml missing; names will have no rarity adjectives");
            return true;
        }
        YamlConfiguration config = new YamlConfiguration();
        try {
            config.load(file);
        } catch (IOException | InvalidConfigurationException ex) {
            Magic.plugin.getLogger().severe("[Magic] Failed to load adjectives.yml: " + ex.getMessage());
            return false;
        }
        ConfigurationSection chance = config.getConfigurationSection("chance");
        if (chance != null) {
            for (String rarityId : chance.getKeys(false)) {
                ArtifactAdjectiveRegistry.setChance(rarityId, chance.getDouble(rarityId, 0.0));
            }
        }
        loadRarityLists(config.getConfigurationSection("global"), null);
        ConfigurationSection byElement = config.getConfigurationSection("by_element");
        if (byElement != null) {
            for (String elementId : byElement.getKeys(false)) {
                loadRarityLists(byElement.getConfigurationSection(elementId), elementId);
            }
        }
        return true;
    }

    private static void loadRarityLists(ConfigurationSection section, String elementId) {
        if (section == null) {
            return;
        }
        for (String rarityId : section.getKeys(false)) {
            List<String> words = new ArrayList<>();
            for (String word : section.getStringList(rarityId)) {
                if (word != null && !word.isBlank()) {
                    words.add(word.trim());
                }
            }
            if (elementId == null) {
                ArtifactAdjectiveRegistry.setGlobal(rarityId, words);
            } else {
                ArtifactAdjectiveRegistry.setElement(elementId.toLowerCase(Locale.ROOT), rarityId, words);
            }
        }
    }
}
