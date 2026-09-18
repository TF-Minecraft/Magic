package net.tfminecraft.magic.artifact.shrine;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;

import net.tfminecraft.magic.Cache;

public final class ShrineScorer {

    private static final int EMITTER_COUNT = 14;

    private ShrineScorer() {}

    public static ShrineScore score(Location center) {
        if (center == null || center.getWorld() == null) {
            return new ShrineScore(Map.of());
        }
        World world = center.getWorld();
        int radius = ShrineRegistry.getRadius();
        int cx = center.getBlockX();
        int cy = center.getBlockY();
        int cz = center.getBlockZ();

        Map<Material, Integer> counts = new EnumMap<>(Material.class);
        Map<Material, List<Location>> locations = new EnumMap<>(Material.class);
        for (int x = cx - radius; x <= cx + radius; x++) {
            for (int y = cy - radius; y <= cy + radius; y++) {
                for (int z = cz - radius; z <= cz + radius; z++) {
                    if (x == cx && y == cy && z == cz) {
                        continue;
                    }
                    Block block = world.getBlockAt(x, y, z);
                    Material type = block.getType();
                    if (type.isAir()) {
                        continue;
                    }
                    counts.merge(type, 1, Integer::sum);
                    locations.computeIfAbsent(type, ignored -> new ArrayList<>()).add(block.getLocation());
                }
            }
        }

        double auraCap = Cache.artifactAuraCap;
        double seconds = ShrineRegistry.getFullChargeSeconds();
        int minFamilies = ShrineRegistry.getMinFamilies();
        double minScore = ShrineRegistry.getMinScore();
        Map<String, ShrineElementScore> byElement = new LinkedHashMap<>();
        long seed = ((long) cx * 73856093L) ^ ((long) cy * 19349663L) ^ ((long) cz * 83492791L);

        for (ShrineElementDef def : ShrineRegistry.getAll()) {
            double raw = 0;
            double perfect = def.perfectScore();
            List<String> active = new ArrayList<>();
            List<List<Location>> familyLocs = new ArrayList<>();
            for (ShrineFamily family : def.getFamilies()) {
                int count = 0;
                List<Location> found = new ArrayList<>();
                for (Material material : family.getMaterials()) {
                    Integer n = counts.get(material);
                    if (n == null) {
                        continue;
                    }
                    count += n;
                    List<Location> at = locations.get(material);
                    if (at != null) {
                        found.addAll(at);
                    }
                }
                if (count <= 0) {
                    continue;
                }
                active.add(family.getId());
                familyLocs.add(found);
                raw += Math.min(count, family.getMaxCount()) * family.getWeight();
            }
            double variety = Math.min(1.0, (double) active.size() / minFamilies);
            double ratio = perfect > 0 ? raw / perfect : 0;
            double score = Math.max(0, Math.min(1.0, ratio * variety));
            if (score < minScore) {
                byElement.put(def.getElementId(), new ShrineElementScore(
                        def.getElementId(), 0, 0, active, List.of()));
                continue;
            }
            double maxAura = score * auraCap;
            double aps = seconds > 0 ? score * (auraCap / seconds) : 0;
            byElement.put(def.getElementId(), new ShrineElementScore(
                    def.getElementId(), maxAura, aps, active, pickEmitters(familyLocs, center, seed)));
        }
        return new ShrineScore(byElement);
    }

    private static List<Location> pickEmitters(List<List<Location>> familyLocs, Location center, long seed) {
        List<Location> unique = uniqueLocations(familyLocs);
        if (unique.isEmpty()) {
            return List.of();
        }
        Random rng = new Random(seed);
        List<Location> picked = new ArrayList<>();
        Set<String> used = new LinkedHashSet<>();
        for (List<Location> family : familyLocs) {
            Location one = farthestFrom(uniqueLocations(List.of(family)), center, used);
            if (one != null) {
                picked.add(one.clone());
                used.add(key(one));
            }
        }
        List<Location> pool = new ArrayList<>();
        for (Location loc : unique) {
            if (!used.contains(key(loc))) {
                pool.add(loc);
            }
        }
        Collections.shuffle(pool, rng);
        if (picked.isEmpty() && !pool.isEmpty()) {
            Location first = pool.remove(0);
            picked.add(first.clone());
            used.add(key(first));
        }
        while (picked.size() < EMITTER_COUNT && !pool.isEmpty()) {
            int best = 0;
            double bestSpread = -1;
            for (int i = 0; i < pool.size(); i++) {
                double nearest = Double.MAX_VALUE;
                Location candidate = pool.get(i);
                for (Location existing : picked) {
                    nearest = Math.min(nearest, dist2(candidate, existing));
                }
                if (nearest > bestSpread) {
                    bestSpread = nearest;
                    best = i;
                }
            }
            Location chosen = pool.remove(best);
            picked.add(chosen.clone());
        }
        return picked;
    }

    private static Location farthestFrom(List<Location> candidates, Location center, Set<String> used) {
        Location best = null;
        double bestDist = -1;
        for (Location loc : candidates) {
            if (loc == null || used.contains(key(loc))) {
                continue;
            }
            double dist = dist2(loc, center);
            if (dist > bestDist) {
                bestDist = dist;
                best = loc;
            }
        }
        return best;
    }

    private static List<Location> uniqueLocations(List<List<Location>> groups) {
        Map<String, Location> unique = new LinkedHashMap<>();
        for (List<Location> group : groups) {
            if (group == null) {
                continue;
            }
            for (Location loc : group) {
                if (loc == null) {
                    continue;
                }
                unique.putIfAbsent(key(loc), loc);
            }
        }
        return new ArrayList<>(unique.values());
    }

    private static String key(Location loc) {
        String world = loc.getWorld() != null ? loc.getWorld().getUID().toString() : "";
        return world + "|" + loc.getBlockX() + "|" + loc.getBlockY() + "|" + loc.getBlockZ();
    }

    private static double dist2(Location a, Location b) {
        double dx = a.getX() - b.getX();
        double dy = a.getY() - b.getY();
        double dz = a.getZ() - b.getZ();
        return dx * dx + dy * dy + dz * dz;
    }
}
