package net.tfminecraft.magic.blood;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import org.mockito.MockedStatic;

import net.Indyuce.mmocore.api.event.PlayerChangeClassEvent;
import net.Indyuce.mmocore.api.player.PlayerData;
import net.Indyuce.mmocore.api.player.profess.PlayerClass;
import net.Indyuce.mmocore.skill.ClassSkill;
import net.Indyuce.mmocore.skill.binding.SkillSlot;

class ClassRewardsTest {
  @TempDir Path temp;
  BloodFixture fx;
  PlayerMock mage;
  BloodRecord record;

  /** Stands in for RPCharacters' ClassSkillPoints. */
  public static final class FakePoints {
    static final Map<String, Integer> pools = new HashMap<>();
    static boolean answer = true;

    private FakePoints() {}

    public static int get(Player player, String classId) {
      return pools.getOrDefault(classId, -1);
    }

    public static boolean set(Player player, String classId, int total) {
      if (answer) {
        pools.put(classId, total);
      }
      return answer;
    }
  }

  /** A ClassSkillPoints whose get answers with something that is not a number. */
  public static final class OddPoints {
    private OddPoints() {}

    public static String get(Player player, String classId) {
      return "many";
    }
  }

  final List<String> logs = new java.util.ArrayList<>();

  @BeforeEach
  void setup() {
    fx = new BloodFixture(temp);
    fx.logger.addHandler(new java.util.logging.Handler() {
      @Override public void publish(java.util.logging.LogRecord line) { logs.add(line.getMessage()); }
      @Override public void flush() {}
      @Override public void close() {}
    });
    BloodConfig.load(new File("src/main/resources/wildmagic/bloodmagic"), fx.logger);
    mage = fx.player("mage");
    record = fx.record("mage", BloodStage.FINAL);
    record.setPath(BloodPath.DEVOTEE);
    ClassUnlockBridge.skillPointsClass = FakePoints.class.getName();
    FakePoints.pools.clear();
    FakePoints.answer = true;
  }

  @AfterEach
  void cleanup() {
    ClassUnlockBridge.skillPointsClass = "net.tfminecraft.rpcharacters.classpick.ClassSkillPoints";
    fx.close();
  }

  String messages() {
    StringBuilder out = new StringBuilder();
    String message;
    while ((message = mage.nextMessage()) != null) {
      out.append(message).append('\n');
    }
    return out.toString();
  }

  @Test
  void rewardsAddUpPerFinalAndAscensionGivesEverything() {
    assertEquals(new HuntConfig.ClassReward(4, List.of(3), List.of()),
        HuntConfig.classRewardsUpTo(BloodPath.DEVOTEE, 2));
    assertEquals(HuntConfig.ClassReward.NONE, HuntConfig.classRewardsUpTo(BloodPath.DEVOTEE, 0));
    assertEquals(HuntConfig.ClassReward.NONE, HuntConfig.classRewardsUpTo(BloodPath.NONE, 5));
    HuntConfig.ClassReward all = HuntConfig.classRewardsUpTo(BloodPath.TRUE, Integer.MAX_VALUE);
    assertEquals(0, all.skillPoints());
    assertEquals(List.of(2, 3, 4, 5, 6), all.slots());
    assertEquals(8, all.spells().size());
    assertEquals(List.of("SANGUINAR_EXSANGUINATE", "SANGUINAR_OSSUARY"), HuntConfig.classRewardsUpTo(BloodPath.TRUE, 1).spells());
  }

  @Test
  void badRewardEntriesAreSkippedAndRepeatsCountOnce() throws Exception {
    Files.writeString(temp.resolve("r.yml"), String.join("\n",
        "finals:",
        "  class_rewards:",
        "    devotee:",
        "      0: { skill_points: 1 }",
        "      x: { skill_points: 1 }",
        "      2: 5",
        "      3: { skill_points: -4, slots: [3, 0, -1], spells: [' ', ' blood_bolt '] }",
        "      4: { skill_points: 2, slots: [3, 4], spells: [BLOOD_BOLT, OPEN_VEINS] }",
        ""));
    BloodConfig.load(temp.resolve("r.yml").toFile(), fx.logger);
    assertEquals(new HuntConfig.ClassReward(2, List.of(3, 4), List.of("BLOOD_BOLT", "OPEN_VEINS")),
        HuntConfig.classRewardsUpTo(BloodPath.DEVOTEE, 9));
    assertEquals(HuntConfig.ClassReward.NONE, HuntConfig.classRewardsUpTo(BloodPath.TRUE, 9));
  }

  @Test
  void pointsFollowTheSacrificesAndOnlyNewOnesAreAnnounced() {
    ClassRewards.apply(mage);
    assertEquals(0, FakePoints.pools.get("bloodsworn"), "Kneeling gives the class its own, empty pool");
    assertEquals("", messages());
    record.setFinalsDone(1);
    ClassRewards.apply(mage);
    assertEquals(2, FakePoints.pools.get("bloodsworn"));
    assertTrue(messages().contains("blood.class.rewarded"));
    ClassRewards.apply(mage);
    assertEquals("", messages(), "Nothing new, nothing said");
    record.setStage(BloodStage.ASCENDED);
    ClassRewards.apply(mage);
    assertEquals(10, FakePoints.pools.get("bloodsworn"));
    messages();
    FakePoints.pools.put("bloodsworn", 30);
    ClassRewards.apply(mage);
    assertEquals(10, FakePoints.pools.get("bloodsworn"));
    assertEquals("", messages(), "Fewer points than before is no gift");
    FakePoints.pools.put("bloodsworn", 3);
    FakePoints.answer = false;
    ClassRewards.apply(mage);
    assertEquals("", messages(), "A pool RPCharacters would not save is not announced");
  }

