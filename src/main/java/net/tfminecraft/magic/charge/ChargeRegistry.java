package net.tfminecraft.magic.charge;

import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import net.tfminecraft.tlibs.TLibs;

public final class ChargeRegistry {

    private static final Map<Integer, ChargeDef> tiers = new LinkedHashMap<>();

    /**
     * Materials the configured charge items resolve to, built on first use.
     *
     * <p>{@link #match} runs on every item that passes through the lore and care paths,
     * so it needs to reject ordinary items without paying for a path check per tier.
     * Null means "not resolved yet"; empty means "resolution failed, check every tier".
     */
    private static Set<Material> materials;

    private ChargeRegistry() {}

    public static void clear() {
        tiers.clear();
        materials = null;
    }

    public static void register(ChargeDef def) {
        if (def == null || def.getTier() <= 0 || def.getItemPath().isEmpty()) {
            return;
        }
        tiers.put(def.getTier(), def);
    }

    public static ChargeDef getByTier(int tier) {
        return tiers.get(tier);
    }

    public static Collection<ChargeDef> getAll() {
        return Collections.unmodifiableCollection(tiers.values());
    }

    public static int size() {
        return tiers.size();
    }

    public static boolean isEmpty() {
        return tiers.isEmpty();
    }

    /**
     * Matches an item against every configured charge tier.
     *
     * @return the matching definition, or null when the item is not a charge
     */
    public static ChargeDef match(ItemStack item) {
        if (item == null || item.getType().isAir() || tiers.isEmpty()) {
            return null;
        }
        Set<Material> known = knownMaterials();
        if (!known.isEmpty() && !known.contains(item.getType())) {
            return null;
        }
        for (ChargeDef def : tiers.values()) {
            try {
                if (TLibs.getItemAPI().getChecker().checkItemWithPath(item, def.getItemPath())) {
                    return def;
                }
            } catch (Exception ignored) {
                // bad path in config, skip this tier
            }
        }
        return null;
    }

    /**
     * Resolves each configured path once to learn its material.
     *
     * <p>A tier that fails to resolve yields an empty set, which disables the fast path
     * rather than silently making that tier unmatchable.
     */
    private static Set<Material> knownMaterials() {
        if (materials != null) {
            return materials;
        }
        Set<Material> found = EnumSet.noneOf(Material.class);
        for (ChargeDef def : tiers.values()) {
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
