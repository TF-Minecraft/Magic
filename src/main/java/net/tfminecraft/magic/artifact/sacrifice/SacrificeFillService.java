package net.tfminecraft.magic.artifact.sacrifice;

import java.util.List;
import java.util.UUID;

import org.bukkit.inventory.ItemStack;

import net.tfminecraft.InteractibleFurniture;
import net.tfminecraft.furniture.Furniture;
import net.tfminecraft.furniture.PlacedSlot;
import net.tfminecraft.magic.Cache;
import net.tfminecraft.magic.artifact.aura.AuraVessel;
import net.tfminecraft.magic.artifact.aura.AuraVessels;
import net.tfminecraft.magic.artifact.ArtifactIds;
import net.tfminecraft.magic.artifact.shrine.ShrineChargeService;
import net.tfminecraft.magic.artifact.shrine.ShrineElementScore;
import net.tfminecraft.magic.artifact.shrine.ShrineScore;
import net.tfminecraft.magic.attunement.AuraLog;
import net.tfminecraft.magic.util.MagicNumbers;

public final class SacrificeFillService {

    private SacrificeFillService() {}

    public static void apply(SacrificeRiteSession session, SacrificeTierDef tier, String characterId, String characterName) {
        if (session == null || !session.isResolved() || tier == null) {
            return;
        }
        if (tier.getAuraFraction() <= 0) {
            return;
        }
        Furniture furniture = session.getFurniture();
        if (furniture == null) {
            return;
        }
        PlacedSlot slot = furniture.getActiveSlot(session.getSlotId()).orElse(null);
        ItemStack item = ShrineChargeService.itemFromSlot(slot);
        AuraVessel artifact = AuraVessels.fromItem(item);
        if (artifact == null || item == null) {
            return;
        }
        String elementId = session.getElementId();
        if (elementId == null || elementId.isBlank()) {
            return;
        }
        if (artifact.getCap(elementId) <= 0) {
            return;
        }
        double factor = shrineFactor(session.getScore(), elementId);
        double gain = Cache.artifactAuraCap * tier.getAuraFraction() * factor;
        double fillBefore = artifact.getFill(elementId);
        artifact.setFill(elementId, fillBefore + gain);
        String stage = SacrificeImprint.stageForTier(tier);
        // Imprints are part of an artifact's history. A charge is spent, so it keeps none.
        if (stage != null && artifact.isArtifact() && characterId != null && !characterId.isBlank()) {
            List<SacrificeImprint> imprints = SacrificeImprint.upsert(
                    SacrificeImprintStore.read(item),
                    new SacrificeImprint(characterId, characterName, stage, elementId));
            SacrificeImprintStore.write(item, imprints);
        }
        artifact.write(item);
        if (slot != null) {
            slot.setCurrentItem(item);
        }
        persist(furniture);
        AuraLog.append(
                "sacrifice artifact=%s element=%s fill=%s->%s gain=%s factor=%s cap=%s character=%s",
                artifactId(item),
                elementId,
                AuraLog.n(fillBefore),
                AuraLog.n(artifact.getFill(elementId)),
                AuraLog.n(gain),
                AuraLog.n(factor),
                AuraLog.n(artifact.getCap(elementId)),
                characterId != null && !characterId.isBlank() ? characterId : "-");
    }

    private static String artifactId(ItemStack item) {
        UUID id = ArtifactIds.read(item);
        return id != null ? id.toString() : "-";
    }

    private static double shrineFactor(ShrineScore score, String elementId) {
        if (score == null || Cache.artifactAuraCap <= 0) {
            return 0.0;
        }
        ShrineElementScore elementScore = score.get(elementId);
        if (elementScore == null) {
            return 0.0;
        }
        return MagicNumbers.clamp(elementScore.getMaxAura() / Cache.artifactAuraCap, 0.0, 1.0);
    }

    private static void persist(Furniture furniture) {
        if (furniture == null) {
            return;
        }
        try {
            InteractibleFurniture.getInstance().getFurnitureManager().persistFurniture(furniture);
        } catch (Exception ignored) {
            // IF may already be disabled
        }
    }
}
