package net.tfminecraft.magic.integration;

import java.util.Locale;

import io.lumine.mythic.lib.skill.Skill;
import io.lumine.mythic.lib.skill.handler.SkillHandler;
import io.lumine.mythic.lib.skill.trigger.TriggerType;
import net.Indyuce.mmocore.MMOCore;
import net.Indyuce.mmocore.skill.CastableSkill;
import net.Indyuce.mmocore.skill.RegisteredSkill;
import net.tfminecraft.magic.Cache;

public final class SkillIdResolver {

    private SkillIdResolver() {}

    /**
     * True for MMOCore casts and for every trigger named in {@code runes.keybinds}.
     * Rune abilities report their keybind ({@code RIGHT_CLICK} and the rest), not {@code CAST}.
     */
    public static boolean isActiveCast(Skill cast) {
        if (cast == null) {
            return false;
        }
        TriggerType trigger = cast.getTrigger();
        if (trigger == null) {
            return false;
        }
        if (trigger == TriggerType.CAST || trigger == TriggerType.API) {
            return true;
        }
        String name = trigger.name();
        if (name != null && Cache.castTriggers.contains(name.toUpperCase(Locale.ROOT))) {
            return true;
        }
        String lower = trigger.getLowerCaseId();
        return lower != null && Cache.castTriggers.contains(lower.toUpperCase(Locale.ROOT));
    }

    /**
     * Spells come from MMOItems key combinations, whose {@code AbilityData} is a plain
     * MythicLib {@link Skill} and never an MMOCore {@link CastableSkill}. The handler id
     * is therefore the id that resolves for both, so it is tried first and the MMOCore
     * skill name only covers casts with no handler.
     */
    public static String resolveSkillId(Skill cast) {
        if (cast == null) {
            return null;
        }
        SkillHandler<?> handler = cast.getHandler();
        if (handler != null && handler.getLowerCaseId() != null && !handler.getLowerCaseId().isBlank()) {
            return handler.getLowerCaseId();
        }

        if (cast instanceof CastableSkill castable) {
            SkillHandler<?> registered = castable.getSkill().getSkill();
            if (registered != null && registered.getName() != null && !registered.getName().isBlank()) {
                return registered.getName();
            }
        }

        return null;
    }

    public static SkillHandler<?> handlerForBinding(String skillId) {
        if (skillId == null || skillId.isBlank() || MMOCore.plugin == null) {
            return null;
        }
        RegisteredSkill exact = MMOCore.plugin.skillManager.getSkill(skillId);
        if (exact != null && exact.getHandler() != null) {
            return exact.getHandler();
        }
        for (RegisteredSkill skill : MMOCore.plugin.skillManager.getAll()) {
            if (skill == null) {
                continue;
            }
            if (skill.getName() != null && skill.getName().equalsIgnoreCase(skillId)) {
                return skill.getHandler();
            }
            SkillHandler<?> handler = skill.getHandler();
            if (handler != null && skillId.equalsIgnoreCase(handler.getLowerCaseId())) {
                return handler;
            }
        }
        return null;
    }
}
