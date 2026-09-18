package net.tfminecraft.magic.integration;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import io.lumine.mythic.lib.api.player.MMOPlayerData;
import io.lumine.mythic.lib.player.modifier.ModifierType;
import io.lumine.mythic.lib.player.skillmod.SkillModifier;
import io.lumine.mythic.lib.skill.handler.SkillHandler;
import net.tfminecraft.magic.Cache;
import net.tfminecraft.magic.Magic;
import net.tfminecraft.magic.gear.GearHand;
import net.tfminecraft.magic.modifier.ModifierTriple;
import net.tfminecraft.magic.modifier.SpellModifiers;
import net.tfminecraft.magic.registry.SkillElementRegistry;
import net.tfminecraft.magic.session.ResonanceSession;

public final class SpellModifierApplyService {

    private static final Map<UUID, List<SkillModifier>> applied = new ConcurrentHashMap<>();
    private static final Set<String> unknownWarned = ConcurrentHashMap.newKeySet();
    private static boolean missingLogged;

    private SpellModifierApplyService() {}

    public static boolean isAvailable() {
        return Bukkit.getPluginManager().isPluginEnabled("MythicLib")
                && Bukkit.getPluginManager().isPluginEnabled("MMOCore");
    }

    public static void sync(Player player, ResonanceSession session) {
        if (player == null) {
            return;
        }
        if (!isAvailable()) {
            logMissingOnce();
            return;
        }
        if (session == null) {
            clear(player);
            return;
        }
        MMOPlayerData data = MMOPlayerData.getOrNull(player);
        if (data == null || !data.isOnline()) {
            clear(player);
            return;
        }
        unregisterStored(player, data);
        List<SkillModifier> next = new ArrayList<>();
        double equilibrium = session.getEquilibrium();
        ItemStack weapon = GearHand.held(player);
        for (Map.Entry<String, String> binding : SkillElementRegistry.bindings().entrySet()) {
            String skillId = binding.getKey();
            SkillHandler<?> handler = SkillIdResolver.handlerForBinding(skillId);
            if (handler == null) {
                warnUnknown(skillId);
                continue;
            }
            String elementId = binding.getValue();
            ModifierTriple triple = SpellModifiers.combine(
                    SpellModifiers.combine(
                            SpellModifiers.resonance(elementId, session.getResonance(elementId)),
                            SpellModifiers.drift(equilibrium)),
                    SpellModifiers.alignment(weapon, elementId));
            if (triple.mana() != 0.0) {
                next.add(register(data, skillId, "mana", handler, triple.mana()));
            }
            if (triple.cooldown() != 0.0) {
                next.add(register(data, skillId, "cooldown", handler, triple.cooldown()));
            }
            if (triple.damage() != 0.0) {
                next.add(register(data, skillId, "damage", handler, triple.damage()));
            }
        }
        if (!next.isEmpty()) {
            applied.put(player.getUniqueId(), next);
        }
    }

    public static void clear(Player player) {
        if (player == null) {
            return;
        }
        if (!isAvailable()) {
            applied.remove(player.getUniqueId());
            return;
        }
        MMOPlayerData data = MMOPlayerData.getOrNull(player);
        unregisterStored(player, data);
    }

    public static void clearAll() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            clear(player);
        }
        applied.clear();
    }

    public static void syncOnline(net.tfminecraft.magic.session.ResonanceSessionManager sessions) {
        if (sessions == null || !isAvailable()) {
            if (!isAvailable()) {
                logMissingOnce();
            }
            return;
        }
        sessions.forEachOnlineSession(SpellModifierApplyService::sync);
    }

    private static SkillModifier register(
            MMOPlayerData data,
            String skillId,
            String parameter,
            SkillHandler<?> handler,
            double decimal) {
        SkillModifier modifier = new SkillModifier(
                "magic-" + parameter + "-" + skillId,
                parameter,
                List.of(handler),
                decimal * 100.0,
                ModifierType.RELATIVE);
        modifier.register(data);
        return modifier;
    }

    private static void unregisterStored(Player player, MMOPlayerData data) {
        List<SkillModifier> previous = applied.remove(player.getUniqueId());
        if (previous == null || data == null) {
            return;
        }
        for (SkillModifier modifier : previous) {
            modifier.unregister(data);
        }
    }

    private static void logMissingOnce() {
        if (missingLogged || Magic.plugin == null) {
            return;
        }
        missingLogged = true;
        Magic.plugin.getLogger().info("[Magic] MythicLib or MMOCore missing; skill modifiers not applied.");
    }

    private static void warnUnknown(String skillId) {
        if (!unknownWarned.add(skillId) || Magic.plugin == null) {
            return;
        }
        if (Cache.debug) {
            Magic.plugin.getLogger().warning("[Magic] skills.yml: no MMOCore skill handler for '" + skillId + "'");
        }
    }
}
