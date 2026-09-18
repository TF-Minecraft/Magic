package net.tfminecraft.magic.gear;

public final class PartTypeDef {

    private final String id;
    private final int slot;
    private final String name;

    public PartTypeDef(String id, int slot) {
        this(id, slot, null);
    }

    public PartTypeDef(String id, int slot, String name) {
        this.id = id == null ? "" : id.trim().toLowerCase();
        this.slot = slot;
        String trimmed = name == null ? "" : name.trim();
        this.name = trimmed.isEmpty() ? SocketLayout.prettyId(this.id) : trimmed;
    }

    public String getId() {
        return id;
    }

    public int getSlot() {
        return slot;
    }

    public String getName() {
        return name;
    }
}
