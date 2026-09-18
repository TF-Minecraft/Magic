package net.tfminecraft.magic.meditation;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import net.tfminecraft.InteractibleFurniture;
import net.tfminecraft.furniture.Furniture;
import net.tfminecraft.furniture.PlacedSlot;
import net.tfminecraft.magic.artifact.Artifact;
import net.tfminecraft.magic.charge.ChargeIds;
import net.tfminecraft.magic.artifact.ArtifactCareStore;
import net.tfminecraft.magic.artifact.ArtifactIds;
import net.tfminecraft.magic.artifact.ArtifactLore;
import net.tfminecraft.magic.registry.ElementRegistry;

public final class MeditationCircle {

    private final Location center;
    private final List<Furniture> pedestals;
    private final List<Furniture> artifactPedestals;
    private final Map<UUID, MeditationCache.ArtifactDef> artifactByFurniture;
    private final Map<String, Double> powerByElement;
    private final double totalPower;

    private MeditationCircle(
            Location center,
            List<Furniture> pedestals,
            List<Furniture> artifactPedestals,
            Map<UUID, MeditationCache.ArtifactDef> artifactByFurniture,
            Map<String, Double> powerByElement) {
        this.center = center;
        this.pedestals = pedestals;
        this.artifactPedestals = artifactPedestals;
        this.artifactByFurniture = artifactByFurniture;
        this.powerByElement = powerByElement;
        double total = 0;
        for (double value : powerByElement.values()) {
            total += value;
        }
        this.totalPower = total;
    }

    public Location getCenter() {
        return center;
    }

    public List<Furniture> getPedestals() {
        return pedestals;
    }

    public List<Furniture> getArtifactPedestals() {
        return artifactPedestals;
    }

    public MeditationCache.ArtifactDef artifactFor(UUID furnitureId) {
        return artifactByFurniture.get(furnitureId);
    }

    public MeditationSitYield stampAndSnapshot(String characterId, long nowMs) {
        stampArtifacts(characterId, nowMs);
        return snapshotYield(nowMs);
    }

    public void stampArtifacts(String characterId, long nowMs) {
        for (Furniture furniture : artifactPedestals) {
            for (PlacedSlot slot : pedestalSlots(furniture)) {
                ItemStack item = itemFromSlot(slot);
                if (item == null || item.getType().isAir() || !ArtifactIds.hasKey(item)) {
                    continue;
                }
                if (characterId != null && !characterId.isBlank()) {
                    ArtifactCareStore.stampUser(item, characterId, nowMs);
                } else {
                    ArtifactCareStore.pruneUsers(item, nowMs);
                }
                ArtifactLore.refreshAttune(item);
                slot.setCurrentItem(item);
            }
        }
    }

    public MeditationSitYield snapshotYield(long nowMs) {
        Map<String, Double> sessionCaps = new HashMap<>();
        Map<String, Integer> users = new HashMap<>();
        for (Furniture furniture : artifactPedestals) {
            ItemStack item = itemOn(furniture);
            UUID uuid = ArtifactIds.read(item);
            if (uuid == null) {
                continue;
            }
            String artifactId = uuid.toString();
            MeditationCache.ArtifactDef def = artifactByFurniture.get(furniture.getEntityId());
            if (def == null || def.elementId == null) {
                continue;
            }
            int n = Math.max(1, ArtifactCareStore.activeUserCount(item, nowMs));
            Artifact aura = Artifact.fromItem(item);
            double fill = aura != null ? aura.getFill(def.elementId) : 0.0;
            double cap = aura != null ? aura.getCap(def.elementId) : 0.0;
            double muffle = ArtifactCareStore.readMuffle(item);
            double usable = ArtifactCareStore.usableFill(fill, cap, muffle);
            sessionCaps.put(artifactId, usable / n);
            users.put(artifactId, n);
        }
        return new MeditationSitYield(sessionCaps, users);
    }

    public ItemStack itemOn(Furniture furniture) {
        if (furniture == null) {
            return null;
        }
        List<ItemStack> items = pedestalItems(furniture);
        return items.isEmpty() ? null : items.get(0);
    }

