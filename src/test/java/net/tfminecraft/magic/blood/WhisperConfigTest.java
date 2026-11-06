package net.tfminecraft.magic.blood;

import static org.junit.jupiter.api.Assertions.*;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.logging.Logger;

import org.bukkit.entity.EntityType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class WhisperConfigTest {
  @TempDir Path temp;
  final Logger log = Logger.getAnonymousLogger();

  @AfterEach
  void reset() {
    BloodConfig.reset();
  }

  @Test
  void bundledVoicesAndSourcesLoad() {
    assertTrue(BloodConfig.load(new File("src/main/resources/wildmagic/bloodmagic"), log));
    assertEquals(30, WhisperConfig.threshold());
    assertEquals(1, WhisperConfig.decayPerHour());
    assertTrue(WhisperConfig.fastTrait(Set.of("HERETIC")));
    assertFalse(WhisperConfig.fastTrait(Set.of("thief")));
    assertFalse(WhisperConfig.fastTrait(java.util.Collections.singleton(null)));
    assertEquals(3, WhisperConfig.fastMultiplier());
    assertEquals(2, WhisperConfig.animalKill());
    assertEquals(2, WhisperConfig.aloneOrNightMultiplier());
    assertEquals(15, WhisperConfig.characterKill());
    assertEquals(1, WhisperConfig.corruption());
    assertEquals(-40, WhisperConfig.corruptionBelow());
    assertTrue(WhisperConfig.isAnimal(EntityType.COW));
    assertFalse(WhisperConfig.isAnimal(EntityType.ZOMBIE));
    assertEquals(15, WhisperConfig.minInterval());
    assertEquals(35, WhisperConfig.maxInterval());
    assertTrue(WhisperConfig.requireAlone());
    assertEquals(24, WhisperConfig.aloneRadius());
    var god = WhisperConfig.voice(WhisperConfig.Speaker.GOD);
    assertEquals(9, god.lines(BloodStage.WHISPERED).size());
    assertTrue(god.lines(BloodStage.HUNT).isEmpty());
    assertEquals("minecraft:ambient.cave", god.sound());
    assertEquals(0.5f, god.pitch());
    assertFalse(WhisperConfig.voice(WhisperConfig.Speaker.SELF).lines(BloodStage.HUNT).isEmpty());
  }

  @Test
  void defaultsAndBadEntriesAreSafe() throws Exception {
    BloodConfig.reset();
    assertEquals("", WhisperConfig.voice(WhisperConfig.Speaker.GOD).style());
    assertTrue(WhisperConfig.voice(WhisperConfig.Speaker.SELF).lines(BloodStage.FINAL).isEmpty());
    String yaml = String.join("\n",
        "whispers:",
        "  threshold: 0",
        "  decay_per_hour: -1",
        "  fast_multiplier: 0",
        "  animal_types: [COW, DRAGONISH]",
        "  sources:",
        "    animal_kill: -1",
        "    alone_or_night_multiplier: 0",
        "    character_kill: -1",
        "    corruption: -1",
        "voices:",
        "  interval_minutes: [40, 0]",
        "  alone_radius: 0",
        "  self:",
        "    lines:",
        "      nowhere: [x]",
        "      hunt: [y]",
        "  god:",
        "    style: s",
        "");
    Files.writeString(temp.resolve("b.yml"), yaml);
    BloodConfig.load(temp.resolve("b.yml").toFile(), log);
    assertEquals(1, WhisperConfig.threshold());
    assertEquals(0, WhisperConfig.decayPerHour());
    assertEquals(1, WhisperConfig.fastMultiplier());
    assertTrue(WhisperConfig.isAnimal(EntityType.COW));
    assertEquals(0, WhisperConfig.animalKill());
    assertEquals(1, WhisperConfig.aloneOrNightMultiplier());
    assertEquals(0, WhisperConfig.characterKill());
    assertEquals(0, WhisperConfig.corruption());
    assertEquals(1, WhisperConfig.minInterval());
    assertEquals(40, WhisperConfig.maxInterval());
    assertEquals(1, WhisperConfig.aloneRadius());
    assertEquals(1, WhisperConfig.voice(WhisperConfig.Speaker.SELF).lines(BloodStage.HUNT).size());
    assertEquals("s", WhisperConfig.voice(WhisperConfig.Speaker.GOD).style());
    assertTrue(WhisperConfig.voice(WhisperConfig.Speaker.GOD).lines(BloodStage.ANIMAL).isEmpty());

    Files.writeString(temp.resolve("c.yml"), "voices:\n  interval_minutes: [3]\n");
    BloodConfig.load(temp.resolve("c.yml").toFile(), log);
    assertEquals(15, WhisperConfig.minInterval());
  }
}
