package net.tfminecraft.magic.gear;

import java.util.List;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

import net.tfminecraft.tlibs.TLibs;
import net.tfminecraft.magic.Messages;
import net.tfminecraft.magic.charge.ChargeIds;
import net.tfminecraft.magic.gear.gui.GearInventoryManager;
import net.tfminecraft.magic.gear.orb.GearOrbService;
import net.tfminecraft.magic.gear.gui.OpenStationManager;

public final class GearStationListener implements Listener {

    private final GearInventoryManager inventory = new GearInventoryManager();

    public GearInventoryManager inventory() {
        return inventory;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        Block block = event.getClickedBlock();
        if (block == null) {
            return;
        }
        try {
            if (!TLibs.getBlockAPI().getChecker().checkBlock(block, GearCache.station)) {
                return;
            }
        } catch (Exception ex) {
            return;
        }
        event.setCancelled(true);
        Player player = event.getPlayer();
        Location location = block.getLocation();
        ItemStack hand = player.getInventory().getItemInMainHand();
        boolean emptyHand = hand == null || hand.getType().isAir() || hand.getType() == Material.AIR;

        if (GearStationStore.isOccupied(location)) {
            if (emptyHand) {
                eject(player, location);
                return;
            }
            if (player.isSneaking()) {
                return;
            }
            if (ChargeIds.isCharge(hand)) {
                GearChargeService.tryApply(player, location, hand);
                return;
            }
            player.sendMessage(Messages.get("gear.station.occupied"));
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
            return;
        }

        if (player.isSneaking()) {
            return;
        }
        if (GearBrokenMarker.isBroken(hand)) {
            reclaim(player, location, hand);
            return;
        }
        OpenStationManager.set(player, location);
        inventory.openAssembly(player);
    }

    /**
     * Deliberate reclaim of runes a revision knocked loose. Runs on the held weapon at an
     * empty station, so it never competes with a prepared craft. A weapon whose part ids
     * were deleted outright stays damaged, since only restoring the ids can fix it.
     */
    private static void reclaim(Player player, Location location, ItemStack weapon) {
        List<ItemStack> runes = GearBrokenMarker.buildOrphanItems(weapon);
        int held = GearBrokenMarker.orphans(weapon).size();
        if (held == 0) {
            player.sendMessage(Messages.get("gear.reclaim.none"));
        }

        ItemStack restored = weapon.clone();
        GearBrokenMarker.clear(restored);
        List<String> missing = GearProvenance.missingPartIds(restored);
        if (missing.isEmpty()) {
            ItemStack refreshed = GearRefresher.refresh(restored, player, true);
            if (refreshed != null) {
                restored = refreshed;
            } else {
                WeaponLore.apply(restored);
            }
        } else {
            GearBrokenMarker.mark(restored);
        }
        player.getInventory().setItemInMainHand(restored);

        Location drop = location.clone().add(0.5, 1.0, 0.5);
        for (ItemStack rune : runes) {
            for (ItemStack leftover : player.getInventory().addItem(rune).values()) {
                if (leftover != null && !leftover.getType().isAir() && drop.getWorld() != null) {
                    drop.getWorld().dropItem(drop, leftover);
                }
            }
        }
        player.updateInventory();

        if (held > 0) {
            player.sendMessage(Messages.get("gear.reclaim.done", "count", String.valueOf(held)));
            player.playSound(location, Sound.BLOCK_GRINDSTONE_USE, 1f, 1.1f);
        }
        if (missing.isEmpty()) {
            player.sendMessage(Messages.get("gear.reclaim.repaired"));
        } else {
            player.sendMessage(Messages.get("gear.reclaim.still_broken", "missing", String.join(", ", missing)));
            player.playSound(location, Sound.ENTITY_VILLAGER_NO, 1f, 1f);
        }
    }

    private static void eject(Player player, Location location) {
        GearStationStore.Occupancy occupancy = GearStationStore.get(location);
        if ((occupancy != null && occupancy.isOrbSessionActive()) || GearOrbService.isActive(location)) {
            return;
        }
        if (occupancy != null && !GearStationStore.isAttuned(occupancy.getItem())) {
            player.sendMessage(Messages.get("gear.eject.unattuned"));
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
            return;
        }
        ItemStack item = GearStationStore.eject(location);
        if (item == null) {
            return;
        }
        Location drop = location.clone().add(0.5, 1.0, 0.5);
        drop.getWorld().dropItem(drop, item);
        player.playSound(location, Sound.ENTITY_ITEM_PICKUP, 1f, 1f);
    }
}
