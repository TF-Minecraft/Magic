package net.tfminecraft.magic.artifact;

import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import net.tfminecraft.magic.Cache;
import net.tfminecraft.magic.artifact.config.ArtifactRarityDef;
import net.tfminecraft.magic.artifact.config.ArtifactRarityRegistry;
import net.tfminecraft.magic.artifact.config.ArtifactTypeDef;
import net.tfminecraft.magic.artifact.config.ArtifactTypeRegistry;
import net.tfminecraft.magic.artifact.config.CapRange;
import net.tfminecraft.magic.util.MagicNumbers;

public final class ArtifactAuraCaps {

    private ArtifactAuraCaps() {}

    public static CapRange rangeForRarity(String rarityId) {
        ArtifactRarityDef rarity = findRarity(rarityId);
        if (rarity == null || rarity.getAuraCapRange() == null) {
            return clampRange(new CapRange(0.0, Cache.artifactAuraCap));
        }
        return clampRange(rarity.getAuraCapRange());
    }

    public static CapRange primaryRange(String typeId, String rarityId) {
        ArtifactTypeDef type = findType(typeId);
        CapRange range = type != null ? type.getPrimaryCap(rarityId) : null;
        return range != null ? clampRange(range) : rangeForRarity(rarityId);
    }

    public static CapRange secondaryRange(String typeId, String rarityId) {
        ArtifactTypeDef type = findType(typeId);
        CapRange range = type != null ? type.getSecondaryCap(rarityId) : null;
        return range != null ? clampRange(range) : rangeForRarity(rarityId);
    }

    public static double forRarity(String rarityId) {
        return rangeForRarity(rarityId).getMax();
    }

    public static double forItem(ItemStack stack) {
        if (stack == null || !stack.hasItemMeta()) {
            return clamp(Cache.artifactAuraCap);
        }
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) {
            return clamp(Cache.artifactAuraCap);
        }
        String rarityId = meta.getPersistentDataContainer().get(
                ArtifactKeys.artifactRarity(), PersistentDataType.STRING);
        String primaryId = meta.getPersistentDataContainer().get(
                ArtifactKeys.artifactPrimary(), PersistentDataType.STRING);
        if (primaryId != null && !primaryId.isBlank()) {
            return primaryRange(primaryId, rarityId).getMax();
        }
        return forRarity(rarityId);
    }

    public static CapRange clampRange(CapRange range) {
        double ceiling = Cache.artifactAuraCap > 0 ? Cache.artifactAuraCap : 150.0;
        if (range == null) {
            return new CapRange(0.01, ceiling);
        }
        double min = MagicNumbers.clamp(range.getMin(), 0.0, ceiling);
        double max = MagicNumbers.clamp(range.getMax(), 0.0, ceiling);
        if (min > max) {
            min = max;
        }
        return new CapRange(min, max);
    }

    private static ArtifactTypeDef findType(String typeId) {
        if (typeId == null || typeId.isBlank()) {
            return null;
        }
        ArtifactTypeDef exact = ArtifactTypeRegistry.getById(typeId);
        if (exact != null) {
            return exact;
        }
        for (ArtifactTypeDef type : ArtifactTypeRegistry.getAll()) {
            if (type.getElementId().equalsIgnoreCase(typeId.trim())) {
                return type;
            }
        }
        return null;
    }

    private static ArtifactRarityDef findRarity(String rarityId) {
        if (rarityId == null || rarityId.isBlank()) {
            return null;
        }
        ArtifactRarityDef exact = ArtifactRarityRegistry.getById(rarityId);
        if (exact != null) {
            return exact;
        }
        for (ArtifactRarityDef rarity : ArtifactRarityRegistry.getAll()) {
            if (rarity.getId().equalsIgnoreCase(rarityId.trim())) {
                return rarity;
            }
        }
        return null;
    }

    private static double clamp(double cap) {
        double max = Cache.artifactAuraCap > 0 ? Cache.artifactAuraCap : 150.0;
        if (cap <= 0) {
            return max;
        }
        return MagicNumbers.clamp(cap, 0.0001, max);
    }
}
