package net.tfminecraft.magic.artifact.sacrifice;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

public final class SacrificeElementDef {

    private final String elementId;
    private final boolean enabled;
    private final List<String> words;
    private final String lorePain;
    private final String loreScreams;
    private final String loreSoul;
    private final double minSceneryAura;

    public SacrificeElementDef(
            String elementId,
            List<String> words,
            String lorePain,
            String loreScreams,
            String loreSoul,
            double minSceneryAura) {
        this(elementId, true, words, lorePain, loreScreams, loreSoul, minSceneryAura);
    }

    public SacrificeElementDef(
            String elementId,
            boolean enabled,
            List<String> words,
            String lorePain,
            String loreScreams,
            String loreSoul,
            double minSceneryAura) {
        this.elementId = elementId == null ? "" : elementId.trim().toLowerCase(Locale.ROOT);
        this.enabled = enabled;
        this.words = Collections.unmodifiableList(new ArrayList<>(
                words != null ? words : List.of()));
        this.lorePain = lorePain != null ? lorePain : "";
        this.loreScreams = loreScreams != null ? loreScreams : "";
        this.loreSoul = loreSoul != null ? loreSoul : "";
        this.minSceneryAura = minSceneryAura;
    }

    public String getElementId() {
        return elementId;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public List<String> getWords() {
        return words;
    }

    public String getLorePain() {
        return lorePain;
    }

    public String getLoreScreams() {
        return loreScreams;
    }

    public String getLoreSoul() {
        return loreSoul;
    }

    public double getMinSceneryAura() {
        return minSceneryAura;
    }
}
