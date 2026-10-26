package net.tfminecraft.magic.blood;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.TileState;
import org.bukkit.block.data.Levelled;
import org.bukkit.block.data.type.Candle;

import net.tfminecraft.magic.meditation.MeditationCache;

/**
 * The altar is also a meditation circle, sat on in the middle of the eight pedestals (meditation
 * cardinal and diagonal offsets). Placing it lays its chamber: open floor out to the farthest pedestal
 * on every side, placement.room.height blocks high, with the floor, walls and ceiling relaid in the
 * room template so every chamber starts the same, and hidden light in the top corners. Openings in
 * the walls and ceiling (the way in) stay open. Too much rock, something that must not move, water or
 * lava in or against the room, claimed ground or opening the rock to the sky refuses the stone instead.
 * Afterwards the chamber, out to the corruption's reach, is locked: only staff may change its blocks.
 */
public final class AltarRoom {

    private static final BlockFace[] FACES = {
        BlockFace.UP, BlockFace.DOWN, BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST
    };
    private static final int LOCK_BELOW = 3;

    public enum Verdict {
        OK,
        TOO_MUCH,
        BLOCKED,
        SHALLOW,
        /** No way out: the chamber is locked once laid, so it needs a doorway two blocks high in a wall. */
        SEALED
    }

