package net.tfminecraft.magic.gear;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Display.Billboard;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import net.tfminecraft.magic.Magic;
import net.tfminecraft.tlibs.TLibs;

public final class GearStationStore {

    private static final Map<String, Occupancy> OCCUPIED = new HashMap<>();
    private static boolean loading;

    private GearStationStore() {}

    public static final class Occupancy {
        private ItemStack item;
        private UUID displayId;
        private boolean orbSessionActive;
        private final UUID owner;
        private final Map<String, Integer> charged;

        Occupancy(ItemStack item, UUID displayId, UUID owner, Map<String, Integer> charged) {
            this.item = item;
            this.displayId = displayId;
            this.owner = owner;
            this.charged = charged == null ? null : Map.copyOf(charged);
        }

        /** Player who prepared the craft. Null for stations saved before owners were recorded. */
        public UUID getOwner() {
            return owner;
        }

        /**
         * Materials taken when the craft was prepared, empty when costs were bypassed.
         * Null for stations saved before charges were recorded.
         */
        public Map<String, Integer> getCharged() {
            return charged;
        }

        public ItemStack getItem() {
            return item;
        }

        public void setItem(ItemStack item) {
            this.item = item;
            refreshDisplay();
        }

        public boolean isOrbSessionActive() {
            return orbSessionActive;
        }

        public void setOrbSessionActive(boolean orbSessionActive) {
            this.orbSessionActive = orbSessionActive;
        }

        private void refreshDisplay() {
            if (displayId == null) {
                return;
            }
            Entity entity = Bukkit.getEntity(displayId);
            if (entity instanceof ItemDisplay display) {
                display.setItemStack(item);
            }
        }
    }

    public static boolean isOccupied(Location location) {
        return location != null && OCCUPIED.containsKey(key(location));
    }

    public static Occupancy get(Location location) {
        return location == null ? null : OCCUPIED.get(key(location));
    }

    public static Occupancy occupy(Location location, ItemStack item, UUID owner, Map<String, Integer> charged) {
        if (location == null || item == null) {
            return null;
        }
        clear(location, false);
        UUID displayId = spawnDisplay(location, item);
        Occupancy occupancy = new Occupancy(item, displayId, owner, charged);
        OCCUPIED.put(key(location), occupancy);
        save();
        return occupancy;
    }

    public static ItemStack eject(Location location) {
        Occupancy occupancy = get(location);
        if (occupancy == null) {
            return null;
        }
        if (occupancy.isOrbSessionActive() || !isAttuned(occupancy.getItem())) {
            return null;
        }
        ItemStack item = occupancy.getItem();
        clear(location, true);
        return item;
    }

    /** Clears the station even if the weapon is unattuned or mid-orb. Does not return the staff. */
    public static ItemStack takeForAbort(Location location) {
        Occupancy occupancy = get(location);
        if (occupancy == null) {
            return null;
        }
        ItemStack item = occupancy.getItem();
        clear(location, true);
        return item;
    }

    /** An unattuned weapon never leaves the station, so band 0 gear cannot exist in the world. */
    public static boolean isAttuned(ItemStack item) {
        return item != null && WeaponRequirement.fromItem(item).highestBand() > 0;
    }

    public static void update(Location location, ItemStack item) {
        Occupancy occupancy = get(location);
        if (occupancy == null || item == null) {
            return;
        }
        occupancy.setItem(item);
        save();
    }

    public static void shutdown() {
        save();
        for (Map.Entry<String, Occupancy> entry : OCCUPIED.entrySet()) {
            Location location = locationFromKey(entry.getKey());
            Occupancy occupancy = entry.getValue();
            if (location != null) {
                removeDisplays(location, null, occupancy == null ? null : occupancy.displayId);
            } else if (occupancy != null && occupancy.displayId != null) {
                Entity entity = Bukkit.getEntity(occupancy.displayId);
                if (entity != null) {
                    entity.remove();
                }
            }
        }
        OCCUPIED.clear();
    }

    public static void clearAll() {
        shutdown();
        save();
    }

