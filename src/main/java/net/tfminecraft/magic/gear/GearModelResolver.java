package net.tfminecraft.magic.gear;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import net.tfminecraft.tlibs.TLibs;
import net.tfminecraft.magic.util.LegacyModelData;
import net.tfminecraft.magic.Magic;
import net.tfminecraft.magic.util.ItemRef;

public final class GearModelResolver {

    private GearModelResolver() {}

    public static String path(GearType type, Collection<PartDef> parts) {
        GearModelScheme scheme = winner(parts);
        if (scheme == null) {
            return null;
        }
        return scheme.pathFor(type);
    }

    public static GearModelScheme winner(Collection<PartDef> parts) {
        if (parts == null) {
            return null;
        }
        Map<String, Long> votes = new LinkedHashMap<>();
        String coreScheme = "";
        for (PartDef part : parts) {
            if (part == null || !part.hasModelScheme()) {
                continue;
            }
            String id = part.getSchemeId();
            if (!GearModelSchemeRegistry.contains(id)) {
                continue;
            }
            votes.merge(id, (long) part.getSchemeWeight(), Long::sum);
            if (PartSlots.CORE.equalsIgnoreCase(part.getPartType())) {
                coreScheme = id;
            }
        }
        if (votes.isEmpty()) {
            return null;
        }
        long best = -1;
        String bestId = null;
        boolean tie = false;
        for (Map.Entry<String, Long> entry : votes.entrySet()) {
            long weight = entry.getValue();
            if (weight > best) {
                best = weight;
                bestId = entry.getKey();
                tie = false;
            } else if (weight == best) {
                tie = true;
            }
        }
        // bestId is already the first scheme seen among the tied leaders; the core wins a tie it is in.
        if (tie && !coreScheme.isEmpty() && votes.getOrDefault(coreScheme, 0L) == best) {
            bestId = coreScheme;
        }
        return GearModelSchemeRegistry.get(bestId);
    }

    public static ItemStack apply(ItemStack stack, GearType type, Collection<PartDef> parts) {
        if (stack == null) {
            return null;
        }
        String path = path(type, parts);
        if (path == null) {
            return stack;
        }
        String normalized = ItemRef.normalize(path);
        int dot = normalized.indexOf('.');
        String prefix = dot < 0 ? normalized : normalized.substring(0, dot);
        try {
            if (prefix.equalsIgnoreCase("ia")) {
                ItemStack merged = TLibs.getItemAPI().getArmorMerger().merge(stack, Optional.empty(), normalized);
                return merged == null || merged.getType().isAir() ? stack : merged;
            }
            if (prefix.equalsIgnoreCase("v")) {
                return applyVanilla(stack, normalized);
            }
            Magic.plugin.getLogger().warning("[Magic] Unknown gear model path: " + path);
        } catch (Exception ex) {
            Magic.plugin.getLogger().warning("[Magic] Failed to apply gear model " + path + ": " + ex.getMessage());
        }
        return stack;
    }

    // This path mutates the existing ItemStack; replacing it would change aliases held by callers.
    @SuppressWarnings("deprecation")
    private static ItemStack applyVanilla(ItemStack stack, String path) {
        String[] parts = path.split("\\.");
        if (parts.length < 2) {
            return stack;
        }
        Material material = Material.matchMaterial(parts[1].toUpperCase());
        if (material == null || material.isAir() || !material.isItem()) {
            Magic.plugin.getLogger().warning("[Magic] Invalid vanilla item material in gear model: " + path);
            return stack;
        }
        Integer model = parts.length >= 3 ? Integer.parseInt(parts[2]) : null;
        stack.setType(material);
        if (model != null) {
            ItemMeta meta = stack.getItemMeta();
            LegacyModelData.set(meta, model);
            stack.setItemMeta(meta);
        }
        return stack;
    }
}
