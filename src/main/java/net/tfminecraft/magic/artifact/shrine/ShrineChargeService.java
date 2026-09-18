package net.tfminecraft.magic.artifact.shrine;

import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import net.tfminecraft.InteractibleFurniture;
import net.tfminecraft.furniture.Furniture;
import net.tfminecraft.furniture.PlacedSlot;
import net.tfminecraft.magic.Cache;
import net.tfminecraft.magic.Messages;
import net.tfminecraft.magic.artifact.ArtifactIds;
import net.tfminecraft.magic.artifact.aura.AuraVessel;
import net.tfminecraft.magic.artifact.aura.AuraVessels;
import net.tfminecraft.magic.charge.Charge;
import net.tfminecraft.magic.attunement.AuraLog;
import net.tfminecraft.magic.artifact.config.ArtifactAffinityRegistry;
import net.tfminecraft.magic.artifact.sacrifice.SacrificeElementDef;
import net.tfminecraft.magic.artifact.sacrifice.SacrificeRegistry;
import net.tfminecraft.magic.artifact.sacrifice.SacrificeTargeting;
import net.tfminecraft.magic.manager.MagicTickService;
import net.tfminecraft.magic.meditation.MeditationCache;
import net.tfminecraft.magic.meditation.MeditationCircle;
import net.tfminecraft.magic.tick.MagicTickContext;

public final class ShrineChargeService {

    private static final double EPSILON = 0.0001;
    private static final long PERSIST_EVERY_SECONDS = 5L;
    private static final Map<String, ShrineChargeSession> sessions = new ConcurrentHashMap<>();

    public enum AdminStart {
        NO_PEDESTAL,
        NO_ARTIFACT,
        NO_CAP,
        ALREADY_FULL,
        STARTED
    }

    private ShrineChargeService() {}

    public static String key(UUID furnitureId, String slotId) {
        return furnitureId + "|" + (slotId == null ? "" : slotId);
    }

    public static void tryStart(Player player, Furniture furniture, String slotId) {
        if (furniture == null || slotId == null || slotId.isBlank()) {
            return;
        }
        PlacedSlot slot = furniture.getActiveSlot(slotId).orElse(null);
        ItemStack item = itemFromSlot(slot);
        if (item == null || item.getType().isAir()) {
            return;
        }
        AuraVessel artifact = AuraVessels.fromItem(item);
        if (artifact == null) {
            return;
        }
        if (MeditationCircle.containing(furniture)) {
            return;
        }
        ShrineScore score = ShrineScorer.score(furniture.getLoc());
        if (artifact instanceof Charge charge && charge.isBlank()) {
            if (!charge.imprint(item, score)) {
                if (player != null) {
                    player.sendMessage(Messages.get("charge.imprint.no_elements"));
                }
                return;
            }
            charge.write(item);
            if (slot != null) {
                slot.setCurrentItem(item);
            }
            persistFurniture(furniture);
            if (player != null) {
                player.sendMessage(Messages.get("charge.imprint.done"));
            }
        }
        String primary = primaryId(item, artifact);
        hintSacrificePlace(player, furniture, primary, score);
        if (!hasChargeable(artifact, item, score)) {
            if (player != null && shrineCanOffer(artifact, item, score)) {
                player.sendMessage(Messages.get("shrine.full"));
            }
            return;
        }
        sessions.put(key(furniture.getEntityId(), slotId), new ShrineChargeSession(furniture, slotId, score));
        ShrineChargeFx.ensureRunning();
        ShrineChargeFx.start(furniture, chargeFxElement(item, artifact, score, false));
    }