    public String artifactIdOn(Furniture furniture) {
        UUID id = ArtifactIds.read(itemOn(furniture));
        return id != null ? id.toString() : null;
    }

    public boolean isFullyAttuned(Map<String, Double> attunedByArtifact, MeditationSitYield yield) {
        if (yield == null) {
            return true;
        }
        return yield.exhausted(attunedByArtifact);
    }

    public Map<String, Double> getPowerByElement() {
        return powerByElement;
    }

    public double getTotalPower() {
        return totalPower;
    }

    public boolean stillIntact() {
        MeditationCircle current = detect(center);
        return current != null;
    }

    public static boolean containing(Furniture furniture) {
        if (furniture == null || furniture.isCarried()) {
            return false;
        }
        Location anchor = furniture.getOriginBlockLocation().orElse(furniture.getLoc());
        if (anchor == null || anchor.getWorld() == null) {
            return false;
        }
        UUID id = furniture.getEntityId();
        if (id == null) {
            return false;
        }
        World world = anchor.getWorld();
        int px = anchor.getBlockX();
        int py = anchor.getBlockY();
        int pz = anchor.getBlockZ();
        for (int[] offset : ringOffsets()) {
            Location sit = new Location(world, px - offset[0] + 0.5, py, pz - offset[1] + 0.5);
            MeditationCircle circle = detect(sit);
            if (circle == null) {
                continue;
            }
            for (Furniture post : circle.getPedestals()) {
                if (post != null && id.equals(post.getEntityId())) {
                    return true;
                }
            }
        }
        return false;
    }

    public static MeditationCircle detect(Location sitLocation) {
        if (sitLocation == null || sitLocation.getWorld() == null) {
            return null;
        }
        Plugin ifPlugin = Bukkit.getPluginManager().getPlugin("InteractibleFurniture");
        if (ifPlugin == null || !ifPlugin.isEnabled()) {
            return null;
        }
        Block centerBlock = sitLocation.getBlock();
        int cx = centerBlock.getX();
        int cy = centerBlock.getY();
        int cz = centerBlock.getZ();
        World world = sitLocation.getWorld();

        int[][] offsets = ringOffsets();
        List<Furniture> candidates = collectNearby(world, cx, cz);
        List<Furniture> matched = new ArrayList<>();
        for (int[] offset : offsets) {
            Furniture found = matchAt(candidates, cx + offset[0], cy, cz + offset[1], matched);
            if (found == null) {
                return null;
            }
            matched.add(found);
        }

        Map<String, Double> power = new HashMap<>();
        Map<UUID, MeditationCache.ArtifactDef> byFurniture = new HashMap<>();
        List<Furniture> withArtifacts = new ArrayList<>();
        for (Furniture furniture : matched) {
            MeditationCache.ArtifactDef artifact = artifactFromFurniture(furniture, power);
            if (artifact == null) {
                continue;
            }
            withArtifacts.add(furniture);
            byFurniture.put(furniture.getEntityId(), artifact);
        }
        Location center = new Location(world, cx + 0.5, cy, cz + 0.5);
        return new MeditationCircle(
                center, List.copyOf(matched), List.copyOf(withArtifacts), Map.copyOf(byFurniture), power);
    }

    private static int[][] ringOffsets() {
        int card = MeditationCache.cardinalOffset;
        int diag = MeditationCache.diagonalOffset;
        return new int[][] {
                {card, 0}, {-card, 0}, {0, card}, {0, -card},
                {diag, diag}, {diag, -diag}, {-diag, diag}, {-diag, -diag}
        };
    }

    private static Furniture matchAt(
            List<Furniture> candidates, int x, int y, int z, List<Furniture> already) {
        Furniture best = null;
        double bestDist = 0.75;
        for (Furniture furniture : candidates) {
            if (already.contains(furniture) || furniture.isCarried()) {
                continue;
            }
            if (!MeditationCache.pedestalId.equalsIgnoreCase(furniture.getId())) {
                continue;
            }
            Location anchor = furniture.getOriginBlockLocation().orElse(furniture.getLoc());
            if (anchor == null || anchor.getWorld() == null) {
                continue;
            }
            if (Math.abs(anchor.getBlockY() - y) > 1) {
                continue;
            }
            double dx = (anchor.getX() - (x + 0.5));
            double dz = (anchor.getZ() - (z + 0.5));
            double dist = Math.sqrt(dx * dx + dz * dz);
            if (dist < bestDist) {
                bestDist = dist;
                best = furniture;
            }
        }
        return best;
    }

