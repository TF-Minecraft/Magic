package net.tfminecraft.magic.loader;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import net.tfminecraft.tlibs.interfaces.LoaderInterface;
import net.tfminecraft.magic.ArtifactCreateCache;
import net.tfminecraft.magic.GuiCache;
import net.tfminecraft.magic.Magic;
import net.tfminecraft.magic.model.CastModeDef;
import net.tfminecraft.magic.util.GridLayout;

public final class GuiLoader implements LoaderInterface {

    @Override
    public void load(File configFile) {
        if (!loadSafe(configFile)) {
            Magic.plugin.getLogger().severe("[Magic] gui.yml load failed.");
        }
    }

    public boolean loadSafe(File configFile) {
        FileConfiguration config = new YamlConfiguration();
        try {
            config.load(configFile);
        } catch (IOException | InvalidConfigurationException ex) {
            Magic.plugin.getLogger().severe("[Magic] Failed to load gui.yml: " + ex.getMessage());
            return false;
        }

        GuiCache.title = config.getString("title", GuiCache.title);

        ConfigurationSection slots = config.getConfigurationSection("slots");
        if (slots != null) {
            GuiCache.characterHeadSlot = slots.getInt("character_head", GuiCache.characterHeadSlot);
            GuiCache.castModeLeftSlot = slots.getInt("cast_mode_left", GuiCache.castModeLeftSlot);
            GuiCache.castModeRightSlot = slots.getInt("cast_mode_right", GuiCache.castModeRightSlot);
            GuiCache.elementRow = slots.getInt("element_row", GuiCache.elementRow);
            GuiCache.elementCenter = slots.getBoolean("element_center", GuiCache.elementCenter);
        }

        ConfigurationSection items = config.getConfigurationSection("items");
        if (items != null) {
            GuiCache.filler = items.getString("filler", GuiCache.filler);
            GuiCache.fillerBorder = items.getString("filler_border", GuiCache.fillerBorder);
            GuiCache.castModeSelected = items.getString("cast_mode_selected", GuiCache.castModeSelected);
            GuiCache.castModeUnselected = items.getString("cast_mode_unselected", GuiCache.castModeUnselected);
        }

        ConfigurationSection castModes = config.getConfigurationSection("cast_modes");
        if (castModes != null) {
            CastModeDef left = parseCastMode(castModes.getConfigurationSection("left"));
            if (left != null) {
                GuiCache.castModeLeft = left;
            }
            CastModeDef right = parseCastMode(castModes.getConfigurationSection("right"));
            if (right != null) {
                GuiCache.castModeRight = right;
            }
        }

        Map<String, String> colorTokens = new LinkedHashMap<>();
        ConfigurationSection colors = config.getConfigurationSection("colors");
        if (colors != null) {
            for (String key : colors.getKeys(false)) {
                String value = colors.getString(key, "");
                if (value != null && !value.isBlank()) {
                    colorTokens.put(key, value);
                }
            }
        }
        GuiCache.resetColors(colorTokens);

        ConfigurationSection resonance = config.getConfigurationSection("resonance");
        if (resonance != null) {
            GuiCache.resonanceBarSegments = Math.max(1, resonance.getInt("bar_segments", GuiCache.resonanceBarSegments));
            GuiCache.resonanceBarChar = resonance.getString("bar_char", GuiCache.resonanceBarChar);
            GuiCache.resonanceBarBracketColor = resonance.getString(
                    "bar_bracket_color", GuiCache.resonanceBarBracketColor);
            GuiCache.resonanceBarEmptyColor = resonance.getString("bar_empty_color", GuiCache.resonanceBarEmptyColor);
            GuiCache.resonanceLoreTemplate = resonance.getString("lore", GuiCache.resonanceLoreTemplate);
            GuiCache.resonanceDriftLore = resonance.getString("drift_lore", GuiCache.resonanceDriftLore);
        }

        loadEquilibriumSection(config.getConfigurationSection("equilibrium"));
        if (config.getConfigurationSection("equilibrium") == null) {
            loadEquilibriumSection(config.getConfigurationSection("corruption_tranquility"));
        }
        GuiCache.focusLore = config.getString("focus_lore", GuiCache.focusLore);
        loadModifiersSection(config.getConfigurationSection("modifiers"));

        boolean createOk = loadArtifactCreate(config.getConfigurationSection("artifact_create"));
        boolean slotsOk = GridLayout.validateSlots();
        Magic.plugin.getLogger().info("[Magic] gui.yml loaded (" + colorTokens.size() + " colors, slots head="
                + GuiCache.characterHeadSlot + " left=" + GuiCache.castModeLeftSlot + " right="
                + GuiCache.castModeRightSlot + ", create preview=" + ArtifactCreateCache.previewSlot + ")");
        return slotsOk && createOk;
    }

