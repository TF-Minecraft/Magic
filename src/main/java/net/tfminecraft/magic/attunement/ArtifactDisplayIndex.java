package net.tfminecraft.magic.attunement;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import net.tfminecraft.interactiblefurniture.InteractibleFurniture;
import net.tfminecraft.interactiblefurniture.furniture.Furniture;
import net.tfminecraft.interactiblefurniture.furniture.PlacedSlot;
import net.tfminecraft.magic.artifact.ArtifactIds;
import net.tfminecraft.magic.artifact.shrine.ShrineChargeService;
import net.tfminecraft.magic.meditation.MeditationCache;

public final class ArtifactDisplayIndex {

    private static final Map<UUID, Integer> displayed = new ConcurrentHashMap<>();

    private ArtifactDisplayIndex() {}

    public static boolean isDisplayFurniture(Furniture furniture) {
        if (furniture == null || furniture.getId() == null) {
            return false;
        }
        if (furniture.isCarried() || furniture.isPersistedCarried()) {
            return false;
        }
        String id = furniture.getId();
        if (id.equalsIgnoreCase(MeditationCache.pedestalId)) {
            return true;
        }
        String displayId = ArtifactCareCache.displayFurnitureId;
        return displayId != null && !displayId.isBlank() && id.equalsIgnoreCase(displayId);
    }

    public static boolean isDisplayed(UUID artifactId) {
        if (artifactId == null) {
            return false;
        }
        Integer count = displayed.get(artifactId);
        return count != null && count > 0;
    }

    public static boolean isDisplayed(String artifactId) {
        UUID id = parse(artifactId);
        return id != null && isDisplayed(id);
    }

    public static void add(ItemStack item) {
        add(ArtifactIds.read(item));
    }

    public static void add(UUID artifactId) {
        if (artifactId == null) {
            return;
        }
        displayed.merge(artifactId, 1, Integer::sum);
    }

    public static void ensure(ItemStack item) {
        ensure(ArtifactIds.read(item));
    }

    public static void ensure(UUID artifactId) {
        if (artifactId == null) {
            return;
        }
        displayed.putIfAbsent(artifactId, 1);
    }

    public static void indexFurniture(Furniture furniture, boolean increment) {
        if (!isDisplayFurniture(furniture)) {
            return;
        }
        for (PlacedSlot slot : furniture.getActiveSlots().values()) {
            ItemStack item = ShrineChargeService.itemFromSlot(slot);
            if (increment) {
                add(item);
            } else {
                ensure(item);
            }
        }
    }

    public static void remove(ItemStack item) {
        remove(ArtifactIds.read(item));
    }

    public static void remove(UUID artifactId) {
        if (artifactId == null) {
            return;
        }
        displayed.compute(artifactId, (id, count) -> {
            if (count == null || count <= 1) {
                return null;
            }
            return count - 1;
        });
    }

    public static void removeFurniture(Furniture furniture) {
        if (furniture == null || !isDisplayFurniture(furniture)) {
            return;
        }
        for (PlacedSlot slot : furniture.getActiveSlots().values()) {
            remove(ShrineChargeService.itemFromSlot(slot));
        }
    }

    public static void rebuildFromSaved() {
        displayed.clear();
        Plugin ifPlugin = Bukkit.getPluginManager().getPlugin("InteractibleFurniture");
        if (ifPlugin == null || !ifPlugin.isEnabled()) {
            return;
        }
        InteractibleFurniture plugin = InteractibleFurniture.getInstance();
        if (plugin == null || plugin.getFurnitureManager() == null) {
            return;
        }
        plugin.getFurnitureManager().visitSavedFurniture(furniture -> indexFurniture(furniture, true));
    }

    public static void ensureChunk(Chunk chunk) {
        if (chunk == null) {
            return;
        }
        Plugin ifPlugin = Bukkit.getPluginManager().getPlugin("InteractibleFurniture");
        if (ifPlugin == null || !ifPlugin.isEnabled()) {
            return;
        }
        InteractibleFurniture plugin = InteractibleFurniture.getInstance();
        if (plugin == null || plugin.getFurnitureManager() == null) {
            return;
        }
        for (Furniture furniture : plugin.getFurnitureManager().getFurnitureInChunk(chunk)) {
            indexFurniture(furniture, false);
        }
    }

    private static UUID parse(String raw) {
        if (raw == null || raw.isBlank() || "unbound".equalsIgnoreCase(raw.trim())) {
            return null;
        }
        try {
            return UUID.fromString(raw.trim());
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }
}
