package net.tfminecraft.magic.blood;

import java.util.List;
import java.util.function.Function;

import org.bukkit.Bukkit;
import org.bukkit.Location;

/** Region lookup for altar placement. WorldGuard types stay out of this class. */
public final class AltarRegions {

    private static Function<Location, List<String>> query;

    private AltarRegions() {}

    /** Replaces the lookup; null goes back to WorldGuard when it is enabled. */
    static void setQuery(Function<Location, List<String>> replacement) {
        query = replacement;
    }

    /** Ids of the regions covering the location; empty without WorldGuard. */
    public static List<String> at(Location location) {
        if (query == null) {
            if (!Bukkit.getPluginManager().isPluginEnabled("WorldGuard")) {
                return List.of();
            }
            query = WorldGuardAltarRegions::regionsAt;
        }
        return query.apply(location);
    }

    /** True when a region the config does not allow covers the location. */
    public static boolean blocked(Location location) {
        if (!BloodConfig.denyRegions()) {
            return false;
        }
        for (String id : at(location)) {
            if (!BloodConfig.regionAllowed(id)) {
                return true;
            }
        }
        return false;
    }
}
