package net.tfminecraft.magic.gear;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import net.tfminecraft.tlibs.TLibs;
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
                costs.merge(entry.getKey(), entry.getValue(),
                        (a, b) -> (int) Math.min(Integer.MAX_VALUE, (long) a + b));
            }
        }
        return costs;
    }

    public static boolean bypasses(Player player) {
        return player != null && player.hasPermission("magic.bypass_crafting_cost");
    }

    /**
     * The share of these costs a re-attunement takes, rounded up per material so a
     * nonzero fraction never makes a material free.
     */
    public static Map<String, Integer> fraction(Map<String, Integer> costs, double fraction) {
        Map<String, Integer> share = new HashMap<>();
        if (costs == null || !(fraction > 0)) {
            return share;
        }
        for (Map.Entry<String, Integer> entry : costs.entrySet()) {
            Integer amount = entry.getValue();
            if (amount == null || amount <= 0) {
                continue;
            }
            // The epsilon keeps float error (10 * 0.3 = 3.0000000000000004) from adding an item.
            share.put(entry.getKey(), (int) Math.min(Integer.MAX_VALUE, (long) Math.ceil(amount * fraction - 1e-9)));
        }
        return share;
    }

    public static boolean has(Player player, Collection<PartDef> parts) {
        return has(player, total(parts));
    }

    public static boolean has(Player player, Map<String, Integer> costs) {
        if (bypasses(player)) {
            return true;
        }
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
        if (player == null || bypasses(player)) {
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

    /** Plans removal on clones; a deposit checks the captured frame again before commit. */
    public static ItemStack[] plan(Player player, Collection<PartDef> parts) {
        return plan(player, total(parts));
    }

    public static ItemStack[] plan(Player player, Map<String, Integer> costs) {
        ItemStack[] after = net.tfminecraft.magic.gear.ownership.GearEffects.cloneContents(player.getInventory().getContents());
        if (bypasses(player)) return after;
        for (var cost : costs.entrySet()) {
            int remaining = cost.getValue();
            for (int i = 0; i < after.length && remaining > 0; i++) {
                ItemStack item = after[i]; if (item == null) continue;
                try { if (!TLibs.getItemAPI().getChecker().checkItemWithPath(item, cost.getKey())) continue; }
                catch (RuntimeException ex) { continue; }
                int remove = Math.min(remaining, item.getAmount()); remaining -= remove;
                if (remove == item.getAmount()) after[i] = null; else item.setAmount(item.getAmount() - remove);
            }
            if (remaining > 0) return null;
        }
        return after;
    }

    public static void refund(Player player, Map<String, Integer> costs, Location drop) {
        if (player == null || costs == null || costs.isEmpty()) {
            return;
        }
        Location at = drop == null ? player.getLocation() : drop.clone().add(0.5, 1.0, 0.5);
        for (Map.Entry<String, Integer> entry : costs.entrySet()) {
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
                    if (at.getWorld() != null) {
                        at.getWorld().dropItem(at, leftover);
                    }
                }
            }
        }
        player.updateInventory();
    }
}
