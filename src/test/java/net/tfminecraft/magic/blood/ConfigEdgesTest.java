package net.tfminecraft.magic.blood;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.nio.file.Path;
import java.util.List;
import java.util.Random;

import org.bukkit.Material;
import org.bukkit.SoundCategory;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Config entries that are half right: what each blood config keeps and what it skips. */
class ConfigEdgesTest {
  @TempDir Path temp;
  BloodFixture fx;

  @BeforeEach
  void setup() {
    fx = new BloodFixture(temp);
  }

  @AfterEach
  void cleanup() {
    fx.close();
  }

  static YamlConfiguration yaml(String... lines) throws InvalidConfigurationException {
    YamlConfiguration config = new YamlConfiguration();
    config.loadFromString(String.join("\n", lines));
    return config;
  }

  @Test
  void meditationSoundsFollowTheirNamedSliderOrFallBack() throws Exception {
    MeditationConfig.load(yaml(
        "sounds:",
        "  music: {sound: ' tfmc:music ', category: ' ambient '}",
        "  charge: {sound: tfmc:charge, category: loudest}"));
    assertEquals("tfmc:music", MeditationConfig.music().sound());
    assertEquals(SoundCategory.AMBIENT, MeditationConfig.music().category());
    assertEquals("tfmc:charge", MeditationConfig.charge().sound());
    assertEquals(SoundCategory.MASTER, MeditationConfig.charge().category(), "An unknown slider falls back");

    // A section that has no category at all (not even a blank one) keeps the fallback too.
    ConfigurationSection meditation = mock(ConfigurationSection.class);
    ConfigurationSection music = mock(ConfigurationSection.class);
    when(meditation.getConfigurationSection("sounds.music")).thenReturn(music);
    when(music.getString("sound", "")).thenReturn("tfmc:music");
    MeditationConfig.load(meditation);
    assertEquals(SoundCategory.RECORDS, MeditationConfig.music().category());
    MeditationConfig.reset();
  }

  @Test
  void solidDecorationsAreDroppedAndARuleOfOnlySolidsIsSkipped() throws Exception {
    CorruptionConfig.load(yaml(
        "decorate:",
        "  - {to: [stone, red_mushroom]}",
        "  - {at: ceiling, to: [cobblestone]}",
        "convert:",
        "  - {from: [stone], to: [netherrack]}"), fx.logger);
    List<CorruptionConfig.Rule> rules = CorruptionConfig.rules(0);
    assertEquals(2, rules.size());
    CorruptionConfig.Rule floor = rules.stream()
        .filter(rule -> rule.on() == CorruptionConfig.Placement.FLOOR).findFirst().orElseThrow();
    assertEquals(List.of(Material.RED_MUSHROOM), floor.to());
    assertTrue(rules.stream().noneMatch(rule -> rule.on() == CorruptionConfig.Placement.CEILING));
    CorruptionConfig.Rule convert = rules.stream()
        .filter(rule -> rule.on() == CorruptionConfig.Placement.CONVERT).findFirst().orElseThrow();
    assertEquals(List.of(Material.NETHERRACK), convert.to(), "Converting keeps solid blocks");
  }

  @Test
  void choiceLinesKeepOnlyOneSpeakerWithWords() throws Exception {
    ChoiceConfig.load(yaml(
        "ask:",
        "  - {god: ''}",
        "  - {self: 5}",
        "  - {witness: hello}",
        "  - {god: a, self: b}",
        "  - plain",
        "  - {self: kneel}"), null, null);
    assertEquals(List.of(new ChoiceConfig.Line(WhisperConfig.Speaker.SELF, "kneel")), ChoiceConfig.ask());
  }

  @Test
  void roomTemplatePartsKeepOnlySolidBlocks() throws Exception {
    RoomTemplate.load(yaml("floor: [diamond, torch, nothing_at_all, stone]"), fx.logger);
    assertTrue(RoomTemplate.hasFloor());
    for (int i = 0; i < 8; i++) {
      assertEquals(Material.STONE, RoomTemplate.floor(new Random(i)));
    }
    RoomTemplate.reset();
  }
}
