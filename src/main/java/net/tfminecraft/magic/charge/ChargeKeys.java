package net.tfminecraft.magic.charge;

import org.bukkit.NamespacedKey;

import net.tfminecraft.magic.Magic;

public final class ChargeKeys {

    private ChargeKeys() {}

    /**
     * Tier stamped on a charge when it first imprints.
     *
     * <p>The tier is also recoverable from the item path, but stamping it means a charge
     * keeps its cap even if the item path changes under it. Charges never receive
     * {@code artifact_id}, which is what keeps them out of the artifact-only systems.
     */
    public static NamespacedKey chargeTier() {
        return new NamespacedKey(Magic.plugin, "charge_tier");
    }
}
