package net.tfminecraft.magic.blood;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.nio.file.Path;
import java.util.UUID;

import net.tfminecraft.magic.manager.MagicTickService;
import net.tfminecraft.magic.tick.MagicTickContext;
import net.tfminecraft.tlibs.character.CharacterStatus;
import net.tfminecraft.tlibs.character.Characters;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockbukkit.mockbukkit.MockBukkit;

class BloodMagicTest {
  @TempDir Path temp;

  @Test
  void startLoadsDataSettlesAltarsAndSweepsOnSchedule() throws Exception {
    try (BloodFixture fx = new BloodFixture(temp)) {
      var plugin = MockBukkit.createMockPlugin("BloodHost");
      String owner = UUID.randomUUID().toString();
      Block target = fx.pocket(0, 10, 0);
      BloodStore store = new BloodStore(new java.io.File(plugin.getDataFolder(), "data/bloodmagic"), fx.logger);
      store.saveAltars(java.util.List.of(BloodAltar.create(owner, "o", target, 0f, 1L)));
      BloodRecord record = BloodRecord.create(owner, "o");
      record.setStage(BloodStage.ANIMAL);
      store.save(record);

      try (var chars = mockStatic(Characters.class)) {
        chars.when(Characters::available).thenReturn(true);
        chars.when(() -> Characters.info(any())).thenReturn(java.util.Optional.empty());
        BloodMagic.start(plugin);
        assertEquals(1, BloodAltars.all().size());
        assertEquals(BloodStage.ANIMAL, BloodRecords.get(owner).getStage());
        fx.server.getScheduler().performOneTick();
        assertEquals(Material.BARRIER, target.getType());

        BloodMagic.tick(MagicTickContext.between(0, 59));
        BloodMagic.tick(MagicTickContext.between(1, 2));
        assertEquals(1, BloodAltars.all().size());
        chars.when(() -> Characters.info(UUID.fromString(owner)))
            .thenReturn(AltarServiceTest.info(owner, CharacterStatus.DEAD));
        BloodMagic.tick(MagicTickContext.between(599, 600));
        assertTrue(BloodAltars.all().isEmpty());
      } finally {
        MagicTickService.clearHandlers();
      }
      assertTrue(BloodMagic.loadConfig(temp.toFile(), fx.logger));
      java.nio.file.Files.writeString(temp.resolve("bloodmagic.yml"), "enabled: true\n");
      assertTrue(BloodMagic.loadConfig(temp.toFile(), fx.logger));
      assertFalse(BloodConfig.isEnabled());
      BloodMagic.stop();
      assertTrue(BloodAltars.all().isEmpty());
      assertTrue(BloodRecords.all().isEmpty());
    }
  }
}
