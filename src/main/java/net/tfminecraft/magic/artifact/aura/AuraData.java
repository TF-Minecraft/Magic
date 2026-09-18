package net.tfminecraft.magic.artifact.aura;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import net.tfminecraft.magic.artifact.ArtifactKeys;
import net.tfminecraft.magic.util.MagicNumbers;

/**
 * Per-element aura cap and fill plus its persistence.
 *
 * <p>Shared by every {@link AuraVessel}. Artifacts and charges store the same shape;
 * only identity, display and eligibility differ between them.
 */
public final class AuraData {

    private final Map<String, Double> cap = new LinkedHashMap<>();
    private final Map<String, Double> fill = new LinkedHashMap<>();

    public AuraData() {}

    /**
     * Reads cap and fill from persistent data. Returns an empty instance when the item
     * carries no aura, so callers decide whether an empty vessel is still valid.
     */
    public static AuraData fromPersistentData(ItemStack stack) {
        return fromPersistentData(stack, ArtifactKeys.auraCap(), ArtifactKeys.auraFill(), ArtifactKeys.auraData());
    }

    public static AuraData fromPersistentData(
            ItemStack stack, NamespacedKey capKey, NamespacedKey fillKey, NamespacedKey blobKey) {
        AuraData data = new AuraData();
        if (stack == null || !stack.hasItemMeta()) {
            return data;
        }
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) {
            return data;
        }
        PersistentDataContainer root = meta.getPersistentDataContainer();
        PersistentDataContainer capContainer = root.get(capKey, PersistentDataType.TAG_CONTAINER);
        PersistentDataContainer fillContainer = root.get(fillKey, PersistentDataType.TAG_CONTAINER);
        if (capContainer != null) {
            for (NamespacedKey key : capContainer.getKeys()) {
                Double value = capContainer.get(key, PersistentDataType.DOUBLE);
                if (value != null && value > 0) {
                    data.cap.put(key.getKey(), value);
                }
            }
        }
        if (fillContainer != null) {
            for (NamespacedKey key : fillContainer.getKeys()) {
                Double value = fillContainer.get(key, PersistentDataType.DOUBLE);
                if (value == null) {
                    continue;
                }
                data.fill.put(key.getKey(), value);
            }
        }
        parseBlob(data, root.get(blobKey, PersistentDataType.STRING));
        if (data.cap.isEmpty() && !data.fill.isEmpty()) {
            for (Map.Entry<String, Double> entry : data.fill.entrySet()) {
                if (entry.getValue() != null && entry.getValue() > 0) {
                    data.cap.put(entry.getKey(), entry.getValue());
                }
            }
        }
        for (String id : data.cap.keySet()) {
            data.fill.put(id, clampFill(data.getFill(id), data.getCap(id)));
        }
        return data;
    }

    /**
     * Root string backup, {@code id:cap:fill,...}. Survives nested TAG_CONTAINER drops
     * that happen on furniture pickup and some MMOItems rebuilds.
     */
    private static void parseBlob(AuraData data, String raw) {
        if (raw == null || raw.isBlank()) {
            return;
        }
        for (String part : raw.split(",")) {
            String[] bits = part.split(":");
            if (bits.length < 2) {
                continue;
            }
            String id = bits[0].trim().toLowerCase(Locale.ROOT);
            if (id.isEmpty()) {
                continue;
            }
            try {
                double capValue = Double.parseDouble(bits[1].trim());
                if (capValue > 0 && data.getCap(id) <= 0) {
                    data.cap.put(id, capValue);
                }
                if (bits.length >= 3) {
                    double fillValue = Double.parseDouble(bits[2].trim());
                    data.fill.putIfAbsent(id, fillValue);
                    if (data.getFill(id) <= 0 && fillValue > 0) {
                        data.fill.put(id, fillValue);
                    }
                }
            } catch (NumberFormatException ignored) {
                // skip bad token
            }
        }
    }

    public boolean isEmpty() {
        return cap.isEmpty();
    }

    public boolean hasStoredAura() {
        for (double value : fill.values()) {
            if (value > 0) {
                return true;
            }
        }
        return false;
    }

    public double totalFill() {
        double sum = 0.0;
        for (double value : fill.values()) {
            if (value > 0) {
                sum += value;
            }
        }
        return sum;
    }

    public double getCap(String elementId) {
        return cap.getOrDefault(normalize(elementId), 0.0);
    }

    public double getFill(String elementId) {
        return fill.getOrDefault(normalize(elementId), 0.0);
    }

    public void setCap(String elementId, double value) {
        String id = normalize(elementId);
        if (id.isEmpty()) {
            return;
        }
        double next = Math.max(0.0, value);
        if (next <= 0) {
            cap.remove(id);
            fill.remove(id);
            return;
        }
        cap.put(id, next);
        fill.put(id, clampFill(getFill(id), next));
    }

    public void setFill(String elementId, double value) {
        String id = normalize(elementId);
        if (id.isEmpty() || getCap(id) <= 0) {
            return;
        }
        fill.put(id, clampFill(value, getCap(id)));
    }

    public Set<String> getCappedElementIds() {
        return Collections.unmodifiableSet(cap.keySet());
    }

    /** Highest-cap element, used when no explicit primary is stored. */
    public String highestCapElementId() {
        String best = "";
        double bestCap = -1;
        for (Map.Entry<String, Double> entry : cap.entrySet()) {
            if (entry.getValue() != null && entry.getValue() > bestCap) {
                bestCap = entry.getValue();
                best = entry.getKey();
            }
        }
        return best;
    }

    public void persistPdc(ItemStack stack) {
        persistPdc(stack, ArtifactKeys.auraCap(), ArtifactKeys.auraFill(), ArtifactKeys.auraData());
    }

    public void persistPdc(
            ItemStack stack, NamespacedKey capKey, NamespacedKey fillKey, NamespacedKey blobKey) {
        if (stack == null || stack.getType().isAir()) {
            return;
        }
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) {
            return;
        }
        PersistentDataContainer root = meta.getPersistentDataContainer();
        PersistentDataContainer capContainer = root.getAdapterContext().newPersistentDataContainer();
        PersistentDataContainer fillContainer = root.getAdapterContext().newPersistentDataContainer();
        StringBuilder blob = new StringBuilder();
        for (Map.Entry<String, Double> entry : cap.entrySet()) {
            if (entry.getValue() == null || entry.getValue() <= 0) {
                continue;
            }
            NamespacedKey key;
            try {
                key = ArtifactKeys.element(entry.getKey());
            } catch (IllegalArgumentException ignored) {
                continue;
            }
            double capValue = entry.getValue();
            double fillValue = clampFill(getFill(entry.getKey()), capValue);
            capContainer.set(key, PersistentDataType.DOUBLE, capValue);
            fillContainer.set(key, PersistentDataType.DOUBLE, fillValue);
            if (blob.length() > 0) {
                blob.append(',');
            }
            blob.append(entry.getKey()).append(':').append(capValue).append(':').append(fillValue);
        }
        root.set(capKey, PersistentDataType.TAG_CONTAINER, capContainer);
        root.set(fillKey, PersistentDataType.TAG_CONTAINER, fillContainer);
        if (blob.length() > 0) {
            root.set(blobKey, PersistentDataType.STRING, blob.toString());
        }
        stack.setItemMeta(meta);
    }

    public static double clampFill(double value, double capValue) {
        return MagicNumbers.clamp(value, 0.0, Math.max(0.0, capValue));
    }

    private static String normalize(String elementId) {
        if (elementId == null) {
            return "";
        }
        return elementId.trim().toLowerCase(Locale.ROOT);
    }
}
