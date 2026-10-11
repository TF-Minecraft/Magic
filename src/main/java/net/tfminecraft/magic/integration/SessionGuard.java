package net.tfminecraft.magic.integration;

import org.bukkit.entity.Player;
import net.tfminecraft.magic.Magic;

import net.tfminecraft.tlibs.character.CharacterSession;
import net.tfminecraft.tlibs.character.Characters;

/** A standalone action remains valid only while there is no character provider. */
public final class SessionGuard {
    private SessionGuard() {}

    public static void runLater(Player player, Runnable action) {
        if (Magic.plugin == null) {
            return;
        }
        CharacterSession session = Characters.currentSession(player).orElse(null);
        if (!isCurrent(session)) {
            return;
        }
        Magic.plugin.getServer().getScheduler().runTask(Magic.plugin, () -> {
            if (player.isOnline() && isCurrent(session)) {
                action.run();
            }
        });
    }

    public static boolean isCurrent(CharacterSession session) {
        return session == null ? !Characters.available() : Characters.isCurrent(session);
    }
}
