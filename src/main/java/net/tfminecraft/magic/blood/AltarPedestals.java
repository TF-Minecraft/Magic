package net.tfminecraft.magic.blood;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import net.tfminecraft.interactiblefurniture.InteractibleFurniture;
import net.tfminecraft.interactiblefurniture.furniture.Furniture;
import net.tfminecraft.interactiblefurniture.furniture.FurnitureType;
import net.tfminecraft.interactiblefurniture.furniture.PlacedSlot;
import net.tfminecraft.interactiblefurniture.furniture.SlotDefinition;
import net.tfminecraft.interactiblefurniture.loaders.FurnitureLoader;
import net.tfminecraft.interactiblefurniture.manager.FurnitureManager;
import net.tfminecraft.interactiblefurniture.manager.handlers.FurniturePlacementHandler;
import net.tfminecraft.interactiblefurniture.manager.handlers.InteractionHandler;
import net.tfminecraft.magic.meditation.MeditationCache;

/**
 * The altar sets out its own meditation ring: an InteractibleFurniture pedestal on every free spot
 * (open, on solid floor) at the meditation offsets around it. Those pedestals belong to the altar:
 * nobody can pick them up, carry them off or break them, and they go when it is purified (whatever
 * sits on them drops). Spots that are taken are left for the player's own pedestals.
 */
public final class AltarPedestals {

    /** Pedestals of purified altars that were not loaded then; they go when their chunk loads. */
    private static final Set<String> LEFTOVERS = new LinkedHashSet<>();
    private static BloodStore store;

    private AltarPedestals() {}

    public static void init(BloodStore source) {
        LEFTOVERS.clear();
        store = source;
        LEFTOVERS.addAll(source.loadLeftovers());
    }

    public static void clear() {
        LEFTOVERS.clear();
        store = null;
    }

    static Set<String> leftovers() {
        return Set.copyOf(LEFTOVERS);
    }

    /** The ring's offsets, the same as the meditation circle's. */
    static int[][] ring() {
        int card = MeditationCache.cardinalOffset;
        int diag = MeditationCache.diagonalOffset;
        return new int[][] {
            {card, 0}, {-card, 0}, {0, card}, {0, -card},
            {diag, diag}, {diag, -diag}, {-diag, diag}, {-diag, -diag}
        };
    }

    /** IF's yaw for a piece placed by someone at the altar looking out toward it (north 0, east 270). */
    static float yaw(int dx, int dz) {
        double bearing = Math.toDegrees(Math.atan2(dx, -dz));
        return (float) ((360 - Math.round(bearing)) % 360 + 360) % 360;
    }

    /** InteractibleFurniture's manager, or null while the plugin is not running. */
    static FurnitureManager manager() {
        Plugin loaded = Bukkit.getPluginManager().getPlugin("InteractibleFurniture");
        if (loaded == null || !loaded.isEnabled()) {
            return null;
        }
        InteractibleFurniture plugin = InteractibleFurniture.getInstance();
        return plugin != null ? plugin.getFurnitureManager() : null;
    }

    /** Sets out pedestals around the altar and remembers them; returns how many were placed. */
    public static int place(BloodAltar altar, Block center) {
        FurnitureType type = FurnitureLoader.getByString(MeditationCache.pedestalId);
        FurnitureManager manager = type != null ? manager() : null;
        if (manager == null) {
            return 0;
        }
        Map<UUID, Furniture> placed = manager.getPlacedFurniture();
        int count = 0;
        for (int[] offset : ring()) {
            Block spot = center.getRelative(offset[0], 0, offset[1]);
            Block floor = spot.getRelative(BlockFace.DOWN);
            if (!spot.isEmpty() || !floor.getType().isSolid()) {
                continue;
            }
            float yaw = yaw(offset[0], offset[1]);
            Location at = spot.getLocation().add(0.5, 0, 0.5);
            ItemDisplay display = FurniturePlacementHandler.spawnDisplayEntity(type, at, yaw, BlockFace.UP);
            if (display == null) {
                continue;
            }
            Furniture furniture = new Furniture(type.getId(), display.getLocation(), display.getUniqueId(),
                    floor.getLocation(), BlockFace.UP);
            furniture.setYaw(yaw);
            FurniturePlacementHandler.placeBarrierBlocks(type.getLayers(), furniture, floor, BlockFace.UP);
            placed.put(display.getUniqueId(), furniture);
            InteractionHandler.spawnInteraction(furniture);
            manager.persistFurniture(furniture);
            altar.getPedestals().add(display.getUniqueId().toString());
            spot.getWorld().playSound(at, Sound.BLOCK_ANCIENT_DEBRIS_PLACE, 0.8f, 0.7f);
            count++;
        }
        return count;
    }

