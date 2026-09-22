package net.tfminecraft.magic.artifact.generate;

import net.tfminecraft.magic.util.LegacyModelData;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import io.lumine.mythic.lib.api.item.NBTItem;
import net.tfminecraft.tlibs.TLibs;
import net.Indyuce.mmoitems.ItemStats;
import net.Indyuce.mmoitems.api.item.mmoitem.LiveMMOItem;
import net.Indyuce.mmoitems.api.item.mmoitem.MMOItem;
import net.Indyuce.mmoitems.stat.data.StringData;
import net.Indyuce.mmoitems.stat.type.NameData;
import net.Indyuce.mmoitems.stat.type.StatHistory;
import net.tfminecraft.magic.Magic;
import net.tfminecraft.magic.artifact.Artifact;
import net.tfminecraft.magic.artifact.ArtifactKeys;
import net.tfminecraft.magic.artifact.config.ArtifactAdjectiveRegistry;
import net.tfminecraft.magic.artifact.config.ArtifactGeneratorCache;
import net.tfminecraft.magic.artifact.config.ArtifactModelEntry;
import net.tfminecraft.magic.artifact.config.ArtifactModelScheme;
import net.tfminecraft.magic.artifact.config.ArtifactModelSchemeRegistry;
import net.tfminecraft.magic.artifact.config.ArtifactNamingScheme;
import net.tfminecraft.magic.artifact.config.ArtifactNamingSchemeRegistry;
import net.tfminecraft.magic.artifact.config.ArtifactRarityDef;
import net.tfminecraft.magic.artifact.config.ArtifactRarityRegistry;
import net.tfminecraft.magic.artifact.config.ArtifactTypeDef;
import net.tfminecraft.magic.artifact.config.ArtifactTypeRegistry;
import net.tfminecraft.magic.model.ElementDef;
import net.tfminecraft.magic.registry.ElementRegistry;
import net.tfminecraft.magic.util.MagicText;

public final class ArtifactItemBuilder {

    private final Random rng;

    public ArtifactItemBuilder() {
        this(ThreadLocalRandom.current());
    }

    public ArtifactItemBuilder(Random rng) {
        this.rng = rng != null ? rng : ThreadLocalRandom.current();
    }

    public ItemStack build(ArtifactRoll roll) {
        return buildInternal(roll, null, null, true);
    }

    public ItemStack build(ArtifactRoll roll, String baseName, String modelPath) {
        return build(roll, baseName, modelPath, false);
    }

    public ItemStack build(ArtifactRoll roll, String baseName, String modelPath, boolean logFailures) {
        return buildInternal(roll, baseName, modelPath, logFailures);
    }

    private ItemStack buildInternal(ArtifactRoll roll, String baseName, String modelPath, boolean logFailures) {
        if (roll == null || roll.isError()) {
            return null;
        }
        if (!pluginsReady()) {
            log(logFailures, "[Magic] Cannot build artifact: MMOItems or MythicLib is missing.");
            return null;
        }
        ArtifactTypeDef type = ArtifactTypeRegistry.getById(roll.getPrimaryId());
        ArtifactRarityDef rarity = ArtifactRarityRegistry.getById(roll.getRarityId());
        if (type == null || rarity == null) {
            log(logFailures, "[Magic] Cannot build artifact: missing type or rarity.");
            return null;
        }

        ItemStack template = TLibs.getItemAPI().getCreator().getItemFromPath(ArtifactGeneratorCache.template);
        if (template == null || template.getType().isAir()) {
            log(logFailures, "[Magic] Artifact template missing: " + ArtifactGeneratorCache.template);
            return null;
        }

        ArtifactModelEntry picked = null;
        String resolvedPath = modelPath != null && !modelPath.isBlank() ? modelPath : null;
        if (resolvedPath == null) {
            picked = pickModel(type, rarity.getId());
            if (picked == null) {
                log(logFailures, "[Magic] No eligible model for artifact type " + type.getElementId()
                        + " rarity " + rarity.getId());
            } else {
                resolvedPath = picked.getPath();
            }
        }
        String kind = picked != null ? picked.getKind() : kindForPath(type, resolvedPath);
        String resolvedName = baseName != null && !baseName.isBlank()
                ? baseName
                : pickBaseName(type, kind, rarity.getId());
        MMOItem mmo = new LiveMMOItem(NBTItem.get(template));
        applyName(mmo, buildDisplayName(type, resolvedName));
        ItemStack built = mmo.newBuilder().build();
        if (built == null || built.getType().isAir()) {
            log(logFailures, "[Magic] Artifact template failed to build.");
            return null;
        }
        built = applyModel(built, type, resolvedPath, logFailures);
        writeIdentity(built, rarity.getId(), type.getElementId());
        return writeAura(built, roll);
    }