    public static AdminStart startAdmin(Player player, String elementId, double amount) {
        if (player == null || player.getWorld() == null || elementId == null || elementId.isBlank()) {
            return AdminStart.NO_PEDESTAL;
        }
        String id = elementId.trim().toLowerCase(Locale.ROOT);
        Location playerLoc = player.getLocation();
        World world = playerLoc.getWorld();
        int radius = ShrineRegistry.getRadius();
        String pedestalId = MeditationCache.pedestalId;
        Furniture bestFurniture = null;
        String bestSlotId = null;
        double bestDist = Double.MAX_VALUE;
        boolean sawPedestal = false;
        boolean sawArtifact = false;
        boolean sawCap = false;
        boolean sawFull = false;
        for (Furniture furniture : SacrificeTargeting.collectNearby(
                world, playerLoc.getBlockX(), playerLoc.getBlockZ())) {
            if (furniture == null || furniture.isCarried()) {
                continue;
            }
            if (!pedestalId.equalsIgnoreCase(furniture.getId())) {
                continue;
            }
            Location origin = SacrificeTargeting.originCenter(furniture);
            if (origin == null || origin.getWorld() != world) {
                continue;
            }
            if (SacrificeTargeting.chebyshevBlocks(playerLoc, origin) > radius) {
                continue;
            }
            sawPedestal = true;
            for (PlacedSlot slot : furniture.getActiveSlots().values()) {
                ItemStack item = itemFromSlot(slot);
                if (item == null || item.getType().isAir()) {
                    continue;
                }
                AuraVessel artifact = AuraVessels.fromItem(item);
                if (artifact == null) {
                    continue;
                }
                sawArtifact = true;
                // Admin fill names the element outright, so a charge can take on one the
                // shrine would never score. This is the only way to reach the elements
                // that have scenery charging disabled.
                if (artifact instanceof Charge charge && charge.getCap(id) <= 0
                        && charge.imprintElement(item, id)) {
                    charge.write(item);
                    if (slot != null) {
                        slot.setCurrentItem(item);
                    }
                    persistFurniture(furniture);
                }
                double cap = artifact.getCap(id);
                if (cap <= 0) {
                    continue;
                }
                sawCap = true;
                double target = Math.min(amount, cap);
                if (artifact.getFill(id) + EPSILON >= target) {
                    sawFull = true;
                    continue;
                }
                double dist = origin.distanceSquared(playerLoc);
                if (dist < bestDist) {
                    bestDist = dist;
                    bestFurniture = furniture;
                    bestSlotId = slot.getId();
                }
            }
        }
        if (bestFurniture == null || bestSlotId == null) {
            if (sawFull) {
                return AdminStart.ALREADY_FULL;
            }
            if (sawArtifact && !sawCap) {
                return AdminStart.NO_CAP;
            }
            if (sawPedestal) {
                return AdminStart.NO_ARTIFACT;
            }
            return AdminStart.NO_PEDESTAL;
        }
        PlacedSlot slot = bestFurniture.getActiveSlot(bestSlotId).orElse(null);
        ItemStack item = itemFromSlot(slot);
        AuraVessel artifact = AuraVessels.fromItem(item);
        if (artifact == null) {
            return AdminStart.NO_ARTIFACT;
        }
        double cap = artifact.getCap(id);
        double target = Math.min(amount, cap);
        double fillBefore = artifact.getFill(id);
        stop(bestFurniture.getEntityId(), bestSlotId);
        ShrineScore score = ShrineScorer.score(bestFurniture.getLoc());
        sessions.put(
                key(bestFurniture.getEntityId(), bestSlotId),
                new ShrineChargeSession(bestFurniture, bestSlotId, score, id, target, true));
        ShrineChargeFx.ensureRunning();
        ShrineChargeFx.start(bestFurniture, id);
        AuraLog.append(
                "shrine-fill admin player=%s artifact=%s element=%s fill=%s target=%s",
                player.getName(),
                artifactId(item),
                id,
                AuraLog.n(fillBefore),
                AuraLog.n(target));
        return AdminStart.STARTED;
    }

    public static void stop(UUID furnitureId, String slotId) {
        ShrineChargeSession session = sessions.remove(key(furnitureId, slotId));
        if (session != null) {
            persist(session, true);
        }
        ShrineChargeFx.stopIfIdle();
    }

