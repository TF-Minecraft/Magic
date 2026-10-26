package net.tfminecraft.magic.blood;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import net.tfminecraft.magic.manager.ResonanceGuiManager;
import net.tfminecraft.magic.session.ResonanceSession;
import net.tfminecraft.magic.session.ResonanceSessionManager;
import net.tfminecraft.magic.tick.MagicTickContext;
import net.tfminecraft.tlibs.character.CharacterInfo;
import net.tfminecraft.tlibs.character.CharacterRef;
import net.tfminecraft.tlibs.character.CharacterStatus;
import net.tfminecraft.tlibs.character.Characters;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Cow;
import org.bukkit.entity.Zombie;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

class WhisperServiceTest {
  @TempDir Path temp;
  BloodFixture fx;
  final AtomicLong now = new AtomicLong(1_000_000L);

  @BeforeEach
  void setup() {
    fx = new BloodFixture(temp);
    BloodConfig.load(new File("src/main/resources/wildmagic/bloodmagic"), fx.logger);
    WhisperService.clock = now::get;
    WhisperService.random = new Random(1);
    WhisperService.equilibrium = player -> Double.NaN;
  }

  @AfterEach
  void cleanup() {
    WhisperService.clear();
    WhisperService.clock = System::currentTimeMillis;
    WhisperService.random = new Random();
    fx.close();
  }

  static MagicTickContext minute(long minutes) {
    return MagicTickContext.between(minutes * 60 - 1, minutes * 60);
  }

  @Test
  void deedsFillTheMeterUntilTheVoicesWake() {
    PlayerMock player = fx.player("mage");
    String id = BloodFixture.id("mage");
    BloodConfig.setEnabled(false);
    assertFalse(WhisperService.addTo(id, "o", 5, player));
    BloodConfig.setEnabled(true);
    assertFalse(WhisperService.addTo(id, "o", 0, player));
    WhisperService.add(player, 10);
    assertEquals(10, BloodRecords.get(id).getWhisper());
    assertFalse(WhisperService.addTo(id, "o", 10, player));
    assertTrue(WhisperService.addTo(id, "o", 10, player));
    assertEquals(BloodStage.WHISPERED, BloodRecords.get(id).getStage());
    assertEquals(now.get() + 60_000L, WhisperService.nextVoice(player.getUniqueId()));
    assertFalse(WhisperService.addTo(id, "o", 99, player));

    PlayerMock bare = fx.server.addPlayer();
    WhisperService.add(bare, 50);
    assertTrue(WhisperService.addTo(UUID.randomUUID().toString(), "o", 50, null));
  }

  @Test
  void fastTraitCharactersFillFaster() {
    String id = UUID.randomUUID().toString();
    try (var chars = mockStatic(Characters.class)) {
      chars.when(() -> Characters.info(any())).thenReturn(Optional.of(new CharacterInfo(
          new CharacterRef(UUID.randomUUID(), UUID.fromString(id)), "N", CharacterStatus.ALIVE, null,
          Set.of("Heretic"), 0, Instant.EPOCH)));
      WhisperService.addTo(id, "o", 2, null);
      assertEquals(6, BloodRecords.get(id).getWhisper());
      chars.when(() -> Characters.info(any())).thenReturn(Optional.empty());
      WhisperService.addTo(id, "o", 2, null);
      assertEquals(8, BloodRecords.get(id).getWhisper());
    }
    assertFalse(WhisperService.fastTrait("not-a-uuid"));
  }

  @Test
  void animalKillsWeighBladesNightAndSolitude() {
    PlayerMock player = fx.player("mage");
    Cow cow = fx.world.spawn(player.getLocation(), Cow.class);
    Zombie zombie = fx.world.spawn(player.getLocation(), Zombie.class);
    fx.world.setTime(1000);
    assertEquals(0, WhisperService.animalKillWeight(player, cow));
    player.getInventory().setItemInMainHand(new ItemStack(Material.IRON_SWORD));
    assertEquals(0, WhisperService.animalKillWeight(player, zombie));
    assertEquals(4, WhisperService.animalKillWeight(player, cow));
    PlayerMock witness = fx.server.addPlayer();
    witness.teleport(player.getLocation());
    assertEquals(2, WhisperService.animalKillWeight(player, cow));
    fx.world.setTime(15000);
    assertEquals(4, WhisperService.animalKillWeight(player, cow));
    fx.world.setTime(23500);
    assertEquals(2, WhisperService.animalKillWeight(player, cow));
    witness.teleport(new Location(fx.world, 500, 11, 500));
    assertTrue(WhisperService.alone(player));

    assertFalse(WhisperService.blade(null));
    assertFalse(WhisperService.blade(new ItemStack(Material.AIR)));
    assertFalse(WhisperService.blade(new ItemStack(Material.STICK)));
    assertTrue(WhisperService.blade(new ItemStack(Material.STONE_AXE)));
    assertTrue(WhisperService.blade(BloodItems.create(BloodItemKind.ALTAR, "x")));
  }

