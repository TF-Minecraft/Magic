package net.tfminecraft.magic.blood;

import java.util.Map;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import net.tfminecraft.magic.integration.RpCharactersBridge;

/**
 * Stat boons earned by final sacrifices, added through MythicLib while the mage's character is
 * played, and the devotee's mark (ChoiceConfig.devoteeTrait): a character bearing it has no mana.
 * Without MythicLib nothing is applied. MythicLib types stay in {@link MythicBoons}.
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
        Map<String, Double> boons = record != null
                ? HuntConfig.boonsUpTo(record.getPath(), record.getFinalsDone()) : Map.of();
        if (!boons.isEmpty()) {
            MythicBoons.add(player, boons);
        }
        String mark = ChoiceConfig.devoteeTrait();
        if (!mark.isBlank() && RpCharactersBridge.hasTrait(player, mark)) {
            MythicBoons.noMana(player);
        }
    }

    /** Removes the player's boons, e.g. when they leave or switch character. */
    public static void clear(Player player) {
        if (available()) {
            MythicBoons.clear(player);
        }
    }
}
