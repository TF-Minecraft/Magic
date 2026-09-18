package net.tfminecraft.magic.charge;

/** One enchanted charge tier: the item that represents it and how much aura it holds. */
public final class ChargeDef {

    private final int tier;
    private final String itemPath;
    private final double auraCap;

    public ChargeDef(int tier, String itemPath, double auraCap) {
        this.tier = tier;
        this.itemPath = itemPath == null ? "" : itemPath.trim();
        this.auraCap = Math.max(0.0, auraCap);
    }

    public int getTier() {
        return tier;
    }

    public String getItemPath() {
        return itemPath;
    }

    /** Cap written per element when this charge imprints at a shrine. */
    public double getAuraCap() {
        return auraCap;
    }
}
