package net.tfminecraft.magic.registry;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import net.tfminecraft.magic.model.ElementDef;

public final class ElementRegistry {

    private static final Map<String, ElementDef> map = new LinkedHashMap<>();

    private ElementRegistry() {}

    public static void clear() {
        map.clear();
    }

    public static void register(ElementDef element) {
        if (element == null || element.getId() == null || element.getId().isBlank()) {
            return;
        }
        map.put(element.getId(), element);
    }

    public static ElementDef getById(String id) {
        return map.get(id);
    }

    public static List<ElementDef> getAll() {
        return Collections.unmodifiableList(new ArrayList<>(map.values()));
    }

    public static List<String> getAllIds() {
        return Collections.unmodifiableList(new ArrayList<>(map.keySet()));
    }

    public static List<Integer> getOccupiedSlots() {
        List<Integer> slots = new ArrayList<>();
        for (ElementDef element : map.values()) {
            if (element.getSlot() >= 0) {
                slots.add(element.getSlot());
            }
        }
        return Collections.unmodifiableList(slots);
    }

    public static boolean contains(String id) {
        return map.containsKey(id);
    }

    public static int size() {
        return map.size();
    }
}