    public static void load() {
        loading = true;
        try {
            OCCUPIED.clear();
            File file = file();
            if (!file.exists()) {
                return;
            }
            YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
            ConfigurationSection root = config.getConfigurationSection("stations");
            if (root == null) {
                return;
            }
            boolean dropped = false;
            for (String key : root.getKeys(false)) {
                ConfigurationSection section = root.getConfigurationSection(key);
                if (section == null) {
                    continue;
                }
                Location location = locationOf(section);
                ItemStack item = section.getItemStack("item");
                if (location == null || item == null) {
                    continue;
                }
                UUID savedDisplay = uuidOf(section.getString("display"));
                UUID owner = uuidOf(section.getString("owner"));
                Map<String, Integer> charged = readCharged(section);
                location.getChunk().load();
                if (furniture(location) == Furniture.ABSENT) {
                    removeDisplays(location, null, savedDisplay);
                    drop(location, item);
                    dropped = true;
                    Magic.plugin.getLogger().warning("[Magic] Gear station at " + key(location)
                            + " has no furniture. Dropped the weapon and removed its display.");
                    continue;
                }
                UUID displayId = ensureDisplay(location, item, savedDisplay);
                OCCUPIED.put(key(location), new Occupancy(item, displayId, owner, charged));
            }
            if (dropped) {
                save();
            }
        } finally {
            loading = false;
        }
    }

    /**
     * When a chunk loads, a saved station with no furniture drops its weapon. A station
     * that is still there keeps a single display, including one left behind in an
     * unloaded chunk.
     */
    public static void reconcile(Chunk chunk) {
        if (loading || chunk == null) {
            return;
        }
        List<String> keys = new ArrayList<>();
        for (String stationKey : OCCUPIED.keySet()) {
            Location location = locationFromKey(stationKey);
            if (location == null || location.getWorld() != chunk.getWorld()) {
                continue;
            }
            if ((location.getBlockX() >> 4) == chunk.getX() && (location.getBlockZ() >> 4) == chunk.getZ()) {
                keys.add(stationKey);
            }
        }
        boolean changed = false;
        for (String stationKey : keys) {
            Location location = locationFromKey(stationKey);
            Occupancy occupancy = OCCUPIED.get(stationKey);
            if (location == null || occupancy == null) {
                continue;
            }
            if (furniture(location) == Furniture.ABSENT) {
                abandon(location);
                continue;
            }
            UUID displayId = ensureDisplay(location, occupancy.getItem(), occupancy.displayId);
            if (displayId != null && !displayId.equals(occupancy.displayId)) {
                occupancy.displayId = displayId;
                changed = true;
            }
        }
        if (changed) {
            save();
        }
    }

    /**
     * Occupied station whose block center is within {@code maxDistance} of {@code origin}.
     * 0.75 reaches a frame in this block and stops short of the next block's center.
     */
    public static Location occupiedWithin(Location origin, double maxDistance) {
        if (origin == null || origin.getWorld() == null || maxDistance < 0) {
            return null;
        }
        double maxSquared = maxDistance * maxDistance;
        Location best = null;
        double bestDistance = Double.MAX_VALUE;
        for (String stationKey : OCCUPIED.keySet()) {
            Location location = locationFromKey(stationKey);
            double distance = distanceSquaredToCenter(location, origin);
            if (distance > maxSquared || distance >= bestDistance) {
                continue;
            }
            bestDistance = distance;
            best = location;
        }
        return best;
    }

    /** Squared distance from {@code at} to the center of {@code station}'s block. */
    public static double distanceSquaredToCenter(Location station, Location at) {
        if (station == null || at == null || station.getWorld() == null || at.getWorld() == null
                || station.getWorld() != at.getWorld()) {
            return Double.MAX_VALUE;
        }
        double dx = at.getX() - (station.getBlockX() + 0.5);
        double dy = at.getY() - (station.getBlockY() + 0.5);
        double dz = at.getZ() - (station.getBlockZ() + 0.5);
        return (dx * dx) + (dy * dy) + (dz * dz);
    }

