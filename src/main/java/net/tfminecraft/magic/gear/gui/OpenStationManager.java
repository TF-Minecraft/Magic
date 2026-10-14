package net.tfminecraft.magic.gear.gui;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.bukkit.Location;
import org.bukkit.entity.Player;

public final class OpenStationManager {

    private static final Map<UUID, Location> OPEN = new HashMap<>();
    private static final Map<UUID, net.tfminecraft.magic.gear.ownership.GearIdentity.Token> TOKENS = new HashMap<>();

    private OpenStationManager() {}

    public static void set(Player player, Location location) {
        if (player == null || location == null) {
            return;
        }
        OPEN.put(player.getUniqueId(), location.clone());
        net.tfminecraft.magic.gear.GearStationStore.property().identity().current(player).ifPresentOrElse(
                token -> TOKENS.put(player.getUniqueId(), token), () -> TOKENS.remove(player.getUniqueId()));
    }

    public static Location get(Player player) {
        if (player == null) {
            return null;
        }
        if (!net.tfminecraft.magic.gear.GearStationStore.property().identity().current(TOKENS.get(player.getUniqueId()), player)) return null;
        Location location = OPEN.get(player.getUniqueId());
        return location == null ? null : location.clone();
    }

    public static void clear(Player player) {
        if (player != null) {
            OPEN.remove(player.getUniqueId());
            TOKENS.remove(player.getUniqueId());
        }
    }
    public static void clear(Player player, net.tfminecraft.magic.gear.ownership.GearIdentity.Token outgoing) {
        if (player != null && java.util.Objects.equals(outgoing, TOKENS.get(player.getUniqueId()))) clear(player);
    }
    public static boolean matches(Player player, net.tfminecraft.magic.gear.ownership.GearIdentity.Token token) {
        return token != null && token.equals(TOKENS.get(player.getUniqueId()));
    }
}
