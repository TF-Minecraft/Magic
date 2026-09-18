package net.tfminecraft.magic.artifact.create;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import net.tfminecraft.magic.artifact.ArtifactAuraCaps;
import net.tfminecraft.magic.artifact.config.ArtifactAffinityRegistry;
import net.tfminecraft.magic.artifact.config.ArtifactModelEntry;
import net.tfminecraft.magic.artifact.config.ArtifactRarityDef;
import net.tfminecraft.magic.artifact.config.ArtifactRarityRegistry;
import net.tfminecraft.magic.artifact.config.ArtifactTypeDef;
import net.tfminecraft.magic.artifact.config.ArtifactTypeRegistry;
import net.tfminecraft.magic.artifact.config.CapRange;
import net.tfminecraft.magic.artifact.generate.ArtifactAuraSlot;
import net.tfminecraft.magic.artifact.generate.ArtifactItemBuilder;
import net.tfminecraft.magic.artifact.generate.ArtifactRoll;
import net.tfminecraft.magic.util.MagicNumbers;

public final class ArtifactCreateSession {

    public enum ElementRole {
        OFF,
        SECONDARY,
        PRIMARY
    }

    private String rarityId;
    private String primaryId;
    private String selectedElementId;
    private final LinkedHashMap<String, Double> caps = new LinkedHashMap<>();
    private String previewBaseName;
    private String previewModelPath;
    private String previewLockPrimaryId;
    private String previewLockRarityId;

    public ArtifactCreateSession() {
        this.rarityId = defaultRarityId();
    }

    private static String defaultRarityId() {
        if (ArtifactRarityRegistry.contains("rare")) {
            return "rare";
        }
        List<ArtifactRarityDef> all = ArtifactRarityRegistry.getAll();
        if (all.isEmpty()) {
            return "";
        }
        return all.get(0).getId();
    }

    public String getRarityId() {
        return rarityId;
    }

    public void setRarityId(String rarityId) {
        this.rarityId = rarityId;
        clampCapsToRarity();
        clearPreviewLock();
    }

    public String getPrimaryId() {
        return primaryId;
    }

    public void setPrimaryId(String primaryId) {
        assignPrimary(primaryId);
    }

    public String getSelectedElementId() {
        return selectedElementId;
    }

    public void setSelectedElementId(String selectedElementId) {
        this.selectedElementId = selectedElementId;
    }

    public Map<String, Double> getCaps() {
        return Collections.unmodifiableMap(caps);
    }

    public double getCap(String elementId) {
        if (elementId == null) {
            return 0.0;
        }
        return caps.getOrDefault(elementId, 0.0);
    }

    public void setCap(String elementId, double cap) {
        if (elementId == null || elementId.isBlank()) {
            return;
        }
        if (cap <= 0) {
            caps.remove(elementId);
            return;
        }
        caps.put(elementId, cap);
    }

    public ElementRole roleOf(String elementId) {
        if (elementId == null || elementId.isBlank() || getCap(elementId) <= 0) {
            return ElementRole.OFF;
        }
        if (elementId.equals(primaryId)) {
            return ElementRole.PRIMARY;
        }
        return ElementRole.SECONDARY;
    }

    public boolean cycleElement(String elementId) {
        if (elementId == null || elementId.isBlank()) {
            return false;
        }
        selectedElementId = elementId;
        ArtifactTypeDef type = ArtifactTypeRegistry.getById(elementId);
        if (type == null || !type.isEnabled()) {
            return false;
        }
        ElementRole role = roleOf(elementId);
        if (role == ElementRole.OFF) {
            if (!ArtifactAffinityRegistry.compatibleWith(caps.keySet(), elementId)) {
                return false;
            }
            setCap(elementId, MagicNumbers.round(capRange(elementId).getMax(), 2));
            return true;
        }
        if (role == ElementRole.SECONDARY) {
            assignPrimary(elementId);
            return true;
        }
        setCap(elementId, 0);
        if (elementId.equals(primaryId)) {
            assignPrimary(null);
        }
        return true;
    }

