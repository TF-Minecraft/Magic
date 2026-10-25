package net.tfminecraft.magic.blood;

import java.util.Map;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

/**
 * Stat boons earned by final sacrifices, added through MythicLib while the mage's character is
 * played. Without MythicLib nothing is applied. MythicLib types stay in {@link MythicBoons}.
 */
public final class BloodBoons {

    private BloodBoons() {}

    static boolean available() {
        return Bukkit.getPluginManager().isPluginEnabled("MythicLib");
    }

    /** Re-applies the boons of the player's current character, replacing any earlier ones. */
    public static void apply(Player player) {
        if (!available()) {
            return;
        }
        MythicBoons.clear(player);
        BloodRecord record = BloodRecords.get(AltarService.currentCharacter(player));
        if (record == null) {
            return;
        }
        Map<String, Double> boons = HuntConfig.boonsUpTo(record.getPath(), record.getFinalsDone());
        if (!boons.isEmpty()) {
            MythicBoons.add(player, boons);
        }
    }

    /** Removes the player's boons, e.g. when they leave or switch character. */
    public static void clear(Player player) {
        if (available()) {
            MythicBoons.clear(player);
        }
    }
}
