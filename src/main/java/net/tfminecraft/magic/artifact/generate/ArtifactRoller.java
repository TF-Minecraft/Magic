package net.tfminecraft.magic.artifact.generate;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;

import net.tfminecraft.magic.artifact.ArtifactAuraCaps;
import net.tfminecraft.magic.artifact.config.ArtifactAffinityRegistry;
import net.tfminecraft.magic.artifact.config.ArtifactGeneratorCache;
import net.tfminecraft.magic.artifact.config.ArtifactRarityDef;
import net.tfminecraft.magic.artifact.config.ArtifactRarityRegistry;
import net.tfminecraft.magic.artifact.config.ArtifactTypeDef;
import net.tfminecraft.magic.artifact.config.ArtifactTypeRegistry;
import net.tfminecraft.magic.artifact.config.CapRange;
import net.tfminecraft.magic.artifact.path.ArtifactPathSpec;
import net.tfminecraft.magic.model.ElementVisibility;
import net.tfminecraft.magic.util.MagicNumbers;

public final class ArtifactRoller {

    private final Random rng;

    public ArtifactRoller() {
        this(ThreadLocalRandom.current());
    }

    public ArtifactRoller(Random rng) {
        this.rng = rng != null ? rng : ThreadLocalRandom.current();
    }

    public ArtifactRoll roll(String primaryId, String rarityId) {
        ArtifactRarityDef rarity;
        ArtifactTypeDef type;
        if (isRandomToken(primaryId)) {
            rarity = resolveRarity(rarityId);
            if (rarity == null) {
                return ArtifactRoll.error(rarityId == null || rarityId.isBlank() || isRollToken(rarityId)
                        ? "artifact.roll.empty_rarities"
                        : "artifact.roll.unknown_rarity");
            }
            type = pickWeightedType(rarity.getId());
            if (type == null) {
                return ArtifactRoll.error("artifact.roll.empty_types");
            }
        } else {
            type = findType(primaryId);
            if (type == null) {
                return ArtifactRoll.error("artifact.roll.unknown_element");
            }
            if (!type.isEnabled()) {
                return ArtifactRoll.error("artifact.roll.disabled", type.getElementId(), rarityId);
            }
            rarity = resolveRarity(rarityId);
            if (rarity == null) {
                return ArtifactRoll.error(rarityId == null || rarityId.isBlank() || isRollToken(rarityId)
                        ? "artifact.roll.empty_rarities"
                        : "artifact.roll.unknown_rarity");
            }
            if (!type.hasRarity(rarity.getId())) {
                return ArtifactRoll.error("artifact.roll.no_bracket", type.getElementId(), rarity.getId());
            }
        }

        List<ArtifactTypeDef> pool = new ArrayList<>();
        for (String companionId : ArtifactAffinityRegistry.companions(type.getElementId())) {
            ArtifactTypeDef companion = findType(companionId);
            if (companion == null || !companion.isEnabled() || !companion.hasRarity(rarity.getId())) {
                continue;
            }
            if (!ElementVisibility.shownOnArtifact(companion.getElementId(), type.getElementId())) {
                continue;
            }
            pool.add(companion);
        }

        int wanted = inclusiveInt(rarity.getMinElements(), rarity.getMaxElements());
        wanted = Math.min(wanted, 1 + pool.size());

        List<ArtifactAuraSlot> slots = new ArrayList<>();
        slots.add(new ArtifactAuraSlot(
                type.getElementId(),
                rollCap(ArtifactAuraCaps.primaryRange(type.getElementId(), rarity.getId()))));

        int secondaryCount = Math.max(0, wanted - 1);
        while (slots.size() < 1 + secondaryCount && !pool.isEmpty()) {
            List<ArtifactTypeDef> eligible = new ArrayList<>();
            for (ArtifactTypeDef companion : pool) {
                if (ArtifactAffinityRegistry.compatibleWith(slotIds(slots), companion.getElementId())) {
                    eligible.add(companion);
                }
            }
            ArtifactTypeDef picked = pickWeighted(eligible);
            if (picked == null) {
                break;
            }
            pool.remove(picked);
            slots.add(new ArtifactAuraSlot(
                    picked.getElementId(),
                    rollCap(ArtifactAuraCaps.secondaryRange(picked.getElementId(), rarity.getId()))));
        }
        return ArtifactRoll.ok(type.getElementId(), rarity.getId(), slots);
    }

