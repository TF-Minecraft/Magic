package net.tfminecraft.magic.session;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.BiConsumer;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import net.tfminecraft.tlibs.character.CharacterSession;
import net.tfminecraft.tlibs.character.CharacterSessionMap;
import net.tfminecraft.tlibs.character.Characters;

public final class ResonanceSessionManager {

    // Standalone gameplay has no character ownership and is never persisted.
    private final Map<UUID, ResonanceSession> standalone = new HashMap<>();
    private CharacterSessionMap<ResonanceSession> sessions;

    public void start(Plugin plugin, BiConsumer<CharacterSession, ResonanceSession> onEnd) {
        if (sessions != null) {
            throw new IllegalStateException("Resonance sessions already started");
        }
        sessions = new CharacterSessionMap<>(plugin, onEnd);
    }

    /** Returns null while a character provider is present but no profile is ready. */
    public ResonanceSession getOrCreate(Player player) {
        if (Characters.available()) {
            return get(player);
        }
        return standalone.computeIfAbsent(player.getUniqueId(), id -> new ResonanceSession());
    }

    public ResonanceSession get(Player player) {
        if (player == null) {
            return null;
        }
        if (Characters.available()) {
            return Characters.currentSession(player).map(this::getSession).orElse(null);
        }
        return standalone.get(player.getUniqueId());
    }

    public ResonanceSession getSession(CharacterSession session) {
        return sessions == null ? null : sessions.get(session).orElse(null);
    }

    public void put(CharacterSession session, ResonanceSession value) {
        standalone.remove(session.ref().ownerId());
        sessions.put(session, value);
    }

    public void forEachOnlineSession(BiConsumer<Player, ResonanceSession> consumer) {
        for (Player player : Bukkit.getOnlinePlayers()) {
            ResonanceSession session = get(player);
            if (session != null) {
                consumer.accept(player, session);
            }
        }
    }

    public String getLoadedCharacterId(Player player) {
        if (player == null) {
            return null;
        }
        return Characters.currentSession(player)
                .filter(session -> getSession(session) != null)
                .map(session -> session.ref().characterId().toString()).orElse(null);
    }

    public void removeStandalone(UUID playerId) {
        standalone.remove(playerId);
    }

    public void close() {
        if (sessions != null) {
            sessions.close();
        }
        standalone.clear();
    }
}