    public static void save() {
        YamlConfiguration config = new YamlConfiguration();
        int index = 0;
        for (Map.Entry<String, Occupancy> entry : OCCUPIED.entrySet()) {
            Occupancy occupancy = entry.getValue();
            Location location = locationFromKey(entry.getKey());
            if (location == null || occupancy.getItem() == null) {
                continue;
            }
            String path = "stations." + index++;
            config.set(path + ".world", location.getWorld() == null ? "" : location.getWorld().getName());
            config.set(path + ".x", location.getBlockX());
            config.set(path + ".y", location.getBlockY());
            config.set(path + ".z", location.getBlockZ());
            config.set(path + ".item", occupancy.getItem());
            if (occupancy.displayId != null) {
                config.set(path + ".display", occupancy.displayId.toString());
            }
            if (occupancy.getOwner() != null) {
                config.set(path + ".owner", occupancy.getOwner().toString());
            }
            if (occupancy.getCharged() != null) {
                config.createSection(path + ".charged");
                int chargedIndex = 0;
                for (Map.Entry<String, Integer> cost : occupancy.getCharged().entrySet()) {
                    String costPath = path + ".charged." + chargedIndex++;
                    config.set(costPath + ".path", cost.getKey());
                    config.set(costPath + ".amount", cost.getValue());
                }
            }
        }
        try {
            File file = file();
            file.getParentFile().mkdirs();
            config.save(file);
        } catch (IOException ex) {
            Magic.plugin.getLogger().warning("[Magic] Failed to save gear stations: " + ex.getMessage());
        }
    }

    private static void clear(Location location, boolean save) {
        Occupancy occupancy = OCCUPIED.remove(key(location));
        removeDisplays(location, null, occupancy == null ? null : occupancy.displayId);
        if (save) {
            save();
        }
    }

    /** Drops the weapon and forgets the station. The display is removed first. */
    private static void abandon(Location location) {
        Occupancy occupancy = OCCUPIED.remove(key(location));
        removeDisplays(location, null, occupancy == null ? null : occupancy.displayId);
        if (occupancy != null && occupancy.getItem() != null) {
            drop(location, occupancy.getItem());
            Magic.plugin.getLogger().warning("[Magic] Gear station at " + key(location)
                    + " has no furniture. Dropped the weapon and removed its display.");
        }
        save();
    }

    private static UUID ensureDisplay(Location location, ItemStack item, UUID savedDisplay) {
        World world = location.getWorld();
        if (world == null) {
            return null;
        }
        if (!world.isChunkLoaded(location.getBlockX() >> 4, location.getBlockZ() >> 4)) {
            location.getChunk().load();
        }
        ItemDisplay existing = displayOrNull(savedDisplay);
        if (existing == null) {
            existing = findTagged(location);
        }
        if (existing != null) {
            existing.setItemStack(item);
            mark(existing, location);
            removeDisplays(location, existing.getUniqueId(), null);
            return existing.getUniqueId();
        }
        removeDisplays(location, null, savedDisplay);
        return spawnDisplay(location, item);
    }

    private static ItemDisplay displayOrNull(UUID displayId) {
        if (displayId == null) {
            return null;
        }
        Entity entity = Bukkit.getEntity(displayId);
        if (entity instanceof ItemDisplay display && display.isValid()) {
            return display;
        }
        return null;
    }

    private static ItemDisplay findTagged(Location location) {
        World world = location.getWorld();
        if (world == null) {
            return null;
        }
        String stationKey = key(location);
        for (Entity entity : world.getNearbyEntities(displayLocation(location), 0.5, 0.5, 0.5)) {
            if (entity instanceof ItemDisplay display && stationKey.equals(markOf(display))) {
                return display;
            }
        }
        return null;
    }

    /**
     * Removes this station's displays. A kept display is the one still in use.
     * Untagged displays are removed only when the item is a mage weapon, so a
     * furniture display sitting nearby is left alone.
     */
    private static void removeDisplays(Location location, UUID keep, UUID savedDisplay) {
        if (location == null || location.getWorld() == null) {
            return;
        }
        World world = location.getWorld();
        try {
            if (!world.isChunkLoaded(location.getBlockX() >> 4, location.getBlockZ() >> 4)) {
                location.getChunk().load();
            }
        } catch (RuntimeException ex) {
            Magic.plugin.getLogger().warning("[Magic] Could not load gear station chunk at "
                    + key(location) + ": " + ex.getMessage());
        }
        String stationKey = key(location);
        for (Entity entity : world.getNearbyEntities(displayLocation(location), 0.5, 0.5, 0.5)) {
            if (!(entity instanceof ItemDisplay display)) {
                continue;
            }
            if (keep != null && keep.equals(display.getUniqueId())) {
                continue;
            }
            if (stationKey.equals(markOf(display)) || isGearDisplay(display)) {
                display.remove();
            }
        }
        if (savedDisplay != null && (keep == null || !keep.equals(savedDisplay))) {
            Entity entity = Bukkit.getEntity(savedDisplay);
            if (entity != null) {
                entity.remove();
            }
        }
    }

