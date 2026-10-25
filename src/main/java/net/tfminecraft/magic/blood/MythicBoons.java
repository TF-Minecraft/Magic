package net.tfminecraft.magic.blood;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.bukkit.entity.Player;

import io.lumine.mythic.lib.api.player.MMOPlayerData;
import io.lumine.mythic.lib.api.stat.modifier.StatModifier;

/** MythicLib side of {@link BloodBoons}; only loaded once MythicLib is known to be enabled. */
final class MythicBoons {

    static final String KEY = "magic_blood_boon";
    private static final Map<UUID, Set<String>> APPLIED = new HashMap<>();

    private MythicBoons() {}

    static void add(Player player, Map<String, Double> boons) {
        MMOPlayerData data = MMOPlayerData.getOrNull(player);
        if (data == null) {
            return;
        }
        Set<String> stats = APPLIED.computeIfAbsent(player.getUniqueId(), id -> new HashSet<>());
        boons.forEach((stat, value) -> {
            new StatModifier(KEY, stat, value).register(data);
            stats.add(stat);
        });
    }

    static void clear(Player player) {
        Set<String> stats = APPLIED.remove(player.getUniqueId());
        MMOPlayerData data = MMOPlayerData.getOrNull(player);
        if (stats == null || data == null) {
            return;
        }
        for (String stat : stats) {
            data.getStatMap().getInstance(stat).remove(KEY);
        }
    }
}
