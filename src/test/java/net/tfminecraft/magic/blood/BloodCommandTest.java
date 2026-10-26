package net.tfminecraft.magic.blood;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Path;
import java.util.List;

import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockbukkit.mockbukkit.command.ConsoleCommandSenderMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

class BloodCommandTest {
  @TempDir Path temp;

  static String[] args(String line) {
    return line.split(" ");
  }

  void run(org.bukkit.command.CommandSender sender, String line) {
    assertTrue(BloodCommand.execute(sender, args(line)));
  }

  @Test
  void usageAndTargetErrors() {
    try (BloodFixture fx = new BloodFixture(temp)) {
      ConsoleCommandSenderMock console = (ConsoleCommandSenderMock) fx.server.getConsoleSender();
      run(console, "blood");
      assertEquals("blood.admin.usage", console.nextMessage());
      run(console, "blood nope");
      assertEquals("blood.admin.usage", console.nextMessage());
      run(console, "blood info");
      assertEquals("blood.admin.usage", console.nextMessage());
      run(console, "blood info Ghost");
      assertEquals("artifact.give.unknown_player", console.nextMessage());
      fx.server.addPlayer("Bare");
      run(console, "blood info Bare");
      assertEquals("resonance.admin.no_character", console.nextMessage());
    }
  }

  @Test
  void infoStagePathGiveAndReset() {
    try (BloodFixture fx = new BloodFixture(temp)) {
      ConsoleCommandSenderMock console = (ConsoleCommandSenderMock) fx.server.getConsoleSender();
      PlayerMock mage = fx.server.addPlayer("Mage");
      fx.characters.start(mage, "mage");
      mage.teleport(new org.bukkit.Location(fx.world, 0.5, 11, 0.5));
      run(console, "blood info Mage");
      assertEquals("blood.admin.no_record", console.nextMessage());

      run(console, "blood stage Mage bogus");
      assertEquals("blood.admin.bad_stage", console.nextMessage());
      run(console, "blood stage Mage animal");
      assertEquals("blood.admin.stage_set", console.nextMessage());
      assertEquals(BloodStage.ANIMAL, BloodRecords.get(BloodFixture.id("mage")).getStage());
      run(console, "blood path Mage bogus");
      assertEquals("blood.admin.bad_path", console.nextMessage());
      run(console, "blood path Mage true");
      assertEquals("blood.admin.path_set", console.nextMessage());
      run(console, "blood info Mage");
      assertEquals("blood.admin.info", console.nextMessage());

      run(console, "blood give Mage spoon");
      assertEquals("blood.admin.bad_item", console.nextMessage());
      run(console, "blood give Mage altar");
      assertEquals("blood.admin.given", console.nextMessage());
      assertTrue(mage.getInventory().containsAtLeast(BloodItems.create(BloodItemKind.ALTAR, BloodFixture.id("mage")), 1));
      for (int i = 0; i < mage.getInventory().getSize(); i++) {
        mage.getInventory().setItem(i, new ItemStack(Material.DIRT, 64));
      }
      int before = fx.world.getEntities().size();
      run(console, "blood give Mage ritual_knife");
      assertEquals(before + 1, fx.world.getEntities().size());
      console.nextMessage();

      Block target = fx.pocket(0, 10, 0);
      AltarService.place(mage, BloodItems.create(BloodItemKind.ALTAR, BloodFixture.id("mage")), target);
      run(console, "blood info Mage");
      assertEquals("blood.admin.info", console.nextMessage());
      run(console, "blood list");
      assertEquals("blood.admin.list_header", console.nextMessage());
      assertEquals("blood.admin.list_line", console.nextMessage());
      run(console, "blood reset Mage");
      assertEquals("blood.admin.reset", console.nextMessage());
      assertNull(BloodRecords.get(BloodFixture.id("mage")));
      assertNull(BloodAltars.at(target));
      run(console, "blood reset Mage");
      assertEquals("blood.admin.reset", console.nextMessage());
    }
  }

  @Test
  void saveFailuresAreReported() throws Exception {
    try (BloodFixture fx = new BloodFixture(temp)) {
      ConsoleCommandSenderMock console = (ConsoleCommandSenderMock) fx.server.getConsoleSender();
      PlayerMock mage = fx.server.addPlayer("Mage");
      fx.characters.start(mage, "mage");
      java.nio.file.Path blocker = temp.resolve("blood/characters/" + BloodFixture.id("mage") + ".json");
      java.nio.file.Files.createDirectories(blocker.resolve("inside"));
      run(console, "blood stage Mage animal");
      assertEquals("values.save_failed", console.nextMessage());
    }
  }

