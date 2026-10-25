package net.tfminecraft.magic.blood;

import java.util.Collection;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import net.tfminecraft.magic.Magic;
import net.tfminecraft.magic.util.ItemRef;

/** The altar's look: a display entity on its block, swapped as the altar's level rises. */
public final class AltarDisplays {

    private AltarDisplays() {}

    public static NamespacedKey key() {
        return new NamespacedKey(Magic.plugin, "blood_altar");
    }

    /**
     * Makes sure a loaded altar has its block and exactly one display showing its level.
     * Returns true when the altar's saved fields changed and need writing.
     */
    public static boolean ensure(BloodAltar altar) {
        Block block = altar.block();
        if (block == null || !block.getWorld().isChunkLoaded(altar.getX() >> 4, altar.getZ() >> 4)) {
            return false;
        }
        if (block.getType() != BloodConfig.altarBlock()) {
            block.setType(BloodConfig.altarBlock(), false);
        }
        Entity existing = altar.getDisplayId() != null ? Bukkit.getEntity(altar.getDisplayId()) : null;
        if (existing instanceof ItemDisplay display) {
            display.setItemStack(model(altar.getLevel()));
            return false;
        }
        ItemDisplay display = spawn(altar);
        altar.setDisplayId(display.getUniqueId());
        return true;
    }

    private static ItemDisplay spawn(BloodAltar altar) {
        Location at = altar.center();
        at.setYaw(altar.getYaw());
        at.setPitch(0f);
        return at.getWorld().spawn(at, ItemDisplay.class, display -> {
            display.setItemStack(model(altar.getLevel()));
            display.setPersistent(true);
            float scale = BloodConfig.displayScale();
            display.setTransformation(new Transformation(new Vector3f(), new AxisAngle4f(),
                    new Vector3f(scale, scale, scale), new AxisAngle4f()));
            display.getPersistentDataContainer().set(key(), PersistentDataType.STRING, altar.getId());
        });
    }

    private static ItemStack model(int level) {
        return ItemRef.buildOrFallback(BloodConfig.model(level), Material.CHISELED_STONE_BRICKS);
    }

    /** Removes tagged displays that belong to no altar, or are not the altar's current display. */
    public static void removeStrays(Collection<? extends Entity> entities) {
        for (Entity entity : entities) {
            String id = entity.getPersistentDataContainer().get(key(), PersistentDataType.STRING);
            if (id == null) {
                continue;
            }
            BloodAltar altar = BloodAltars.byId(id);
            if (altar == null || !entity.getUniqueId().equals(altar.getDisplayId())) {
                entity.remove();
            }
        }
    }

    /** Takes the altar out of the world: display gone, altar block cleared. */
    public static void remove(BloodAltar altar) {
        if (altar.getDisplayId() != null) {
            Entity entity = Bukkit.getEntity(altar.getDisplayId());
            if (entity != null) {
                entity.remove();
            }
        }
        Block block = altar.block();
        if (block != null && block.getType() == BloodConfig.altarBlock()) {
            block.setType(Material.AIR, false);
        }
    }
}