    /**
     * Sets the item on one of the altar's loaded pedestals: the one showing the artefact with the
     * replaced id (which goes), else the first empty one. False when none is loaded and free.
     */
    public static boolean offer(BloodAltar altar, ItemStack item, String replaced) {
        FurnitureManager manager = manager();
        if (manager == null || item == null) {
            return false;
        }
        Map<UUID, Furniture> placed = manager.getPlacedFurniture();
        Furniture chosen = null;
        for (String id : altar.getPedestals()) {
            Furniture furniture = find(placed, id);
            SlotDefinition slot = furniture != null ? itemSlot(furniture) : null;
            if (slot == null || !(Bukkit.getEntity(furniture.getEntityId()) instanceof ItemDisplay)) {
                continue;
            }
            Optional<PlacedSlot> active = furniture.getActiveSlot(slot.getId());
            if (active.isEmpty()) {
                chosen = chosen != null ? chosen : furniture;
            } else if (replaced != null && replaced.equals(BloodArtefacts.id(shown(active.get())))) {
                furniture.removeActiveSlot(slot.getId());
                chosen = furniture;
                break;
            }
        }
        if (chosen == null) {
            return false;
        }
        SlotDefinition slot = itemSlot(chosen);
        ItemDisplay display = (ItemDisplay) Bukkit.getEntity(chosen.getEntityId());
        chosen.getOrCreatePlacedSlot(slot.getId()).spawnDisplayStand(chosen.getLoc(), item, display, null);
        manager.persistFurniture(chosen);
        return true;
    }

    /** The pedestal's first slot for an item (not for nested furniture), or null. */
    private static SlotDefinition itemSlot(Furniture furniture) {
        FurnitureType type = furniture.getType();
        if (type == null) {
            return null;
        }
        for (SlotDefinition slot : type.getSlots().values()) {
            if (!slot.isFurnitureSlot()) {
                return slot;
            }
        }
        return null;
    }

    /** What a slot shows: its item, or the item on its display stand. */
    private static ItemStack shown(PlacedSlot slot) {
        if (slot.getCurrentItem() != null || slot.getDisplayStandId() == null) {
            return slot.getCurrentItem();
        }
        Entity stand = Bukkit.getEntity(slot.getDisplayStandId());
        return stand instanceof ItemDisplay display ? display.getItemStack() : null;
    }

    /**
     * Takes the altar's pedestals away; whatever sits on them drops. Pedestals that are not loaded
     * stay bound and are removed when their chunk loads (see {@link #sweep()}).
     */
    public static void remove(BloodAltar altar) {
        FurnitureManager manager = manager();
        Map<UUID, Furniture> placed = manager != null ? manager.getPlacedFurniture() : Map.of();
        boolean left = false;
        for (String id : List.copyOf(altar.getPedestals())) {
            Furniture furniture = find(placed, id);
            if (furniture != null) {
                manager.removePlugin(furniture);
            } else {
                left |= LEFTOVERS.add(id);
            }
        }
        altar.getPedestals().clear();
        if (left) {
            save();
        }
    }

    /** Removes leftover pedestals that have loaded since their altar was purified. */
    public static void sweep() {
        FurnitureManager manager = LEFTOVERS.isEmpty() ? null : manager();
        if (manager == null) {
            return;
        }
        Map<UUID, Furniture> placed = manager.getPlacedFurniture();
        boolean changed = false;
        for (String id : List.copyOf(LEFTOVERS)) {
            Furniture furniture = find(placed, id);
            if (furniture != null) {
                manager.removePlugin(furniture);
            }
            if (furniture != null || !id.matches("[0-9a-fA-F-]{36}")) {
                LEFTOVERS.remove(id);
                changed = true;
            }
        }
        if (changed) {
            save();
        }
    }

    private static Furniture find(Map<UUID, Furniture> placed, String id) {
        try {
            return placed.get(UUID.fromString(id));
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private static void save() {
        if (store != null) {
            store.saveLeftovers(LEFTOVERS);
        }
    }

    /** The altar's own pedestals, and those of purified altars still waiting to go, are not for taking. */
    public static boolean bound(Furniture furniture) {
        return owner(furniture) != null
                || (furniture != null && furniture.getEntityId() != null
                        && LEFTOVERS.contains(furniture.getEntityId().toString()));
    }

    /** The altar a piece of furniture belongs to, or null. */
    public static BloodAltar owner(Furniture furniture) {
        if (furniture == null || furniture.getEntityId() == null) {
            return null;
        }
        String id = furniture.getEntityId().toString();
        for (BloodAltar altar : new ArrayList<>(BloodAltars.all())) {
            if (altar.getPedestals().contains(id)) {
                return altar;
            }
        }
        return null;
    }
}
