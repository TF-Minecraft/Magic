package net.tfminecraft.magic.artifact.generate;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class ArtifactRoll {

    public enum Status {
        OK,
        ERROR
    }

    private final Status status;
    private final String errorKey;
    private final String primaryId;
    private final String rarityId;
    private final List<ArtifactAuraSlot> slots;

    private ArtifactRoll(
            Status status,
            String errorKey,
            String primaryId,
            String rarityId,
            List<ArtifactAuraSlot> slots) {
        this.status = status;
        this.errorKey = errorKey;
        this.primaryId = primaryId;
        this.rarityId = rarityId;
        this.slots = slots == null ? List.of() : Collections.unmodifiableList(new ArrayList<>(slots));
    }

    public static ArtifactRoll ok(String primaryId, String rarityId, List<ArtifactAuraSlot> slots) {
        return new ArtifactRoll(Status.OK, null, primaryId, rarityId, slots);
    }

    public static ArtifactRoll error(String errorKey) {
        return new ArtifactRoll(Status.ERROR, errorKey, null, null, List.of());
    }

    public static ArtifactRoll error(String errorKey, String primaryId, String rarityId) {
        return new ArtifactRoll(Status.ERROR, errorKey, primaryId, rarityId, List.of());
    }

    public Status getStatus() {
        return status;
    }

    public boolean isError() {
        return status == Status.ERROR;
    }

    public String getErrorKey() {
        return errorKey;
    }

    public String getPrimaryId() {
        return primaryId;
    }

    public String getRarityId() {
        return rarityId;
    }

    public List<ArtifactAuraSlot> getSlots() {
        return slots;
    }
}
