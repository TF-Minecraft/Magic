package net.tfminecraft.magic;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import net.tfminecraft.magic.model.CastModeDef;

/**
 * GUI item refs, slot map, cast modes, and colour tokens from gui.yml.
 */
public final class GuiCache {

    private GuiCache() {}

    public static String title = "{color:title}Resonance";

    public static int characterHeadSlot = 4;
    public static int castModeLeftSlot = 12;
    public static int castModeRightSlot = 14;
    public static int elementRow = 4;
    public static boolean elementCenter = true;

    public static String filler = "vanilla.PURPLE_STAINED_GLASS_PANE";
    public static String fillerBorder = "vanilla.BLACK_STAINED_GLASS_PANE";
    public static String castModeSelected = "vanilla.LIME_STAINED_GLASS_PANE";
    public static String castModeUnselected = "vanilla.GRAY_STAINED_GLASS_PANE";

    public static int resonanceBarSegments = 20;
    public static String resonanceBarChar = "|";
    public static String resonanceBarBracketColor = "§7";
    public static String resonanceBarEmptyColor = "{color:resonance_low}";
    public static String resonanceLoreTemplate = "{bar} {color:label_body}{current}/{max}";
    public static String resonanceDriftLore =
            "{color:drift_sign}{sign}{rate}{color:drift_suffix}/h";

    public static double equilibriumMin = -100.0;
    public static double equilibriumMax = 100.0;
    public static int equilibriumBarSegments = 20;
    public static String equilibriumBarChar = "|";
    public static String equilibriumBarBracketColor = "§7";
    public static String equilibriumBarNeutralColor = "§7";
    public static String equilibriumBarCorruptionColor = "{color:corruption}";
    public static String equilibriumBarTranquilityColor = "{color:tranquility}";
    public static String equilibriumCorruptionLore = "{color:corruption}Corruption {bar} {value}";
    public static String equilibriumTranquilityLore = "{color:tranquility}Tranquility {bar} {value}";
    public static String equilibriumNeutralLore = "{color:label_muted}Balance {bar} {value}";
    public static String equilibriumDriftLore =
            "{color:drift_sign}{sign}{rate}{color:drift_suffix}/h";
    public static String focusLore = "{color:label_muted}Mental points {color:label_body}{current}/{max}";

    public static String modifierAtResonance = "{color:label_muted}At {value} Resonance";
    public static String modifierAtCorruption = "{color:label_muted}At {value} Corruption";
    public static String modifierAtTranquility = "{color:label_muted}At {value} Tranquility";

    public static CastModeDef castModeLeft = new CastModeDef(
            "surge",
            "v.BLAZE_POWDER",
            "{color:mode_surge}Surge",
            java.util.List.of("{color:label_muted}Power now. Corruption later."));
    public static CastModeDef castModeRight = new CastModeDef(
            "flow",
            "v.AMETHYST_SHARD",
            "{color:mode_flow}Flow",
            java.util.List.of("{color:label_muted}Slow start. Stronger at depth."));

    public static Map<String, String> colors = Collections.emptyMap();

    public static void resetColors(Map<String, String> loaded) {
        if (loaded == null || loaded.isEmpty()) {
            colors = Collections.emptyMap();
            return;
        }
        colors = Collections.unmodifiableMap(new LinkedHashMap<>(loaded));
    }

    public static String color(String key, String fallback) {
        String value = colors.get(key);
        if (value == null || value.isBlank()) {
            return fallback;
        }
        return value;
    }
}
