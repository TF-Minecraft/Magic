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

    public static NamespacedKey socketsLocked() {
        return new NamespacedKey(Magic.plugin, "gear_sockets_locked");
    }

    public static NamespacedKey archetype() {
        return new NamespacedKey(Magic.plugin, "gear_archetype");
    }

    public static NamespacedKey archetypeRevision() {
        return new NamespacedKey(Magic.plugin, "gear_archetype_revision");
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
}
