package net.tfminecraft.magic.gear;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import me.Plugins.TLibs.TLibs;
import net.tfminecraft.magic.util.ItemRef;

public final class GearCosts {

    private GearCosts() {}

    public static Map<String, Integer> total(Collection<PartDef> parts) {
        Map<String, Integer> costs = new HashMap<>();
        if (parts == null) {
            return costs;
        }
        for (PartDef part : parts) {
            if (part == null || !part.hasCost()) {
                continue;
            }
            for (Map.Entry<String, Integer> entry : part.getCost().entrySet()) {
                costs.merge(entry.getKey(), entry.getValue(), Integer::sum);
            }
        }
        return costs;
    }

    public static boolean has(Player player, Collection<PartDef> parts) {
        if (player != null && player.hasPermission("magic.bypass_crafting_cost")) {
            return true;
        }
        Map<String, Integer> costs = total(parts);
        for (Map.Entry<String, Integer> entry : costs.entrySet()) {
            int remaining = entry.getValue();
            for (ItemStack item : player.getInventory().getContents()) {
                if (item == null) {
                    continue;
                }
                try {
                    if (TLibs.getItemAPI().getChecker().checkItemWithPath(item, entry.getKey())) {
                        remaining -= item.getAmount();
                        if (remaining <= 0) {
                            break;
                        }
                    }
                } catch (Exception ignored) {
                    // bad path
                }
            }
            if (remaining > 0) {
                return false;
            }
        }
        return true;
    }

    public static void take(Player player, Collection<PartDef> parts) {
        if (player == null || player.hasPermission("magic.bypass_crafting_cost")) {
            return;
        }
        for (Map.Entry<String, Integer> entry : total(parts).entrySet()) {
            int toRemove = entry.getValue();
            for (ItemStack item : player.getInventory().getContents()) {
                if (item == null || toRemove <= 0) {
                    continue;
                }
                try {
                    if (!TLibs.getItemAPI().getChecker().checkItemWithPath(item, entry.getKey())) {
                        continue;
                    }
                } catch (Exception ex) {
                    continue;
                }
                int remove = Math.min(item.getAmount(), toRemove);
                item.setAmount(item.getAmount() - remove);
                toRemove -= remove;
            }
        }
        player.updateInventory();
    }

    public static void refund(Player player, Collection<PartDef> parts, Location drop) {
        if (player == null) {
            return;
        }
        Location at = drop == null ? player.getLocation() : drop.clone().add(0.5, 1.0, 0.5);
        for (Map.Entry<String, Integer> entry : total(parts).entrySet()) {
            int remaining = entry.getValue();
            while (remaining > 0) {
                ItemStack stack = ItemRef.build(entry.getKey());
                if (stack == null || stack.getType().isAir()) {
                    break;
                }
                int give = Math.min(remaining, Math.max(1, stack.getMaxStackSize()));
                stack.setAmount(give);
                remaining -= give;
                for (ItemStack leftover : player.getInventory().addItem(stack).values()) {
                    if (leftover != null && !leftover.getType().isAir() && at.getWorld() != null) {
                        at.getWorld().dropItem(at, leftover);
                    }
                }
            }
        }
        player.updateInventory();
    }
}
