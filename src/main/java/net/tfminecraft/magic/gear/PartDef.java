package net.tfminecraft.magic.gear;

import java.util.Collections;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.stream.Collectors;

public final class PartDef {

    private final String id;
    private final String name;
    private final String partType;
    private final Set<GearType> types;
    private final String itemPath;
    private final Map<String, Integer> cost;
    private final Map<String, Integer> sockets;
    private final List<String> lore;
    private final boolean disabled;
    private int revision = 1;

    public PartDef(
            String id,
            String name,
            String partType,
            Set<GearType> types,
            String itemPath,
            Map<String, Integer> cost,
            Map<String, Integer> sockets,
            List<String> lore,
            boolean disabled) {
        this.id = id == null ? "" : id.trim().toLowerCase(Locale.ROOT);
        this.name = name == null || name.isBlank() ? this.id : name;
        this.partType = partType == null ? "" : partType.trim().toLowerCase(Locale.ROOT);
        this.types = types == null || types.isEmpty()
                ? EnumSet.noneOf(GearType.class)
                : EnumSet.copyOf(types);
        this.itemPath = itemPath == null ? "" : itemPath.trim();
        this.cost = cost == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(cost));
        Map<String, Integer> socketCopy = new LinkedHashMap<>();
        if (sockets != null) {
            for (Map.Entry<String, Integer> entry : sockets.entrySet()) {
                if (entry.getKey() == null || entry.getValue() == null || entry.getValue() <= 0) {
                    continue;
                }
                socketCopy.put(entry.getKey().trim().toLowerCase(Locale.ROOT), entry.getValue());
            }
        }
        this.sockets = Collections.unmodifiableMap(socketCopy);
        this.lore = lore == null ? List.of() : List.copyOf(lore);
        this.disabled = disabled;
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getPartType() {
        return partType;
    }

    public Set<GearType> getTypes() {
        return types;
    }

    public boolean supports(GearType type) {
        return type != null && types.contains(type);
    }

    public String getItemPath() {
        return itemPath;
    }

    public Map<String, Integer> getCost() {
        return cost;
    }

    public boolean hasCost() {
        return !cost.isEmpty();
    }

    public Map<String, Integer> getSockets() {
        return sockets;
    }

    public int socketCount(String slotId) {
        if (slotId == null) {
            return 0;
        }
        return sockets.getOrDefault(slotId.trim().toLowerCase(Locale.ROOT), 0);
    }

    public int totalSocketCount() {
        int sum = 0;
        for (int value : sockets.values()) {
            sum += value;
        }
        return sum;
    }

    public List<String> getLore() {
        return lore;
    }

    public boolean isDisabled() {
        return disabled;
    }

    public int getRevision() {
        return revision;
    }

    public void setRevision(int revision) {
        this.revision = revision;
    }

    /**
     * Canonical string of the fields that change gameplay. Name and lore are left out
     * so cosmetic edits do not bump the revision and refresh every crafted weapon.
     */
    public String buildRevisionContent() {
        StringBuilder out = new StringBuilder();
        out.append("type=").append(partType).append(';');
        out.append("gear=").append(new TreeSet<>(types.stream()
                .map(Enum::name).collect(Collectors.toList()))).append(';');
        out.append("item=").append(itemPath).append(';');
        out.append("cost=").append(new TreeMap<>(cost)).append(';');
        out.append("sockets=").append(new TreeMap<>(sockets)).append(';');
        out.append("disabled=").append(disabled);
        return out.toString();
    }
}