  @Test
  void altarActionsByPlayerOrId() {
    try (BloodFixture fx = new BloodFixture(temp)) {
      ConsoleCommandSenderMock console = (ConsoleCommandSenderMock) fx.server.getConsoleSender();
      PlayerMock mage = fx.server.addPlayer("Mage");
      fx.characters.start(mage, "mage");
      fx.record("mage", BloodStage.ANIMAL);
      PlayerMock staff = fx.server.addPlayer("Staff");
      run(console, "blood altar info");
      assertEquals("blood.admin.usage", console.nextMessage());
      run(console, "blood altar dance Mage");
      assertEquals("blood.admin.usage", console.nextMessage());
      run(console, "blood altar info Mage");
      assertEquals("blood.admin.no_altar", console.nextMessage());
      run(console, "blood altar info Nobody");
      assertEquals("blood.admin.no_altar", console.nextMessage());
      Block target = fx.pocket(0, 10, 0);
      AltarService.place(mage, BloodItems.create(BloodItemKind.ALTAR, BloodFixture.id("mage")), target);
      BloodAltar altar = BloodAltars.at(target);
      run(console, "blood altar info Mage");
      assertEquals("blood.admin.list_line", console.nextMessage());
      run(console, "blood altar refresh " + altar.getId());
      assertEquals("blood.admin.altar_refreshed", console.nextMessage());
      run(console, "blood altar tp Mage");
      assertEquals("blood.admin.tp_unavailable", console.nextMessage());
      run(staff, "blood altar tp Mage");
      assertEquals(11.5, staff.getLocation().getY(), 1.0);
      run(console, "blood altar purify Mage");
      assertEquals("blood.admin.altar_purified", console.nextMessage());
      assertNull(BloodAltars.at(target));

      BloodAltar lost = BloodAltar.create("x", "o", fx.pocket(5, 10, 5), 0f, 1L);
      BloodAltars.add(lost);
      try {
        var world = BloodAltar.class.getDeclaredField("world");
        world.setAccessible(true);
        world.set(lost, "unloaded");
      } catch (ReflectiveOperationException ex) {
        fail(ex);
      }
      run(staff, "blood altar tp " + lost.getId());
      assertEquals("blood.admin.tp_unavailable", staff.nextMessage());
    }
  }

  @Test
  void completions() {
    try (BloodFixture fx = new BloodFixture(temp)) {
      fx.server.addPlayer("Mage");
      assertEquals(BloodCommand.SUBCOMMANDS, BloodCommand.complete(new String[] {"blood", ""}));
      assertEquals(List.of("info", "tp", "refresh", "purify"), BloodCommand.complete(new String[] {"blood", "altar", ""}));
      assertEquals(List.of(), BloodCommand.complete(new String[] {"blood", "list", ""}));
      assertEquals(List.of("Mage"), BloodCommand.complete(new String[] {"blood", "info", ""}));
      assertTrue(BloodCommand.complete(new String[] {"blood", "stage", "Mage", ""}).contains("hunt"));
      assertTrue(BloodCommand.complete(new String[] {"blood", "path", "Mage", ""}).contains("devotee"));
      assertTrue(BloodCommand.complete(new String[] {"blood", "give", "Mage", ""}).contains("blood_knife"));
      BloodAltar altar = BloodAltar.create("x", "o", fx.pocket(0, 10, 0), 0f, 1L);
      BloodAltars.add(altar);
      assertEquals(List.of("Mage", altar.getId()), BloodCommand.complete(new String[] {"blood", "altar", "tp", ""}));
      assertEquals(List.of(), BloodCommand.complete(new String[] {"blood", "info", "Mage", ""}));
      assertEquals(List.of(), BloodCommand.complete(new String[] {"blood", "info", "Mage", "x", ""}));
    }
  }

  @Test
  void whisperAndVoiceTools() {
    try (BloodFixture fx = new BloodFixture(temp)) {
      BloodConfig.load(new java.io.File("src/main/resources/wildmagic/bloodmagic"), fx.logger);
      ConsoleCommandSenderMock console = (ConsoleCommandSenderMock) fx.server.getConsoleSender();
      PlayerMock mage = fx.server.addPlayer("Mage");
      fx.characters.start(mage, "mage");
      run(console, "blood whisper");
      assertEquals("blood.admin.usage", console.nextMessage());
      run(console, "blood whisper Mage 1 2");
      assertEquals("blood.admin.usage", console.nextMessage());
      run(console, "blood voice Mage");
      assertEquals("blood.admin.no_voices", console.nextMessage());
      run(console, "blood whisper Mage lots");
      assertEquals("values.invalid_amount", console.nextMessage());
      run(console, "blood whisper Mage 5");
      assertEquals("blood.admin.whisper_added", console.nextMessage());
      run(console, "blood voice Mage");
      assertEquals("blood.admin.no_voices", console.nextMessage());
      run(console, "blood whisper Mage");
      assertEquals("blood.admin.whispered", console.nextMessage());
      assertEquals(BloodStage.WHISPERED, BloodRecords.get(BloodFixture.id("mage")).getStage());
      run(console, "blood whisper Mage");
      assertEquals("blood.admin.already_whispered", console.nextMessage());
      run(console, "blood whisper Mage 5");
      assertEquals("blood.admin.whisper_added", console.nextMessage());
      run(console, "blood voice Mage");
      assertEquals("blood.admin.voiced", console.nextMessage());
      assertNotNull(mage.nextMessage());
      BloodRecords.delete(BloodFixture.id("mage"));
      run(console, "blood whisper Mage 0");
      assertEquals("blood.admin.whisper_added", console.nextMessage());
      WhisperService.clear();
    }
  }
}
