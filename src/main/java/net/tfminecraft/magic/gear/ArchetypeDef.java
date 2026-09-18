package net.tfminecraft.magic.gear;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;

public final class ArchetypeDef {

    private final GearType type;
    private final String name;
    private final String template;
    private final String icon;
    private final boolean melee;
    private final List<String> required;
    private final Map<String, String> slots;
    private int revision = 1;

    public ArchetypeDef(
            GearType type,
            String name,
            String template,
            String icon,
            boolean melee,
            List<String> required,
            Map<String, String> slots) {
        this.type = type;
        this.name = name == null || name.isBlank() ? type.getDisplayName() : name;
        this.template = template == null ? "" : template.trim();
        this.icon = icon == null ? "" : icon.trim();
        this.melee = melee;
        this.required = List.copyOf(required == null ? List.of() : required);
        Map<String, String> copy = new LinkedHashMap<>();
        if (slots != null) {
            for (Map.Entry<String, String> entry : slots.entrySet()) {
                if (entry.getKey() == null || entry.getValue() == null || entry.getValue().isBlank()) {
                    continue;
                }
                copy.put(entry.getKey().trim().toLowerCase(Locale.ROOT), entry.getValue().trim());
            }
        }
        this.slots = Collections.unmodifiableMap(copy);
    }

    public GearType getType() {
        return type;
    }

    public String getName() {
        return name;
    }

    public String getTemplate() {
        return template;
    }

    public String getIcon() {
        return icon;
    }

    public boolean isMelee() {
        return melee;
    }

    public List<String> getRequired() {
        return required;
    }

    public Map<String, String> getSlots() {
        return slots;
    }

    public String slotSuffix(String slotId) {
        if (slotId == null) {
            return "";
        }
        return slots.getOrDefault(slotId.trim().toLowerCase(Locale.ROOT), "");
    }

    public List<String> slotIds() {
        return new ArrayList<>(slots.keySet());
    }

    public int getRevision() {
        return revision;
    }

    public void setRevision(int revision) {
        this.revision = revision;
    }

    /** Canonical string of the fields that change gameplay. Name is left out. */
    public String buildRevisionContent() {
        StringBuilder out = new StringBuilder();
        out.append("template=").append(template).append(';');
        out.append("melee=").append(melee).append(';');
        out.append("required=").append(new TreeSet<>(required)).append(';');
        out.append("slots=").append(new TreeMap<>(slots));
        return out.toString();
    }
}
