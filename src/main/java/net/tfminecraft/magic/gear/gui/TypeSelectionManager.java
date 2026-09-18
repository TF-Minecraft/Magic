package net.tfminecraft.magic.gear.gui;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.bukkit.entity.Player;

import net.tfminecraft.magic.gear.GearType;

public final class TypeSelectionManager {

    private static final Map<UUID, GearType> SELECTED = new HashMap<>();

    private TypeSelectionManager() {}

    public static GearType get(Player player) {
        if (player == null) {
            return GearType.STAFF;
        }
        return SELECTED.getOrDefault(player.getUniqueId(), GearType.STAFF);
    }

    public static void set(Player player, GearType type) {
        if (player == null || type == null) {
            return;
        }
        SELECTED.put(player.getUniqueId(), type);
    }

    public static void clear(Player player) {
        if (player != null) {
            SELECTED.remove(player.getUniqueId());
        }
    }
}
