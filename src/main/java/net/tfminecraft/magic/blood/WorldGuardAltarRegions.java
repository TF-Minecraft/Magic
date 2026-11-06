package net.tfminecraft.magic.blood;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import org.bukkit.Location;

import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;

/** Only loaded once WorldGuard is known to be enabled. The global region is never listed. */
final class WorldGuardAltarRegions {

    /** WorldEdit's adapter; tests swap it because it cannot initialise without a server. */
    static Function<Location, com.sk89q.worldedit.util.Location> adapter = BukkitAdapter::adapt;

    private WorldGuardAltarRegions() {}

    static List<String> regionsAt(Location location) {
        List<String> ids = new ArrayList<>();
        for (ProtectedRegion region : WorldGuard.getInstance().getPlatform().getRegionContainer()
                .createQuery().getApplicableRegions(adapter.apply(location))) {
            ids.add(region.getId());
        }
        return ids;
    }
}