    public static void stopAll(UUID furnitureId) {
        if (furnitureId == null) {
            return;
        }
        String prefix = furnitureId + "|";
        for (String sessionKey : Set.copyOf(sessions.keySet())) {
            if (sessionKey.startsWith(prefix)) {
                ShrineChargeSession session = sessions.remove(sessionKey);
                if (session != null) {
                    persist(session, true);
                }
            }
        }
        ShrineChargeFx.stopIfIdle();
    }

    public static void clearAll() {
        for (ShrineChargeSession session : sessions.values()) {
            persist(session, true);
        }
        sessions.clear();
        ShrineChargeFx.stopIfIdle();
    }

    public static void tick(MagicTickContext context) {
        for (String sessionKey : Set.copyOf(sessions.keySet())) {
            ShrineChargeSession session = sessions.get(sessionKey);
            if (session == null) {
                continue;
            }
            if (!tickOne(session, context)) {
                sessions.remove(sessionKey);
                persist(session, true);
                ShrineChargeFx.stopIfIdle();
            }
        }
    }

    private static boolean tickOne(ShrineChargeSession session, MagicTickContext context) {
        Furniture furniture = session.getFurniture();
        if (furniture == null || furniture.isCarried()) {
            return false;
        }
        if (!session.isAdminForced() && MeditationCircle.containing(furniture)) {
            return false;
        }
        PlacedSlot slot = furniture.getActiveSlot(session.getSlotId()).orElse(null);
        ItemStack item = itemFromSlot(slot);
        if (item == null || item.getType().isAir()) {
            return false;
        }
        AuraVessel artifact = AuraVessels.fromItem(item);
        if (artifact == null) {
            return false;
        }
        if (session.isAdminForced()) {
            return tickAdmin(session, context, furniture, slot, item, artifact);
        }
        Set<String> allowed = allowedElements(item, artifact);
        boolean stillCharging = false;
        boolean progressed = false;
        boolean crossedClamp = false;
        for (String elementId : allowed) {
            if (blocksVanillaCharge(elementId)) {
                continue;
            }
            ShrineElementScore elementScore = session.getScore().get(elementId);
            if (elementScore == null || elementScore.getMaxAura() <= 0 || elementScore.getAuraPerSecond() <= 0) {
                continue;
            }
            double cap = artifact.getCap(elementId);
            if (cap <= 0) {
                continue;
            }
            double clamp = Math.min(cap, elementScore.getMaxAura());
            double fill = artifact.getFill(elementId);
            if (fill + EPSILON >= clamp) {
                continue;
            }
            stillCharging = true;
            double next = fill + elementScore.getAuraPerSecond();
            artifact.setFill(elementId, next);
            progressed = true;
            AuraLog.append(
                    "shrine artifact=%s element=%s fill=%s->%s clamp=%s perSecond=%s",
                    artifactId(item),
                    elementId,
                    AuraLog.n(fill),
                    AuraLog.n(artifact.getFill(elementId)),
                    AuraLog.n(clamp),
                    AuraLog.n(elementScore.getAuraPerSecond()));
            if (next + EPSILON >= clamp) {
                crossedClamp = true;
            }
        }
        if (!progressed) {
            return stillCharging;
        }
        artifact.write(item);
        if (slot != null) {
            slot.setCurrentItem(item);
        }
        if (crossedClamp) {
            ShrineChargeFx.complete(furniture, artifact, item, session.getScore());
        }
        long now = context != null ? context.seconds() : MagicTickService.getElapsedSeconds();
        if (now - session.getLastPersistSeconds() >= PERSIST_EVERY_SECONDS) {
            persist(session, false);
            session.setLastPersistSeconds(now);
        }
        return true;
    }

