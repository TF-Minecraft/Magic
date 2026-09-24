package net.tfminecraft.magic.util;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import net.tfminecraft.tlibs.TLibs;
import net.tfminecraft.tlibs.objects.api.subapi.StringFormatter;

public final class CostFormatter {

    private CostFormatter() {}

    public static void appendInput(List<String> lore, Map<String, Integer> costs) {
        if (lore == null || costs == null || costs.isEmpty()) {
            return;
        }
        lore.add("");
        lore.add(StringFormatter.formatHex("#76de91§lInput:"));
        lore.addAll(getCostsFormatted(costs));
    }

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
