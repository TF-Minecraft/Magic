package net.tfminecraft.magic.artifact.sacrifice;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.bukkit.Sound;

public final class SacrificeFxDef {

    private final Set<String> surgeTiers;
    private final Sound surgeSound;
    private final float surgeVolume;
    private final float surgePitch;
    private final Sound boomSound;
    private final float boomVolume;
    private final float boomPitch;

    public SacrificeFxDef(
            Set<String> surgeTiers,
            Sound surgeSound,
            float surgeVolume,
            float surgePitch,
            Sound boomSound,
            float boomVolume,
            float boomPitch) {
        Set<String> tiers = new LinkedHashSet<>();
        if (surgeTiers != null) {
            for (String id : surgeTiers) {
                if (id != null && !id.isBlank()) {
                    tiers.add(id.trim().toLowerCase(Locale.ROOT));
                }
            }
        }
        this.surgeTiers = Collections.unmodifiableSet(tiers);
        this.surgeSound = surgeSound;
        this.surgeVolume = surgeVolume;
        this.surgePitch = surgePitch;
        this.boomSound = boomSound;
        this.boomVolume = boomVolume;
        this.boomPitch = boomPitch;
    }

    public static SacrificeFxDef empty() {
        return new SacrificeFxDef(Set.of(), null, 0.85f, 0.7f, null, 0.45f, 0.85f);
    }

    public boolean surges(String tierId) {
        if (tierId == null || tierId.isBlank() || surgeTiers.isEmpty()) {
            return false;
        }
        return surgeTiers.contains(tierId.trim().toLowerCase(Locale.ROOT));
    }

    public List<String> getSurgeTiers() {
        return Collections.unmodifiableList(new ArrayList<>(surgeTiers));
    }

    public Sound getSurgeSound() {
        return surgeSound;
    }

    public float getSurgeVolume() {
        return surgeVolume;
    }

    public float getSurgePitch() {
        return surgePitch;
    }

    public Sound getBoomSound() {
        return boomSound;
    }

    public float getBoomVolume() {
        return boomVolume;
    }

    public float getBoomPitch() {
        return boomPitch;
    }
}
