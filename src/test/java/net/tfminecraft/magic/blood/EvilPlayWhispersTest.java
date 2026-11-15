package net.tfminecraft.magic.blood;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

class EvilPlayWhispersTest {
  @TempDir Path temp;

  /** Stands in for RPCharacters' EvilRpPlayEvent (Magic builds against an RPCharacters without it). */
  public static class FakePlay extends Event {
    private static final HandlerList HANDLERS = new HandlerList();
    private final Player player;
    private final boolean started;
    private final boolean broken;

    FakePlay(Player player, boolean started, boolean broken) {
      this.player = player;
      this.started = started;
      this.broken = broken;
    }

    public Player getPlayer() {
      if (broken) {
        throw new IllegalStateException("broken");
      }
      return player;
    }

    public boolean isSessionStarted() {
      return started;
    }

    @Override
    public HandlerList getHandlers() {
      return HANDLERS;
    }

    public static HandlerList getHandlerList() {
      return HANDLERS;
    }
  }

  @Test
  void anEvilPlayThatStartsASessionFeedsTheCurrentCharactersMeterOnce() {
    try (BloodFixture fx = new BloodFixture(temp)) {
      PlayerMock thief = fx.player("thief");
      String id = BloodFixture.id("thief");
      EvilPlayWhispers.onPlay(thief, false);
      assertNull(BloodRecords.get(id), "A play that only resets a running session doesn't count");
      EvilPlayWhispers.onPlay(null, true);
      EvilPlayWhispers.onPlay(thief, true);
      assertEquals(WhisperConfig.robbery(), BloodRecords.get(id).getWhisper());
      assertEquals(4, WhisperConfig.robbery());
      PlayerMock nobody = fx.server.addPlayer("NoCharacter");
      EvilPlayWhispers.onPlay(nobody, true);
    }
  }

  @Test
  void registersByNameOnlyWhenRpCharactersHasTheEvent() {
    try (BloodFixture fx = new BloodFixture(temp)) {
      var plugin = MockBukkit.createMockPlugin();
      assertFalse(EvilPlayWhispers.register(plugin), "The pinned RPCharacters has no evil RP play event");
      assertFalse(EvilPlayWhispers.register(plugin, "java.lang.String", fx.logger), "Not an event");
      assertTrue(EvilPlayWhispers.register(plugin, FakePlay.class.getName(), fx.logger));

      PlayerMock robber = fx.player("robber");
      String id = BloodFixture.id("robber");
      Bukkit.getPluginManager().callEvent(new FakePlay(robber, true, false));
      assertEquals(4, BloodRecords.get(id).getWhisper());
      Bukkit.getPluginManager().callEvent(new FakePlay(robber, false, false));
      Bukkit.getPluginManager().callEvent(new FakePlay(robber, true, true));
      assertEquals(4, BloodRecords.get(id).getWhisper(), "Resets and unreadable plays add nothing");

      // The listener ignores any other event handed to it.
      Event other = new Event() {
        @Override
        public HandlerList getHandlers() {
          return FakePlay.getHandlerList();
        }
      };
      for (var registered : FakePlay.getHandlerList().getRegisteredListeners()) {
        if (registered.getListener() instanceof EvilPlayWhispers) {
          assertDoesNotThrow(() -> registered.callEvent(other));
        }
      }
      assertEquals(4, BloodRecords.get(id).getWhisper(), "Other events add nothing");
    }
  }
}