    public ArtifactRoll roll(ArtifactPathSpec spec) {
        if (spec == null || spec.getPrimaryId() == null || spec.getPrimaryId().isBlank()) {
            String rarityArg = spec != null && spec.getRarityId() != null ? spec.getRarityId() : "roll";
            return roll("random", rarityArg);
        }
        return rollLockedPrimary(spec);
    }

    private ArtifactRoll rollLockedPrimary(ArtifactPathSpec spec) {
        ArtifactTypeDef type = findType(spec.getPrimaryId());
        if (type == null) {
            return ArtifactRoll.error("artifact.roll.unknown_element");
        }
        if (!type.isEnabled()) {
            return ArtifactRoll.error("artifact.roll.disabled", type.getElementId(), spec.getRarityId());
        }
        ArtifactRarityDef rarity = resolveRarity(spec.getRarityId());
        if (rarity == null) {
            return ArtifactRoll.error("artifact.roll.empty_rarities");
        }
        if (!type.hasRarity(rarity.getId())) {
            return ArtifactRoll.error("artifact.roll.no_bracket", type.getElementId(), rarity.getId());
        }

        CapRange primaryRange = ArtifactAuraCaps.primaryRange(type.getElementId(), rarity.getId());
        Double lockedPrimaryCap = spec.getExtras().get(type.getElementId());
        double primaryCap = lockedPrimaryCap != null
                ? clampToRange(lockedPrimaryCap, primaryRange)
                : rollCap(primaryRange);
        List<ArtifactAuraSlot> slots = new ArrayList<>();
        slots.add(new ArtifactAuraSlot(type.getElementId(), primaryCap));

        for (Map.Entry<String, Double> extra : spec.getExtras().entrySet()) {
            if (extra.getKey() == null || extra.getKey().equalsIgnoreCase(type.getElementId())) {
                continue;
            }
            ArtifactTypeDef companion = findType(extra.getKey());
            if (companion == null || !companion.isEnabled() || !companion.hasRarity(rarity.getId())) {
                continue;
            }
            if (!ElementVisibility.shownOnArtifact(companion.getElementId(), type.getElementId())) {
                continue;
            }
            if (!ArtifactAffinityRegistry.compatibleWith(slotIds(slots), companion.getElementId())) {
                continue;
            }
            CapRange secondaryRange = ArtifactAuraCaps.secondaryRange(companion.getElementId(), rarity.getId());
            double cap = extra.getValue() != null
                    ? clampToRange(extra.getValue(), secondaryRange)
                    : rollCap(secondaryRange);
            if (cap <= 0) {
                continue;
            }
            slots.add(new ArtifactAuraSlot(companion.getElementId(), cap));
        }
        return ArtifactRoll.ok(type.getElementId(), rarity.getId(), slots);
    }

    private ArtifactTypeDef pickWeightedType(String rarityId) {
        List<ArtifactTypeDef> eligible = new ArrayList<>();
        for (ArtifactTypeDef type : ArtifactTypeRegistry.getAll()) {
            if (type.isEnabled() && type.getWeight() > 0 && type.hasRarity(rarityId)
                    && ArtifactGeneratorCache.inRandomPool(type.getElementId())) {
                eligible.add(type);
            }
        }
        return pickWeighted(eligible);
    }

