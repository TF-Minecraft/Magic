package net.tfminecraft.magic.artifact.sacrifice;

import java.util.ArrayList;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;

import net.tfminecraft.RPCharacters.Objects.RPCharacter;
import net.tfminecraft.furniture.Furniture;
import net.tfminecraft.furniture.PlacedSlot;
import net.tfminecraft.magic.Magic;
import net.tfminecraft.magic.Messages;
import net.tfminecraft.magic.artifact.aura.AuraVessels;
import net.tfminecraft.magic.artifact.shrine.ShrineChargeService;
import net.tfminecraft.magic.integration.RpCharactersBridge;

public final class SacrificeRiteService {

    private static final Map<UUID, SacrificeRiteSession> byCaster = new ConcurrentHashMap<>();
    private static BukkitTask task;

    private SacrificeRiteService() {}

    public static boolean hasSessions() {
        return !byCaster.isEmpty();
    }

    static Iterable<SacrificeRiteSession> sessions() {
        return byCaster.values();
    }

    public static boolean hasCaster(UUID casterId) {
        return casterId != null && byCaster.containsKey(casterId);
    }

    public static void tryStart(Player caster, String elementId) {
        if (caster == null || elementId == null || elementId.isBlank()) {
            return;
        }
        if (!SacrificeRegistry.isEnabled() || SacrificeRegistry.size() <= 0) {
            return;
        }
        SacrificeElementDef def = SacrificeRegistry.getById(elementId);
        if (def == null || !def.isEnabled()) {
            return;
        }
        if ((SacrificeRegistry.isOneRitePerCaster() || SacrificeRegistry.isRefuseNewWordsWhileActive())
                && hasCaster(caster.getUniqueId())) {
            caster.sendMessage(Messages.get("sacrifice.fail.active"));
            return;
        }
        if (SacrificeRegistry.isRequireDaggerInHand()
                && !SacrificeDaggerMatcher.matches(caster.getInventory().getItemInMainHand())) {
            caster.sendMessage(Messages.get("sacrifice.fail.no_dagger"));
            return;
        }
        SacrificeTargeting.Result target = SacrificeTargeting.find(caster, elementId);
        if (!target.isOk()) {
            sendFail(caster, target.getFail());
            return;
        }
        SacrificeRiteSession session = new SacrificeRiteSession(
                caster.getUniqueId(),
                target.getVictim().getUniqueId(),
                target.getFurniture(),
                target.getSlotId(),
                elementId,
                target.getScore(),
                System.currentTimeMillis());
        byCaster.put(caster.getUniqueId(), session);
        target.getVictim().sendMessage(Messages.get("sacrifice.dread"));
        ensureTicker();
        SacrificeRiteFx.ensureRunning();
    }

    public static void stop(UUID furnitureId, String slotId) {
        if (furnitureId == null) {
            return;
        }
        for (UUID casterId : Set.copyOf(byCaster.keySet())) {
            SacrificeRiteSession session = byCaster.get(casterId);
            if (session == null) {
                continue;
            }
            if (!furnitureId.equals(session.getFurnitureId())) {
                continue;
            }
            if (slotId != null && !slotId.equals(session.getSlotId())) {
                continue;
            }
            cancel(session, true);
        }
    }

    public static void stopAll(UUID furnitureId) {
        stop(furnitureId, null);
    }

    public static void cancelForPlayer(UUID playerId) {
        if (playerId == null) {
            return;
        }
        for (UUID casterId : Set.copyOf(byCaster.keySet())) {
            SacrificeRiteSession session = byCaster.get(casterId);
            if (session == null) {
                continue;
            }
            if (playerId.equals(session.getCasterId()) || playerId.equals(session.getVictimId())) {
                cancel(session, true);
            }
        }
    }

    public static void handleDeath(Player player) {
        if (player == null) {
            return;
        }
        Player killer = daggerKiller(player);
        if (killer != null) {
            SacrificeRiteSession match = null;
            for (SacrificeRiteSession session : byCaster.values()) {
                if (player.getUniqueId().equals(session.getVictimId())
                        && killer.getUniqueId().equals(session.getCasterId())) {
                    match = session;
                    break;
                }
            }
            if (match != null) {
                resolveKill(match, player, killer);
            }
        }
        cancelForPlayer(player.getUniqueId());
    }

