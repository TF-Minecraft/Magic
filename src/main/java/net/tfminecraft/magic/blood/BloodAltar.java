package net.tfminecraft.magic.blood;

import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;

/** A placed altar. Saved in data/bloodmagic/altars.json. */
public final class BloodAltar {

    private String id;
    private String ownerCharacter;
    private String ownerUuid;
    private String world;
    private int x;
    private int y;
    private int z;
    private float yaw;
    private int level;
    private String displayId;
    private long placedAt;

    public BloodAltar() {}

    public static BloodAltar create(String ownerCharacter, String ownerUuid, Block block, float yaw, long now) {
        BloodAltar altar = new BloodAltar();
        altar.id = UUID.randomUUID().toString();
        altar.ownerCharacter = ownerCharacter;
        altar.ownerUuid = ownerUuid;
        altar.world = block.getWorld().getName();
        altar.x = block.getX();
        altar.y = block.getY();
        altar.z = block.getZ();
        altar.yaw = yaw;
        altar.placedAt = now;
        return altar;
    }

    /** False when a hand edit left the file without the fields an altar needs. */
    public boolean isValid() {
        return id != null && !id.isBlank() && ownerCharacter != null && !ownerCharacter.isBlank()
                && world != null && !world.isBlank();
    }

    public String getId() {
        return id;
    }

    public String getOwnerCharacter() {
        return ownerCharacter;
    }

    public String getOwnerUuid() {
        return ownerUuid;
    }

    public String getWorld() {
        return world;
    }

    public int getX() {
        return x;
    }

    public int getY() {
        return y;
    }

    public int getZ() {
        return z;
    }

    public float getYaw() {
        return yaw;
    }

    public int getLevel() {
        return level;
    }

    public void setLevel(int level) {
        this.level = Math.max(0, level);
    }

    public UUID getDisplayId() {
        if (displayId == null) {
            return null;
        }
        try {
            return UUID.fromString(displayId);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    public void setDisplayId(UUID displayId) {
        this.displayId = displayId != null ? displayId.toString() : null;
    }

    public long getPlacedAt() {
        return placedAt;
    }

    public String blockKey() {
        return key(world, x, y, z);
    }

    public String chunkKey() {
        return chunk(world, x >> 4, z >> 4);
    }

    /** The altar block, or null when its world is not loaded. */
    public Block block() {
        World loaded = Bukkit.getWorld(world);
        return loaded != null ? loaded.getBlockAt(x, y, z) : null;
    }

    /** Centre of the altar block, or null when its world is not loaded. */
    public Location center() {
        Block block = block();
        return block != null ? block.getLocation().add(0.5, 0.5, 0.5) : null;
    }

    public static String key(String world, int x, int y, int z) {
        return world + "," + x + "," + y + "," + z;
    }

    public static String key(Block block) {
        return key(block.getWorld().getName(), block.getX(), block.getY(), block.getZ());
    }

    public static String chunk(String world, int chunkX, int chunkZ) {
        return world + "," + chunkX + "," + chunkZ;
    }
}
