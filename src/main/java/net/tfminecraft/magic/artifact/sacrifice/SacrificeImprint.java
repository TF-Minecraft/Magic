package net.tfminecraft.magic.artifact.sacrifice;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class SacrificeImprint {

    public static final String PAIN = "pain";
    public static final String SCREAMS = "screams";
    public static final String SOUL = "soul";

    private final String characterId;
    private final String characterName;
    private final String stage;
    private final String elementId;

    public SacrificeImprint(String characterId, String characterName, String stage) {
        this(characterId, characterName, stage, "");
    }

    public SacrificeImprint(String characterId, String characterName, String stage, String elementId) {
        this.characterId = characterId != null ? characterId : "";
        this.characterName = characterName != null ? characterName : "";
        this.stage = normalizeStage(stage);
        this.elementId = elementId != null ? elementId.trim().toLowerCase(Locale.ROOT) : "";
    }

    public String getCharacterId() {
        return characterId;
    }

    public String getCharacterName() {
        return characterName;
    }

    public String getStage() {
        return stage;
    }

    public String getElementId() {
        return elementId;
    }

    public int rank() {
        return rankOf(stage);
    }

    public static String normalizeStage(String stage) {
        if (stage == null) {
            return PAIN;
        }
        String key = stage.trim().toLowerCase(Locale.ROOT);
        if ("death".equals(key)) {
            return SOUL;
        }
        if (SOUL.equals(key) || SCREAMS.equals(key) || PAIN.equals(key)) {
            return key;
        }
        return PAIN;
    }

    public static int rankOf(String stage) {
        String key = normalizeStage(stage);
        if (SOUL.equals(key)) {
            return 2;
        }
        if (SCREAMS.equals(key)) {
            return 1;
        }
        return 0;
    }

    public static String stageForTier(SacrificeTierDef tier) {
        if (tier == null) {
            return null;
        }
        if (tier.isPermadeath() || "death".equals(tier.getId())) {
            return SOUL;
        }
        if ("maim".equals(tier.getId()) || "permanent".equals(tier.getInjury())) {
            return SCREAMS;
        }
        if ("wound".equals(tier.getId()) || "healing".equals(tier.getInjury())) {
            return PAIN;
        }
        return null;
    }

    public static List<SacrificeImprint> upsert(List<SacrificeImprint> existing, SacrificeImprint next) {
        List<SacrificeImprint> out = new ArrayList<>(existing != null ? existing : List.of());
        if (next == null || next.getCharacterId().isBlank()) {
            return out;
        }
        for (int i = 0; i < out.size(); i++) {
            SacrificeImprint current = out.get(i);
            if (!next.getCharacterId().equals(current.getCharacterId())) {
                continue;
            }
            String name = !next.getCharacterName().isBlank() ? next.getCharacterName() : current.getCharacterName();
            String stage = next.rank() >= current.rank() ? next.getStage() : current.getStage();
            String element = !next.getElementId().isBlank() ? next.getElementId() : current.getElementId();
            out.set(i, new SacrificeImprint(current.getCharacterId(), name, stage, element));
            return out;
        }
        out.add(next);
        return out;
    }
}