    private static boolean tickAdmin(
            ShrineChargeSession session,
            MagicTickContext context,
            Furniture furniture,
            PlacedSlot slot,
            ItemStack item,
            AuraVessel artifact) {
        String elementId = session.getForcedElement();
        if (elementId == null || elementId.isBlank()) {
            return false;
        }
        double cap = artifact.getCap(elementId);
        if (cap <= 0) {
            return false;
        }
        double clamp = Math.min(cap, session.getTargetFill());
        double fill = artifact.getFill(elementId);
        if (fill + EPSILON >= clamp) {
            ShrineChargeFx.complete(furniture, elementId);
            return false;
        }
        double seconds = ShrineRegistry.getFullChargeSeconds();
        double aps = seconds > 0 && Cache.artifactAuraCap > 0
                ? Cache.artifactAuraCap / seconds
                : 0;
        if (aps <= 0) {
            return false;
        }
        double next = Math.min(clamp, fill + aps);
        artifact.setFill(elementId, next);
        AuraLog.append(
                "shrine-fill artifact=%s element=%s fill=%s->%s clamp=%s perSecond=%s",
                artifactId(item),
                elementId,
                AuraLog.n(fill),
                AuraLog.n(artifact.getFill(elementId)),
                AuraLog.n(clamp),
                AuraLog.n(aps));
        artifact.write(item);
        if (slot != null) {
            slot.setCurrentItem(item);
        }
        if (next + EPSILON >= clamp) {
            ShrineChargeFx.complete(furniture, elementId);
            persist(session, true);
            return false;
        }
        long now = context != null ? context.seconds() : MagicTickService.getElapsedSeconds();
        if (now - session.getLastPersistSeconds() >= PERSIST_EVERY_SECONDS) {
            persist(session, false);
            session.setLastPersistSeconds(now);
        }
        return true;
    }

    private static void hintSacrificePlace(Player player, Furniture furniture, String primaryId, ShrineScore score) {
        if (player == null || primaryId == null || primaryId.isBlank()) {
            return;
        }
        if (!SacrificeRegistry.isEnabled()) {
            return;
        }
        SacrificeElementDef def = SacrificeRegistry.getById(primaryId);
        if (def == null || !def.isEnabled()) {
            return;
        }
        ShrineElementScore elementScore = score != null ? score.get(primaryId) : null;
        double aura = elementScore != null ? elementScore.getMaxAura() : 0;
        double ready = SacrificeRegistry.minSceneryAura(primaryId);
        double hintMin = SacrificeRegistry.getHintMinAura();
        if (aura + EPSILON >= ready) {
            player.sendMessage(Messages.get("sacrifice.place.ready"));
            ShrineChargeFx.sacrificeReady(furniture, primaryId);
            return;
        }
        if (aura > hintMin) {
            player.sendMessage(Messages.get("sacrifice.place.weak"));
        }
    }

    static String chargeFxElement(ItemStack item, AuraVessel artifact, ShrineScore score, boolean includeFull) {
        if (artifact == null || score == null) {
            return primaryId(item, artifact);
        }
        String primary = primaryId(item, artifact);
        String best = null;
        double bestCap = -1;
        for (String elementId : allowedElements(item, artifact)) {
            if (blocksVanillaCharge(elementId)) {
                continue;
            }
            ShrineElementScore elementScore = score.get(elementId);
            if (elementScore == null || elementScore.getMaxAura() <= 0 || elementScore.getAuraPerSecond() <= 0) {
                continue;
            }
            double cap = artifact.getCap(elementId);
            if (cap <= 0) {
                continue;
            }
            double clamp = Math.min(cap, elementScore.getMaxAura());
            if (!includeFull && artifact.getFill(elementId) + EPSILON >= clamp) {
                continue;
            }
            boolean isPrimary = primary != null && primary.equalsIgnoreCase(elementId);
            if (best == null || cap > bestCap + EPSILON) {
                best = elementId;
                bestCap = cap;
            } else if (Math.abs(cap - bestCap) <= EPSILON && isPrimary) {
                best = elementId;
            }
        }
        return best != null ? best : primary;
    }

    static boolean hasChargeable(AuraVessel artifact, ItemStack item, ShrineScore score) {
        if (artifact == null || score == null) {
            return false;
        }
        for (String elementId : allowedElements(item, artifact)) {
            if (blocksVanillaCharge(elementId)) {
                continue;
            }
            ShrineElementScore elementScore = score.get(elementId);
            if (elementScore == null || elementScore.getMaxAura() <= 0 || elementScore.getAuraPerSecond() <= 0) {
                continue;
            }
            double cap = artifact.getCap(elementId);
            if (cap <= 0) {
                continue;
            }
            double clamp = Math.min(cap, elementScore.getMaxAura());
            if (artifact.getFill(elementId) + EPSILON < clamp) {
                return true;
            }
        }
        return false;
    }

