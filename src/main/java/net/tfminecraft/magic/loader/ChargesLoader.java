package net.tfminecraft.magic.loader;

import java.io.File;
import java.io.IOException;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;

import net.tfminecraft.magic.Magic;
import net.tfminecraft.magic.charge.ChargeDef;
import net.tfminecraft.magic.charge.ChargeRegistry;
import net.tfminecraft.magic.charge.TierBands;
import net.tfminecraft.magic.registry.ElementRegistry;

public final class ChargesLoader {

    public boolean loadSafe(File configFile) {
        ChargeRegistry.clear();
        TierBands.clear();
        if (configFile == null || !configFile.exists()) {
            Magic.plugin.getLogger().warning("[Magic] charges.yml missing.");
            return false;
        }
        YamlConfiguration config = new YamlConfiguration();
        try {
            config.load(configFile);
        } catch (IOException | InvalidConfigurationException ex) {
            Magic.plugin.getLogger().severe("[Magic] Failed to load charges.yml: " + ex.getMessage());
            return false;
        }
        int skipped = loadTiers(config.getConfigurationSection("tiers"));
        loadBands(config.getConfigurationSection("bands"));
        Magic.plugin.getLogger().info("[Magic] Loaded " + ChargeRegistry.size()
                + " charge tier(s), " + TierBands.size() + " band table(s)"
                + (skipped > 0 ? " (" + skipped + " skipped)" : "") + ".");
        return true;
    }

    private static int loadTiers(ConfigurationSection tiers) {
        if (tiers == null) {
            return 0;
        }
        int skipped = 0;
        for (String key : tiers.getKeys(false)) {
            ConfigurationSection section = tiers.getConfigurationSection(key);
            if (section == null) {
                skipped++;
                continue;
            }
            int tier;
            try {
                tier = Integer.parseInt(key.trim());
            } catch (NumberFormatException ex) {
                Magic.plugin.getLogger().warning("[Magic] charges.yml: tier '" + key + "' is not a number");
                skipped++;
                continue;
            }
            String itemPath = section.getString("item");
            if (itemPath == null || itemPath.isBlank()) {
                Magic.plugin.getLogger().warning("[Magic] charges.yml: tier " + tier + " has no item path");
                skipped++;
                continue;
            }
            double auraCap = section.getDouble("aura_cap", 0);
            if (auraCap <= 0) {
                Magic.plugin.getLogger().warning("[Magic] charges.yml: tier " + tier + " has no aura_cap");
                skipped++;
                continue;
            }
            ChargeRegistry.register(new ChargeDef(tier, itemPath, auraCap));
        }
        return skipped;
    }

    private static void loadBands(ConfigurationSection bands) {
        if (bands == null) {
            return;
        }
        for (String elementKey : bands.getKeys(false)) {
            ConfigurationSection table = bands.getConfigurationSection(elementKey);
            if (table == null) {
                continue;
            }
            if (!"default".equalsIgnoreCase(elementKey) && !ElementRegistry.contains(elementKey)) {
                Magic.plugin.getLogger().warning(
                        "[Magic] charges.yml: unknown element '" + elementKey + "' in bands");
                continue;
            }
            for (String bandKey : table.getKeys(false)) {
                try {
                    TierBands.register(elementKey, Integer.parseInt(bandKey.trim()), table.getDouble(bandKey));
                } catch (NumberFormatException ex) {
                    Magic.plugin.getLogger().warning(
                            "[Magic] charges.yml: band '" + bandKey + "' is not a number");
                }
            }
        }
    }
}
