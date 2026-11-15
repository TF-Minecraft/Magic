package net.tfminecraft.magic.blood;

import org.bukkit.Tag;
import org.bukkit.block.Block;

/** Where an altar may stand: free space on a solid floor, deep enough underground, outside regions. */
public final class AltarPlacement {

    public enum Result {
        OK,
        WORLD,
        OCCUPIED,
        NO_FLOOR,
        SKY,
        SHALLOW,
        REGION
    }

    private AltarPlacement() {}

    public static Result check(Block target) {
        if (!BloodConfig.worldAllowed(target.getWorld().getName())) {
            return Result.WORLD;
        }
        if (!replaceable(target)) {
            return Result.OCCUPIED;
        }
        if (BloodAltars.at(target) != null) {
            return Result.OCCUPIED;
        }
        if (!target.getRelative(0, -1, 0).getType().isSolid()) {
            return Result.NO_FLOOR;
        }
        if (BloodConfig.requireUnderground()) {
            if (target.getLightFromSky() > 0) {
                return Result.SKY;
            }
            if (cover(target) < BloodConfig.minCover()) {
                return Result.SHALLOW;
            }
        }
        if (AltarRegions.blocked(target.getLocation())) {
            return Result.REGION;
        }
        return Result.OK;
    }

    /** Air, or something placing a block would replace, like grass or snow layers. */
    public static boolean replaceable(Block block) {
        return block.isEmpty() || Tag.REPLACEABLE.isTagged(block.getType());
    }

    /** Solid, light-blocking blocks above the target, up to the scan height or the world top. */
    static int cover(Block target) {
        int top = Math.min(target.getWorld().getMaxHeight() - 1, target.getY() + BloodConfig.coverScanHeight());
        int count = 0;
        for (int y = target.getY() + 1; y <= top; y++) {
            if (target.getWorld().getBlockAt(target.getX(), y, target.getZ()).getType().isOccluding()) {
                count++;
            }
        }
        return count;
    }
}