  @Test
  void teachingLinesRunInOrderAndEndInTheGift() {
    PlayerMock player = fx.player("mage");
    String id = BloodFixture.id("mage");
    BloodRecord record = fx.record("mage", BloodStage.WHISPERED);
    int lines = WhisperConfig.voice(WhisperConfig.Speaker.GOD).lines(BloodStage.WHISPERED).size();
    for (int i = 0; i < lines - 1; i++) {
      WhisperService.speakNext(player, record);
      assertNotNull(player.nextMessage());
    }
    assertEquals(BloodStage.WHISPERED, record.getStage());
    WhisperService.speakNext(player, record);
    assertEquals(BloodStage.ANIMAL, record.getStage());
    assertTrue(player.getInventory().containsAtLeast(BloodItems.create(BloodItemKind.RITUAL_KNIFE, id), 1));
    assertTrue(player.getInventory().containsAtLeast(BloodItems.create(BloodItemKind.ALTAR, id), 1));

    record.setStage(BloodStage.WHISPERED);
    record.setWhisperLine(lines + 3);
    BloodAltars.add(BloodAltar.create(id, "o", fx.pocket(0, 10, 0), 0f, 1L));
    player.getInventory().clear();
    WhisperService.speakNext(player, record);
    assertEquals(BloodStage.ANIMAL, record.getStage());
    assertFalse(player.getInventory().containsAtLeast(BloodItems.create(BloodItemKind.ALTAR, id), 1));
  }

  @SuppressWarnings("deprecation")
  static String plain(String message) {
    return net.md_5.bungee.api.ChatColor.stripColor(message);
  }

  @Test
  void atTheQuestionTheVoidSpeaksBackHalfTheTime() throws Exception {
    PlayerMock player = fx.player("mage");
    BloodRecord record = fx.record("mage", BloodStage.CHOICE);
    java.util.List<String> self = WhisperConfig.voice(WhisperConfig.Speaker.SELF).lines(BloodStage.CHOICE);
    java.util.List<String> god = WhisperConfig.voice(WhisperConfig.Speaker.GOD).lines(BloodStage.CHOICE);
    WhisperService.random = new java.util.Random() {
      boolean flip;
      @Override public boolean nextBoolean() { return flip = !flip; }
    };
    WhisperService.speakNext(player, record);
    assertTrue(self.contains(plain(player.nextMessage())));
    WhisperService.speakNext(player, record);
    assertTrue(god.contains(plain(player.nextMessage())));
    Files.writeString(temp.resolve("godonly.yml"),
        "enabled: true\nvoices:\n  god:\n    style: '#111111'\n    lines:\n      choice: [kneel]\n");
    BloodConfig.load(temp.resolve("godonly.yml").toFile(), fx.logger);
    WhisperService.speakNext(player, record);
    WhisperService.speakNext(player, record);
    assertTrue(player.nextMessage().endsWith("kneel"));
    assertTrue(player.nextMessage().endsWith("kneel"));
  }

  @Test
  void laterStagesPickALineAndTheTruePathHearsOnlyItself() throws Exception {
    PlayerMock player = fx.player("mage");
    BloodRecord record = fx.record("mage", BloodStage.ANIMAL);
    assertEquals(WhisperConfig.Speaker.GOD, WhisperService.speaker(record));
    WhisperService.speakNext(player, record);
    assertNotNull(player.nextMessage());
    record.setPath(BloodPath.TRUE);
    record.setStage(BloodStage.HUNT);
    assertEquals(WhisperConfig.Speaker.SELF, WhisperService.speaker(record));
    WhisperService.speakNext(player, record);
    assertNotNull(player.nextMessage());
    record.setStage(BloodStage.ASCENDED);
    WhisperService.speakNext(player, record);
    assertNotNull(player.nextMessage());
    record.setPath(BloodPath.NONE);
    record.setStage(BloodStage.HUNT);
    WhisperService.speakNext(player, record);
    assertNull(player.nextMessage());

    Files.writeString(temp.resolve("quiet.yml"),
        "enabled: true\nvoices:\n  god:\n    style: ''\n    lines:\n      whispered: []\n");
    BloodConfig.load(temp.resolve("quiet.yml").toFile(), fx.logger);
    record.setPath(BloodPath.NONE);
    record.setStage(BloodStage.WHISPERED);
    WhisperService.speakNext(player, record);
    assertEquals(BloodStage.ANIMAL, record.getStage());
    WhisperService.speak(player, WhisperConfig.Speaker.GOD, "plain");
    assertEquals("plain", player.nextMessage());
  }

