package net.tfminecraft.magic.profile;

import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import net.tfminecraft.rpcharacters.objects.RPCharacter;
import net.tfminecraft.magic.Cache;
import net.tfminecraft.magic.Magic;
import net.tfminecraft.magic.integration.RpCharactersBridge;
import net.tfminecraft.magic.session.ResonanceSession;
import net.tfminecraft.magic.session.ResonanceSessionManager;

public final class MagicProfileService {

    private final MagicProfileStore store;
    private final ResonanceSessionManager sessionManager;

    public MagicProfileService(MagicProfileStore store, ResonanceSessionManager sessionManager) {
        this.store = store;
        this.sessionManager = sessionManager;
    }

    public String characterId(Player player) {
        return sessionManager.getLoadedCharacterId(player);
    }

    public Player findOnlineWithCharacter(String characterId) {
        if (characterId == null || characterId.isBlank()) {
            return null;
        }
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (characterId.equals(sessionManager.getLoadedCharacterId(player))) {
                return player;
            }
            RPCharacter active = RpCharactersBridge.getActiveCharacter(player);
            if (active != null && characterId.equals(active.getId())) {
                return player;
            }
        }
        return null;
    }

    public void activate(Player player, RPCharacter character) {
        if (player == null || character == null || character.getId() == null || character.getId().isBlank()) {
            return;
        }
        String characterId = character.getId();
        String loaded = sessionManager.getLoadedCharacterId(player);
        ResonanceSession live = sessionManager.get(player);
        if (characterId.equals(loaded) && live != null) {
            return;
        }
        MagicProfile profile = store.load(characterId);
        if (profile == null) {
            profile = MagicProfile.fromDefaults(characterId, player.getUniqueId());
            store.save(profile);
            if (Cache.debug) {
                Magic.plugin.getLogger().info("[Magic] Created profile for character " + characterId);
            }
        } else {
            profile.setOwnerUuid(player.getUniqueId().toString());
        }
        ResonanceSession session = sessionManager.getOrCreate(player);
        profile.applyTo(session);
        sessionManager.setLoadedCharacterId(player, characterId);
    }

    public void savePrevious(Player player, RPCharacter previous) {
        if (player == null || previous == null || previous.getId() == null || previous.getId().isBlank()) {
            return;
        }
        ResonanceSession session = sessionManager.get(player);
        if (session == null) {
            return;
        }
        store.save(MagicProfile.fromSession(previous.getId(), player.getUniqueId(), session));
    }

    public void deactivate(Player player) {
        if (player == null) {
            return;
        }
        saveLoaded(player);
        sessionManager.remove(player.getUniqueId());
    }

    public void saveAllOnline() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            saveLoaded(player);
        }
    }

    public void loadOnlineCharacters() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            RPCharacter character = RpCharactersBridge.getActiveCharacter(player);
            if (character != null) {
                activate(player, character);
            }
        }
    }

    private void saveLoaded(Player player) {
        String characterId = sessionManager.getLoadedCharacterId(player);
        if (characterId == null || characterId.isBlank()) {
            RPCharacter active = RpCharactersBridge.getActiveCharacter(player);
            characterId = active != null ? active.getId() : null;
        }
        if (characterId == null || characterId.isBlank()) {
            return;
        }
        ResonanceSession session = sessionManager.get(player);
        if (session == null) {
            return;
        }
        UUID owner = player.getUniqueId();
        store.save(MagicProfile.fromSession(characterId, owner, session));
    }

    public void savePlayer(Player player) {
        saveLoaded(player);
    }
}
