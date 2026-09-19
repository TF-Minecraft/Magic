package net.tfminecraft.magic.gear;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Display.Billboard;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import net.tfminecraft.magic.Magic;

public final class GearStationStore {

    private static final Map<String, Occupancy> OCCUPIED = new HashMap<>();

    private GearStationStore() {}

    public static final class Occupancy {
        private ItemStack item;
        private UUID displayId;
        private boolean orbSessionActive;

        Occupancy(ItemStack item, UUID displayId) {
            this.item = item;
            this.displayId = displayId;
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

    public static Occupancy occupy(Location location, ItemStack item) {
        if (location == null || item == null) {
            return null;
        }
        clear(location, false);
        UUID displayId = spawnDisplay(location, item);
        Occupancy occupancy = new Occupancy(item, displayId);
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
        for (Occupancy occupancy : OCCUPIED.values()) {
            if (occupancy != null && occupancy.displayId != null) {
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
            UUID displayId = spawnDisplay(location, item);
            OCCUPIED.put(key(location), new Occupancy(item, displayId));
        }
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
        if (occupancy != null && occupancy.displayId != null) {
            Entity entity = Bukkit.getEntity(occupancy.displayId);
            if (entity != null) {
                entity.remove();
            }
        }
        if (save) {
            save();
        }
    }

    private static UUID spawnDisplay(Location location, ItemStack item) {
        World world = location.getWorld();
        if (world == null) {
            return null;
        }
        Location at = location.clone().add(0.5, 1.15, 0.5);
        ItemDisplay display = world.spawn(at, ItemDisplay.class, spawned -> {
            spawned.setItemStack(item);
            spawned.setBillboard(Billboard.CENTER);
            spawned.setPersistent(true);
            spawned.setInterpolationDuration(0);
            Transformation transform = spawned.getTransformation();
            spawned.setTransformation(new Transformation(
                    transform.getTranslation(),
                    new AxisAngle4f(),
                    new Vector3f(0.6f, 0.6f, 0.6f),
                    new AxisAngle4f()));
        });
        return display.getUniqueId();
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

    private static Location locationFromKey(String key) {
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
