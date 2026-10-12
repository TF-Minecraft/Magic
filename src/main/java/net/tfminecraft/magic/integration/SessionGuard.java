package net.tfminecraft.magic.integration;

import org.bukkit.entity.Player;
import net.tfminecraft.magic.Magic;

import net.tfminecraft.tlibs.character.CharacterSession;
import net.tfminecraft.tlibs.character.Characters;

/** A standalone action remains valid only while there is no character provider. */
public final class SessionGuard {
    private SessionGuard() {}

    /**
     * Runs next tick unless the player started, ended or switched character in between.
     * A player with no character still gets the work done.
     */
    public static void runLater(Player player, Runnable action) {
        if (Magic.plugin == null) {
            return;
        }
        CharacterSession session = Characters.currentSession(player).orElse(null);
        Magic.plugin.getServer().getScheduler().runTask(Magic.plugin, () -> {
            if (player.isOnline() && unchanged(player, session)) {
                action.run();
            }
        });
    }

    /** Whether the player may start character-bound work: a current character, or no provider. */
    public static boolean ready(Player player) {
        return isCurrent(Characters.currentSession(player).orElse(null));
    }

    public static boolean isCurrent(CharacterSession session) {
        return session == null ? !Characters.available() : Characters.isCurrent(session);
    }

    private static boolean unchanged(Player player, CharacterSession session) {
        return session == null ? Characters.currentSession(player).isEmpty() : Characters.isCurrent(session);
    }
}
