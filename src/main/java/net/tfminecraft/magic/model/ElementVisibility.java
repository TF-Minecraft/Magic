package net.tfminecraft.magic.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import net.tfminecraft.magic.artifact.aura.AuraData;
import net.tfminecraft.magic.artifact.aura.AuraVessel;
import net.tfminecraft.magic.artifact.config.ArtifactTypeDef;
import net.tfminecraft.magic.artifact.config.ArtifactTypeRegistry;
import net.tfminecraft.magic.artifact.sacrifice.SacrificeElementDef;
import net.tfminecraft.magic.artifact.sacrifice.SacrificeRegistry;
import net.tfminecraft.magic.artifact.shrine.ShrineChargeService;
import net.tfminecraft.magic.artifact.shrine.ShrineElementDef;
import net.tfminecraft.magic.artifact.shrine.ShrineRegistry;

/**
 * Which elements may be written onto artifacts and enchanted charges.
 *
 * <p>An element is playable when players can actually put aura into it: scenery
 * charging is on, or its sacrifice rite is enabled. Schools that are switched off
 * (Shadowmancy, and the other inert schools) stay in the resonance menu but are
 * not stamped onto items. An artifact still names its own primary, so a staff-made
 * item of that school keeps its line. Disabled artifact types never appear.
 */
public final class ElementVisibility {

    private ElementVisibility() {}

    public static boolean typeEnabled(String elementId) {
        ArtifactTypeDef type = typeOf(elementId);
        return type == null || type.isEnabled();
    }

    /**
     * True when a player can fill this element at a shrine or through an enabled rite.
     * Missing shrine data counts as playable so a failed config load does not blank items.
     */
    public static boolean playerCanCharge(String elementId) {
        if (elementId == null || elementId.isBlank() || !typeEnabled(elementId)) {
            return false;
        }
        ShrineElementDef shrine = ShrineRegistry.getById(elementId);
        boolean sceneryOpen = shrine == null || shrine.isSceneryCharge();
        if (sceneryOpen && !ShrineChargeService.blocksVanillaCharge(elementId)) {
            return true;
        }
        if (!SacrificeRegistry.isEnabled()) {
            return false;
        }
        SacrificeElementDef rite = SacrificeRegistry.getById(elementId);
        return rite != null && rite.isEnabled();
    }

    /** Charges only list elements a player can fill. */
    public static boolean shownOnCharge(String elementId) {
        return playerCanCharge(elementId);
    }

    /**
     * Artifact lines list playable elements, plus the artifact's own primary even when
     * that school cannot be filled from a shrine.
     */
    public static boolean shownOnArtifact(String elementId, String primaryId) {
        if (!typeEnabled(elementId)) {
            return false;
        }
        if (playerCanCharge(elementId)) {
            return true;
        }
        return primaryId != null && !primaryId.isBlank() && primaryId.equalsIgnoreCase(elementId);
    }

    public static void stripCharge(AuraVessel vessel) {
        if (vessel == null) {
            return;
        }
        for (String elementId : copyIds(vessel.getCappedElementIds())) {
            if (!shownOnCharge(elementId)) {
                vessel.setCap(elementId, 0);
            }
        }
    }

    public static void stripCharge(AuraData data) {
        if (data == null) {
            return;
        }
        for (String elementId : copyIds(data.getCappedElementIds())) {
            if (!shownOnCharge(elementId)) {
                data.setCap(elementId, 0);
            }
        }
    }

    public static void stripArtifact(AuraVessel vessel, String primaryId) {
        if (vessel == null) {
            return;
        }
        for (String elementId : copyIds(vessel.getCappedElementIds())) {
            if (!shownOnArtifact(elementId, primaryId)) {
                vessel.setCap(elementId, 0);
            }
        }
    }

    private static List<String> copyIds(Set<String> ids) {
        return new ArrayList<>(ids);
    }

    private static ArtifactTypeDef typeOf(String elementId) {
        if (elementId == null || elementId.isBlank()) {
            return null;
        }
        ArtifactTypeDef exact = ArtifactTypeRegistry.getById(elementId);
        if (exact != null) {
            return exact;
        }
        String id = elementId.trim().toLowerCase(Locale.ROOT);
        exact = ArtifactTypeRegistry.getById(id);
        if (exact != null) {
            return exact;
        }
        for (ArtifactTypeDef type : ArtifactTypeRegistry.getAll()) {
            if (type.getElementId() != null && type.getElementId().equalsIgnoreCase(id)) {
                return type;
            }
        }
        return null;
    }
}
