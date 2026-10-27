package net.tfminecraft.magic.blood;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

/**
 * The altar room's ambience (meditation.sounds.room): its owner alone hears it, looped, while inside
 * the room and not meditating there (the meditation has its own music). It stops when they leave.
 */
public final class BloodAmbience {

    /** When each listener's loop plays next (epoch millis). */
    private static final Map<UUID, Long> NEXT = new HashMap<>();

    private BloodAmbience() {}

    /** Every second: starts, loops or stops each owner's room ambience. */
    static void tick(long now) {
        MeditationConfig.Cue room = MeditationConfig.room();
        if (!room.isSet()) {
            return;
        }
        for (Player player : Bukkit.getOnlinePlayers()) {
            UUID id = player.getUniqueId();
            if (!inOwnRoom(player) || BloodMeditation.isMeditating(id)) {
                if (NEXT.remove(id) != null) {
                    room.stop(player);
                }
                continue;
            }
            Long next = NEXT.get(id);
            if (next == null || now >= next) {
                room.play(player);
                NEXT.put(id, room.seconds() > 0 ? now + room.seconds() * 1000L : Long.MAX_VALUE);
            }
        }
    }

    private static boolean inOwnRoom(Player player) {
        BloodAltar altar = BloodAltars.ofCharacter(AltarService.currentCharacter(player));
        return altar != null && altar == AltarRoom.lockedBy(player.getLocation().getBlock());
    }

    /** The meditation's music takes over: the room's ambience stops and starts again afterwards. */
    static void pause(Player player) {
        if (NEXT.remove(player.getUniqueId()) != null) {
            MeditationConfig.room().stop(player);
        }
    }

    static void forget(UUID player) {
        NEXT.remove(player);
    }

    static void clear() {
        NEXT.clear();
    }
}
