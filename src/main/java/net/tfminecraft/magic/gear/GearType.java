package net.tfminecraft.magic.gear;

import org.bukkit.Material;

public enum GearType {
    STAFF("Staff", Material.STICK),
    WAND("Wand", Material.BLAZE_ROD),
    SWORD("Sword", Material.IRON_SWORD);

    private final String displayName;
    private final Material icon;

    GearType(String displayName, Material icon) {
        this.displayName = displayName;
        this.icon = icon;
    }

    public String getDisplayName() {
        return displayName;
    }

    public Material getIcon() {
        return icon;
    }

    public static GearType fromId(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }
}
