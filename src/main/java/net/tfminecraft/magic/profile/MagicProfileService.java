package net.tfminecraft.magic.profile;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import net.tfminecraft.magic.Cache;
import net.tfminecraft.magic.Magic;
import net.tfminecraft.magic.session.ResonanceSession;
import net.tfminecraft.magic.session.ResonanceSessionManager;
import net.tfminecraft.tlibs.character.CharacterSession;
import net.tfminecraft.tlibs.character.Characters;

public final class MagicProfileService {

    private final MagicProfileStore store;
    private final ResonanceSessionManager sessionManager;

    public MagicProfileService(Plugin plugin, MagicProfileStore store, ResonanceSessionManager sessionManager) {
        this.store = store;
        this.sessionManager = sessionManager;
        sessionManager.start(plugin, this::save);
    }

    public String characterId(Player player) {
        return sessionManager.getLoadedCharacterId(player);
    }

    public Player findOnlineWithCharacter(String characterId) {
        if (characterId == null || characterId.isBlank()) {
            return null;
        }
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (characterId.equalsIgnoreCase(characterId(player))) {
                return player;
            }
        }
        return null;
    }

    public void activate(Player player, CharacterSession identity) {
        if (player == null || identity == null || !player.getUniqueId().equals(identity.ref().ownerId())
                || !Characters.isCurrent(identity) || sessionManager.getSession(identity) != null) {
            return;
        }
        String characterId = identity.ref().characterId().toString();
        MagicProfile profile = store.load(characterId);
        if (profile == null) {
            profile = MagicProfile.fromDefaults(characterId, identity.ref().ownerId());
            store.save(profile);
            if (Cache.debug) {
                Magic.plugin.getLogger().info("[Magic] Created profile for character " + characterId);
            }
        }
        ResonanceSession session = new ResonanceSession();
        profile.applyTo(session);
        sessionManager.put(identity, session);
    }

    public void loadOnlineCharacters() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            Characters.currentSession(player).ifPresent(session -> activate(player, session));
        }
    }

    public void savePlayer(Player player) {
        if (player == null) {
            return;
        }
        Characters.currentSession(player).ifPresent(identity -> {
            ResonanceSession session = sessionManager.getSession(identity);
            if (session != null) {
                save(identity, session);
            }
        });
    }

    public boolean savePlayerChecked(Player player) {
        return Characters.currentSession(player).map(identity -> {
            ResonanceSession session = sessionManager.getSession(identity);
            return session != null && store.saveChecked(MagicProfile.fromSession(
                    identity.ref().characterId().toString(), identity.ref().ownerId(), session));
        }).orElse(false);
    }

    private void save(CharacterSession identity, ResonanceSession session) {
        // End events invalidate the token before delivery. Flush the captured value directly.
        store.save(MagicProfile.fromSession(identity.ref().characterId().toString(),
                identity.ref().ownerId(), session));
    }

    public void close() {
        sessionManager.close();
    }
}