  @Test
  void giftDropsWhatDoesNotFit() {
    PlayerMock player = fx.player("mage");
    for (int i = 0; i < player.getInventory().getSize(); i++) {
      player.getInventory().setItem(i, new ItemStack(Material.DIRT, 64));
    }
    int before = fx.world.getEntities().size();
    WhisperService.give(player, new ItemStack(Material.STICK));
    assertTrue(fx.world.getEntities().size() >= before);
  }

  @Test
  void minuteTicksDrainCorruptAndSpeakWhenAloneAndDue() {
    PlayerMock player = fx.player("mage");
    String id = BloodFixture.id("mage");
    fx.server.addPlayer();
    BloodConfig.setEnabled(false);
    WhisperService.tick(minute(10));
    BloodConfig.setEnabled(true);
    WhisperService.tick(MagicTickContext.between(5, 6));
    WhisperService.tick(minute(10));
    assertNull(BloodRecords.get(id));

    WhisperService.equilibrium = p -> -80;
    WhisperService.tick(minute(10));
    assertEquals(1 - 1 / 6.0, BloodRecords.get(id).getWhisper(), 1e-9);
    WhisperService.equilibrium = p -> 0;
    WhisperService.tick(minute(20));
    assertEquals(1 - 2 / 6.0, BloodRecords.get(id).getWhisper(), 1e-9);
    BloodRecords.get(id).setWhisper(0);
    WhisperService.tick(minute(30));
    WhisperService.equilibrium = p -> Double.NaN;
    WhisperService.tick(minute(40));

    BloodRecord record = BloodRecords.getOrCreate(id, "o");
    record.setStage(BloodStage.ANIMAL);
    record.setWhisper(3);
    WhisperService.tick(minute(50));
    assertEquals(3, record.getWhisper());
    WhisperService.forget(player.getUniqueId());
    WhisperService.tick(minute(41));
    Long due = WhisperService.nextVoice(player.getUniqueId());
    assertNotNull(due);
    assertTrue(due >= now.get() + 15 * 60_000L && due <= now.get() + 35 * 60_000L);
    WhisperService.tick(minute(42));
    assertNull(player.nextMessage());
    now.set(due);
    PlayerMock witness = fx.server.addPlayer();
    witness.teleport(player.getLocation());
    WhisperService.tick(minute(43));
    assertNull(player.nextMessage());
    witness.teleport(new Location(fx.world, 900, 11, 900));
    WhisperService.tick(minute(44));
    assertNotNull(player.nextMessage());

    WhisperService.voiceSoon(player);
    assertEquals(now.get(), WhisperService.nextVoice(player.getUniqueId()));
    WhisperService.forget(player.getUniqueId());
    assertNull(WhisperService.nextVoice(player.getUniqueId()));
  }

  @Test
  void fixedIntervalAndCrowdedVoicesWhenSolitudeIsNotRequired() throws Exception {
    Files.writeString(temp.resolve("v.yml"), String.join("\n", "enabled: true", "voices:",
        "  interval_minutes: [5, 5]", "  require_alone: false", "  god:", "    lines:",
        "      animal: [hello]", ""));
    BloodConfig.load(temp.resolve("v.yml").toFile(), fx.logger);
    PlayerMock player = fx.player("mage");
    PlayerMock witness = fx.server.addPlayer();
    witness.teleport(player.getLocation());
    fx.record("mage", BloodStage.ANIMAL);
    WhisperService.voiceSoon(player);
    WhisperService.tick(minute(1));
    assertEquals("hello", player.nextMessage());
    assertEquals(now.get() + 5 * 60_000L, WhisperService.nextVoice(player.getUniqueId()));
  }

  @Test
  void sessionEquilibriumComesFromTheResonanceSession() throws Exception {
    PlayerMock player = fx.player("mage");
    var gui = mock(ResonanceGuiManager.class);
    var sessions = mock(ResonanceSessionManager.class);
    when(net.tfminecraft.magic.Magic.plugin.getResonanceGuiManager()).thenReturn(gui);
    when(gui.getSessionManager()).thenReturn(sessions);
    var method = WhisperService.class.getDeclaredMethod("sessionEquilibrium", org.bukkit.entity.Player.class);
    method.setAccessible(true);
    assertTrue(Double.isNaN((double) method.invoke(null, player)));
    var session = new ResonanceSession();
    session.setEquilibrium(-12);
    when(sessions.get(player)).thenReturn(session);
    assertEquals(-12, (double) method.invoke(null, player));
  }
}
