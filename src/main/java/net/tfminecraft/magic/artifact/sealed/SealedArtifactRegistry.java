package net.tfminecraft.magic.artifact.sealed;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import net.tfminecraft.tlibs.TLibs;

/**
 * Sealed artifact items loaded from artifacts/sealed.yml.
 */
public final class SealedArtifactRegistry {

    private static final Map<String, SealedArtifactDef> defs = new LinkedHashMap<>();
    private static Set<Material> materials;
    private static String sound = "";

    private SealedArtifactRegistry() {}

    public static void clear() {
        defs.clear();
        materials = null;
        sound = "";
    }

    public static void register(SealedArtifactDef def) {
        defs.put(def.getId(), def);
        materials = null;
    }

    public static List<SealedArtifactDef> getAll() {
        return new ArrayList<>(defs.values());
    }

    public static int size() {
        return defs.size();
    }

    /** Sound key played when a seal breaks; blank for none. */
    public static String getSound() {
        return sound;
    }

    public static void setSound(String value) {
        sound = value == null ? "" : value.trim();
    }

    /**
     * Matches an item against every sealed artifact.
     *
     * @return the matching definition, or null when the item is not sealed
     */
    public static SealedArtifactDef match(ItemStack item) {
        if (item == null || item.getType().isAir() || defs.isEmpty()) {
            return null;
        }
        Set<Material> known = knownMaterials();
        if (!known.isEmpty() && !known.contains(item.getType())) {
            return null;
        }
        for (SealedArtifactDef def : defs.values()) {
            try {
                if (TLibs.getItemAPI().getChecker().checkItemWithPath(item, def.getItemPath())) {
                    return def;
                }
            } catch (Exception ignored) {
                // bad path in config, skip this entry
            }
        }
        return null;
    }

    /**
     * Resolves each configured path once to learn its material, so most right-clicks
     * skip the path checks.
     *
     * <p>An entry that fails to resolve yields an empty set, which disables the fast path
     * rather than making that entry unmatchable.
     */
    private static Set<Material> knownMaterials() {
        if (materials != null) {
            return materials;
        }
        Set<Material> found = EnumSet.noneOf(Material.class);
        for (SealedArtifactDef def : defs.values()) {
            ItemStack resolved;
            try {
                resolved = TLibs.getItemAPI().getCreator().getItemFromPath(def.getItemPath());
            } catch (Exception ex) {
                resolved = null;
            }
            if (resolved == null || resolved.getType().isAir()) {
                materials = Set.of();
                return materials;
            }
            found.add(resolved.getType());
        }
        materials = found;
        return materials;
    }
}
