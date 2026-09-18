package net.tfminecraft.magic.artifact;

import java.util.LinkedHashMap;
import java.util.Map;

import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import net.tfminecraft.magic.Cache;
import net.tfminecraft.magic.charge.ChargeIds;
import net.tfminecraft.magic.attunement.ArtifactCareCache;
import net.tfminecraft.magic.model.ElementDef;
import net.tfminecraft.magic.registry.ElementRegistry;

public final class ArtifactCareStore {

    private ArtifactCareStore() {}

    public static double readMuffle(ItemStack stack) {
        return clamp01(readDouble(stack, ArtifactKeys.careMuffle()));
    }

    public static long readLastTickMs(ItemStack stack) {
        return readLong(stack, ArtifactKeys.careLastTick());
    }

    public static Map<String, Long> readUsers(ItemStack stack) {
        ItemMeta meta = metaOf(stack);
        if (meta == null) {
            return new LinkedHashMap<>();
        }
        return parseUsers(meta.getPersistentDataContainer().get(
                ArtifactKeys.careUsers(), PersistentDataType.STRING));
    }

    public static void pruneUsers(ItemStack stack, long nowMs) {
        ItemMeta meta = metaOf(stack);
        if (meta == null) {
            return;
        }
        Map<String, Long> users = parseUsers(meta.getPersistentDataContainer().get(
                ArtifactKeys.careUsers(), PersistentDataType.STRING));
        int before = users.size();
        users.entrySet().removeIf(entry -> entry.getValue() == null || entry.getValue() <= nowMs);
        if (users.size() == before) {
            return;
        }
        writeUsers(meta, users);
        stack.setItemMeta(meta);
    }

    public static double usableMax(double cap, double muffle) {
        return Math.max(0.0, cap) * (1.0 - clamp01(muffle));
    }

    public static double usableFill(double fill, double cap, double muffle) {
        return Math.min(Math.max(0.0, fill), usableMax(cap, muffle));
    }

    public static int activeUserCount(ItemStack stack, long nowMs) {
        Map<String, Long> users = readUsers(stack);
        users.entrySet().removeIf(entry -> entry.getValue() == null || entry.getValue() <= nowMs);
        return users.size();
    }

    public enum Persist {
        ALWAYS,
        IF_VISIBLE
    }

    public static boolean tick(ItemStack stack, boolean housed, long nowMs) {
        return apply(stack, housed, nowMs, Persist.ALWAYS);
    }

    public static boolean apply(ItemStack stack, boolean housed, long nowMs, Persist persist) {
        if (stack == null || stack.getType().isAir() || ChargeIds.isCharge(stack)) {
            return false;
        }
        ItemMeta meta = metaOf(stack);
        if (meta == null) {
            return false;
        }
        PersistentDataContainer root = meta.getPersistentDataContainer();
        long last = readLong(root, ArtifactKeys.careLastTick());
        double elapsedSec = 0.0;
        if (last > 0L) {
            elapsedSec = Math.max(0L, nowMs - last) / 1000.0;
        }
        double scale = Cache.secondsPerHour > 0 ? Cache.secondsPerHour : 3600.0;
        double dtHours = elapsedSec / scale;

        double muffle = clamp01(readDouble(root, ArtifactKeys.careMuffle()));
        if (ArtifactCareCache.muffledEnabled && last > 0L) {
            if (housed) {
                muffle = clamp01(muffle - ArtifactCareCache.muffledRecoverPerHour * dtHours);
            } else {
                muffle = clamp01(muffle + ArtifactCareCache.muffledOffPerHour * dtHours);
            }
        }
        boolean force = persist == Persist.ALWAYS;
        boolean visible = ArtifactLore.careVisibleWouldChange(stack, muffle, dtHours);
        if (!force && !visible) {
            return false;
        }
        if (ArtifactCareCache.muffledEnabled) {
            root.set(ArtifactKeys.careMuffle(), PersistentDataType.DOUBLE, muffle);
        }
        root.set(ArtifactKeys.careLastTick(), PersistentDataType.LONG, nowMs);
        stack.setItemMeta(meta);
        if (visible) {
            ArtifactLore.apply(stack);
            decayFill(stack, dtHours);
        }
        return true;
    }

