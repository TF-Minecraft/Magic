package net.tfminecraft.magic.gui;

import java.util.ArrayList;
import java.util.List;

import org.bukkit.inventory.ItemStack;

import net.tfminecraft.magic.GuiCache;
import net.tfminecraft.magic.model.ElementDef;
import net.tfminecraft.magic.modifier.ModifierParameters;
import net.tfminecraft.magic.modifier.ModifierSet;
import net.tfminecraft.magic.modifier.ModifierStat;
import net.tfminecraft.magic.modifier.SpellModifiers;
import net.tfminecraft.magic.session.ResonanceSession;
import net.tfminecraft.magic.util.GuiText;
import net.tfminecraft.magic.util.MagicNumbers;
import net.tfminecraft.magic.util.MagicText;

/**
 * Spell modifier lines for the resonance menu. Every "now" figure is the same
 * resonance x balance x weapon alignment product a cast uses, and each tooltip
 * names the parts it is made of, so a player can tell their current numbers from
 * the curve peaks.
 */
public final class ModifierLore {

    private static final String MANA_FALLBACK = "#5b9bd4";
    private static final String DAMAGE_FALLBACK = "#c45c4a";
    private static final String COOLDOWN_FALLBACK = "#7dba6e";
    private static final String SUPPORT_FALLBACK = "#e0c45c";
    private static final String DURATION_FALLBACK = "#b48ad6";

    private ModifierLore() {}

    public static List<String> linesForElement(ElementDef element, ResonanceSession session, ItemStack weapon) {
        List<String> lines = new ArrayList<>();
        if (element == null) {
            return lines;
        }
        double resonance = session != null ? session.getResonance(element.getId()) : 0.0;
        double equilibrium = equilibrium(session);
        ModifierSet fromResonance = SpellModifiers.resonance(element.getId(), resonance);
        lines.add(GuiText.format(GuiCache.modifierNow.replace("{school}", MagicText.elementName(element))));
        lines.add(row("", now(element, session, weapon)));
        lines.add(GuiText.format(GuiCache.modifierMadeOf));
        lines.add(row(withValue(GuiCache.modifierSourceResonance, resonance), fromResonance));
        if (equilibrium > 0.0) {
            lines.add(row(withValue(GuiCache.modifierSourceTranquility, equilibrium), SpellModifiers.drift(equilibrium)));
        } else if (equilibrium < 0.0) {
            lines.add(row(
                    withValue(GuiCache.modifierSourceCorruption, Math.abs(equilibrium)),
                    SpellModifiers.drift(equilibrium)));
        }
        ModifierSet fromWeapon = SpellModifiers.alignment(weapon, element.getId());
        if (!fromWeapon.isZero()) {
            lines.add(row(GuiCache.modifierSourceWeapon, fromWeapon));
        }
        lines.add(GuiText.format(withValue(GuiCache.modifierAtResonance, 100.0)));
        lines.add(row("", SpellModifiers.resonanceAt100(element.getId())));
        return lines;
    }

    public static List<String> linesForSurge(ResonanceSession session, List<ElementDef> schools, ItemStack weapon) {
        double equilibrium = equilibrium(session);
        return balanceLines(
                session,
                schools,
                weapon,
                equilibrium < 0.0,
                Math.abs(equilibrium),
                GuiCache.modifierYourCorruption,
                GuiCache.modifierNoCorruption,
                GuiCache.modifierAtCorruption,
                60.0,
                SpellModifiers.surgeAt60());
    }

    public static List<String> linesForFlow(ResonanceSession session, List<ElementDef> schools, ItemStack weapon) {
        double equilibrium = equilibrium(session);
        return balanceLines(
                session,
                schools,
                weapon,
                equilibrium > 0.0,
                equilibrium,
                GuiCache.modifierYourTranquility,
                GuiCache.modifierNoTranquility,
                GuiCache.modifierAtTranquility,
                100.0,
                SpellModifiers.tranquilityAt100());
    }

