package net.tfminecraft.magic.artifact.shrine;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

public final class ShrineElementDef {

    private final String elementId;
    private final boolean sceneryCharge;
    private final List<ShrineFamily> families;

    public ShrineElementDef(String elementId, List<ShrineFamily> families) {
        this(elementId, true, families);
    }

    public ShrineElementDef(String elementId, boolean sceneryCharge, List<ShrineFamily> families) {
        this.elementId = elementId == null ? "" : elementId.trim().toLowerCase(Locale.ROOT);
        this.sceneryCharge = sceneryCharge;
        this.families = Collections.unmodifiableList(new ArrayList<>(
                families != null ? families : List.of()));
    }

    public String getElementId() {
        return elementId;
    }

    public boolean isSceneryCharge() {
        return sceneryCharge;
    }

    public List<ShrineFamily> getFamilies() {
        return families;
    }

    public double perfectScore() {
        double total = 0;
        for (ShrineFamily family : families) {
            total += family.getMaxCount() * family.getWeight();
        }
        return total;
    }
}
