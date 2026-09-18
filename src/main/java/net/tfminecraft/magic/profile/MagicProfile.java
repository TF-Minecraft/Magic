package net.tfminecraft.magic.profile;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import net.tfminecraft.magic.Cache;
import net.tfminecraft.magic.attunement.ArtifactClaim;
import net.tfminecraft.magic.model.ElementDef;
import net.tfminecraft.magic.registry.ElementRegistry;
import net.tfminecraft.magic.session.ResonanceSession;
import net.tfminecraft.magic.util.MagicNumbers;

public final class MagicProfile {

    private static final double EPSILON = 0.0001;

    private String characterId;
    private String ownerUuid;
    private String castMode;
    private double equilibrium;
    private Map<String, Double> resonance = new HashMap<>();
    /** Load-only. Folded into {@link #resonance} then cleared so Gson omits it on save. */
    private Map<String, ArtifactClaim> claims;

    public MagicProfile() {}

    public static MagicProfile fromDefaults(String characterId, UUID ownerUuid) {
        MagicProfile profile = new MagicProfile();
        profile.characterId = characterId;
        profile.ownerUuid = ownerUuid != null ? ownerUuid.toString() : null;
        profile.castMode = Cache.defaultCastMode;
        profile.equilibrium = Cache.defaultEquilibrium;
        profile.resonance = new HashMap<>();
        profile.claims = null;
        return profile;
    }

    public static MagicProfile fromSession(String characterId, UUID ownerUuid, ResonanceSession session) {
        MagicProfile profile = new MagicProfile();
        profile.characterId = characterId;
        profile.ownerUuid = ownerUuid != null ? ownerUuid.toString() : null;
        profile.castMode = session.getCastModeId();
        profile.equilibrium = session.getEquilibrium();
        profile.resonance = session.copyResonance();
        profile.claims = null;
        profile.pruneEmptyResonance();
        return profile;
    }

    public void migrateClaims() {
        if (resonance == null) {
            resonance = new HashMap<>();
        }
        if (claims != null) {
            for (ArtifactClaim claim : claims.values()) {
                if (claim == null) {
                    continue;
                }
                for (Map.Entry<String, Double> entry : claim.getByElement().entrySet()) {
                    if (entry.getKey() == null || entry.getValue() == null) {
                        continue;
                    }
                    String id = entry.getKey().trim().toLowerCase(Locale.ROOT);
                    if (id.isEmpty()) {
                        continue;
                    }
                    resonance.merge(id, entry.getValue(), Double::sum);
                }
            }
            claims = null;
        }
        pruneEmptyResonance();
    }

    public void pruneEmptyResonance() {
        if (resonance == null) {
            resonance = new HashMap<>();
            return;
        }
        Iterator<Map.Entry<String, Double>> iterator = resonance.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<String, Double> entry = iterator.next();
            if (entry.getKey() == null || entry.getValue() == null || entry.getValue() <= EPSILON) {
                iterator.remove();
                continue;
            }
            ElementDef element = ElementRegistry.getById(entry.getKey());
            if (element != null) {
                double clamped = MagicNumbers.round(
                        MagicNumbers.clamp(entry.getValue(), 0.0, element.getMaxResonance()), 2);
                if (clamped <= EPSILON) {
                    iterator.remove();
                } else {
                    entry.setValue(clamped);
                }
            }
        }
    }

    public void applyTo(ResonanceSession session) {
        migrateClaims();
        session.setCastModeId(castMode);
        session.setEquilibrium(equilibrium);
        session.setResonanceMap(copyResonance());
    }

    public Map<String, Double> copyResonance() {
        Map<String, Double> copy = new HashMap<>();
        if (resonance == null) {
            return copy;
        }
        for (Map.Entry<String, Double> entry : resonance.entrySet()) {
            if (entry.getKey() == null || entry.getValue() == null || entry.getValue() <= EPSILON) {
                continue;
            }
            copy.put(entry.getKey(), entry.getValue());
        }
        return copy;
    }

    public String getCharacterId() {
        return characterId;
    }

    public void setCharacterId(String characterId) {
        this.characterId = characterId;
    }

    public String getOwnerUuid() {
        return ownerUuid;
    }

    public void setOwnerUuid(String ownerUuid) {
        this.ownerUuid = ownerUuid;
    }

    public String getCastMode() {
        return castMode;
    }

    public void setCastMode(String castMode) {
        this.castMode = castMode;
    }

    public double getEquilibrium() {
        return equilibrium;
    }

    public void setEquilibrium(double equilibrium) {
        this.equilibrium = equilibrium;
    }

    public Map<String, Double> getResonance() {
        if (resonance == null) {
            resonance = new HashMap<>();
        }
        return resonance;
    }

    public void setResonance(Map<String, Double> resonance) {
        this.resonance = resonance != null ? resonance : new HashMap<>();
    }
}
