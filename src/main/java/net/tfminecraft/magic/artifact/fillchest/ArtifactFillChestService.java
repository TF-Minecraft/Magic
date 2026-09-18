package net.tfminecraft.magic.artifact.fillchest;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.Chest;
import org.bukkit.entity.HumanEntity;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import net.tfminecraft.magic.artifact.config.ArtifactTypeDef;
import net.tfminecraft.magic.artifact.config.ArtifactTypeRegistry;
import net.tfminecraft.magic.artifact.generate.ArtifactItemBuilder;
import net.tfminecraft.magic.artifact.generate.ArtifactRoll;
import net.tfminecraft.magic.artifact.generate.ArtifactRoller;

public final class ArtifactFillChestService {

    public enum Mode {
        TYPE,
        RANDOM,
        ALL
    }

    public record Pending(Mode mode, String typeId, long expireAtMs) {}

    private static final long ARM_MS = 30_000L;
    private static final Map<UUID, Pending> pending = new ConcurrentHashMap<>();

    private ArtifactFillChestService() {}

    public static void arm(UUID playerId, Mode mode, String typeId) {
        pending.put(playerId, new Pending(mode, typeId, System.currentTimeMillis() + ARM_MS));
    }

    public static void clear(UUID playerId) {
        if (playerId != null) {
            pending.remove(playerId);
        }
    }

    public static void clearAll() {
        pending.clear();
    }

    public static Pending get(UUID playerId) {
        if (playerId == null) {
            return null;
        }
        return pending.get(playerId);
    }

    public static void consume(UUID playerId) {
        pending.remove(playerId);
    }

    public static boolean isChestBlock(Block block) {
        if (block == null) {
            return false;
        }
        Material type = block.getType();
        return type == Material.CHEST || type == Material.TRAPPED_CHEST;
    }

    public static int fill(Block block, Pending held) {
        if (!(block.getState() instanceof Chest chest)) {
            return 0;
        }
        Inventory inventory = chest.getInventory();
        for (HumanEntity viewer : List.copyOf(inventory.getViewers())) {
            viewer.closeInventory();
        }
        int size = inventory.getSize();
        ItemStack[] contents = new ItemStack[size];
        ArtifactRoller roller = new ArtifactRoller();
        ArtifactItemBuilder builder = new ArtifactItemBuilder();
        String lockedPrimary = resolveLockedPrimary(held);
        int placed = 0;
        for (int slot = 0; slot < size; slot++) {
            String primaryArg;
            if (held.mode() == Mode.ALL) {
                primaryArg = "random";
            } else if (lockedPrimary == null) {
                continue;
            } else {
                primaryArg = lockedPrimary;
            }
            ArtifactRoll roll = roller.roll(primaryArg, "roll");
            if (roll == null || roll.isError()) {
                continue;
            }
            ItemStack stack = builder.build(roll, null, null, false);
            if (stack == null || stack.getType().isAir()) {
                continue;
            }
            contents[slot] = stack;
            placed++;
        }
        inventory.clear();
        inventory.setContents(contents);
        return placed;
    }

    private static String resolveLockedPrimary(Pending held) {
        if (held.mode() == Mode.TYPE) {
            return held.typeId();
        }
        if (held.mode() == Mode.RANDOM) {
            return "random";
        }
        return null;
    }

    public static Mode parseMode(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String id = raw.toLowerCase(Locale.ROOT);
        if ("all".equals(id)) {
            return Mode.ALL;
        }
        if ("random".equals(id)) {
            return Mode.RANDOM;
        }
        if (ArtifactTypeRegistry.contains(id)) {
            ArtifactTypeDef type = ArtifactTypeRegistry.getById(id);
            if (type != null && type.isEnabled()) {
                return Mode.TYPE;
            }
            return null;
        }
        return null;
    }
}
