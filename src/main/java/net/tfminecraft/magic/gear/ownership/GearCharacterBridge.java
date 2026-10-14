package net.tfminecraft.magic.gear.ownership;

import java.util.Optional;
import java.util.UUID;
import net.tfminecraft.tlibs.character.CharacterSession;
import net.tfminecraft.tlibs.character.Characters;
import org.bukkit.entity.Player;

/** Capability-probed property APIs; profile and progression behavior is separate. */
public final class GearCharacterBridge implements GearIdentity {
    public GearCharacterBridge() {}
    public static Token token(CharacterSession session) {
        return new Token(new Owner(session.ref().ownerId(), session.ref().characterId()), session.sessionId());
    }
    public Optional<Token> current(Player player) {
        if (player == null) return Optional.empty();
        return Characters.currentSession(player)
                .filter(session -> player.getUniqueId().equals(session.ref().ownerId()))
                .filter(Characters::isCurrent)
                .filter(session -> Characters.info(session.ref().characterId())
                        .filter(info -> session.ref().equals(info.ref()) && info.alive()).isPresent())
                .map(GearCharacterBridge::token);
    }
    public boolean current(Token token, Player player) {
        return token != null && current(player).filter(token::equals).isPresent();
    }
    public boolean heirsReady() { return Characters.legacyHeirsReady(); }
    public Optional<Owner> heir(UUID account) {
        if (account == null) return Optional.empty();
        return Characters.legacyHeir(account).filter(ref -> account.equals(ref.ownerId()))
                .map(ref -> new Owner(ref.ownerId(), ref.characterId()));
    }
}