    public String pickBaseName(ArtifactTypeDef type, String kind) {
        return pickBaseName(type, kind, null);
    }

    public String pickBaseName(ArtifactTypeDef type, String kind, String rarityId) {
        if (type == null) {
            return "";
        }
        ArtifactNamingScheme scheme = ArtifactNamingSchemeRegistry.getById(type.getNamingSchemeId());
        String base = type.getElementId();
        if (scheme != null && !scheme.isEmpty()) {
            List<String> names = scheme.namesFor(kind);
            if (names.isEmpty()) {
                names = scheme.namesFor("manuscript");
            }
            if (names.isEmpty()) {
                names = scheme.firstNonEmptyKindNames();
            }
            if (!names.isEmpty()) {
                base = names.get(rng.nextInt(names.size()));
            }
        }
        return applyAdjective(base, type.getElementId(), rarityId);
    }

    private String applyAdjective(String base, String elementId, String rarityId) {
        if (base == null || base.isBlank() || rarityId == null || rarityId.isBlank()) {
            return base;
        }
        if (rng.nextDouble() >= ArtifactAdjectiveRegistry.chance(rarityId)) {
            return base;
        }
        List<String> pool = ArtifactAdjectiveRegistry.pool(elementId, rarityId);
        if (pool.isEmpty()) {
            return base;
        }
        String adjective = pool.get(rng.nextInt(pool.size()));
        if (adjective == null || adjective.isBlank()) {
            return base;
        }
        return insertAdjective(base, adjective.trim());
    }

    private static String insertAdjective(String base, String adjective) {
        String trimmed = base.trim();
        int space = trimmed.indexOf(' ');
        if (space > 0) {
            String first = trimmed.substring(0, space);
            if (isArticle(first)) {
                return first + " " + adjective + trimmed.substring(space);
            }
        }
        return adjective + " " + trimmed;
    }

    private static boolean isArticle(String word) {
        return "A".equalsIgnoreCase(word) || "An".equalsIgnoreCase(word) || "The".equalsIgnoreCase(word);
    }

    public ArtifactModelEntry pickModel(ArtifactTypeDef type, String rarityId) {
        if (type == null) {
            return null;
        }
        ArtifactModelScheme scheme = ArtifactModelSchemeRegistry.getById(type.getModelSchemeId());
        if (scheme == null || scheme.getModels().isEmpty()) {
            return null;
        }
        List<ArtifactModelEntry> eligible = new ArrayList<>();
        for (ArtifactModelEntry entry : scheme.getModels()) {
            if (entry != null && entry.eligible(rarityId)) {
                eligible.add(entry);
            }
        }
        if (eligible.isEmpty()) {
            return null;
        }
        return eligible.get(rng.nextInt(eligible.size()));
    }

    public String pickModelPath(ArtifactTypeDef type, String rarityId) {
        ArtifactModelEntry entry = pickModel(type, rarityId);
        return entry != null ? entry.getPath() : null;
    }

    public static String kindForPath(ArtifactTypeDef type, String path) {
        if (type == null || path == null || path.isBlank()) {
            return null;
        }
        ArtifactModelScheme scheme = ArtifactModelSchemeRegistry.getById(type.getModelSchemeId());
        if (scheme == null) {
            return null;
        }
        for (ArtifactModelEntry entry : scheme.getModels()) {
            if (entry != null && path.equals(entry.getPath())) {
                return entry.getKind();
            }
        }
        return null;
    }

