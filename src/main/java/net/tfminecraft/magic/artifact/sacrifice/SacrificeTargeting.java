package net.tfminecraft.magic.artifact.sacrifice;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import net.tfminecraft.InteractibleFurniture;
import net.tfminecraft.furniture.Furniture;
import net.tfminecraft.furniture.PlacedSlot;
import net.tfminecraft.magic.artifact.aura.AuraVessel;
import net.tfminecraft.magic.artifact.aura.AuraVessels;
import net.tfminecraft.magic.artifact.shrine.ShrineChargeService;
import net.tfminecraft.magic.util.PedestalFx;
import net.tfminecraft.magic.artifact.shrine.ShrineElementScore;
import net.tfminecraft.magic.artifact.shrine.ShrineRegistry;
import net.tfminecraft.magic.artifact.shrine.ShrineScore;
import net.tfminecraft.magic.artifact.shrine.ShrineScorer;

public final class SacrificeTargeting {

    private static final double EPSILON = 0.0001;

    public enum Fail {
            NONE,
        NO_TARGET,
        NO_SCORE,
        MIN_SCORE,
        FULL
    }

    public static final class Result {
        private final Furniture furniture;
        private final String slotId;
        private final Player victim;
        private final ShrineScore score;
        private final Fail fail;

        private Result(Furniture furniture, String slotId, Player victim, ShrineScore score, Fail fail) {
            this.furniture = furniture;
            this.slotId = slotId;
            this.victim = victim;
            this.score = score;
            this.fail = fail;
        }

        public static Result ok(Furniture furniture, String slotId, Player victim, ShrineScore score) {
            return new Result(furniture, slotId, victim, score, Fail.NONE);
        }

        public static Result fail(Fail fail) {
            return new Result(null, null, null, null, fail);
        }

        public boolean isOk() {
            return fail == Fail.NONE && furniture != null && victim != null && slotId != null;
        }

        public Furniture getFurniture() {
            return furniture;
        }

        public String getSlotId() {
            return slotId;
        }

        public Player getVictim() {
            return victim;
        }

        public ShrineScore getScore() {
            return score;
        }

        public Fail getFail() {
            return fail;
        }
    }

    private SacrificeTargeting() {}

    public static Result find(Player caster, String elementId) {
        if (caster == null || elementId == null || elementId.isBlank() || caster.getWorld() == null) {
            return Result.fail(Fail.NO_TARGET);
        }
        Location casterLoc = caster.getLocation();
        World world = casterLoc.getWorld();
        int radius = ShrineRegistry.getRadius();
        String pedestalId = SacrificeRegistry.getPedestalId();
        List<Candidate> ready = new ArrayList<>();
        boolean sawLowScore = false;
        boolean sawNoScore = false;
        boolean sawFull = false;
        boolean sawReadyNoVictim = false;

        for (Furniture furniture : collectNearby(world, casterLoc.getBlockX(), casterLoc.getBlockZ())) {
            if (furniture == null || furniture.isCarried()) {
                continue;
            }
            if (!pedestalId.equalsIgnoreCase(furniture.getId())) {
                continue;
            }
            Location origin = originCenter(furniture);
            if (origin == null || origin.getWorld() != world) {
                continue;
            }
            if (chebyshevBlocks(casterLoc, origin) > radius) {
                continue;
            }
            ShrineScore score = ShrineScorer.score(origin);
            List<PlacedSlot> eligibleSlots = new ArrayList<>();
            for (PlacedSlot slot : furniture.getActiveSlots().values()) {
                SlotCheck check = checkSlot(slot, elementId, score);
                if (check == SlotCheck.OK) {
                    eligibleSlots.add(slot);
                } else if (check == SlotCheck.FULL) {
                    sawFull = true;
                } else if (check == SlotCheck.LOW_SCORE) {
                    sawLowScore = true;
                } else if (check == SlotCheck.NO_SCORE) {
                    sawNoScore = true;
                }
            }
            if (eligibleSlots.isEmpty()) {
                continue;
            }
            Player victim = closestVictim(world, caster, origin);
            if (victim == null) {
                sawReadyNoVictim = true;
                continue;
            }
            PlacedSlot slot = closestSlot(eligibleSlots, casterLoc, origin);
            if (slot == null) {
                continue;
            }
            ready.add(new Candidate(furniture, slot.getId(), victim, score, origin.distanceSquared(casterLoc)));
        }

        Candidate best = null;
        for (Candidate candidate : ready) {
            if (best == null || candidate.distanceSq < best.distanceSq) {
                best = candidate;
            }
        }
        if (best != null) {
            return Result.ok(best.furniture, best.slotId, best.victim, best.score);
        }
        if (sawLowScore && SacrificeRegistry.isRequireMinScore()) {
            return Result.fail(Fail.MIN_SCORE);
        }
        if (sawNoScore) {
            return Result.fail(Fail.NO_SCORE);
        }
        if (sawReadyNoVictim) {
            return Result.fail(Fail.NO_TARGET);
        }
        if (sawFull) {
            return Result.fail(Fail.FULL);
        }
        return Result.fail(Fail.NO_SCORE);
    }

