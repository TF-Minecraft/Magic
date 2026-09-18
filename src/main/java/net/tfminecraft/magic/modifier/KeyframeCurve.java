package net.tfminecraft.magic.modifier;

import java.util.Map;
import java.util.NavigableMap;
import java.util.TreeMap;

import org.bukkit.configuration.ConfigurationSection;

public final class KeyframeCurve {

    private final NavigableMap<Double, ModifierTriple> keys;

    public KeyframeCurve(NavigableMap<Double, ModifierTriple> keys) {
        this.keys = keys == null || keys.isEmpty()
                ? new TreeMap<>(Map.of(0.0, ModifierTriple.ZERO))
                : new TreeMap<>(keys);
    }

    public static KeyframeCurve fromSection(ConfigurationSection section) {
        TreeMap<Double, ModifierTriple> map = new TreeMap<>();
        if (section == null) {
            return empty();
        }
        for (String rawKey : section.getKeys(false)) {
            if (rawKey == null || rawKey.isBlank()) {
                continue;
            }
            double at;
            try {
                at = Double.parseDouble(rawKey.trim());
            } catch (NumberFormatException ex) {
                continue;
            }
            ConfigurationSection point = section.getConfigurationSection(rawKey);
            if (point == null) {
                continue;
            }
            map.put(at, ModifierTriple.from(point));
        }
        if (map.isEmpty()) {
            return empty();
        }
        return new KeyframeCurve(map);
    }

    public static KeyframeCurve empty() {
        TreeMap<Double, ModifierTriple> map = new TreeMap<>();
        map.put(0.0, ModifierTriple.ZERO);
        return new KeyframeCurve(map);
    }

    public static KeyframeCurve defaultResonance() {
        TreeMap<Double, ModifierTriple> map = new TreeMap<>();
        map.put(0.0, new ModifierTriple(0.20, -0.20, 0.20));
        map.put(100.0, new ModifierTriple(-0.20, 0.20, -0.20));
        return new KeyframeCurve(map);
    }

    public static KeyframeCurve defaultSurge() {
        TreeMap<Double, ModifierTriple> map = new TreeMap<>();
        map.put(0.0, ModifierTriple.ZERO);
        map.put(15.0, new ModifierTriple(-0.05, 0.10, -0.10));
        map.put(60.0, new ModifierTriple(-0.10, 0.15, -0.15));
        map.put(100.0, new ModifierTriple(-0.05, 0.10, -0.10));
        return new KeyframeCurve(map);
    }

    public static KeyframeCurve defaultTranquility() {
        TreeMap<Double, ModifierTriple> map = new TreeMap<>();
        map.put(0.0, ModifierTriple.ZERO);
        map.put(100.0, new ModifierTriple(-0.20, 0.25, -0.05));
        return new KeyframeCurve(map);
    }

    public ModifierTriple sample(double amount) {
        Map.Entry<Double, ModifierTriple> first = keys.firstEntry();
        Map.Entry<Double, ModifierTriple> last = keys.lastEntry();
        if (amount <= first.getKey()) {
            return first.getValue();
        }
        if (amount >= last.getKey()) {
            return last.getValue();
        }
        Map.Entry<Double, ModifierTriple> floor = keys.floorEntry(amount);
        Map.Entry<Double, ModifierTriple> ceil = keys.ceilingEntry(amount);
        if (floor == null) {
            return first.getValue();
        }
        if (ceil == null || ceil.getKey().equals(floor.getKey())) {
            return floor.getValue();
        }
        double span = ceil.getKey() - floor.getKey();
        double t = span == 0.0 ? 0.0 : (amount - floor.getKey()) / span;
        return floor.getValue().lerp(ceil.getValue(), t);
    }

    public int size() {
        return keys.size();
    }
}
