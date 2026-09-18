package net.tfminecraft.magic.gear;

import java.util.ArrayList;
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
    private final int tier;
    private final Set<GearType> types;
    private final String itemPath;
    private final Map<String, Integer> cost;
    private final List<String> partLimit;
    private final Map<String, Double> stats;
    private final Map<String, Integer> sockets;
    private final List<String> lore;
    private final String schemeId;
    private final int schemeWeight;
    private final boolean disabled;
    private int revision = 1;

    public PartDef(
            String id,
            String name,
            String partType,
            int tier,
            Set<GearType> types,
            String itemPath,
            Map<String, Integer> cost,
            List<String> partLimit,
            Map<String, Double> stats,
            Map<String, Integer> sockets,
            List<String> lore,
            String schemeId,
            int schemeWeight,
            boolean disabled) {
        this.id = id == null ? "" : id.trim().toLowerCase(Locale.ROOT);
        this.name = name == null || name.isBlank() ? this.id : name;
        this.partType = partType == null ? "" : partType.trim().toLowerCase(Locale.ROOT);
        this.tier = tier < 1 ? 0 : tier;
        this.types = types == null || types.isEmpty()
                ? EnumSet.noneOf(GearType.class)
                : EnumSet.copyOf(types);
        this.itemPath = itemPath == null ? "" : itemPath.trim();
        this.cost = cost == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(cost));
        List<String> limitCopy = new ArrayList<>();
        if (partLimit != null) {
            for (String category : partLimit) {
                if (category == null || category.isBlank()) {
                    continue;
                }
                String key = category.trim().toLowerCase(Locale.ROOT);
                if (!limitCopy.contains(key)) {
                    limitCopy.add(key);
                }
            }
        }
        this.partLimit = List.copyOf(limitCopy);
        this.stats = stats == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(stats));
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
        this.schemeId = schemeId == null ? "" : schemeId.trim().toLowerCase(Locale.ROOT);
        this.schemeWeight = schemeWeight < 1 ? 1 : schemeWeight;
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

    public int getTier() {
        return tier;
    }

    public boolean hasTier() {
        return tier > 0;
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

    public List<String> getPartLimit() {
        return partLimit;
    }

    public boolean hasPartLimit() {
        return !partLimit.isEmpty();
    }

    public boolean allowsPart(String category) {
        if (!hasPartLimit()) {
            return true;
        }
        if (category == null || category.isBlank()) {
            return false;
        }
        return partLimit.contains(category.trim().toLowerCase(Locale.ROOT));
    }

    public Map<String, Double> getStats() {
        return stats;
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

    public String getSchemeId() {
        return schemeId;
    }

    public int getSchemeWeight() {
        return schemeWeight;
    }

    public boolean hasModelScheme() {
        return !schemeId.isEmpty();
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
        out.append("tier=").append(tier).append(';');
        out.append("gear=").append(new TreeSet<>(types.stream()
                .map(Enum::name).collect(Collectors.toList()))).append(';');
        out.append("item=").append(itemPath).append(';');
        out.append("cost=").append(new TreeMap<>(cost)).append(';');
        out.append("part-limit=").append(partLimit).append(';');
        out.append("stats=").append(new TreeMap<>(stats)).append(';');
        out.append("sockets=").append(new TreeMap<>(sockets)).append(';');
        out.append("scheme=").append(schemeId).append(';');
        out.append("scheme-weight=").append(schemeWeight).append(';');
        out.append("disabled=").append(disabled);
        return out.toString();
    }
}
