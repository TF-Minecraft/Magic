package net.tfminecraft.magic.blood;

import java.util.ArrayList;
import java.util.List;

import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.TileState;

import net.tfminecraft.magic.meditation.MeditationCache;

/**
 * The altar is also a meditation circle, sat on in the middle of the eight pedestals (meditation
 * cardinal and diagonal offsets). Its room must give them open floor: a square reaching the farthest
 * pedestal on every side, placement.room.height blocks high. Small gaps are carved and floor holes
 * filled with the stone the player stands on; too much rock, something that must not move, claimed
 * ground or opening the rock to the sky refuses the stone instead.
 */
public final class AltarRoom {

    public enum Verdict {
        OK,
        TOO_MUCH,
        BLOCKED,
        SHALLOW
    }

    /** What placing the altar would change: blocks carved to air and floor holes filled. */
    public record Plan(Verdict verdict, List<Block> carve, List<Block> fill, Material floor) {
        static Plan refused(Verdict verdict) {
            return new Plan(verdict, List.of(), List.of(), Material.STONE);
        }

        public void apply() {
            for (Block block : carve) {
                block.setType(Material.AIR, false);
            }
            for (Block block : fill) {
                block.setType(floor, false);
            }
        }
    }

    private AltarRoom() {}

    /** Blocks from the altar to the room's edge: the farthest pedestal. */
    public static int halfWidth() {
        return Math.max(MeditationCache.cardinalOffset, MeditationCache.diagonalOffset);
    }

    /** The room's side, altar in the middle. */
    public static int size() {
        return halfWidth() * 2 + 1;
    }

    /** The plan for an altar at target, floor holes filled like standingOn (else the altar's floor). */
    public static Plan plan(Block target, Block standingOn) {
        if (!BloodConfig.roomEnabled()) {
            return new Plan(Verdict.OK, List.of(), List.of(), Material.STONE);
        }
        int half = halfWidth();
        int height = BloodConfig.roomHeight();
        World world = target.getWorld();
        List<Block> carve = new ArrayList<>();
        List<Block> fill = new ArrayList<>();
        for (int x = target.getX() - half; x <= target.getX() + half; x++) {
            for (int z = target.getZ() - half; z <= target.getZ() + half; z++) {
                Block floor = world.getBlockAt(x, target.getY() - 1, z);
                if (!floor.getType().isSolid()) {
                    if (!movable(floor)) {
                        return Plan.refused(Verdict.BLOCKED);
                    }
                    fill.add(floor);
                }
                for (int y = target.getY(); y < target.getY() + height; y++) {
                    Block block = world.getBlockAt(x, y, z);
                    if (block.isEmpty() || block.equals(target)) {
                        continue;
                    }
                    if (!movable(block)) {
                        return Plan.refused(Verdict.BLOCKED);
                    }
                    carve.add(block);
                }
                if (BloodConfig.requireUnderground()
                        && cover(world, x, target.getY() + height - 1, z) < BloodConfig.minCover()) {
                    return Plan.refused(Verdict.SHALLOW);
                }
                if (AltarRegions.blocked(floor.getLocation())) {
                    return Plan.refused(Verdict.BLOCKED);
                }
            }
        }
        if (carve.size() + fill.size() > BloodConfig.roomMaxChanges()) {
            return Plan.refused(Verdict.TOO_MUCH);
        }
        return new Plan(Verdict.OK, List.copyOf(carve), List.copyOf(fill), floorOf(standingOn, target.getRelative(0, -1, 0)));
    }

    /** Neither liquid, unbreakable, a container or other block entity, an altar nor what one changed. */
    static boolean movable(Block block) {
        if (block.isLiquid() || block.getType().getHardness() < 0 || block.getState() instanceof TileState) {
            return false;
        }
        return BloodAltars.at(block) == null && !AltarCorruption.isCorrupted(block);
    }

    /** The first of these that makes a plain floor: solid, light-blocking, breakable, no block entity. */
    static Material floorOf(Block... candidates) {
        for (Block candidate : candidates) {
            if (candidate != null && candidate.getType().isOccluding() && movable(candidate)) {
                return candidate.getType();
            }
        }
        return Material.STONE;
    }

    /** Light-blocking blocks above the room's top in this column, up to the scan height. */
    static int cover(World world, int x, int top, int z) {
        int highest = Math.min(world.getMaxHeight() - 1, top + BloodConfig.coverScanHeight());
        int count = 0;
        for (int y = top + 1; y <= highest; y++) {
            if (world.getBlockAt(x, y, z).getType().isOccluding()) {
                count++;
            }
        }
        return count;
    }
}