    static boolean shrineCanOffer(AuraVessel artifact, ItemStack item, ShrineScore score) {
        if (artifact == null || score == null) {
            return false;
        }
        for (String elementId : allowedElements(item, artifact)) {
            if (blocksVanillaCharge(elementId)) {
                continue;
            }
            ShrineElementScore elementScore = score.get(elementId);
            if (elementScore != null && elementScore.getMaxAura() > 0 && elementScore.getAuraPerSecond() > 0) {
                return true;
            }
        }
        return false;
    }

    public static boolean blocksVanillaCharge(String elementId) {
        if (SacrificeRegistry.blocksSceneryCharge(elementId)) {
            return true;
        }
        ShrineElementDef def = ShrineRegistry.getById(elementId);
        return def != null && !def.isSceneryCharge();
    }

    public static Set<String> allowedElements(ItemStack item, AuraVessel artifact) {
        Set<String> allowed = new HashSet<>();
        if (artifact == null) {
            return allowed;
        }
        if (artifact.isCharge()) {
            // A charge took its elements from the shrine, so affinity has nothing to add.
            for (String id : artifact.getCappedElementIds()) {
                if (id != null && !id.isBlank() && artifact.getCap(id) > 0) {
                    allowed.add(id.trim().toLowerCase(Locale.ROOT));
                }
            }
            return allowed;
        }
        String primary = primaryId(item, artifact);
        if (primary == null || primary.isBlank()) {
            return allowed;
        }
        Set<String> affinity = new HashSet<>();
        affinity.add(primary);
        for (String companion : ArtifactAffinityRegistry.companions(primary)) {
            if (companion != null && !companion.isBlank()) {
                affinity.add(companion.trim().toLowerCase(Locale.ROOT));
            }
        }
        for (String id : artifact.getCappedElementIds()) {
            if (id == null || id.isBlank() || artifact.getCap(id) <= 0) {
                continue;
            }
            if (affinity.contains(id.trim().toLowerCase(Locale.ROOT))) {
                allowed.add(id.trim().toLowerCase(Locale.ROOT));
            }
        }
        return allowed;
    }

    static String primaryId(ItemStack item, AuraVessel artifact) {
        if (artifact == null) {
            return null;
        }
        String primary = artifact.primaryElementId();
        return primary == null || primary.isBlank() ? null : primary;
    }

    public static boolean hasSessions() {
        return !sessions.isEmpty();
    }

    static Iterable<ShrineChargeSession> sessions() {
        return sessions.values();
    }

    public static ItemStack itemFromSlot(PlacedSlot slot) {
        if (slot == null) {
            return null;
        }
        ItemStack item = slot.getCurrentItem();
        if (item != null && !item.getType().isAir()) {
            return item;
        }
        UUID standId = slot.getDisplayStandId();
        Entity stand = standId != null ? Bukkit.getEntity(standId) : null;
        if (stand instanceof ItemDisplay display) {
            ItemStack shown = display.getItemStack();
            if (shown != null && !shown.getType().isAir()) {
                slot.setModel(shown);
                return shown;
            }
        }
        return item;
    }

    private static String artifactId(ItemStack item) {
        UUID id = ArtifactIds.read(item);
        return id != null ? id.toString() : "-";
    }

    private static void persist(ShrineChargeSession session, boolean force) {
        if (session == null || session.getFurniture() == null) {
            return;
        }
        persistFurniture(session.getFurniture());
        if (force) {
            session.setLastPersistSeconds(MagicTickService.getElapsedSeconds());
        }
    }

    private static void persistFurniture(Furniture furniture) {
        if (furniture == null) {
            return;
        }
        try {
            InteractibleFurniture.getInstance().getFurnitureManager().persistFurniture(furniture);
        } catch (Exception ignored) {
            // IF may already be disabled
        }
    }
}
