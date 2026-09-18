package net.tfminecraft.magic.artifact.shrine;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.bukkit.Location;

public final class ShrineElementScore {

    private final String elementId;
    private final double maxAura;
    private final double auraPerSecond;
    private final List<String> activeFamilyIds;
    private final List<Location> contributingBlocks;

    public ShrineElementScore(
            String elementId,
            double maxAura,
            double auraPerSecond,
            List<String> activeFamilyIds,
            List<Location> contributingBlocks) {
        this.elementId = elementId;
        this.maxAura = maxAura;
        this.auraPerSecond = auraPerSecond;
        this.activeFamilyIds = Collections.unmodifiableList(new ArrayList<>(
                activeFamilyIds != null ? activeFamilyIds : List.of()));
        this.contributingBlocks = Collections.unmodifiableList(new ArrayList<>(
                contributingBlocks != null ? contributingBlocks : List.of()));
    }

    public String getElementId() {
        return elementId;
    }

    public double getMaxAura() {
        return maxAura;
    }

    public double getAuraPerSecond() {
        return auraPerSecond;
    }

    public List<String> getActiveFamilyIds() {
        return activeFamilyIds;
    }

    public List<Location> getContributingBlocks() {
        return contributingBlocks;
    }
}
