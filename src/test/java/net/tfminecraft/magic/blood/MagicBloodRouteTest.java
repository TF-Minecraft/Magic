package net.tfminecraft.magic.blood;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Path;

import net.tfminecraft.magic.command.MagicCommand;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockbukkit.mockbukkit.command.ConsoleCommandSenderMock;

class MagicBloodRouteTest {
  @TempDir Path temp;

  @Test
  void magicCommandRoutesBloodToStaffTools() {
    try (BloodFixture fx = new BloodFixture(temp)) {
      ConsoleCommandSenderMock console = (ConsoleCommandSenderMock) fx.server.getConsoleSender();
      MagicCommand command = new MagicCommand();
      assertTrue(command.onCommand(console, null, "magic", new String[] {"blood", "list"}));
      assertEquals("blood.admin.list_header", console.nextMessage());
      assertEquals(java.util.List.of("list"), command.onTabComplete(console, null, "magic", new String[] {"blood", "li"}));
    }
  }
}