    private enum SlotCheck {
        SKIP,
        OK,
        FULL,
        NO_SCORE,
        LOW_SCORE
    }

    private static SlotCheck checkSlot(PlacedSlot slot, String elementId, ShrineScore score) {
        if (slot == null || slot.getId() == null) {
            return SlotCheck.SKIP;
        }
        String filter = SacrificeRegistry.getPedestalSlot();
        if (filter != null && !filter.equals("*") && !filter.equalsIgnoreCase(slot.getId())) {
            return SlotCheck.SKIP;
        }
        ItemStack item = ShrineChargeService.itemFromSlot(slot);
        AuraVessel artifact = AuraVessels.fromItem(item);
        if (artifact == null) {
            return SlotCheck.SKIP;
        }
        Set<String> allowed = ShrineChargeService.allowedElements(item, artifact);
        if (!allowed.contains(elementId.trim().toLowerCase(Locale.ROOT))) {
            return SlotCheck.SKIP;
        }
        double cap = artifact.getCap(elementId);
        if (cap <= 0) {
            return SlotCheck.SKIP;
        }
        if (artifact.getFill(elementId) + EPSILON >= cap) {
            return SlotCheck.FULL;
        }
        if (SacrificeRegistry.isRequireMinScore()) {
            ShrineElementScore elementScore = score != null ? score.get(elementId) : null;
            double maxAura = elementScore != null ? elementScore.getMaxAura() : 0;
            if (maxAura <= EPSILON) {
                return SlotCheck.NO_SCORE;
            }
            if (maxAura + EPSILON < SacrificeRegistry.minSceneryAura(elementId)) {
                return SlotCheck.LOW_SCORE;
            }
        }
        return SlotCheck.OK;
    }

    private static Player closestVictim(World world, Player caster, Location origin) {
        double range = SacrificeRegistry.getVictimRange();
        double rangeSq = range * range;
        Player best = null;
        double bestSq = Double.MAX_VALUE;
        for (Player player : world.getPlayers()) {
            if (player == null || player.equals(caster) || !player.isOnline() || player.isDead()) {
                continue;
            }
            Location loc = player.getLocation();
            if (loc.getWorld() != world) {
                continue;
            }
            double distSq = loc.distanceSquared(origin);
            if (distSq > rangeSq || distSq >= bestSq) {
                continue;
            }
            best = player;
            bestSq = distSq;
        }
        return best;
    }

    private static PlacedSlot closestSlot(List<PlacedSlot> slots, Location casterLoc, Location origin) {
        PlacedSlot best = null;
        double bestSq = Double.MAX_VALUE;
        for (PlacedSlot slot : slots) {
            Location loc = slotLocation(slot, origin);
            double distSq = loc.distanceSquared(casterLoc);
            if (best == null
                    || distSq < bestSq
                    || (Math.abs(distSq - bestSq) < EPSILON
                            && slot.getId().compareToIgnoreCase(best.getId()) < 0)) {
                best = slot;
                bestSq = distSq;
            }
        }
        return best;
    }

    private static Location slotLocation(PlacedSlot slot, Location origin) {
        if (slot.getDisplayStandId() != null) {
            Entity entity = org.bukkit.Bukkit.getEntity(slot.getDisplayStandId());
            if (entity instanceof ItemDisplay && entity.getLocation().getWorld() == origin.getWorld()) {
                return entity.getLocation();
            }
        }
        return origin;
    }

    public static Location originCenter(Furniture furniture) {
        return PedestalFx.artifactPoint(furniture);
    }

    public static int chebyshevBlocks(Location a, Location b) {
        return Math.max(
                Math.max(Math.abs(a.getBlockX() - b.getBlockX()), Math.abs(a.getBlockY() - b.getBlockY())),
                Math.abs(a.getBlockZ() - b.getBlockZ()));
    }

    public static List<Furniture> collectNearby(World world, int cx, int cz) {
        List<Furniture> out = new ArrayList<>();
        InteractibleFurniture plugin = InteractibleFurniture.getInstance();
        if (plugin == null) {
            return out;
        }
        int chunkX = cx >> 4;
        int chunkZ = cz >> 4;
        int pad = Math.max(1, (ShrineRegistry.getRadius() >> 4) + 1);
        for (int dx = -pad; dx <= pad; dx++) {
            for (int dz = -pad; dz <= pad; dz++) {
                Chunk chunk = world.getChunkAt(chunkX + dx, chunkZ + dz);
                out.addAll(plugin.getFurnitureManager().getFurnitureInChunk(chunk));
            }
        }
        return out;
    }

    private static final class Candidate {
        private final Furniture furniture;
        private final String slotId;
        private final Player victim;
        private final ShrineScore score;
        private final double distanceSq;

        private Candidate(Furniture furniture, String slotId, Player victim, ShrineScore score, double distanceSq) {
            this.furniture = furniture;
            this.slotId = slotId;
            this.victim = victim;
            this.score = score;
            this.distanceSq = distanceSq;
        }
    }
}
