package net.tfminecraft.magic.model;

import java.util.List;

public final class CastModeDef {

    private final String id;
    private final String icon;
    private final String name;
    private final List<String> lore;

    public CastModeDef(String id, String icon, String name, List<String> lore) {
        this.id = id != null ? id : "";
        this.icon = icon != null ? icon : "v.BARRIER";
        this.name = name != null ? name : id;
        this.lore = lore != null ? List.copyOf(lore) : List.of();
    }

    public String getId() {
        return id;
    }

    public String getIcon() {
        return icon;
    }

    public String getName() {
        return name;
    }

    public List<String> getLore() {
        return lore;
    }
}
