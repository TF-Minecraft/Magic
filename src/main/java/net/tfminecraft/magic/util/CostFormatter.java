package net.tfminecraft.magic.util;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import me.Plugins.TLibs.TLibs;
import me.Plugins.TLibs.Objects.API.SubAPI.StringFormatter;

public final class CostFormatter {

    private CostFormatter() {}

    public static List<String> getCostsFormatted(Map<String, Integer> map) {
        List<String> result = new ArrayList<>();
        if (map == null || map.isEmpty()) {
            return result;
        }
        for (String path : new TreeSet<>(map.keySet())) {
            Integer amount = map.get(path);
            if (path == null || amount == null) {
                continue;
            }
            result.add("§7- §f" + displayName(path) + " §e" + amount);
        }
        return result;
    }

    private static String displayName(String path) {
        try {
            ItemStack item = TLibs.getItemAPI().getCreator().getItemFromPath(path);
            if (item != null && item.getType() != Material.AIR) {
                String name = StringFormatter.getName(item);
                if (name != null && !name.isBlank()) {
                    return name;
                }
            }
        } catch (Exception ignored) {
            // fall through to path
        }
        return path;
    }
}
