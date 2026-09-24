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
        Map<String, Integer> votes = new LinkedHashMap<>();
        String coreScheme = "";
        for (PartDef part : parts) {
            if (part == null || !part.hasModelScheme()) {
                continue;
            }
            String id = part.getSchemeId();
            if (!GearModelSchemeRegistry.contains(id)) {
                continue;
            }
            votes.merge(id, part.getSchemeWeight(), Integer::sum);
            if (PartSlots.CORE.equalsIgnoreCase(part.getPartType())) {
                coreScheme = id;
            }
        }
        if (votes.isEmpty()) {
            return null;
        }
        int best = -1;
        String bestId = null;
        boolean tie = false;
        for (Map.Entry<String, Integer> entry : votes.entrySet()) {
            int weight = entry.getValue();
            if (weight > best) {
                best = weight;
                bestId = entry.getKey();
                tie = false;
            } else if (weight == best) {
                tie = true;
            }
        }
        // bestId is already the first scheme seen among the tied leaders; the core wins a tie it is in.
        if (tie && !coreScheme.isEmpty() && votes.getOrDefault(coreScheme, 0) == best) {
            bestId = coreScheme;
        }
        return GearModelSchemeRegistry.get(bestId);
    }

    public static ItemStack apply(ItemStack stack, GearType type, Collection<PartDef> parts) {
        if (stack == null) {
            return null;
        }
        String path = path(type, parts);
        if (path == null || path.isBlank()) {
            return stack;
        }
        String normalized = ItemRef.normalize(path);
        String prefix = normalized.split("\\.")[0];
        try {
            if (prefix.equalsIgnoreCase("ia")) {
                return TLibs.getItemAPI().getArmorMerger().merge(stack, Optional.empty(), normalized);
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
        if (material == null) {
            Magic.plugin.getLogger().warning("[Magic] Unknown vanilla material in gear model: " + path);
            return stack;
        }
        stack.setType(material);
        if (parts.length >= 3) {
            ItemMeta meta = stack.getItemMeta();
            if (meta != null) {
                LegacyModelData.set(meta, Integer.parseInt(parts[2]));
                stack.setItemMeta(meta);
            }
        }
        return stack;
    }
}
