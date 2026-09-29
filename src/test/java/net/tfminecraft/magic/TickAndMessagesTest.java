package net.tfminecraft.magic;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.logging.Logger;
import net.tfminecraft.magic.command.ResonanceCommand;
import net.tfminecraft.magic.manager.*;
import net.tfminecraft.magic.tick.*;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.mockbukkit.mockbukkit.*;

class TickAndMessagesTest {
  @TempDir Path temp;
  ServerMock server;

  @BeforeEach
  void setup() {
    server = MockBukkit.mock();
    Magic.plugin = mock(Magic.class);
    when(Magic.plugin.getLogger()).thenReturn(Logger.getAnonymousLogger());
    when(Magic.plugin.getServer()).thenReturn(server);
    when(Magic.plugin.isEnabled()).thenReturn(true);
    when(Magic.plugin.getName()).thenReturn("Magic");
    Cache.tickIntervalTicks = 20;
    Cache.debug = false;
    MagicTickService.clearHandlers();
  }

  @AfterEach
  void cleanup() {
    MagicTickService.stop();
    MagicTickService.clearHandlers();
    Magic.plugin = null;
    MockBukkit.unmock();
    Cache.debug = false;
    Cache.tickIntervalTicks = 20;
  }

  @Test
  void contextsExposeTimeAndPeriodicBoundaries() {
    var c = MagicTickContext.of(7200);
    assertEquals(7200, c.seconds());
    assertEquals(120, c.minutes());
    assertEquals(2, c.hours());
    assertTrue(c.every(5));
    assertFalse(c.every(0));
    assertFalse(c.every(-1));
    assertFalse(c.every(7));
    assertTrue(c.everyMinutes(2));
    assertTrue(c.everyHours(2));
    assertFalse(c.everyHours(3));
  }

  @Test
  void schedulerRunsAllHandlersDespiteFailureAndStopsCleanly() {
    assertFalse(MagicTickService.isRunning());
    MagicTickService.stop();
    MagicTickService.register(null);
    List<Long> calls = new ArrayList<>();
    MagicTickHandler recorder = c -> calls.add(c.seconds());
    MagicTickService.register(
        c -> {
          throw new IllegalStateException("test failure");
        });
    MagicTickService.register(recorder);
    long before = MagicTickService.getElapsedSeconds();
    Cache.debug = true;
    MagicTickService.start();
    assertTrue(MagicTickService.isRunning());
    server.getScheduler().performTicks(1200);
    assertEquals(60, calls.size());
    assertEquals(before + 60, MagicTickService.getElapsedSeconds());
    MagicTickService.unregister(recorder);
    server.getScheduler().performTicks(20);
    assertEquals(60, calls.size());
    MagicTickService.start();
    MagicTickService.stop();
    assertFalse(MagicTickService.isRunning());
    long stopped = MagicTickService.getElapsedSeconds();
    server.getScheduler().performTicks(40);
    assertEquals(stopped, MagicTickService.getElapsedSeconds());
  }

  @Test
  void messagesLoadLiveOverrideAndBundledFallbackWithPlaceholders() throws Exception {
    String resource = "fallback: 'Bundled {name}'\nblank: ''\nlive: Bundled\n";
    when(Magic.plugin.getResource("messages.yml"))
        .thenAnswer(i -> new ByteArrayInputStream(resource.getBytes(StandardCharsets.UTF_8)));
    Path file = temp.resolve("messages.yml");
    Files.writeString(file, "live: 'Hello {name}'\nfallback: ''\n");
    Messages.load(file.toFile());
    assertEquals("Hello Alice", Messages.get("live", "name", "Alice"));
    assertEquals("Hello ", Messages.get("live", "name", null, "ignored"));
    assertEquals("Bundled Alex", Messages.get("fallback", "name", "Alex"));
    assertEquals("blank", Messages.get("blank"));
    assertEquals("missing", Messages.get("missing"));
    Messages.load(temp.resolve("missing").toFile());
    assertEquals("Bundled", Messages.get("live"));
    Messages.loadFromResources();
    assertEquals("Bundled", Messages.getRaw("live"));
    when(Magic.plugin.getResource("messages.yml")).thenReturn(null);
    Messages.loadFromResources();
    Messages.load(temp.resolve("missing").toFile());
    assertEquals("live", Messages.getRaw("live"));
    when(Magic.plugin.getResource("messages.yml"))
        .thenThrow(new IllegalStateException("resource failure"));
    Messages.loadFromResources();
    assertEquals("missing", Messages.getRaw("missing"));
  }

  @Test
  void resonanceCommandChecksPermissionAndPlayerBeforeOpening() {
    var command = new ResonanceCommand();
    CommandSender sender = mock(CommandSender.class);
    assertTrue(command.onCommand(sender, null, "resonance", new String[0]));
    verify(sender).sendMessage(Messages.get("open.no_permission"));
    when(sender.hasPermission("magic.use")).thenReturn(true);
    assertTrue(command.onCommand(sender, null, "resonance", new String[0]));
    verify(sender).sendMessage(Messages.get("open.players_only"));
    Player player = mock(Player.class);
    when(player.hasPermission("magic.use")).thenReturn(true);
    var gui = mock(ResonanceGuiManager.class);
    when(Magic.plugin.getResonanceGuiManager()).thenReturn(gui);
    assertTrue(command.onCommand(player, null, "resonance", new String[0]));
    verify(gui).tryOpen(player, false);
  }

  @Test
  void elapsedSecondsTrackActualSchedulerIntervals() {
    Cache.tickIntervalTicks = 40;
    long before = MagicTickService.getElapsedSeconds();
    List<MagicTickContext> contexts = new ArrayList<>();
    MagicTickService.register(contexts::add);
    MagicTickService.start();
    server.getScheduler().performTicks(39);
    assertEquals(before, MagicTickService.getElapsedSeconds());
    server.getScheduler().performTicks(81);
    assertEquals(before + 6, MagicTickService.getElapsedSeconds());
    assertEquals(3, contexts.size());
  }

  @Test
  void subsecondCallbacksTriggerEachSecondOnlyOnce() {
    Cache.tickIntervalTicks = 1;
    long before = MagicTickService.getElapsedSeconds();
    List<Long> boundaries = new ArrayList<>();
    MagicTickService.register(
        c -> {
          if (c.every(1)) boundaries.add(c.seconds());
        });
    MagicTickService.start();
    server.getScheduler().performTicks(60);
    assertEquals(before + 3, MagicTickService.getElapsedSeconds());
    assertEquals(List.of(before + 1, before + 2, before + 3), boundaries);
  }
}
