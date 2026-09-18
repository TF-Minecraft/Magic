package net.tfminecraft.magic.util;

import org.bukkit.Location;

import net.tfminecraft.furniture.Furniture;

/**
 * Pedestal artifact sit point: origin is the block under the barrier.
 * Barrier top minus 0.2 is origin Y + 1.8.
 */
public final class PedestalFx {

    private PedestalFx() {}

    public static Location artifactPoint(Furniture furniture) {
        if (furniture == null) {
            return null;
        }
        Location raw = furniture.getOriginBlockLocation().orElse(furniture.getLoc());
        if (raw == null || raw.getWorld() == null) {
            return null;
        }
        return new Location(
                raw.getWorld(),
                raw.getBlockX() + 0.5,
                raw.getBlockY() + 1.8,
                raw.getBlockZ() + 0.5);
    }
}