    private static void loadEquilibriumSection(ConfigurationSection section) {
        if (section == null) {
            return;
        }
        GuiCache.equilibriumMin = section.getDouble("min", GuiCache.equilibriumMin);
        GuiCache.equilibriumMax = section.getDouble("max", GuiCache.equilibriumMax);
        GuiCache.equilibriumBarSegments = Math.max(1, section.getInt(
                "bar_segments", GuiCache.equilibriumBarSegments));
        GuiCache.equilibriumBarChar = section.getString("bar_char", GuiCache.equilibriumBarChar);
        GuiCache.equilibriumBarBracketColor = section.getString(
                "bar_bracket_color", GuiCache.equilibriumBarBracketColor);
        GuiCache.equilibriumBarNeutralColor = section.getString(
                "bar_neutral_color", GuiCache.equilibriumBarNeutralColor);
        GuiCache.equilibriumBarCorruptionColor = section.getString(
                "bar_corruption_color", GuiCache.equilibriumBarCorruptionColor);
        GuiCache.equilibriumBarTranquilityColor = section.getString(
                "bar_tranquility_color", GuiCache.equilibriumBarTranquilityColor);
        GuiCache.equilibriumCorruptionLore = section.getString(
                "corruption_lore", GuiCache.equilibriumCorruptionLore);
        GuiCache.equilibriumTranquilityLore = section.getString(
                "tranquility_lore", GuiCache.equilibriumTranquilityLore);
        GuiCache.equilibriumNeutralLore = section.getString(
                "neutral_lore", GuiCache.equilibriumNeutralLore);
        GuiCache.equilibriumDriftLore = section.getString(
                "drift_lore", GuiCache.equilibriumDriftLore);
    }

    private static void loadModifiersSection(ConfigurationSection section) {
        if (section == null) {
            return;
        }
        GuiCache.modifierAtResonance = section.getString("at_resonance", GuiCache.modifierAtResonance);
        GuiCache.modifierAtCorruption = section.getString("at_corruption", GuiCache.modifierAtCorruption);
        GuiCache.modifierAtTranquility = section.getString("at_tranquility", GuiCache.modifierAtTranquility);
    }

    private static boolean loadArtifactCreate(ConfigurationSection section) {
        if (section != null) {
            ArtifactCreateCache.title = section.getString("title", ArtifactCreateCache.title);
            ConfigurationSection slots = section.getConfigurationSection("slots");
            if (slots != null) {
                ArtifactCreateCache.previewSlot = slots.getInt("preview", ArtifactCreateCache.previewSlot);
                ArtifactCreateCache.capMinusSlot = slots.getInt("cap_minus", ArtifactCreateCache.capMinusSlot);
                ArtifactCreateCache.capPlusSlot = slots.getInt("cap_plus", ArtifactCreateCache.capPlusSlot);
                ArtifactCreateCache.confirmSlot = slots.getInt("confirm", ArtifactCreateCache.confirmSlot);
                ArtifactCreateCache.cancelSlot = slots.getInt("cancel", ArtifactCreateCache.cancelSlot);
                List<Integer> raritySlots = slots.getIntegerList("rarities");
                if (raritySlots != null && !raritySlots.isEmpty()) {
                    ArtifactCreateCache.raritySlots = List.copyOf(raritySlots);
                }
            }
            ConfigurationSection items = section.getConfigurationSection("items");
            if (items != null) {
                CastModeDef minus = parseCastMode(items.getConfigurationSection("cap_minus"));
                if (minus != null) {
                    ArtifactCreateCache.capMinus = minus;
                }
                CastModeDef plus = parseCastMode(items.getConfigurationSection("cap_plus"));
                if (plus != null) {
                    ArtifactCreateCache.capPlus = plus;
                }
                CastModeDef confirm = parseCastMode(items.getConfigurationSection("confirm"));
                if (confirm != null) {
                    ArtifactCreateCache.confirm = confirm;
                }
                CastModeDef cancel = parseCastMode(items.getConfigurationSection("cancel"));
                if (cancel != null) {
                    ArtifactCreateCache.cancel = cancel;
                }
            }
        }
        return validateArtifactCreateSlots();
    }

    private static boolean validateArtifactCreateSlots() {
        List<Integer> slots = new ArrayList<>();
        slots.add(ArtifactCreateCache.previewSlot);
        slots.add(ArtifactCreateCache.capMinusSlot);
        slots.add(ArtifactCreateCache.capPlusSlot);
        slots.add(ArtifactCreateCache.confirmSlot);
        slots.add(ArtifactCreateCache.cancelSlot);
        slots.addAll(ArtifactCreateCache.raritySlots);
        Set<Integer> seen = new HashSet<>();
        boolean ok = true;
        for (Integer slot : slots) {
            if (slot == null || slot < 0 || slot >= GridLayout.SIZE) {
                Magic.plugin.getLogger().severe("[Magic] Invalid artifact_create slot: " + slot);
                ok = false;
                continue;
            }
            if (!seen.add(slot)) {
                Magic.plugin.getLogger().severe("[Magic] Duplicate artifact_create slot: " + slot);
                ok = false;
            }
        }
        return ok;
    }

    private static CastModeDef parseCastMode(ConfigurationSection section) {
        if (section == null) {
            return null;
        }
        return new CastModeDef(
                section.getString("id", ""),
                section.getString("icon", "v.BARRIER"),
                section.getString("name", section.getString("id", "")),
                section.getStringList("lore"));
    }
}
