package net.tfminecraft.magic;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.nio.file.*;
import java.util.*;
import net.tfminecraft.magic.attunement.*;
import net.tfminecraft.magic.session.*;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

class ProfileResidualTest {
  @TempDir Path temp;

  @Test
  void loggingBeforeConfigurationDoesNotCreateAFile() throws Exception {
    var field = AuraLog.class.getDeclaredField("logFile");
    field.setAccessible(true);
    Object previous = field.get(null);
    try {
      field.set(null, null);
      AuraLog.configure(true, true, null);
      AuraLog.append("before path configuration");
      assertEquals(0, Files.list(temp).count());
    } finally {
      field.set(null, previous);
      AuraLog.configure(false, false, null);
    }
  }

  @Test
  void negativeLegacyClaimsAreEmpty() {
    var claim = new ArtifactClaim();
    claim.setByElement(Map.of("fire", -1d));
    assertFalse(claim.hasAnyAmount());
    claim.setByElement(Map.of("fire", 1d));
    assertFalse(claim.isEmpty());
  }

  @Test
  void disconnectedPlayersDoNotReceiveSessionTicks() {
    var server = org.mockbukkit.mockbukkit.MockBukkit.mock();
    try {
      var sessions = new ResonanceSessionManager();
      var player = server.addPlayer();
      sessions.getOrCreate(player);
      var seen = new ArrayList<Player>();
      sessions.forEachOnlineSession((p, s) -> seen.add(p));
      assertEquals(List.of(player), seen);
      player.disconnect();
      sessions.forEachOnlineSession((p, s) -> fail("offline player must not receive callbacks"));
    } finally {
      org.mockbukkit.mockbukkit.MockBukkit.unmock();
    }
  }
}
