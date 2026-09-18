package net.tfminecraft.magic.session;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

public final class ResonanceSessionManager {

    private final Map<UUID, ResonanceSession> sessions = new ConcurrentHashMap<>();
    private final Map<UUID, String> loadedCharacterIds = new ConcurrentHashMap<>();

    public ResonanceSession getOrCreate(Player player) {
        return sessions.computeIfAbsent(player.getUniqueId(), id -> new ResonanceSession());
    }

    public ResonanceSession get(Player player) {
        if (player == null) {
            return null;
        }
        return sessions.get(player.getUniqueId());
    }

    public void forEachOnlineSession(BiConsumer<Player, ResonanceSession> consumer) {
        for (Map.Entry<UUID, ResonanceSession> entry : sessions.entrySet()) {
            Player player = Bukkit.getPlayer(entry.getKey());
            if (player != null && player.isOnline()) {
                consumer.accept(player, entry.getValue());
            }
        }
    }

    public String getLoadedCharacterId(Player player) {
        if (player == null) {
            return null;
        }
        return loadedCharacterIds.get(player.getUniqueId());
    }

    public void setLoadedCharacterId(Player player, String characterId) {
        if (player == null) {
            return;
        }
        if (characterId == null || characterId.isBlank()) {
            loadedCharacterIds.remove(player.getUniqueId());
            return;
        }
        loadedCharacterIds.put(player.getUniqueId(), characterId);
    }

    public void remove(UUID playerUuid) {
        if (playerUuid != null) {
            sessions.remove(playerUuid);
            loadedCharacterIds.remove(playerUuid);
        }
    }
}