    private static boolean isGearDisplay(ItemDisplay display) {
        ItemStack stack = display.getItemStack();
        if (stack == null || !stack.hasItemMeta()) {
            return false;
        }
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) {
            return false;
        }
        return meta.getPersistentDataContainer().has(GearKeys.archetype(), PersistentDataType.STRING)
                || meta.getPersistentDataContainer().has(GearKeys.parts(), PersistentDataType.STRING);
    }

    private static String markOf(ItemDisplay display) {
        return display.getPersistentDataContainer().get(GearKeys.stationDisplay(), PersistentDataType.STRING);
    }

    private static void mark(ItemDisplay display, Location location) {
        display.getPersistentDataContainer().set(
                GearKeys.stationDisplay(), PersistentDataType.STRING, key(location));
    }

    private static void drop(Location location, ItemStack item) {
        if (location.getWorld() == null || item == null) {
            return;
        }
        location.getWorld().dropItem(location.clone().add(0.5, 1.0, 0.5), item.clone());
    }

    private enum Furniture {
        PRESENT,
        ABSENT,
        UNKNOWN
    }

    /**
     * Present when the station furniture still occupies the block. Absent only when
     * the barrier hitbox is gone, so a lookup miss on a still-standing station does
     * not drop the weapon.
     */
    private static Furniture furniture(Location location) {
        Block block = location.getBlock();
        try {
            if (TLibs.getBlockAPI().getChecker().checkBlock(block, GearCache.station)) {
                return Furniture.PRESENT;
            }
        } catch (RuntimeException ex) {
            return Furniture.UNKNOWN;
        }
        if (block.getType() != Material.BARRIER) {
            return Furniture.ABSENT;
        }
        return Furniture.UNKNOWN;
    }

    private static UUID spawnDisplay(Location location, ItemStack item) {
        World world = location.getWorld();
        if (world == null) {
            return null;
        }
        Location at = displayLocation(location);
        ItemDisplay display = world.spawn(at, ItemDisplay.class, spawned -> {
            spawned.setItemStack(item);
            spawned.setBillboard(Billboard.CENTER);
            spawned.setPersistent(true);
            spawned.setInterpolationDuration(0);
            mark(spawned, location);
            Transformation transform = spawned.getTransformation();
            spawned.setTransformation(new Transformation(
                    transform.getTranslation(),
                    new AxisAngle4f(),
                    new Vector3f(0.6f, 0.6f, 0.6f),
                    new AxisAngle4f()));
        });
        return display.getUniqueId();
    }

    private static Location displayLocation(Location location) {
        return location.clone().add(0.5, 1.15, 0.5);
    }

    private static UUID uuidOf(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private static Map<String, Integer> readCharged(ConfigurationSection section) {
        ConfigurationSection chargedSection = section.getConfigurationSection("charged");
        if (chargedSection == null) {
            return null;
        }
        Map<String, Integer> charged = new LinkedHashMap<>();
        for (String index : chargedSection.getKeys(false)) {
            String path = chargedSection.getString(index + ".path");
            int amount = chargedSection.getInt(index + ".amount");
            if (path != null && !path.isBlank() && amount > 0) {
                charged.merge(path, amount, Integer::sum);
            }
        }
        return charged;
    }

    private static File file() {
        return new File(Magic.plugin.getDataFolder(), "data/gear-stations.yml");
    }

    public static String key(Location location) {
        if (location == null || location.getWorld() == null) {
            return "";
        }
        return location.getWorld().getName() + "," + location.getBlockX() + ","
                + location.getBlockY() + "," + location.getBlockZ();
    }

    public static Location locationFromKey(String key) {
        String[] bits = key.split(",");
        if (bits.length != 4) {
            return null;
        }
        World world = Bukkit.getWorld(bits[0]);
        if (world == null) {
            return null;
        }
        try {
            return new Location(world, Integer.parseInt(bits[1]), Integer.parseInt(bits[2]), Integer.parseInt(bits[3]));
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private static Location locationOf(ConfigurationSection section) {
        World world = Bukkit.getWorld(section.getString("world", ""));
        if (world == null) {
            return null;
        }
        return new Location(world, section.getInt("x"), section.getInt("y"), section.getInt("z"));
    }
}