    /**
     * What placing the altar would change: blocks carved to air, blocks laid, hidden lights in the top
     * corners, lit candles in the floor corners, and the glow just above the altar (or null).
     */
    public record Plan(Verdict verdict, List<Block> carve, Map<Block, Material> lay, List<Block> lights,
            List<Block> candles, Block glow) {
        static Plan refused(Verdict verdict) {
            return new Plan(verdict, List.of(), Map.of(), List.of(), List.of(), null);
        }

        public void apply() {
            for (Block block : carve) {
                block.setType(Material.AIR, false);
            }
            for (Map.Entry<Block, Material> entry : lay.entrySet()) {
                entry.getKey().setType(entry.getValue(), false);
            }
            for (Block block : lights) {
                light(block, RoomTemplate.light());
            }
            for (Block block : candles) {
                block.setType(RoomTemplate.candle(), false);
                Candle data = (Candle) block.getBlockData();
                data.setCandles(RoomTemplate.candles());
                data.setLit(true);
                block.setBlockData(data, false);
            }
            if (glow != null) {
                light(glow, RoomTemplate.altarLight());
            }
        }

        private static void light(Block block, int level) {
            block.setType(Material.LIGHT, false);
            Levelled data = (Levelled) block.getBlockData();
            data.setLevel(level);
            block.setBlockData(data, false);
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

    /**
     * The plan for an altar at target. Floor holes take the template floor, or without one the stone
     * the player stands on (else the altar's floor).
     */
    public static Plan plan(Block target, Block standingOn) {
        if (!BloodConfig.roomEnabled()) {
            return new Plan(Verdict.OK, List.of(), Map.of(), List.of(), List.of(), null);
        }
        int half = halfWidth();
        int height = BloodConfig.roomHeight();
        World world = target.getWorld();
        Random random = new Random(target.getX() * 341873128712L + target.getY() * 132897987541L + target.getZ());
        Material holes = RoomTemplate.hasFloor() ? null : floorOf(standingOn, target.getRelative(0, -1, 0));
        List<Block> carve = new ArrayList<>();
        Map<Block, Material> lay = new LinkedHashMap<>();
        List<Block> lights = new ArrayList<>();
        List<Block> candles = new ArrayList<>();
        int filled = 0;
        boolean doorway = false;
        for (int dx = -half - 1; dx <= half + 1; dx++) {
            for (int dz = -half - 1; dz <= half + 1; dz++) {
                boolean inside = Math.abs(dx) <= half && Math.abs(dz) <= half;
                int x = target.getX() + dx;
                int z = target.getZ() + dz;
                if (AltarRegions.blocked(world.getBlockAt(x, target.getY() - 1, z).getLocation())) {
                    return Plan.refused(Verdict.BLOCKED);
                }
                for (int dy = -1; dy <= height; dy++) {
                    Block block = world.getBlockAt(x, target.getY() + dy, z);
                    if (block.equals(target)) {
                        continue;
                    }
                    if (inside && dy >= 0 && dy < height) {
                        if (!block.isEmpty()) {
                            if (!movable(block)) {
                                return Plan.refused(Verdict.BLOCKED);
                            }
                            carve.add(block);
                        }
                        boolean corner = Math.abs(dx) == half && Math.abs(dz) == half;
                        if (corner && RoomTemplate.light() > 0 && dy == height - 1) {
                            lights.add(block);
                        }
                        if (corner && RoomTemplate.candle() != null && dy == 0) {
                            candles.add(block);
                        }
                        continue;
                    }
                    // The shell: floor, ceiling and the wall ring around the room.
                    if (block.isLiquid() || BloodAltars.at(block) != null || AltarCorruption.isCorrupted(block)) {
                        return Plan.refused(Verdict.BLOCKED);
                    }
                    boolean floor = inside && dy == -1;
                    if (!block.getType().isSolid()) {
                        if (floor) {
                            if (!movable(block)) {
                                return Plan.refused(Verdict.BLOCKED);
                            }
                            lay.put(block, holes != null ? holes : RoomTemplate.floor(random));
                            filled++;
                        } else if (touchesLiquid(block)) {
                            // An opening left in the wall or ceiling must not let water or lava in.
                            return Plan.refused(Verdict.BLOCKED);
                        } else if (dy == 0 && Math.abs(dx) != Math.abs(dz)
                                && !block.getRelative(0, 1, 0).getType().isSolid()) {
                            doorway = true;
                        }
                        continue;
                    }
                    Material laid = floor ? RoomTemplate.floor(random)
                            : inside ? RoomTemplate.ceiling(random) : RoomTemplate.wall(random);
                    if (laid != null && movable(block)) {
                        lay.put(block, laid);
                    }
                }
                if (inside && BloodConfig.requireUnderground()
                        && cover(world, x, target.getY() + height - 1, z) < BloodConfig.minCover()) {
                    return Plan.refused(Verdict.SHALLOW);
                }
            }
        }
        if (carve.size() + filled > BloodConfig.roomMaxChanges()) {
            return Plan.refused(Verdict.TOO_MUCH);
        }
        if (!doorway) {
            return Plan.refused(Verdict.SEALED);
        }
        Block glow = RoomTemplate.altarLight() > 0 ? target.getRelative(BlockFace.UP) : null;
        return new Plan(Verdict.OK, List.copyOf(carve), Collections.unmodifiableMap(lay), List.copyOf(lights),
                List.copyOf(candles), glow);
    }

    /** The four outer corners of the chamber at the altar's height, for the claim check. */
    public static List<Block> corners(Block target) {
        int edge = halfWidth() + 1;
        return List.of(target.getRelative(edge, 0, edge), target.getRelative(edge, 0, -edge),
                target.getRelative(-edge, 0, edge), target.getRelative(-edge, 0, -edge));
    }

    static boolean touchesLiquid(Block block) {
        for (BlockFace face : FACES) {
            if (block.getRelative(face).isLiquid()) {
                return true;
            }
        }
        return false;
    }

    /** How far from an altar its chamber is locked: the room's walls, or the corruption's reach if further. */
    static int lockRadius() {
        return Math.max(halfWidth() + 1, CorruptionConfig.maxRadius());
    }

    /** The altar whose locked chamber holds this block, or null. */
    public static BloodAltar lockedBy(Block block) {
        int radius = lockRadius();
        int above = Math.max(BloodConfig.roomHeight(), LOCK_BELOW);
        String world = block.getWorld().getName();
        for (BloodAltar altar : BloodAltars.all()) {
            if (world.equals(altar.getWorld())
                    && Math.abs(block.getX() - altar.getX()) <= radius && Math.abs(block.getZ() - altar.getZ()) <= radius
                    && block.getY() - altar.getY() >= -LOCK_BELOW && block.getY() - altar.getY() <= above) {
                return altar;
            }
        }
        return null;
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
