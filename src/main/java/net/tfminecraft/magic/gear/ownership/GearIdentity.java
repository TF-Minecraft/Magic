package net.tfminecraft.magic.gear.ownership;

import java.util.Optional;
import java.util.UUID;
import org.bukkit.entity.Player;

/** Optional character APIs stay outside queue files and deferred tokens. */
public interface GearIdentity {
    record Owner(UUID account, UUID character) {}
    record Token(Owner owner, long session) {}
    Optional<Token> current(Player player);
    boolean current(Token token, Player player);
    boolean heirsReady();
    Optional<Owner> heir(UUID account);

    static GearIdentity load() {
        try {
            Class<?> api = Class.forName("net.tfminecraft.tlibs.character.Characters");
            api.getMethod("legacyHeir", UUID.class);
            api.getMethod("legacyHeirsReady");
            return (GearIdentity) Class.forName("net.tfminecraft.magic.gear.ownership.GearCharacterBridge")
                    .getConstructor().newInstance();
        } catch (ReflectiveOperationException | LinkageError unavailable) {
            return new GearIdentity() {
                public Optional<Token> current(Player player) { return Optional.empty(); }
                public boolean current(Token token, Player player) { return false; }
                public boolean heirsReady() { return false; }
                public Optional<Owner> heir(UUID account) { return Optional.empty(); }
            };
        }
    }
}