    private static String buildDisplayName(ArtifactTypeDef type, String baseName) {
        String rolledName = baseName != null && !baseName.isBlank() ? baseName : type.getElementId();
        ElementDef element = ElementRegistry.getById(type.getElementId());
        String elementColor = element != null && element.getColor() != null && !element.getColor().isBlank()
                ? element.getColor()
                : "#ffffff";
        return MagicText.format(elementColor + rolledName);
    }

    private static void applyName(MMOItem mmo, String displayName) {
        StringData itemName = (StringData) mmo.getData(ItemStats.NAME);
        if (itemName == null) {
            itemName = new StringData(displayName);
        } else {
            itemName.setString(displayName);
        }
        mmo.replaceData(ItemStats.NAME, itemName);
        StatHistory hist = mmo.computeStatHistory(ItemStats.NAME);
        if (hist != null) {
            NameData original = (NameData) hist.getOriginalData();
            original.setString(displayName);
            mmo.setStatHistory(ItemStats.NAME, hist);
        }
    }

    private ItemStack applyModel(ItemStack stack, ArtifactTypeDef type, String modelPath, boolean logFailures) {
        String path = modelPath != null && !modelPath.isBlank() ? modelPath : null;
        if (path == null) {
            log(logFailures, "[Magic] No model scheme for artifact type " + type.getElementId());
            return stack;
        }
        String prefix = path.split("\\.")[0];
        try {
            if (prefix.equalsIgnoreCase("ia")) {
                return TLibs.getItemAPI().getArmorMerger().merge(stack, Optional.empty(), path);
            }
            if (prefix.equalsIgnoreCase("v")) {
                return applyVanillaModel(stack, path);
            }
            log(logFailures, "[Magic] Unknown artifact model path: " + path);
        } catch (Exception ex) {
            log(logFailures, "[Magic] Failed to apply artifact model " + path + ": " + ex.getMessage());
        }
        return stack;
    }

    // This path mutates the existing ItemStack; replacing it would change aliases held by callers.
    @SuppressWarnings("deprecation")
    private static ItemStack applyVanillaModel(ItemStack stack, String path) {
        String[] parts = path.split("\\.");
        if (parts.length < 2) {
            return stack;
        }
        stack.setType(Material.valueOf(parts[1].toUpperCase()));
        if (parts.length >= 3) {
            ItemMeta meta = stack.getItemMeta();
            if (meta != null) {
                LegacyModelData.set(meta, Integer.parseInt(parts[2]));
                stack.setItemMeta(meta);
            }
        }
        return stack;
    }

    private static void writeIdentity(ItemStack stack, String rarityId, String primaryId) {
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) {
            return;
        }
        meta.getPersistentDataContainer().set(ArtifactKeys.artifactRarity(), PersistentDataType.STRING, rarityId);
        meta.getPersistentDataContainer().set(ArtifactKeys.artifactPrimary(), PersistentDataType.STRING, primaryId);
        meta.getPersistentDataContainer().set(
                ArtifactKeys.artifactId(), PersistentDataType.STRING, UUID.randomUUID().toString());
        stack.setItemMeta(meta);
    }

    private static ItemStack writeAura(ItemStack stack, ArtifactRoll roll) {
        Artifact artifact = Artifact.create();
        for (ArtifactAuraSlot slot : roll.getSlots()) {
            if (slot == null || slot.getElementId() == null || slot.getElementId().isBlank()) {
                continue;
            }
            artifact.setCap(slot.getElementId(), slot.getCap());
            artifact.setFill(slot.getElementId(), 0);
        }
        return artifact.write(stack);
    }

    private static boolean pluginsReady() {
        Plugin mmo = Bukkit.getPluginManager().getPlugin("MMOItems");
        Plugin mythic = Bukkit.getPluginManager().getPlugin("MythicLib");
        return mmo != null && mmo.isEnabled() && mythic != null && mythic.isEnabled();
    }

    private static void log(boolean logFailures, String message) {
        if (logFailures) {
            Magic.plugin.getLogger().warning(message);
        }
    }
}
