package net.tfminecraft.magic.gear.gui;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.bukkit.Location;
import org.bukkit.entity.Player;

public final class OpenStationManager {

    private static final Map<UUID, Location> OPEN = new HashMap<>();

    private OpenStationManager() {}

    public static void set(Player player, Location location) {
        if (player == null || location == null) {
            return;
        }
        OPEN.put(player.getUniqueId(), location.clone());
    }

    public static Location get(Player player) {
        if (player == null) {
            return null;
        }
        Location location = OPEN.get(player.getUniqueId());
        return location == null ? null : location.clone();
    }

    public static void clear(Player player) {
        if (player != null) {
            OPEN.remove(player.getUniqueId());
        }
    }
}
