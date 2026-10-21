package net.tfminecraft.magic.gear;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import net.tfminecraft.magic.Messages;
import net.tfminecraft.magic.gear.ownership.GearEffects;
import net.tfminecraft.magic.gear.ownership.GearIdentity;
import net.tfminecraft.magic.gear.ownership.GearItems;
import net.tfminecraft.magic.gear.ownership.GearPropertyStore;
import net.tfminecraft.magic.util.CostFormatter;

/**
 * Wipes an attuned weapon's attunement so it can be attuned anew. The first sneak-click
 * names the fee; a second one within the confirm window pays it and rests the weapon.
 * The old attunement holds until a run catches something.
 */
public final class GearReattuneService {

    private static final Map<UUID, Confirm> CONFIRMS = new HashMap<>();

    private GearReattuneService() {}

    private record Confirm(String stationKey, GearIdentity.Token token, String itemHash, long expiresAt) {}

    /** The weapon's current part costs, scaled by {@code gear.reattune.cost_fraction}. */
    public static Map<String, Integer> fee(ItemStack weapon) {
        return GearCosts.fraction(GearCosts.total(GearProvenance.resolveParts(weapon)), GearCache.reattuneCostFraction);
    }

    public static void clear(Player player) {
        CONFIRMS.remove(player.getUniqueId());
    }

    public static void request(Player player, Location station, ItemStack hand) {
        var current = GearStationStore.property().identity().current(player);
        if (current.isEmpty()) return;
        GearIdentity.Token token = current.orElseThrow();
        boolean bypass = GearCosts.bypasses(player);
        Map<String, Integer> fee = bypass ? Map.of() : fee(hand);
        String stationKey = GearStationStore.key(station);
        String itemHash = GearPropertyStore.hash(GearItems.bytes(hand));
        Confirm confirm = CONFIRMS.remove(player.getUniqueId());
        if (confirm == null || System.currentTimeMillis() > confirm.expiresAt() || !confirm.stationKey().equals(stationKey)
                || !confirm.token().equals(token) || !confirm.itemHash().equals(itemHash)) {
            CONFIRMS.put(player.getUniqueId(), new Confirm(stationKey, token, itemHash, System.currentTimeMillis() + GearCache.confirmMillis));
            player.sendMessage(Messages.get("gear.reattune.warn"));
            List<String> lines = CostFormatter.getCostsFormatted(fee);
            if (lines.isEmpty()) {
                player.sendMessage(Messages.get("gear.reattune.free"));
            }
            for (String line : lines) {
                player.sendMessage(line);
            }
            player.sendMessage(Messages.get("gear.reattune.confirm"));
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1f, 0.8f);
            return;
        }
        List<String> before = GearEffects.frame(player.getInventory().getContents());
        ItemStack[] after = GearCosts.plan(player, fee);
        int slot = player.getInventory().getHeldItemSlot();
        if (after == null || after[slot] == null || !Arrays.equals(GearItems.bytes(hand), GearItems.bytes(after[slot]))) {
            player.sendMessage(Messages.get("gear.reattune.lacking"));
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
            return;
        }
        if (after[slot].getAmount() <= 1) after[slot] = null; else after[slot].setAmount(after[slot].getAmount() - 1);
        ItemStack placed = hand.clone();
        placed.setAmount(1);
        if (GearEffects.deposit(player, token, station, placed, fee, true, true, after, before) == null) return;
        player.sendMessage(Messages.get("gear.reattune.placed"));
        if (bypass) {
            player.sendMessage(Messages.get("gear.craft.bypassed"));
        }
        player.playSound(station, Sound.BLOCK_ANVIL_USE, 1f, 1.2f);
    }
}
