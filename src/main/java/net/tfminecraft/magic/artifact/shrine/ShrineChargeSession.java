package net.tfminecraft.magic.artifact.shrine;

import java.util.UUID;

import net.tfminecraft.furniture.Furniture;

public final class ShrineChargeSession {

    private final UUID furnitureId;
    private final String slotId;
    private final Furniture furniture;
    private final ShrineScore score;
    private final String forcedElement;
    private final double targetFill;
    private final boolean adminForced;
    private long lastPersistSeconds;

    public ShrineChargeSession(Furniture furniture, String slotId, ShrineScore score) {
        this(furniture, slotId, score, null, 0, false);
    }

    public ShrineChargeSession(
            Furniture furniture,
            String slotId,
            ShrineScore score,
            String forcedElement,
            double targetFill,
            boolean adminForced) {
        this.furniture = furniture;
        this.furnitureId = furniture.getEntityId();
        this.slotId = slotId;
        this.score = score;
        this.forcedElement = forcedElement;
        this.targetFill = targetFill;
        this.adminForced = adminForced;
        this.lastPersistSeconds = 0;
    }

    public UUID getFurnitureId() {
        return furnitureId;
    }

    public String getSlotId() {
        return slotId;
    }

    public Furniture getFurniture() {
        return furniture;
    }

    public ShrineScore getScore() {
        return score;
    }

    public String getForcedElement() {
        return forcedElement;
    }

    public double getTargetFill() {
        return targetFill;
    }

    public boolean isAdminForced() {
        return adminForced;
    }

    public long getLastPersistSeconds() {
        return lastPersistSeconds;
    }

    public void setLastPersistSeconds(long lastPersistSeconds) {
        this.lastPersistSeconds = lastPersistSeconds;
    }
}