    /**
     * One cast mode's side of the balance: the player's current amount (or that they
     * have none), the curve peak, and, while they are on this side, what each unlocked
     * school casts at right now.
     */
    private static List<String> balanceLines(
            ResonanceSession session,
            List<ElementDef> schools,
            ItemStack weapon,
            boolean onThisSide,
            double amount,
            String yourTemplate,
            String noneTemplate,
            String peakTemplate,
            double peak,
            ModifierSet atPeak) {
        List<String> lines = new ArrayList<>();
        if (onThisSide) {
            lines.add(GuiText.format(withValue(yourTemplate, amount)));
            lines.add(row("", SpellModifiers.drift(equilibrium(session))));
        } else {
            lines.add(GuiText.format(noneTemplate));
        }
        lines.add(GuiText.format(withValue(peakTemplate, peak)));
        lines.add(row("", atPeak));
        if (onThisSide && schools != null && !schools.isEmpty()) {
            lines.add(GuiText.format(GuiCache.modifierSchoolsNow));
            for (ElementDef school : schools) {
                lines.add(row(
                        GuiCache.modifierSchoolRow.replace("{school}", MagicText.elementName(school)),
                        now(school, session, weapon)));
            }
        }
        return lines;
    }

    /**
     * The modifiers a cast of this school gets right now, combined in the same order
     * as SpellModifierApplyService: resonance x balance, then the held weapon's alignment.
     */
    static ModifierSet now(ElementDef element, ResonanceSession session, ItemStack weapon) {
        double resonance = session != null ? session.getResonance(element.getId()) : 0.0;
        return SpellModifiers.combine(
                SpellModifiers.combine(
                        SpellModifiers.resonance(element.getId(), resonance),
                        SpellModifiers.drift(equilibrium(session))),
                SpellModifiers.alignment(weapon, element.getId()));
    }

    private static double equilibrium(ResonanceSession session) {
        return session != null ? session.getEquilibrium() : 0.0;
    }

    private static String withValue(String template, double value) {
        return template.replace("{value}", MagicNumbers.format(value));
    }

    /**
     * One line. Damage and healing share a column while they match (the default, since
     * support follows damage); duration only shows when something sets it.
     */
    private static String row(String prefix, ModifierSet raw) {
        ModifierSet modifiers = applied(raw);
        StringBuilder line = new StringBuilder(" ").append(prefix)
                .append(stat("stat_mana", MANA_FALLBACK, "Mana", modifiers.mana(), false)).append("  ")
                .append(stat("stat_cooldown", COOLDOWN_FALLBACK, "Cooldown", modifiers.cooldown(), false)).append("  ");
        if (Double.compare(modifiers.damage(), modifiers.support()) == 0) {
            line.append(stat("stat_damage", DAMAGE_FALLBACK, "Damage & Healing", modifiers.damage(), true));
        } else {
            line.append(stat("stat_damage", DAMAGE_FALLBACK, "Damage", modifiers.damage(), true)).append("  ")
                    .append(stat("stat_support", SUPPORT_FALLBACK, "Healing", modifiers.support(), true));
        }
        if (Math.abs(modifiers.duration()) >= 0.0000001) {
            line.append("  ").append(stat("stat_duration", DURATION_FALLBACK, "Duration", modifiers.duration(), true));
        }
        return GuiText.format(line.toString());
    }

    /** Zeroes any stat that modifier_parameters points at no skill parameter, so rows show what casts get. */
    static ModifierSet applied(ModifierSet modifiers) {
        return new ModifierSet(
                live(ModifierStat.MANA, modifiers),
                live(ModifierStat.DAMAGE, modifiers),
                live(ModifierStat.COOLDOWN, modifiers),
                live(ModifierStat.SUPPORT, modifiers),
                live(ModifierStat.DURATION, modifiers));
    }

    private static double live(ModifierStat stat, ModifierSet modifiers) {
        return ModifierParameters.of(stat).isEmpty() ? 0.0 : modifiers.get(stat);
    }

    private static String stat(
            String colorKey, String fallbackHex, String label, double decimal, boolean higherIsBetter) {
        String labelHex = GuiCache.color(colorKey, fallbackHex);
        return labelHex + label + " " + percentColor(decimal, higherIsBetter) + formatPercent(decimal);
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
