package net.tfminecraft.magic;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import net.tfminecraft.tlibs.character.CharacterIdentity;
import net.tfminecraft.tlibs.character.CharacterInfo;
import net.tfminecraft.tlibs.character.CharacterRef;
import net.tfminecraft.tlibs.character.CharacterSession;
import net.tfminecraft.tlibs.character.CharacterSessionEndEvent;
import net.tfminecraft.tlibs.character.CharacterSessionEndEvent.Reason;
import net.tfminecraft.tlibs.character.CharacterSessionStartEvent;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.ServicePriority;
import org.mockbukkit.mockbukkit.MockBukkit;

/** Real facade/provider wiring with controllable character lifecycle events. */
public final class CharacterTestSupport {
  private static final AtomicLong NEXT_SESSION = new AtomicLong();
  private final Map<UUID, CharacterSession> current = new HashMap<>();
  public final Plugin plugin = MockBukkit.createMockPlugin("CharacterTestProvider");
  private final CharacterIdentity identity = new CharacterIdentity() {
    @Override
    public Optional<CharacterSession> currentSession(Player player) {
      return player != null && player.isOnline()
          ? Optional.ofNullable(current.get(player.getUniqueId())) : Optional.empty();
    }

    @Override
    public boolean isCurrent(CharacterSession session) {
      Player player = Bukkit.getPlayer(session.ref().ownerId());
      return player != null && player.isOnline() && session.equals(current.get(session.ref().ownerId()));
    }

    @Override
    public Optional<CharacterInfo> info(UUID characterId) { return Optional.empty(); }

    @Override
    public Optional<List<CharacterInfo>> characters(UUID ownerId) { return Optional.empty(); }

    @Override
    public Optional<CharacterInfo> lastPlayed(UUID ownerId) { return Optional.empty(); }

    @Override
    public String displayName(Player player) { return player.getName(); }
  };

  public CharacterTestSupport() {
    Bukkit.getServicesManager().register(CharacterIdentity.class, identity, plugin, ServicePriority.Normal);
  }

  public static String id(String label) {
    return UUID.nameUUIDFromBytes(label.getBytes(StandardCharsets.UTF_8)).toString();
  }

  public CharacterSession start(Player player, String label) {
    end(player, Reason.SWITCH);
    CharacterSession session = new CharacterSession(
        new CharacterRef(player.getUniqueId(), UUID.fromString(id(label))), NEXT_SESSION.incrementAndGet());
    current.put(player.getUniqueId(), session);
    Bukkit.getPluginManager().callEvent(new CharacterSessionStartEvent(player, session));
    return session;
  }

  public void end(Player player, Reason reason) {
    CharacterSession session = current.remove(player.getUniqueId());
    if (session != null) {
      Bukkit.getPluginManager().callEvent(new CharacterSessionEndEvent(player, session, reason));
    }
  }

  public void unregister() {
    Bukkit.getServicesManager().unregister(CharacterIdentity.class, identity);
  }
}