  @Test
  void charactersOffTheFinalsGetNothing() {
    PlayerMock stranger = fx.server.addPlayer("Stranger");
    ClassRewards.apply(stranger);
    record.setStage(BloodStage.CHOICE);
    ClassRewards.apply(mage);
    record.setStage(BloodStage.FINAL);
    record.setPath(BloodPath.NONE);
    ClassRewards.apply(mage);
    record.setPath(BloodPath.DEVOTEE);
    ChoiceConfig.reset();
    ClassRewards.apply(mage);
    assertTrue(FakePoints.pools.isEmpty());
  }

  @Test
  void applySoonWaitsATickAndSkipsPlayersWhoLeft() {
    ClassRewards.applySoon(mage);
    assertTrue(FakePoints.pools.isEmpty());
    fx.server.getScheduler().performOneTick();
    assertEquals(0, FakePoints.pools.get("bloodsworn"));
    FakePoints.pools.clear();
    ClassRewards.applySoon(mage);
    mage.disconnect();
    fx.server.getScheduler().performOneTick();
    assertTrue(FakePoints.pools.isEmpty());
  }

  @Test
  void theBridgeCopesWithoutClassSkillPoints() {
    ClassUnlockBridge.skillPointsClass = OddPoints.class.getName();
    assertEquals(-1, ClassUnlockBridge.skillPoints(mage, "bloodsworn"));
    assertFalse(ClassUnlockBridge.setSkillPoints(mage, "bloodsworn", 4));
    ClassUnlockBridge.skillPointsClass = "no.such.Points";
    assertEquals(-1, ClassUnlockBridge.skillPoints(mage, "bloodsworn"));
    assertFalse(ClassUnlockBridge.setSkillPoints(mage, "bloodsworn", 4));
    FakePoints.answer = false;
    ClassUnlockBridge.skillPointsClass = FakePoints.class.getName();
    assertFalse(ClassUnlockBridge.setSkillPoints(mage, "bloodsworn", 4));
  }

  @Test
  void slotsAndSpellsOpenOnlyWhileThePathsClassIsPlayed() {
    record.setPath(BloodPath.TRUE);
    record.setFinalsDone(1);
    MockBukkit.createMockPlugin("MMOCore");
    PlayerData data = mock(PlayerData.class);
    PlayerClass sanguinar = mock(PlayerClass.class);
    when(sanguinar.getId()).thenReturn("SANGUINAR");
    SkillSlot slot = mock(SkillSlot.class);
    ClassSkill exsanguinate = mock(ClassSkill.class);
    ClassSkill ossuary = mock(ClassSkill.class);
    when(ossuary.isUnlockedByDefault()).thenReturn(true);
    when(sanguinar.getSkillSlot(2)).thenReturn(slot);
    when(sanguinar.getSkill("SANGUINAR_EXSANGUINATE")).thenReturn(exsanguinate);
    when(sanguinar.getSkill("SANGUINAR_OSSUARY")).thenReturn(ossuary);
    when(data.unlock(any())).thenReturn(true);
    try (MockedStatic<PlayerData> players = mockStatic(PlayerData.class)) {
      ClassRewards.apply(mage);
      assertEquals("", messages(), "No MMOCore data yet");
      players.when(() -> PlayerData.has(mage)).thenReturn(true);
      players.when(() -> PlayerData.get((OfflinePlayer) mage)).thenReturn(data);
      ClassRewards.apply(mage);
      PlayerClass mageClass = mock(PlayerClass.class);
      when(mageClass.getId()).thenReturn("MAGE");
      when(data.getProfess()).thenReturn(mageClass);
      ClassRewards.apply(mage);
      verify(data, never()).unlock(any());
      when(data.getProfess()).thenReturn(sanguinar);
      ClassRewards.apply(mage);
      verify(data).unlock(slot);
      verify(data).unlock(exsanguinate);
      verify(data, never()).unlock(ossuary);
      assertTrue(messages().contains("blood.class.rewarded"));
      when(data.unlock(any())).thenReturn(false);
      ClassRewards.apply(mage);
      assertEquals("", messages());
      when(sanguinar.getSkillSlot(2)).thenReturn(null);
      ClassRewards.apply(mage);
      assertTrue(logs.stream().anyMatch(line -> line.contains("class sanguinar has no slot 2")));

      when(data.getPlayer()).thenReturn(mage);
      new MmoClassRewards().onClassChange(new PlayerChangeClassEvent(data, sanguinar));
      fx.server.getScheduler().performOneTick();
      verify(data, atLeast(4)).unlock(exsanguinate);
    }
  }
}
