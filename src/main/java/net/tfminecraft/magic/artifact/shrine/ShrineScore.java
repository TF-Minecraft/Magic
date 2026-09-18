package net.tfminecraft.magic.artifact.shrine;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public final class ShrineScore {

    private final Map<String, ShrineElementScore> byElement;

    public ShrineScore(Map<String, ShrineElementScore> byElement) {
        Map<String, ShrineElementScore> copy = new LinkedHashMap<>();
        if (byElement != null) {
            copy.putAll(byElement);
        }
        this.byElement = Collections.unmodifiableMap(copy);
    }

    public Map<String, ShrineElementScore> getByElement() {
        return byElement;
    }

    public ShrineElementScore get(String elementId) {
        return byElement.get(elementId);
    }
}
