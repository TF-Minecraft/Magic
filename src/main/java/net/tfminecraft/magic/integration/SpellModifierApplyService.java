package net.tfminecraft.magic.integration;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
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
import net.tfminecraft.magic.charge.TierBands;
import net.tfminecraft.magic.gear.GearCache;
import net.tfminecraft.magic.gear.GearHand;
import net.tfminecraft.magic.gear.WeaponRequirement;
import net.tfminecraft.magic.modifier.ModifierTriple;
import net.tfminecraft.magic.modifier.SpellModifiers;
import net.tfminecraft.magic.registry.SkillElementRegistry;
import net.tfminecraft.magic.session.ResonanceSession;

public final class SpellModifierApplyService {

    private static final Map<UUID, AppliedState> applied = new ConcurrentHashMap<>();
    private static final Map<String, SkillHandler<?>> handlers = new ConcurrentHashMap<>();
    private static final Set<String> unknownWarned = ConcurrentHashMap.newKeySet();
    private static boolean missingLogged;

    private SpellModifierApplyService() {}

    public static boolean isAvailable() {
        return Bukkit.getPluginManager().isPluginEnabled("MythicLib")
                && Bukkit.getPluginManager().isPluginEnabled("MMOCore");
    }

    public static void sync(Player player, ResonanceSession session) {
        sync(player, session, false);
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
        applied.remove(player.getUniqueId());
    }

    public static void clearAll() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            clear(player);
        }
        applied.clear();
        handlers.clear();
    }

    public static void syncOnline(net.tfminecraft.magic.session.ResonanceSessionManager sessions) {
        if (sessions == null || !isAvailable()) {
            if (!isAvailable()) {
                logMissingOnce();
            }
            return;
        }
        handlers.clear();
        sessions.forEachOnlineSession((player, session) -> sync(player, session, true));
    }

    public static void syncChangedOnline(net.tfminecraft.magic.session.ResonanceSessionManager sessions) {
        if (sessions == null || !isAvailable()) {
            if (!isAvailable()) {
                logMissingOnce();
            }
            return;
        }
        sessions.forEachOnlineSession((player, session) -> {
            if (session == null) {
                sync(player, null, false);
                return;
            }
            AppliedState previous = applied.get(player.getUniqueId());
            if (previous != null && previous.revision == session.modifierRevision()) {
                return;
            }
            sync(player, session, false);
        });
    }

    private static void sync(Player player, ResonanceSession session, boolean forceApply) {
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
        DesiredSnapshot desired = plan(player, session);
        AppliedState previous = applied.get(player.getUniqueId());
        if (!forceApply && previous != null && previous.snapshot.equals(desired)) {
            previous.revision = session.modifierRevision();
            return;
        }
        unregisterStored(player, data);
        List<SkillModifier> next = new ArrayList<>();
        for (SkillPlan plan : desired.skills) {
            if (plan.mana != 0.0) {
                next.add(register(data, plan.skillId, "mana", plan.handler, plan.mana));
            }
            if (plan.cooldown != 0.0) {
                next.add(register(data, plan.skillId, "cooldown", plan.handler, plan.cooldown));
            }
            if (plan.damage != 0.0) {
                next.add(register(data, plan.skillId, "damage", plan.handler, plan.damage));
            }
        }
        applied.put(player.getUniqueId(), new AppliedState(session.modifierRevision(), desired, next));
    }

    private static DesiredSnapshot plan(Player player, ResonanceSession session) {
        ItemStack weapon = GearHand.held(player);
        WeaponRequirement requirement = weapon == null ? null : WeaponRequirement.fromItem(weapon);
        TreeMap<String, Integer> alignmentBands = alignmentFingerprint(requirement);
        List<SkillPlan> skills = new ArrayList<>();
        double equilibrium = session.getEquilibrium();
        for (Map.Entry<String, String> binding : SkillElementRegistry.bindings().entrySet()) {
            String skillId = binding.getKey();
            SkillHandler<?> handler = handlerFor(skillId);
            if (handler == null) {
                warnUnknown(skillId);
                continue;
            }
            String elementId = binding.getValue();
            ModifierTriple triple = SpellModifiers.combine(
                    SpellModifiers.combine(
                            SpellModifiers.resonance(elementId, session.getResonance(elementId)),
                            SpellModifiers.drift(equilibrium)),
                    SpellModifiers.alignment(requirement, elementId));
            skills.add(new SkillPlan(skillId, handler, triple.mana(), triple.cooldown(), triple.damage()));
        }
        return new DesiredSnapshot(alignmentBands, skills);
    }

    private static TreeMap<String, Integer> alignmentFingerprint(WeaponRequirement requirement) {
        TreeMap<String, Integer> bands = new TreeMap<>();
        if (!GearCache.alignmentEnabled || requirement == null) {
            return bands;
        }
        for (String elementId : requirement.aura().getCappedElementIds()) {
            double fill = requirement.aura().getFill(elementId);
            if (fill <= 0) {
                continue;
            }
            int band = TierBands.bandOf(elementId, fill);
            if (band > 0) {
                bands.put(elementId, band);
            }
        }
        return bands;
    }

    private static SkillHandler<?> handlerFor(String skillId) {
        SkillHandler<?> cached = handlers.get(skillId);
        if (cached != null) {
            return cached;
        }
        SkillHandler<?> resolved = SkillIdResolver.handlerForBinding(skillId);
        if (resolved != null) {
            handlers.put(skillId, resolved);
        }
        return resolved;
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
        AppliedState previous = applied.remove(player.getUniqueId());
        if (previous == null || previous.modifiers == null || data == null) {
            return;
        }
        for (SkillModifier modifier : previous.modifiers) {
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

    private static final class AppliedState {
        private long revision;
        private final DesiredSnapshot snapshot;
        private final List<SkillModifier> modifiers;

        private AppliedState(long revision, DesiredSnapshot snapshot, List<SkillModifier> modifiers) {
            this.revision = revision;
            this.snapshot = snapshot;
            this.modifiers = modifiers;
        }
    }

    private static final class DesiredSnapshot {
        private final TreeMap<String, Integer> alignmentBands;
        private final List<SkillPlan> skills;

        private DesiredSnapshot(TreeMap<String, Integer> alignmentBands, List<SkillPlan> skills) {
            this.alignmentBands = alignmentBands;
            this.skills = skills;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (!(obj instanceof DesiredSnapshot other)) {
                return false;
            }
            return Objects.equals(alignmentBands, other.alignmentBands)
                    && Objects.equals(skills, other.skills);
        }

        @Override
        public int hashCode() {
            return Objects.hash(alignmentBands, skills);
        }
    }

    private static final class SkillPlan {
        private final String skillId;
        private final SkillHandler<?> handler;
        private final double mana;
        private final double cooldown;
        private final double damage;

        private SkillPlan(
                String skillId,
                SkillHandler<?> handler,
                double mana,
                double cooldown,
                double damage) {
            this.skillId = skillId;
            this.handler = handler;
            this.mana = mana;
            this.cooldown = cooldown;
            this.damage = damage;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (!(obj instanceof SkillPlan other)) {
                return false;
            }
            return Objects.equals(skillId, other.skillId)
                    && Double.compare(mana, other.mana) == 0
                    && Double.compare(cooldown, other.cooldown) == 0
                    && Double.compare(damage, other.damage) == 0;
        }

        @Override
        public int hashCode() {
            return Objects.hash(skillId, mana, cooldown, damage);
        }
    }
}
