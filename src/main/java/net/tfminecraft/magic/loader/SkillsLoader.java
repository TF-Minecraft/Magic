package net.tfminecraft.magic.loader;

import java.io.File;
import java.io.IOException;
import java.util.Locale;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;

import net.tfminecraft.magic.Magic;
import net.tfminecraft.magic.registry.ElementRegistry;
import net.tfminecraft.magic.registry.SkillElementRegistry;

public final class SkillsLoader {

    public boolean loadSafe(File configFile) {
        SkillElementRegistry.clear();
        if (configFile == null || !configFile.exists()) {
            Magic.plugin.getLogger().warning("[Magic] skills.yml missing.");
            return false;
        }
        YamlConfiguration config = new YamlConfiguration();
        try {
            config.load(configFile);
        } catch (IOException | InvalidConfigurationException ex) {
            Magic.plugin.getLogger().severe("[Magic] Failed to load skills.yml: " + ex.getMessage());
            return false;
        }
        int skipped = 0;
        for (String skillId : config.getKeys(false)) {
            if (skillId == null || skillId.isBlank()) {
                continue;
            }
            String elementId;
            if (config.isConfigurationSection(skillId)) {
                ConfigurationSection section = config.getConfigurationSection(skillId);
                if (section == null) {
                    skipped++;
                    continue;
                }
                elementId = section.getString("element");
            } else {
                elementId = config.getString(skillId);
            }
            if (elementId == null || elementId.isBlank()) {
                skipped++;
                continue;
            }
            String normalized = elementId.trim().toLowerCase(Locale.ROOT);
            if (!ElementRegistry.contains(normalized)) {
                Magic.plugin.getLogger().warning("[Magic] skills.yml: unknown element '" + elementId
                        + "' for skill '" + skillId + "'");
                skipped++;
                continue;
            }
            SkillElementRegistry.register(skillId, normalized);
        }
        Magic.plugin.getLogger().info("[Magic] Loaded " + SkillElementRegistry.size()
                + " skill binding(s)" + (skipped > 0 ? " (" + skipped + " skipped)" : "") + ".");
        return true;
    }
}