    private static void resolveKill(SacrificeRiteSession session, Player victim, Player caster) {
        if (session == null) {
            return;
        }
        double charge = session.charge();
        SacrificeTierDef tier = SacrificeRegistry.tierForCharge(charge);
        RPCharacter character = RpCharactersBridge.getActiveCharacter(victim);
        String characterId = character != null ? character.getId() : null;
        String characterName = character != null ? character.getName() : null;
        boolean success = RpCharactersBridge.applySacrificeTier(victim, caster, tier);
        session.markResolved(success, tier != null ? tier.getId() : "none", charge);
        if (success) {
            SacrificeFillService.apply(session, tier, characterId, characterName);
            SacrificeRiteFx.resolve(session, victim.getLocation());
        }
        cancel(session, false);
    }

    private static Player daggerKiller(Player victim) {
        if (victim == null) {
            return null;
        }
        EntityDamageEvent cause = victim.getLastDamageCause();
        if (!(cause instanceof EntityDamageByEntityEvent damage)) {
            return null;
        }
        Entity damager = damage.getDamager();
        if (damager instanceof Projectile) {
            return null;
        }
        if (!(damager instanceof Player killer)) {
            return null;
        }
        ItemStack weapon = killer.getInventory().getItemInMainHand();
        if (!SacrificeDaggerMatcher.matches(weapon)) {
            return null;
        }
        return killer;
    }

    public static void clearAll() {
        for (SacrificeRiteSession session : new ArrayList<>(byCaster.values())) {
            cancel(session, true);
        }
        stopTicker();
        SacrificeRiteFx.stopIfIdle();
    }

    private static void sendFail(Player caster, SacrificeTargeting.Fail fail) {
        if (fail == SacrificeTargeting.Fail.NO_SCORE) {
            caster.sendMessage(Messages.get("sacrifice.fail.no_score"));
            return;
        }
        if (fail == SacrificeTargeting.Fail.MIN_SCORE) {
            caster.sendMessage(Messages.get("sacrifice.fail.min_score"));
            return;
        }
        if (fail == SacrificeTargeting.Fail.FULL) {
            caster.sendMessage(Messages.get("sacrifice.fail.full"));
            return;
        }
        caster.sendMessage(Messages.get("sacrifice.fail.no_target"));
    }

    private static void cancel(SacrificeRiteSession session, boolean notifyVictim) {
        if (session == null) {
            return;
        }
        byCaster.remove(session.getCasterId());
        if (notifyVictim) {
            Player victim = Bukkit.getPlayer(session.getVictimId());
            if (victim != null && victim.isOnline()) {
                victim.sendMessage(Messages.get("sacrifice.subside"));
            }
        }
        if (byCaster.isEmpty()) {
            stopTicker();
        }
        SacrificeRiteFx.stopIfIdle();
    }

    private static void ensureTicker() {
        if (task != null && !task.isCancelled()) {
            return;
        }
        if (Magic.plugin == null) {
            return;
        }
        task = Bukkit.getScheduler().runTaskTimer(Magic.plugin, SacrificeRiteService::tick, 1L, 2L);
    }

    private static void stopTicker() {
        if (task != null) {
            task.cancel();
            task = null;
        }
    }

    private static void tick() {
        for (UUID casterId : Set.copyOf(byCaster.keySet())) {
            SacrificeRiteSession session = byCaster.get(casterId);
            if (session == null) {
                continue;
            }
            if (!tickOne(session)) {
                cancel(session, true);
            }
        }
    }

    private static boolean tickOne(SacrificeRiteSession session) {
        Furniture furniture = session.getFurniture();
        if (furniture == null || furniture.isCarried()) {
            return false;
        }
        PlacedSlot slot = furniture.getActiveSlot(session.getSlotId()).orElse(null);
        ItemStack item = ShrineChargeService.itemFromSlot(slot);
        if (AuraVessels.fromItem(item) == null) {
            return false;
        }
        Player victim = Bukkit.getPlayer(session.getVictimId());
        if (victim == null || !victim.isOnline()) {
            return false;
        }
        if (victim.isDead()) {
            return true;
        }
        Location origin = SacrificeTargeting.originCenter(furniture);
        if (origin == null || origin.getWorld() != victim.getWorld()) {
            return false;
        }
        double range = SacrificeRegistry.getVictimRange();
        if (victim.getLocation().distanceSquared(origin) > range * range) {
            return false;
        }
        Player caster = Bukkit.getPlayer(session.getCasterId());
        if (caster == null || !caster.isOnline()) {
            return false;
        }
        if (session.charge() >= 1.0 && !session.isNearSent()) {
            session.setNearSent(true);
            victim.sendMessage(Messages.get("sacrifice.near"));
        }
        return true;
    }
}
