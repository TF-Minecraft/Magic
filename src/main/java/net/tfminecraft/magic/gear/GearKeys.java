package net.tfminecraft.magic.gear;

import org.bukkit.NamespacedKey;

import net.tfminecraft.magic.Magic;

public final class GearKeys {

    private GearKeys() {}

    public static NamespacedKey weaponReqCap() {
        return new NamespacedKey(Magic.plugin, "weapon_req_cap");
    }

    public static NamespacedKey weaponReqFill() {
        return new NamespacedKey(Magic.plugin, "weapon_req_fill");
    }

    public static NamespacedKey weaponReq() {
        return new NamespacedKey(Magic.plugin, "weapon_req");
    }

    public static NamespacedKey parts() {
        return new NamespacedKey(Magic.plugin, "gear_parts");
    }

    /** Materials actually charged when the weapon was crafted, as a JSON map of item path to amount. */
    public static NamespacedKey craftInputs() {
        return new NamespacedKey(Magic.plugin, "gear_craft_inputs");
    }

    public static NamespacedKey socketsLocked() {
        return new NamespacedKey(Magic.plugin, "gear_sockets_locked");
    }

    public static NamespacedKey archetype() {
        return new NamespacedKey(Magic.plugin, "gear_archetype");
    }

    public static NamespacedKey archetypeRevision() {
        return new NamespacedKey(Magic.plugin, "gear_archetype_revision");
    }

    public static NamespacedKey majorityTier() {
        return new NamespacedKey(Magic.plugin, "majority_tier");
    }

    public static NamespacedKey broken() {
        return new NamespacedKey(Magic.plugin, "gear_broken");
    }

    public static NamespacedKey orphans() {
        return new NamespacedKey(Magic.plugin, "gear_orphans");
    }

    public static NamespacedKey rift() {
        return new NamespacedKey(Magic.plugin, "gear_rift");
    }

    public static NamespacedKey partPick() {
        return new NamespacedKey(Magic.plugin, "gear_part_id");
    }

    /** Template revision of each socketed rune, by its MMOItems gem history id. */
    public static NamespacedKey runeRevisions() {
        return new NamespacedKey(Magic.plugin, "gear_rune_revisions");
    }

    /** Marks the ItemDisplay that shows the weapon resting on a gear station. */
    public static NamespacedKey stationDisplay() {
        return new NamespacedKey(Magic.plugin, "gear_station_display");
    }
}
