package net.tfminecraft.magic.gear;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

public final class MajorityTierResolver {

    private MajorityTierResolver() {}

    public static int resolve(Collection<PartDef> parts) {
        if (parts == null || parts.isEmpty()) {
            return 0;
        }
        Map<Integer, Integer> votes = new HashMap<>();
        for (PartDef part : parts) {
            if (part == null || !part.hasTier()) {
                continue;
            }
            votes.merge(part.getTier(), 1, Integer::sum);
        }
        int bestTier = 0;
        int bestCount = 0;
        for (Map.Entry<Integer, Integer> entry : votes.entrySet()) {
            int tier = entry.getKey();
            int count = entry.getValue();
            if (count > bestCount || (count == bestCount && tier > bestTier)) {
                bestCount = count;
                bestTier = tier;
            }
        }
        return bestTier;
    }

    public static String toRoman(int tier) {
        return switch (tier) {
            case 1 -> "I";
            case 2 -> "II";
            case 3 -> "III";
            case 4 -> "IV";
            default -> String.valueOf(tier);
        };
    }
}
