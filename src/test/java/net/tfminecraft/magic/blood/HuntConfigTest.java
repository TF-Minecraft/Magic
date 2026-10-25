package net.tfminecraft.magic.blood;

import static org.junit.jupiter.api.Assertions.*;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.logging.Logger;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class HuntConfigTest {
  @TempDir Path temp;
  final Logger log = Logger.getAnonymousLogger();

  @AfterEach
  void reset() {
    BloodConfig.reset();
  }

  @Test
  void bundledHuntBondsAndFinalsLoad() {
    assertTrue(BloodConfig.load(new File("src/main/resources/bloodmagic.yml"), log));
    assertEquals(8, HuntConfig.dragRange());
    assertEquals(10, HuntConfig.riteSeconds());
    assertEquals(10, HuntConfig.minPlaytimeHours());
    assertEquals(7, HuntConfig.minAgeDays());
    assertTrue(HuntConfig.noRepeatPlayer());
    assertEquals(1, HuntConfig.points(0));
    assertEquals(3, HuntConfig.points(6));
    assertEquals(3, HuntConfig.points(10));
    assertEquals(2, HuntConfig.points(11));
    assertEquals(1, HuntConfig.points(40));
    assertEquals(5, HuntConfig.sampleMinutes());
    assertEquals(16, HuntConfig.nearRadius());
    assertEquals(3, HuntConfig.conversationWeight());
    assertEquals(2, HuntConfig.hourWeight());
    assertEquals(40, HuntConfig.heirBonus());
    assertEquals(10, HuntConfig.heirMinHours());
    assertEquals(20, HuntConfig.heirMinConversations());
    assertEquals(5, HuntConfig.finalCount());
    assertEquals(72 * 3_600_000L, HuntConfig.failCooldownMillis());
    assertEquals(21 * 86_400_000L, HuntConfig.inactiveMillis());
    assertEquals(Map.of(), HuntConfig.boonsUpTo(BloodPath.TRUE, 0));
    assertEquals(Map.of("MAX_HEALTH", 4.0, "MAX_MANA", 20.0), HuntConfig.boonsUpTo(BloodPath.TRUE, 3));
    assertEquals(Map.of(), HuntConfig.boonsUpTo(BloodPath.NONE, 5));
  }

  @Test
  void badEntriesAreClampedOrSkipped() throws Exception {
    String yaml = String.join("\n",
        "hunt:",
        "  drag_range: 0",
        "  rite_seconds: 0",
        "  victims: { min_playtime_hours: -1, min_age_days: -1, no_repeat_player: false }",
        "  points:",
        "    stranger: -1",
        "    default: -1",
        "    ranks:",
        "      - { up_to: 20, points: 2 }",
        "      - { up_to: 8, points: -4 }",
        "      - { up_to: x }",
        "      - { up_to: 3 }",
        "bonds: { sample_minutes: 0, near_radius: 0, conversation_weight: -1, hour_weight: -1, heir_bonus: -1, heir_min_hours: -1, heir_min_conversations: -1 }",
        "finals:",
        "  count: 0",
        "  fail_cooldown_hours: -1",
        "  inactive_days: 0",
        "  boons:",
        "    devotee:",
        "      0: { MAX_HEALTH: 1 }",
        "      x: { MAX_HEALTH: 1 }",
        "      2: 5",
        "      3: { MAX_HEALTH: 1.5 }",
        "");
    Files.writeString(temp.resolve("h.yml"), yaml);
    BloodConfig.load(temp.resolve("h.yml").toFile(), log);
    assertEquals(1, HuntConfig.dragRange());
    assertEquals(1, HuntConfig.riteSeconds());
    assertEquals(0, HuntConfig.minPlaytimeHours());
    assertEquals(0, HuntConfig.minAgeDays());
    assertFalse(HuntConfig.noRepeatPlayer());
    assertEquals(0, HuntConfig.points(0));
    assertEquals(0, HuntConfig.points(5));
    assertEquals(2, HuntConfig.points(15));
    assertEquals(0, HuntConfig.points(99));
    assertEquals(1, HuntConfig.sampleMinutes());
    assertEquals(1, HuntConfig.nearRadius());
    assertEquals(0, HuntConfig.conversationWeight());
    assertEquals(0, HuntConfig.hourWeight());
    assertEquals(0, HuntConfig.heirBonus());
    assertEquals(0, HuntConfig.heirMinHours());
    assertEquals(0, HuntConfig.heirMinConversations());
    assertEquals(1, HuntConfig.finalCount());
    assertEquals(0, HuntConfig.failCooldownMillis());
    assertEquals(86_400_000L, HuntConfig.inactiveMillis());
    assertEquals(Map.of("MAX_HEALTH", 1.5), HuntConfig.boonsUpTo(BloodPath.DEVOTEE, 5));
    assertEquals(Map.of(), HuntConfig.boonsUpTo(BloodPath.TRUE, 5));

    Files.writeString(temp.resolve("f.yml"), "finals:\n  count: 3\n");
    BloodConfig.load(temp.resolve("f.yml").toFile(), log);
    assertEquals(3, HuntConfig.finalCount());
    assertEquals(Map.of(), HuntConfig.boonsUpTo(BloodPath.DEVOTEE, 5));
  }
}
