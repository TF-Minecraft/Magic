package net.tfminecraft.magic.gear.gui;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.bukkit.entity.Player;

public final class SelectedPartsManager {

    private static final Map<UUID, Map<String, String>> SELECTED = new HashMap<>();

    private SelectedPartsManager() {}

    public static String get(Player player, String categoryId) {
        if (player == null || categoryId == null) {
            return null;
        }
        Map<String, String> map = SELECTED.get(player.getUniqueId());
        return map == null ? null : map.get(categoryId);
    }

    public static void set(Player player, String categoryId, String partId) {
        if (player == null || categoryId == null || partId == null) {
            return;
        }
        SELECTED.computeIfAbsent(player.getUniqueId(), key -> new HashMap<>())
                .put(categoryId, partId);
    }

    public static void clear(Player player) {
        if (player != null) {
            SELECTED.remove(player.getUniqueId());
        }
    }
}