    public boolean adjustSelectedCap(double delta) {
        String elementId = selectedElementId;
        if (elementId == null || elementId.isBlank() || roleOf(elementId) == ElementRole.OFF) {
            return false;
        }
        CapRange range = capRange(elementId);
        double next = MagicNumbers.round(
                MagicNumbers.clamp(getCap(elementId) + delta, range.getMin(), range.getMax()),
                2);
        if (next == getCap(elementId)) {
            return false;
        }
        setCap(elementId, next);
        return true;
    }

    public CapRange capRange(String elementId) {
        boolean primary = elementId != null && (elementId.equals(primaryId) || primaryId == null || primaryId.isBlank());
        CapRange range = primary
                ? ArtifactAuraCaps.primaryRange(elementId, rarityId)
                : ArtifactAuraCaps.secondaryRange(elementId, rarityId);
        double min = range.getMin() > 0 ? range.getMin() : 0.01;
        double max = range.getMax() > min ? range.getMax() : min;
        return new CapRange(min, max);
    }

    public ArtifactRoll toRoll() {
        if (primaryId == null || primaryId.isBlank() || getCap(primaryId) <= 0) {
            return null;
        }
        if (!ArtifactTypeRegistry.contains(primaryId) || !ArtifactRarityRegistry.contains(rarityId)) {
            return null;
        }
        ArtifactTypeDef primaryType = ArtifactTypeRegistry.getById(primaryId);
        if (primaryType == null || !primaryType.isEnabled()) {
            return null;
        }
        List<ArtifactAuraSlot> slots = new ArrayList<>();
        slots.add(new ArtifactAuraSlot(primaryId, getCap(primaryId)));
        for (Map.Entry<String, Double> entry : caps.entrySet()) {
            if (primaryId.equals(entry.getKey()) || entry.getValue() == null || entry.getValue() <= 0) {
                continue;
            }
            slots.add(new ArtifactAuraSlot(entry.getKey(), entry.getValue()));
        }
        return ArtifactRoll.ok(primaryId, rarityId, slots);
    }

    public String getPreviewBaseName() {
        return previewBaseName;
    }

    public String getPreviewModelPath() {
        return previewModelPath;
    }

    public void ensurePreviewLock(ArtifactItemBuilder builder) {
        if (builder == null || primaryId == null || getCap(primaryId) <= 0) {
            clearPreviewLock();
            return;
        }
        if (primaryId.equals(previewLockPrimaryId)
                && rarityId != null
                && rarityId.equals(previewLockRarityId)
                && previewBaseName != null
                && !previewBaseName.isBlank()) {
            return;
        }
        ArtifactTypeDef type = ArtifactTypeRegistry.getById(primaryId);
        if (type == null) {
            clearPreviewLock();
            return;
        }
        ArtifactModelEntry picked = builder.pickModel(type, rarityId);
        String pickedPath = picked != null ? picked.getPath() : null;
        String kind = picked != null ? picked.getKind() : ArtifactItemBuilder.kindForPath(type, pickedPath);
        previewBaseName = builder.pickBaseName(type, kind, rarityId);
        previewModelPath = pickedPath == null ? "" : pickedPath;
        previewLockPrimaryId = primaryId;
        previewLockRarityId = rarityId;
    }

    private void clampCapsToRarity() {
        for (String elementId : new ArrayList<>(caps.keySet())) {
            CapRange range = capRange(elementId);
            double clamped = MagicNumbers.round(
                    MagicNumbers.clamp(getCap(elementId), range.getMin(), range.getMax()),
                    2);
            setCap(elementId, clamped);
        }
        if (primaryId != null && getCap(primaryId) <= 0) {
            assignPrimary(null);
        }
    }

    private void assignPrimary(String next) {
        if (Objects.equals(primaryId, next)) {
            return;
        }
        primaryId = next;
        if (next != null) {
            clampCapsToRarity();
        }
        clearPreviewLock();
    }

    private void clearPreviewLock() {
        previewBaseName = null;
        previewModelPath = null;
        previewLockPrimaryId = null;
        previewLockRarityId = null;
    }
}
