package net.tfminecraft.magic.gui;

import java.util.ArrayList;
import java.util.List;

import net.tfminecraft.magic.GuiCache;
import net.tfminecraft.magic.model.ElementDef;
import net.tfminecraft.magic.modifier.ModifierTriple;
import net.tfminecraft.magic.modifier.SpellModifiers;
import net.tfminecraft.magic.session.ResonanceSession;
import net.tfminecraft.magic.util.GuiText;
import net.tfminecraft.magic.util.MagicNumbers;

public final class ModifierLore {

    private static final String MANA_FALLBACK = "#5b9bd4";
    private static final String DAMAGE_FALLBACK = "#c45c4a";
    private static final String COOLDOWN_FALLBACK = "#7dba6e";

    private ModifierLore() {}

    public static List<String> linesForElement(ElementDef element, ResonanceSession session) {
        List<String> lines = new ArrayList<>();
        if (element == null) {
            return lines;
        }
        double resonance = session != null ? session.getResonance(element.getId()) : 0.0;
        double equilibrium = session != null ? session.getEquilibrium() : 0.0;
        ModifierTriple now = SpellModifiers.combine(
                SpellModifiers.resonance(element.getId(), resonance),
                SpellModifiers.drift(equilibrium));
        addBlock(lines, axisHeader(GuiCache.modifierAtResonance, resonance), now);
        addBlock(lines, axisHeader(GuiCache.modifierAtResonance, 100.0), SpellModifiers.resonanceAt100(element.getId()));
        return lines;
    }

    public static List<String> linesForSurge(ResonanceSession session) {
        List<String> lines = new ArrayList<>();
        double equilibrium = session != null ? session.getEquilibrium() : 0.0;
        if (equilibrium < 0.0) {
            addBlock(lines, axisHeader(GuiCache.modifierAtCorruption, Math.abs(equilibrium)), SpellModifiers.drift(equilibrium));
        }
        addBlock(lines, axisHeader(GuiCache.modifierAtCorruption, 60.0), SpellModifiers.surgeAt60());
        return lines;
    }

    public static List<String> linesForFlow(ResonanceSession session) {
        List<String> lines = new ArrayList<>();
        double equilibrium = session != null ? session.getEquilibrium() : 0.0;
        if (equilibrium > 0.0) {
            addBlock(lines, axisHeader(GuiCache.modifierAtTranquility, equilibrium), SpellModifiers.drift(equilibrium));
        }
        addBlock(lines, axisHeader(GuiCache.modifierAtTranquility, 100.0), SpellModifiers.tranquilityAt100());
        return lines;
    }

    private static void addBlock(List<String> lines, String header, ModifierTriple triple) {
        if (triple == null) {
            return;
        }
        lines.add(GuiText.format(header));
        lines.add(statLine("stat_mana", MANA_FALLBACK, "Mana", triple.mana(), false));
        lines.add(statLine("stat_cooldown", COOLDOWN_FALLBACK, "Cooldown", triple.cooldown(), false));
        lines.add(statLine("stat_damage", DAMAGE_FALLBACK, "Damage", triple.damage(), true));
    }

    private static String axisHeader(String template, double value) {
        String raw = template != null ? template : "{color:label_muted}At {value}";
        return raw.replace("{value}", MagicNumbers.format(value));
    }

    private static String statLine(
            String colorKey, String fallbackHex, String label, double decimal, boolean higherIsBetter) {
        String labelHex = GuiCache.color(colorKey, fallbackHex);
        return GuiText.format(labelHex + label + ": " + percentColor(decimal, higherIsBetter) + formatPercent(decimal));
    }

    private static String percentColor(double decimal, boolean higherIsBetter) {
        if (Math.abs(decimal) < 0.0000001) {
            return "§7";
        }
        boolean good = higherIsBetter ? decimal > 0.0 : decimal < 0.0;
        return good ? "§a" : "§c";
    }

    static String formatPercent(double decimal) {
        double percent = decimal * 100.0;
        String magnitude = MagicNumbers.format(Math.abs(percent));
        if (percent > 0.0) {
            return "+" + magnitude + "%";
        }
        if (percent < 0.0) {
            return "-" + magnitude + "%";
        }
        return magnitude + "%";
    }
}
