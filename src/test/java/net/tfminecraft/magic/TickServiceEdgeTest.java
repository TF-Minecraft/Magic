package net.tfminecraft.magic;

import static org.junit.jupiter.api.Assertions.*;

import net.tfminecraft.magic.manager.MagicTickService;
import org.junit.jupiter.api.*;

class TickServiceEdgeTest {
  TickAndMessagesTest f = new TickAndMessagesTest();

  @BeforeEach
  void setup() {
    f.setup();
  }

  @AfterEach
  void cleanup() {
    f.cleanup();
  }

  @Test
  void externallyCancelledPluginTasksAreReportedStopped() {
    MagicTickService.start();
    assertTrue(MagicTickService.isRunning());
    f.server.getScheduler().cancelTasks(Magic.plugin);
    assertFalse(MagicTickService.isRunning());
    MagicTickService.stop();
    assertFalse(MagicTickService.isRunning());
  }
}
