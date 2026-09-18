package net.tfminecraft.magic.gear;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import net.tfminecraft.magic.Magic;
import net.tfminecraft.magic.Messages;
import net.tfminecraft.magic.charge.Charge;
import net.tfminecraft.magic.charge.TierBands;
import net.tfminecraft.magic.gear.orb.GearOrbService;
import net.tfminecraft.magic.model.ElementDef;
import net.tfminecraft.magic.registry.ElementRegistry;
import net.tfminecraft.magic.session.ResonanceSession;

public final class GearChargeService {

    private static final Map<UUID, Confirm> CONFIRMS = new HashMap<>();

    private GearChargeService() {}

    private static final class Confirm {
        private final String stationKey;
        private final long expiresAt;

        private Confirm(String stationKey, long expiresAt) {
            this.stationKey = stationKey;
            this.expiresAt = expiresAt;
        }
    }

    public static void tryApply(Player player, Location station, ItemStack hand) {
        Charge charge = Charge.fromItem(hand);
        if (charge == null) {
            player.sendMessage(Messages.get("gear.charge.not_charge"));
            return;
        }
        if (charge.isBlank() || WeaponRequirement.highestBand(charge) <= 0) {
            player.sendMessage(Messages.get("gear.charge.blank"));
            return;
        }
        GearStationStore.Occupancy occupancy = GearStationStore.get(station);
        if (occupancy == null) {
            player.sendMessage(Messages.get("gear.station.empty"));
            return;
        }
        if (occupancy.isOrbSessionActive() || GearOrbService.isActive(station)) {
            player.sendMessage(Messages.get("gear.orbs.busy"));
            return;
        }
        if (GearBrokenMarker.isBroken(occupancy.getItem())) {
            player.sendMessage(Messages.get("gear.broken.charge"));
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
            return;
        }
        String stationKey = GearStationStore.key(station);
        List<String> over = overResonance(player, charge);
        if (!over.isEmpty() && !confirmed(player, stationKey)) {
            player.sendMessage(Messages.get("gear.charge.warn"));
            for (String line : over) {
                player.sendMessage(line);
            }
            player.sendMessage(Messages.get("gear.charge.warn_again"));
            CONFIRMS.put(player.getUniqueId(), new Confirm(
                    stationKey, System.currentTimeMillis() + GearCache.confirmMillis));
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1f, 0.8f);
            return;
        }
        CONFIRMS.remove(player.getUniqueId());
        Map<String, Double> snapshot = new LinkedHashMap<>();
        for (String elementId : charge.getCappedElementIds()) {
            double fill = charge.getFill(elementId);
            if (fill > 0) {
                snapshot.put(elementId, fill);
            }
        }
        // The charge is spent up front. What reaches the weapon is decided by the orbs.
        occupancy.setOrbSessionActive(true);
        if (!GearOrbService.begin(player, station, snapshot, charge.primaryElementId(), charge.getTier())) {
            occupancy.setOrbSessionActive(false);
            player.sendMessage(Messages.get("gear.orbs.busy"));
            return;
        }
        consumeOne(player, hand);
        player.sendMessage(Messages.get("gear.charge.applied"));
        player.playSound(player.getLocation(), Sound.BLOCK_ENCHANTMENT_TABLE_USE, 1f, 1.2f);
    }

    private static boolean confirmed(Player player, String stationKey) {
        Confirm confirm = CONFIRMS.get(player.getUniqueId());
        if (confirm == null || System.currentTimeMillis() > confirm.expiresAt) {
            return false;
        }
        return confirm.stationKey.equals(stationKey);
    }

    private static List<String> overResonance(Player player, Charge charge) {
        List<String> lines = new ArrayList<>();
        ResonanceSession session = Magic.plugin.getResonanceGuiManager().getSessionManager().get(player);
        for (ElementDef element : ElementRegistry.getAll()) {
            double fill = charge.getFill(element.getId());
            if (charge.getCap(element.getId()) <= 0 && fill <= 0) {
                continue;
            }
            int needed = TierBands.bandOf(element.getId(), fill);
            if (needed <= 0) {
                continue;
            }
            double actual = session == null ? 0.0 : session.getResonance(element.getId());
            int have = TierBands.bandOf(element.getId(), actual);
            if (have < needed) {
                String needNum = TierBands.numeral(needed);
                String haveNum = have <= 0 ? "-" : TierBands.numeral(have);
                lines.add(Messages.get(
                        "gear.charge.warn_line",
                        "element", element.getName(),
                        "need", needNum,
                        "have", haveNum));
            }
        }
        return lines;
    }

    private static void consumeOne(Player player, ItemStack hand) {
        if (hand.getAmount() <= 1) {
            player.getInventory().setItemInMainHand(null);
        } else {
            hand.setAmount(hand.getAmount() - 1);
        }
        player.updateInventory();
    }
}
