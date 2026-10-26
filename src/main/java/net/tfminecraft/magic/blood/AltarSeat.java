package net.tfminecraft.magic.blood;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;

import net.tfminecraft.magic.Magic;

/**
 * The altar is the middle of a meditation circle: its owner sits on it (right-click with an empty
 * hand) and meditation starts there like on any seat. The seat is an invisible, unsaved display on
 * top of the altar; it goes as soon as it is left.
 */
public final class AltarSeat {

    private static final Map<String, Entity> SEATS = new HashMap<>();

    private AltarSeat() {}

    static NamespacedKey key() {
        return new NamespacedKey(Magic.plugin, "altar_seat");
    }

    /** Sits the player on top of the altar; false when they already ride something or it is not loaded. */
    public static boolean sit(Player player, BloodAltar altar) {
        Location center = altar.center();
        if (player.isInsideVehicle() || center == null) {
            return false;
        }
        clear(altar);
        Location top = center.clone().add(0, 0.5, 0);
        top.setYaw(player.getLocation().getYaw());
        ItemDisplay seat = top.getWorld().spawn(top, ItemDisplay.class, display -> {
            display.setPersistent(false);
            display.getPersistentDataContainer().set(key(), PersistentDataType.STRING, altar.getId());
        });
        seat.addPassenger(player);
        SEATS.put(altar.getId(), seat);
        BloodFx.play("altar_sit", center, player);
        return true;
    }

    public static boolean isSeat(Entity entity) {
        return entity != null && entity.getPersistentDataContainer().has(key(), PersistentDataType.STRING);
    }

    /** A seat that was left goes. */
    public static void left(Entity vehicle) {
        if (!isSeat(vehicle)) {
            return;
        }
        SEATS.values().remove(vehicle);
        vehicle.remove();
    }

    /** Takes the altar's seat away, whoever sits on it, e.g. when the altar is purified. */
    public static void clear(BloodAltar altar) {
        Entity seat = SEATS.remove(altar.getId());
        if (seat != null) {
            unseat(seat);
        }
    }

    static void clearAll() {
        List<Entity> seats = List.copyOf(SEATS.values());
        SEATS.clear();
        for (Entity seat : seats) {
            unseat(seat);
        }
    }

    private static void unseat(Entity seat) {
        for (Entity passenger : List.copyOf(seat.getPassengers())) {
            passenger.leaveVehicle();
        }
        seat.remove();
    }
}
