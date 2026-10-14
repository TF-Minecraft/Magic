package net.tfminecraft.magic;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.tfminecraft.tlibs.character.*;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.ServicePriority;
import org.mockbukkit.mockbukkit.MockBukkit;

/** Explicit alive character fixtures for property tests; higher-priority lifecycle fixtures override. */
final class GearCharacterFixture implements CharacterIdentity {
    final Map<UUID, CharacterRef> refs = new HashMap<>();
    final Map<UUID, CharacterSession> sessions = new HashMap<>();
    GearCharacterFixture() { Bukkit.getServicesManager().register(CharacterIdentity.class, this, MockBukkit.createMockPlugin("GearCharacters"), ServicePriority.Lowest); }
    CharacterRef ref(UUID account) { return refs.computeIfAbsent(account, id -> new CharacterRef(id, UUID.nameUUIDFromBytes((id + ":gear-test-character").getBytes(java.nio.charset.StandardCharsets.UTF_8)))); }
    public Optional<CharacterSession> currentSession(Player player) { return player == null || !player.isOnline() ? Optional.empty() : Optional.of(sessions.computeIfAbsent(player.getUniqueId(), id -> new CharacterSession(ref(id), 1))); }
    public boolean isCurrent(CharacterSession session) { return currentSession(Bukkit.getPlayer(session.ref().ownerId())).filter(session::equals).isPresent(); }
    public Optional<CharacterInfo> info(UUID id) { return refs.values().stream().filter(ref -> id.equals(ref.characterId())).findFirst().map(ref -> new CharacterInfo(ref, "Gear", CharacterStatus.ALIVE, null, Set.of(), 0, Instant.EPOCH)); }
    public Optional<List<CharacterInfo>> characters(UUID account) { return Optional.of(info(ref(account).characterId()).stream().toList()); }
    public Optional<CharacterInfo> lastPlayed(UUID account) { return info(ref(account).characterId()); }
    public boolean legacyHeirsReady() { return true; }
    public Optional<CharacterRef> legacyHeir(UUID account) { return Optional.of(ref(account)); }
    public String displayName(Player player) { return player.getName(); }
}
