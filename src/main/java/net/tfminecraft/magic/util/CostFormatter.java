package net.tfminecraft.magic.util;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

import org.bukkit.inventory.ItemStack;

import me.Plugins.TLibs.TLibs;
import me.Plugins.TLibs.Objects.API.SubAPI.StringFormatter;

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
        for (String path : new TreeSet<>(map.keySet())) {
            int amount = map.get(path);
            ItemStack item = TLibs.getItemAPI().getCreator().getItemFromPath(path);
            result.add("§7- §f" + StringFormatter.getName(item) + " §e" + amount);
        }
        return result;
    }
}
