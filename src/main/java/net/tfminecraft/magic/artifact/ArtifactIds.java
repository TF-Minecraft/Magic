package net.tfminecraft.magic.artifact;

import java.util.UUID;

import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

public final class ArtifactIds {

    private ArtifactIds() {}

    public static boolean hasKey(ItemStack stack) {
        if (stack == null || stack.getType().isAir() || !stack.hasItemMeta()) {
            return false;
        }
        ItemMeta meta = stack.getItemMeta();
        return meta != null && meta.getPersistentDataContainer().has(
                ArtifactKeys.artifactId(), PersistentDataType.STRING);
    }

    public static UUID read(ItemStack stack) {
        if (stack == null || !stack.hasItemMeta()) {
            return null;
        }
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) {
            return null;
        }
        String raw = meta.getPersistentDataContainer().get(
                ArtifactKeys.artifactId(), PersistentDataType.STRING);
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(raw.trim());
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    public static boolean hasId(ItemStack stack) {
        return read(stack) != null;
    }

    public static void writeNew(ItemStack stack) {
        if (stack == null || stack.getType().isAir()) {
            return;
        }
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) {
            return;
        }
        meta.getPersistentDataContainer().set(
                ArtifactKeys.artifactId(),
                PersistentDataType.STRING,
                UUID.randomUUID().toString());
        stack.setItemMeta(meta);
    }
}