    public static void stampUser(ItemStack stack, String characterId, long nowMs) {
        if (characterId == null || characterId.isBlank() || ChargeIds.isCharge(stack)) {
            return;
        }
        ItemMeta meta = metaOf(stack);
        if (meta == null) {
            return;
        }
        String id = characterId.trim();
        Map<String, Long> users = parseUsers(meta.getPersistentDataContainer().get(
                ArtifactKeys.careUsers(), PersistentDataType.STRING));
        users.entrySet().removeIf(entry -> entry.getValue() == null || entry.getValue() <= nowMs);
        users.put(id, nowMs + ArtifactCareCache.usersTtlMs());
        writeUsers(meta, users);
        stack.setItemMeta(meta);
    }

    private static void decayFill(ItemStack stack, double dtHours) {
        if (dtHours <= 0.0) {
            return;
        }
        Artifact artifact = Artifact.fromItem(stack);
        if (artifact == null) {
            return;
        }
        boolean dirty = false;
        for (String elementId : artifact.getCappedElementIds()) {
            ElementDef element = ElementRegistry.getById(elementId);
            if (element == null) {
                continue;
            }
            double rate = element.getAuraDecayPerHour();
            if (rate == 0.0) {
                continue;
            }
            double fill = artifact.getFill(elementId);
            if (rate < 0.0 && fill <= 0.0001) {
                continue;
            }
            double next = fill + rate * dtHours;
            artifact.setFill(elementId, next);
            if (Math.abs(artifact.getFill(elementId) - fill) > 0.0001) {
                dirty = true;
            }
        }
        if (!dirty) {
            return;
        }
        artifact.persistPdc(stack);
        ArtifactLore.updateItem(stack);
    }

    private static ItemMeta metaOf(ItemStack stack) {
        if (stack == null || stack.getType().isAir()) {
            return null;
        }
        return stack.getItemMeta();
    }

    private static Map<String, Long> parseUsers(String raw) {
        Map<String, Long> users = new LinkedHashMap<>();
        if (raw == null || raw.isBlank()) {
            return users;
        }
        for (String part : raw.split(";")) {
            if (part == null || part.isBlank()) {
                continue;
            }
            int colon = part.lastIndexOf(':');
            if (colon <= 0 || colon >= part.length() - 1) {
                continue;
            }
            String id = part.substring(0, colon).trim();
            if (id.isEmpty()) {
                continue;
            }
            try {
                users.put(id, Long.parseLong(part.substring(colon + 1).trim()));
            } catch (NumberFormatException ignored) {
                // skip bad token
            }
        }
        return users;
    }

    private static void writeUsers(ItemMeta meta, Map<String, Long> users) {
        if (users == null || users.isEmpty()) {
            meta.getPersistentDataContainer().remove(ArtifactKeys.careUsers());
            return;
        }
        StringBuilder out = new StringBuilder();
        for (Map.Entry<String, Long> entry : users.entrySet()) {
            if (entry.getKey() == null || entry.getKey().isBlank() || entry.getValue() == null) {
                continue;
            }
            if (out.length() > 0) {
                out.append(';');
            }
            out.append(entry.getKey()).append(':').append(entry.getValue());
        }
        if (out.length() == 0) {
            meta.getPersistentDataContainer().remove(ArtifactKeys.careUsers());
            return;
        }
        meta.getPersistentDataContainer().set(
                ArtifactKeys.careUsers(), PersistentDataType.STRING, out.toString());
    }

    private static double readDouble(ItemStack stack, NamespacedKey key) {
        ItemMeta meta = metaOf(stack);
        if (meta == null) {
            return 0.0;
        }
        return readDouble(meta.getPersistentDataContainer(), key);
    }

    private static double readDouble(PersistentDataContainer root, NamespacedKey key) {
        Double value = root.get(key, PersistentDataType.DOUBLE);
        return value != null ? value : 0.0;
    }

    private static long readLong(ItemStack stack, NamespacedKey key) {
        ItemMeta meta = metaOf(stack);
        if (meta == null) {
            return 0L;
        }
        return readLong(meta.getPersistentDataContainer(), key);
    }

    private static long readLong(PersistentDataContainer root, NamespacedKey key) {
        Long value = root.get(key, PersistentDataType.LONG);
        return value != null ? value : 0L;
    }

    private static double clamp01(double value) {
        if (value < 0.0) {
            return 0.0;
        }
        if (value > 1.0) {
            return 1.0;
        }
        return value;
    }
}
