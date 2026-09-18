package net.tfminecraft.magic.loader;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;

import me.Plugins.TLibs.Interface.LoaderInterface;
import net.tfminecraft.magic.Magic;
import net.tfminecraft.magic.model.ElementDef;
import net.tfminecraft.magic.registry.ElementRegistry;
import net.tfminecraft.magic.util.YamlFolder;

public final class ElementsLoader implements LoaderInterface {

    @Override
    public void load(File configFile) {
        loadSafe(configFile);
    }

    public boolean loadSafe(File configFile) {
        return loadFile(configFile);
    }

    public boolean loadFolder(File folder) {
        ElementRegistry.clear();
        if (!folder.exists() || !folder.isDirectory()) {
            Magic.plugin.getLogger().warning("[Magic] Elements folder missing: " + folder.getPath());
            return false;
        }

        List<File> files = new ArrayList<>(YamlFolder.listYamlFiles(folder));
        if (files.isEmpty()) {
            Magic.plugin.getLogger().warning("[Magic] No element yaml files in " + folder.getPath());
            return false;
        }

        files.sort(Comparator.comparing(file -> file.getName().toLowerCase()));
        boolean ok = true;
        for (File file : files) {
            ok &= loadFile(file);
        }

        Magic.plugin.getLogger().info("[Magic] Loaded " + ElementRegistry.size() + " element(s): "
                + String.join(", ", ElementRegistry.getAllIds()));
        return ok;
    }

    private boolean loadFile(File configFile) {
        YamlConfiguration config = new YamlConfiguration();
        try {
            config.load(configFile);
        } catch (IOException | InvalidConfigurationException ex) {
            Magic.plugin.getLogger().severe("[Magic] Failed to load elements file: " + configFile.getName()
                    + ": " + ex.getMessage());
            return false;
        }

        boolean ok = true;
        for (String key : config.getKeys(false)) {
            if (key.isBlank()) {
                Magic.plugin.getLogger().severe("[Magic] Empty element id in " + configFile.getName());
                ok = false;
                continue;
            }
            if (ElementRegistry.contains(key)) {
                Magic.plugin.getLogger().severe("[Magic] Duplicate element id '" + key + "' in "
                        + configFile.getName());
                ok = false;
                continue;
            }

            ConfigurationSection section = config.getConfigurationSection(key);
            if (section == null) {
                Magic.plugin.getLogger().severe("[Magic] Element '" + key + "' has no data in "
                        + configFile.getName());
                ok = false;
                continue;
            }

            if (!section.contains("icon") || section.getString("icon", "").isBlank()) {
                Magic.plugin.getLogger().warning("[Magic] Element '" + key + "' missing icon in "
                        + configFile.getName() + ", using v.BARRIER");
            }

            int slot = section.getInt("slot", -1);
            if (slot < 0 || slot >= 54) {
                Magic.plugin.getLogger().severe("[Magic] Element '" + key + "' has invalid slot "
                        + slot + " in " + configFile.getName() + " (must be 0-53)");
                ok = false;
                continue;
            }
            boolean slotTaken = false;
            for (ElementDef existing : ElementRegistry.getAll()) {
                if (existing.getSlot() == slot) {
                    Magic.plugin.getLogger().severe("[Magic] Duplicate element slot " + slot
                            + " for '" + key + "' and '" + existing.getId() + "' in " + configFile.getName());
                    ok = false;
                    slotTaken = true;
                    break;
                }
            }
            if (slotTaken) {
                continue;
            }

            ElementRegistry.register(new ElementDef(key, section));
        }
        return ok;
    }
}
