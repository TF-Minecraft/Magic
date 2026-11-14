package net.tfminecraft.magic.blood;

import java.util.Map;

import org.bukkit.entity.Player;

import io.lumine.mythic.lib.api.player.MMOPlayerData;
import io.lumine.mythic.lib.api.stat.StatInstance;
import io.lumine.mythic.lib.api.stat.modifier.StatModifier;
import io.lumine.mythic.lib.player.modifier.ModifierType;

/** MythicLib side of {@link BloodBoons}; only loaded once MythicLib is known to be enabled. */
final class MythicBoons {

    static final String KEY = "magic_blood_boon";

    private MythicBoons() {}

    static void add(Player player, Map<String, Double> boons) {
        MMOPlayerData data = MMOPlayerData.getOrNull(player);
        if (data == null) {
            return;
        }
        boons.forEach((stat, value) -> new StatModifier(KEY, stat, value).register(data));
    }

    /** Takes away all max mana (-100%), under the same key as the boons. */
    static void noMana(Player player) {
        MMOPlayerData data = MMOPlayerData.getOrNull(player);
        if (data != null) {
            new StatModifier(KEY, "MAX_MANA", -100, ModifierType.RELATIVE).register(data);
        }
    }

    /** Removes every blood boon by key, so nothing survives a reload or a forgotten stat. */
    static void clear(Player player) {
        MMOPlayerData data = MMOPlayerData.getOrNull(player);
        if (data == null) {
            return;
        }
        for (StatInstance instance : data.getStatMap().getInstances()) {
            instance.remove(KEY);
        }
    }
}