    private ArtifactTypeDef pickWeighted(List<ArtifactTypeDef> types) {
        if (types == null || types.isEmpty()) {
            return null;
        }
        double total = 0.0;
        for (ArtifactTypeDef type : types) {
            if (type.getWeight() > 0) {
                total += type.getWeight();
            }
        }
        if (total <= 0) {
            return null;
        }
        double pick = rng.nextDouble() * total;
        double acc = 0.0;
        ArtifactTypeDef last = null;
        for (ArtifactTypeDef type : types) {
            if (type.getWeight() <= 0) {
                continue;
            }
            acc += type.getWeight();
            last = type;
            if (pick <= acc) {
                return type;
            }
        }
        return last;
    }

    private static List<String> slotIds(List<ArtifactAuraSlot> slots) {
        List<String> ids = new ArrayList<>();
        if (slots == null) {
            return ids;
        }
        for (ArtifactAuraSlot slot : slots) {
            if (slot != null && slot.getElementId() != null && !slot.getElementId().isBlank()) {
                ids.add(slot.getElementId());
            }
        }
        return ids;
    }

    private static double clampToRange(double value, CapRange range) {
        CapRange usable = ArtifactAuraCaps.clampRange(range);
        double min = usable.getMin() > 0 ? usable.getMin() : 0.01;
        double max = usable.getMax() > min ? usable.getMax() : min;
        return MagicNumbers.round(MagicNumbers.clamp(value, min, max), 2);
    }

    private double rollCap(CapRange range) {
        CapRange usable = ArtifactAuraCaps.clampRange(range);
        double min = usable.getMin() > 0 ? usable.getMin() : 0.01;
        double max = usable.getMax() > min ? usable.getMax() : min;
        if (min >= max) {
            return MagicNumbers.round(max, 2);
        }
        return MagicNumbers.round(min + rng.nextDouble() * (max - min), 2);
    }

    private ArtifactRarityDef resolveRarity(String rarityId) {
        if (rarityId == null || rarityId.isBlank() || isRollToken(rarityId)) {
            return pickWeightedRarity();
        }
        return findRarity(rarityId);
    }

    private ArtifactRarityDef pickWeightedRarity() {
        List<ArtifactRarityDef> rarities = ArtifactRarityRegistry.getAll();
        double total = 0.0;
        for (ArtifactRarityDef rarity : rarities) {
            if (rarity.getWeight() > 0) {
                total += rarity.getWeight();
            }
        }
        if (total <= 0) {
            return null;
        }
        double pick = rng.nextDouble() * total;
        double acc = 0.0;
        ArtifactRarityDef last = null;
        for (ArtifactRarityDef rarity : rarities) {
            if (rarity.getWeight() <= 0) {
                continue;
            }
            acc += rarity.getWeight();
            last = rarity;
            if (pick <= acc) {
                return rarity;
            }
        }
        return last;
    }

    private int inclusiveInt(int min, int max) {
        if (min >= max) {
            return min;
        }
        return min + rng.nextInt(max - min + 1);
    }

    private static ArtifactTypeDef findType(String id) {
        if (id == null || id.isBlank()) {
            return null;
        }
        ArtifactTypeDef exact = ArtifactTypeRegistry.getById(id);
        if (exact != null) {
            return exact;
        }
        for (ArtifactTypeDef type : ArtifactTypeRegistry.getAll()) {
            if (type.getElementId().equalsIgnoreCase(id)) {
                return type;
            }
        }
        return null;
    }

    private static ArtifactRarityDef findRarity(String id) {
        if (id == null || id.isBlank()) {
            return null;
        }
        ArtifactRarityDef exact = ArtifactRarityRegistry.getById(id);
        if (exact != null) {
            return exact;
        }
        for (ArtifactRarityDef rarity : ArtifactRarityRegistry.getAll()) {
            if (rarity.getId().equalsIgnoreCase(id)) {
                return rarity;
            }
        }
        return null;
    }

    private static boolean isRandomToken(String value) {
        return value == null || value.isBlank() || "random".equalsIgnoreCase(value);
    }

    private static boolean isRollToken(String value) {
        return "roll".equalsIgnoreCase(value);
    }
}