    private static List<Furniture> collectNearby(World world, int cx, int cz) {
        List<Furniture> out = new ArrayList<>();
        InteractibleFurniture plugin = InteractibleFurniture.getInstance();
        int chunkX = cx >> 4;
        int chunkZ = cz >> 4;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                Chunk chunk = world.getChunkAt(chunkX + dx, chunkZ + dz);
                out.addAll(plugin.getFurnitureManager().getFurnitureInChunk(chunk));
            }
        }
        return out;
    }

    private static MeditationCache.ArtifactDef artifactFromFurniture(
            Furniture furniture, Map<String, Double> powerByElement) {
        Map<String, Double> fills = new HashMap<>();
        for (ItemStack item : pedestalItems(furniture)) {
            // Charges gather aura but are not artifacts, so they lend no circle power.
            if (ChargeIds.isCharge(item)) {
                continue;
            }
            Artifact artifact = Artifact.fromItem(item);
            if (artifact == null || !artifact.hasStoredAura()) {
                continue;
            }
            for (String elementId : artifact.getCappedElementIds()) {
                if (ElementRegistry.getById(elementId) == null) {
                    continue;
                }
                double stored = artifact.getFill(elementId);
                if (stored <= 0) {
                    continue;
                }
                fills.merge(elementId, stored, Double::sum);
            }
        }
        if (fills.isEmpty()) {
            return null;
        }
        String dominant = null;
        double dominantFill = 0.0;
        for (Map.Entry<String, Double> entry : fills.entrySet()) {
            powerByElement.merge(entry.getKey(), entry.getValue(), Double::sum);
            if (entry.getValue() > dominantFill) {
                dominantFill = entry.getValue();
                dominant = entry.getKey();
            }
        }
        return new MeditationCache.ArtifactDef(dominant, dominantFill);
    }

    private static List<PlacedSlot> pedestalSlots(Furniture furniture) {
        List<PlacedSlot> slots = new ArrayList<>();
        if (furniture == null) {
            return slots;
        }
        String slotId = MeditationCache.pedestalSlot;
        if (slotId == null || slotId.isBlank() || "*".equals(slotId)) {
            slots.addAll(furniture.getActiveSlots().values());
            return slots;
        }
        furniture.getActiveSlot(slotId).ifPresent(slots::add);
        return slots;
    }

    private static List<ItemStack> pedestalItems(Furniture furniture) {
        List<ItemStack> items = new ArrayList<>();
        String slotId = MeditationCache.pedestalSlot;
        if (slotId == null || slotId.isBlank() || "*".equals(slotId)) {
            for (PlacedSlot slot : furniture.getActiveSlots().values()) {
                addIfPresent(items, itemFromSlot(slot));
            }
            return items;
        }
        furniture.getActiveSlot(slotId)
                .map(MeditationCircle::itemFromSlot)
                .ifPresent(item -> addIfPresent(items, item));
        return items;
    }

    private static ItemStack itemFromSlot(PlacedSlot slot) {
        if (slot == null) {
            return null;
        }
        ItemStack item = slot.getCurrentItem();
        if (item != null && !item.getType().isAir()) {
            return item;
        }
        UUID standId = slot.getDisplayStandId();
        Entity stand = standId != null ? Bukkit.getEntity(standId) : null;
        if (stand instanceof ItemDisplay display) {
            ItemStack shown = display.getItemStack();
            if (shown != null && !shown.getType().isAir()) {
                slot.setModel(shown);
                return shown;
            }
        }
        return item;
    }

    private static void addIfPresent(List<ItemStack> items, ItemStack item) {
        if (item != null && !item.getType().isAir()) {
            items.add(item);
        }
    }
}
