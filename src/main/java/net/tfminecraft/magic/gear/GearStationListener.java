package net.tfminecraft.magic.gear;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

import dev.lone.itemsadder.api.Events.FurnitureBreakEvent;
import net.tfminecraft.tlibs.TLibs;
import net.tfminecraft.magic.Magic;
import net.tfminecraft.magic.Messages;
import net.tfminecraft.magic.charge.ChargeIds;
import net.tfminecraft.magic.gear.gui.GearInventoryManager;
import net.tfminecraft.magic.gear.orb.GearOrbService;
import net.tfminecraft.magic.gear.gui.OpenStationManager;

public final class GearStationListener implements Listener {

    /** ItemsAdder breaks furniture two ticks after the swing, so an abort swing needs cover. */
    private static final long ABORT_BREAK_GUARD_MILLIS = 1000L;

    /** Reaches a frame in this station's block and not the center of a neighbouring block. */
    private static final double STATION_REACH = 0.75;

    private final GearInventoryManager inventory = new GearInventoryManager();
    private final Map<String, Long> recentAborts = new HashMap<>();

    public GearInventoryManager inventory() {
        return inventory;
    }

    /**
     * Left-clicks at the station (orb hits, aborts) are swings, and ItemsAdder breaks
     * furniture on a swing. A station holding a weapon or running orbs must survive them.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onFurnitureBreak(FurnitureBreakEvent event) {
        if (!isStationFurniture(event.getNamespacedID())) {
            return;
        }
        Entity entity = event.getBukkitEntity();
        if (entity == null) {
            return;
        }
        Location at = entity.getLocation();
        if (recentlyAbortedNear(at) || GearStationStore.occupiedWithin(at, STATION_REACH) != null) {
            event.setCancelled(true);
        }
    }

    /**
     * A break that was not cancelled has removed the furniture. Drop that station's
     * weapon instead of leaving the display in the air.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onFurnitureBroken(FurnitureBreakEvent event) {
        if (!isStationFurniture(event.getNamespacedID())) {
            return;
        }
        Entity entity = event.getBukkitEntity();
        if (entity == null) {
            return;
        }
        Location station = GearStationStore.occupiedWithin(entity.getLocation(), STATION_REACH);
        if (station == null) {
            return;
        }
        GearOrbService.abort(station);
        ItemStack weapon = GearStationStore.takeForAbort(station);
        if (weapon != null && station.getWorld() != null) {
            station.getWorld().dropItem(station.clone().add(0.5, 1.0, 0.5), weapon);
            Magic.plugin.getLogger().warning("[Magic] Gear station at " + GearStationStore.key(station)
                    + " lost its furniture. Dropped the weapon and removed its display.");
        }
    }

    @EventHandler
    public void onChunkLoad(ChunkLoadEvent event) {
        GearStationStore.reconcile(event.getChunk());
    }

    private boolean recentlyAbortedNear(Location at) {
        long now = System.currentTimeMillis();
        recentAborts.entrySet().removeIf(entry -> now - entry.getValue() >= ABORT_BREAK_GUARD_MILLIS);
        for (Map.Entry<String, Long> entry : recentAborts.entrySet()) {
            Location station = GearStationStore.locationFromKey(entry.getKey());
            if (station != null
                    && GearStationStore.distanceSquaredToCenter(station, at) <= STATION_REACH * STATION_REACH) {
                return true;
            }
        }
        return false;
    }

    private static boolean isStationFurniture(String namespacedId) {
        String station = GearCache.station == null ? "" : GearCache.station.trim();
        int open = station.indexOf('(');
        int close = station.indexOf(')', open + 1);
        if (namespacedId == null || !station.toLowerCase().startsWith("iaf(") || close <= open) {
            return false;
        }
        return station.substring(open + 1, close).equalsIgnoreCase(namespacedId);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() == Action.LEFT_CLICK_BLOCK) {
            tryAbort(event);
            return;
        }
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

    private void tryAbort(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        if (!event.getPlayer().isSneaking()) {
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
        GearStationStore.Occupancy occupancy = GearStationStore.get(location);
        if (occupancy == null) {
            return;
        }
        if (GearStationStore.isAttuned(occupancy.getItem())) {
            return;
        }
        UUID owner = occupancy.getOwner() != null ? occupancy.getOwner() : GearOrbService.sessionOwner(location);
        if (owner != null && !owner.equals(player.getUniqueId()) && !player.hasPermission("magic.admin")) {
            player.sendMessage(Messages.get("gear.abort.not_yours"));
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
            return;
        }
        GearOrbService.abort(location);
        ItemStack weapon = GearStationStore.takeForAbort(location);
        if (weapon == null) {
            return;
        }
        recentAborts.values().removeIf(at -> System.currentTimeMillis() - at >= ABORT_BREAK_GUARD_MILLIS);
        recentAborts.put(GearStationStore.key(location), System.currentTimeMillis());
        Map<String, Integer> charged = occupancy.getCharged() != null
                ? occupancy.getCharged()
                : GearCosts.total(GearProvenance.resolveParts(weapon));
        GearCosts.refund(player, charged, location);
        player.sendMessage(Messages.get("gear.abort.done"));
        player.playSound(location, Sound.BLOCK_ANVIL_LAND, 0.6f, 1.4f);
    }
}
