package net.tfminecraft.magic.artifact;

import org.bukkit.NamespacedKey;

import net.tfminecraft.magic.Magic;

public final class ArtifactKeys {

    private ArtifactKeys() {}

    public static NamespacedKey auraCap() {
        return new NamespacedKey(Magic.plugin, "aura_cap");
    }

    public static NamespacedKey auraFill() {
        return new NamespacedKey(Magic.plugin, "aura_fill");
    }

    public static NamespacedKey artifactRarity() {
        return new NamespacedKey(Magic.plugin, "artifact_rarity");
    }

    public static NamespacedKey artifactPrimary() {
        return new NamespacedKey(Magic.plugin, "artifact_primary");
    }

    public static NamespacedKey artifactId() {
        return new NamespacedKey(Magic.plugin, "artifact_id");
    }

    /** Resonance this artifact has granted in meditation, summed over every user. */
    public static NamespacedKey artifactWear() {
        return new NamespacedKey(Magic.plugin, "artifact_wear");
    }

    /** Set on a spent husk. Holds the id the artifact had before it crumbled. */
    public static NamespacedKey artifactSpent() {
        return new NamespacedKey(Magic.plugin, "artifact_spent");
    }

    public static NamespacedKey attuneStart() {
        return new NamespacedKey(Magic.plugin, "attune_start");
    }

    public static NamespacedKey attuneCount() {
        return new NamespacedKey(Magic.plugin, "attune_count");
    }

    /** Root string: characterId:expiryMs;... Survives nested TAG_CONTAINER drops. */
    public static NamespacedKey careUsers() {
        return new NamespacedKey(Magic.plugin, "care_users");
    }

    public static NamespacedKey careMuffle() {
        return new NamespacedKey(Magic.plugin, "care_muffle");
    }

    public static NamespacedKey careLastTick() {
        return new NamespacedKey(Magic.plugin, "care_last_tick");
    }

    /** Root string backup: id:cap:fill,id:cap:fill. Survives nested TAG_CONTAINER drops. */
    public static NamespacedKey auraData() {
        return new NamespacedKey(Magic.plugin, "aura_data");
    }

    /** Root string: id|name|stage;id|name|stage. Survives nested TAG_CONTAINER drops. */
    public static NamespacedKey sacrificeImprints() {
        return new NamespacedKey(Magic.plugin, "sacrifice_imprints");
    }

    public static NamespacedKey element(String elementId) {
        return new NamespacedKey(Magic.plugin, elementId);
    }
}
