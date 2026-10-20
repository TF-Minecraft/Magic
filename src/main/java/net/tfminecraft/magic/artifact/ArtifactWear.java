package net.tfminecraft.magic.artifact;

import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import net.tfminecraft.magic.Cache;

/**
 * Lifetime wear. Every point of resonance an artifact grants in meditation wears it,
 * whoever receives it. Its budget is {@code artifacts.wear.budget_multiplier} times its
 * highest aura cap. At the budget it crumbles into a spent husk: no aura and no artifact
 * id, so every Magic system ignores it, and only the Recycler still takes it.
 */
public final class ArtifactWear {

    private static final double EPSILON = 0.0001;

    public enum Result {
        NONE,
        WORN,
        CRUMBLED
    }

    private ArtifactWear() {}

    public static boolean enabled() {
        return Cache.artifactWearMultiplier > 0.0;
    }

    public static double readWear(ItemStack stack) {
        if (stack == null || stack.getType().isAir()) {
            return 0.0;
        }
        Double stored = stack.getItemMeta().getPersistentDataContainer().get(
                ArtifactKeys.artifactWear(), PersistentDataType.DOUBLE);
        if (stored == null || !Double.isFinite(stored) || stored < 0.0) {
            return 0.0;
        }
        return stored;
    }

    /** Total resonance the artifact can grant before it crumbles. 0 when wear is off. */
    public static double budget(Artifact artifact) {
        if (artifact == null || !enabled()) {
            return 0.0;
        }
        double highest = 0.0;
        for (String elementId : artifact.getCappedElementIds()) {
            highest = Math.max(highest, artifact.getCap(elementId));
        }
        return highest * Cache.artifactWearMultiplier;
    }

    /**
     * Resonance the artifact can still grant before it crumbles. Infinite when wear is
     * off or the stack is not an artifact, so callers can always clamp with it.
     */
    public static double remaining(ItemStack stack) {
        Artifact artifact = Artifact.fromItem(stack);
        double budget = budget(artifact);
        if (budget <= 0.0) {
            return Double.POSITIVE_INFINITY;
        }
        return Math.max(0.0, budget - readWear(stack));
    }

    /** Share of the budget left, 0-1. Always 1 when wear is off. */
    public static double integrity(ItemStack stack, Artifact artifact) {
        double budget = budget(artifact);
        if (budget <= 0.0) {
            return 1.0;
        }
        return Math.max(0.0, Math.min(1.0, 1.0 - readWear(stack) / budget));
    }

    /**
     * Adds {@code amount} of wear to the artifact. Crumbles it in place when that reaches
     * its budget. The caller writes the stack back to wherever it lives.
     */
    public static Result wear(ItemStack stack, double amount) {
        if (amount <= 0.0 || !enabled()) {
            return Result.NONE;
        }
        Artifact artifact = Artifact.fromItem(stack);
        if (artifact == null) {
            return Result.NONE;
        }
        double budget = budget(artifact);
        double next = readWear(stack) + amount;
        if (next + EPSILON >= budget) {
            crumble(stack);
            return Result.CRUMBLED;
        }
        ItemMeta meta = stack.getItemMeta();
        meta.getPersistentDataContainer().set(ArtifactKeys.artifactWear(), PersistentDataType.DOUBLE, next);
        stack.setItemMeta(meta);
        ArtifactLore.refreshAttune(stack);
        return Result.WORN;
    }

    /** The id the husk had as an artifact, or null when the stack is not a husk. */
    public static String spentId(ItemStack stack) {
        if (!isHusk(stack)) {
            return null;
        }
        return stack.getItemMeta().getPersistentDataContainer().get(
                ArtifactKeys.artifactSpent(), PersistentDataType.STRING);
    }

    public static boolean isHusk(ItemStack stack) {
        if (stack == null || stack.getType().isAir()) {
            return false;
        }
        return stack.getItemMeta().getPersistentDataContainer().has(
                ArtifactKeys.artifactSpent(), PersistentDataType.STRING);
    }

    /**
     * Turns the artifact into a spent husk. Its aura, care state, users and id are removed;
     * rarity, primary element and sacrifice history stay, so the husk still shows what it
     * was and the Recycler can price it.
     */
    public static void crumble(ItemStack stack) {
        if (stack == null || stack.getType().isAir()) {
            return;
        }
        ItemMeta meta = stack.getItemMeta();
        PersistentDataContainer root = meta.getPersistentDataContainer();
        String id = root.get(ArtifactKeys.artifactId(), PersistentDataType.STRING);
        for (NamespacedKey key : new NamespacedKey[] {
                ArtifactKeys.auraCap(),
                ArtifactKeys.auraFill(),
                ArtifactKeys.auraData(),
                ArtifactKeys.careUsers(),
                ArtifactKeys.careMuffle(),
                ArtifactKeys.careLastTick(),
                ArtifactKeys.attuneStart(),
                ArtifactKeys.attuneCount(),
                ArtifactKeys.artifactId(),
                ArtifactKeys.artifactWear()}) {
            root.remove(key);
        }
        root.set(ArtifactKeys.artifactSpent(), PersistentDataType.STRING, id == null || id.isBlank() ? "-" : id);
        stack.setItemMeta(meta);
        ArtifactLore.applyHusk(stack);
    }
}
