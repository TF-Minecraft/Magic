package net.tfminecraft.magic.artifact.sacrifice;

import java.util.UUID;

import net.tfminecraft.interactiblefurniture.furniture.Furniture;
import net.tfminecraft.magic.artifact.shrine.ShrineScore;

public final class SacrificeRiteSession {

    private final UUID casterId;
    private final UUID victimId;
    private final UUID furnitureId;
    private final String slotId;
    private final String elementId;
    private final Furniture furniture;
    private final ShrineScore score;
    private final long startMillis;
    private boolean nearSent;
    private boolean resolved;
    private String resolvedTierId;
    private double resolvedCharge;

    public SacrificeRiteSession(
            UUID casterId,
            UUID victimId,
            Furniture furniture,
            String slotId,
            String elementId,
            ShrineScore score,
            long startMillis) {
        this.casterId = casterId;
        this.victimId = victimId;
        this.furniture = furniture;
        this.furnitureId = furniture != null ? furniture.getEntityId() : null;
        this.slotId = slotId;
        this.elementId = elementId;
        this.score = score;
        this.startMillis = startMillis;
    }

    public UUID getCasterId() {
        return casterId;
    }

    public UUID getVictimId() {
        return victimId;
    }

    public UUID getFurnitureId() {
        return furnitureId;
    }

    public String getSlotId() {
        return slotId;
    }

    public String getElementId() {
        return elementId;
    }

    public Furniture getFurniture() {
        return furniture;
    }

    public ShrineScore getScore() {
        return score;
    }

    public long getStartMillis() {
        return startMillis;
    }

    public boolean isNearSent() {
        return nearSent;
    }

    public void setNearSent(boolean nearSent) {
        this.nearSent = nearSent;
    }

    public boolean isResolved() {
        return resolved;
    }

    public String getResolvedTierId() {
        return resolvedTierId;
    }

    public double getResolvedCharge() {
        return resolvedCharge;
    }

    public void markResolved(boolean success, String tierId, double charge) {
        this.resolved = success;
        this.resolvedTierId = tierId;
        this.resolvedCharge = charge;
    }

    public double charge() {
        double seconds = SacrificeRegistry.getChargeSeconds();
        if (seconds <= 0) {
            return 1.0;
        }
        double elapsed = (System.currentTimeMillis() - startMillis) / 1000.0;
        return Math.min(1.0, Math.max(0.0, elapsed / seconds));
    }
}
