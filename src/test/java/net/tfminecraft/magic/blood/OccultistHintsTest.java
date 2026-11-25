package net.tfminecraft.magic.blood;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Random;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Handler;
import java.util.logging.LogRecord;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Cow;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import net.tfminecraft.magic.tick.MagicTickContext;

class OccultistHintsTest {
  private static final long MINUTE = 60_000L;

  @TempDir Path temp;
  BloodFixture fx;
  final AtomicLong now = new AtomicLong(1_000_000L);

  @BeforeEach
  void setup() {
    fx = new BloodFixture(temp);
    BloodFixture.loadBundled(fx.logger);
    WhisperService.clock = now::get;
    WhisperService.random = new Random(1);
    WhisperService.equilibrium = player -> Double.NaN;
    WhisperService.fastTraitOf = character -> true;
  }

  @AfterEach
  void cleanup() {
    WhisperService.clear();
    WhisperService.clock = System::currentTimeMillis;
    WhisperService.random = new Random();
    WhisperService.fastTraitOf = WhisperService::fastTrait;
    WhisperService.equilibrium = player -> Double.NaN;
    FakeHusbandry.restore();
    fx.close();
  }

  static String plain(String message) {
    return WhisperServiceTest.plain(message);
  }

  static List<String> lines(String path) throws Exception {
    org.bukkit.configuration.file.YamlConfiguration yaml = org.bukkit.configuration.file.YamlConfiguration
        .loadConfiguration(new java.io.File("src/main/resources/wildmagic/bloodmagic/voices.yml"));
    return yaml.getStringList("hints." + path);
  }

  void load(String... yaml) throws Exception {
    Path file = temp.resolve("hints" + System.nanoTime() + ".yml");
    Files.writeString(file, String.join("\n", yaml) + "\n");
    BloodConfig.load(file.toFile(), fx.logger);
    BloodConfig.setEnabled(true);
  }

  @Test
  void aDeedThatFeedsAnOccultistsMeterGetsAFaintLineOncePerCooldown() throws Exception {
    PlayerMock player = fx.player("occultist");
    String id = BloodFixture.id("occultist");
    assertFalse(WhisperService.addTo(id, "o", 4, player, OccultistHints.Deed.ROBBERY));
    assertEquals(12, BloodRecords.get(id).getWhisper(), "still tripled");
    String line = player.nextMessage();
    assertTrue(lines("deeds.robbery").contains(plain(line)), line);
    assertTrue(line.contains("§o"), "in the hints' style");
    player.assertSoundHeard("tfmc_blood:heart");

    WhisperService.addTo(id, "o", 1, player, OccultistHints.Deed.ROBBERY);
    assertNull(player.nextMessage(), "the same deed waits out its cooldown");
    WhisperService.addTo(id, "o", 1, player, OccultistHints.Deed.CHARACTER_KILL);
    assertTrue(lines("deeds.character_kill").contains(plain(player.nextMessage())), "another deed has its own");
    now.addAndGet(60 * MINUTE);
    WhisperService.addTo(id, "o", 1, player, OccultistHints.Deed.ROBBERY);
    assertNotNull(player.nextMessage(), "after the cooldown");
  }

  @Test
  void noHintWithoutTheTraitADeedAPlayerOrWhenTheVoicesWake() {
    PlayerMock player = fx.player("occultist");
    String id = BloodFixture.id("occultist");
    WhisperService.addTo(id, "o", 1, player);
    assertNull(player.nextMessage(), "staff additions are no deed");
    WhisperService.addTo(id, "o", 1, null, OccultistHints.Deed.ROBBERY);
    WhisperService.fastTraitOf = character -> false;
    WhisperService.addTo(id, "o", 1, player, OccultistHints.Deed.ROBBERY);
    assertNull(player.nextMessage(), "no fast trait");
    WhisperService.fastTraitOf = character -> true;
    assertTrue(WhisperService.addTo(id, "o", 30, player, OccultistHints.Deed.ROBBERY));
    assertNull(player.nextMessage(), "the voices take over");
    WhisperService.addTo(id, "o", 30, player, OccultistHints.Deed.ROBBERY);
    assertNull(player.nextMessage(), "awake: the meter no longer fills");
  }

  @Test
  void eachDeedSourceNamesItsDeed() throws Exception {
    FakeHusbandry.use();
    PlayerMock player = fx.player("occultist");
    PlayerMock bare = fx.server.addPlayer();
    player.getInventory().setItemInMainHand(new ItemStack(Material.IRON_SWORD));
    bare.getInventory().setItemInMainHand(new ItemStack(Material.IRON_SWORD));
    Cow cow = fx.world.spawn(player.getLocation(), Cow.class);
    WhisperServiceTest.keep(cow, java.util.UUID.randomUUID());
    WhisperService.animalKill(bare, cow);
    assertNull(bare.nextMessage(), "no character, nothing");
    WhisperService.animalKill(player, cow);
    assertTrue(lines("deeds.stolen_animal").contains(plain(player.nextMessage())));
    WhisperServiceTest.keep(cow, player.getUniqueId());
    WhisperService.animalKill(player, cow);
    assertTrue(lines("deeds.own_animal").contains(plain(player.nextMessage())));

    WhisperService.equilibrium = p -> -50;
    WhisperService.tick(MagicTickContext.between(599, 600));
    assertTrue(lines("deeds.corruption").contains(plain(player.nextMessage())));
  }

  @Test
  void riddlesComeRarelyWhenAloneAndStopOnceTheVoicesWake() throws Exception {
    PlayerMock player = fx.player("occultist");
    String id = BloodFixture.id("occultist");
    OccultistHints.tick(player, id, null);
    long due = OccultistHints.nextRiddle(player.getUniqueId());
    assertTrue(due >= now.get() + 30 * MINUTE && due <= now.get() + 90 * MINUTE, "first a while after joining");
    OccultistHints.tick(player, id, null);
    assertNull(player.nextMessage(), "not yet");

    now.set(due);
    PlayerMock witness = fx.server.addPlayer();
    witness.teleport(player.getLocation());
    OccultistHints.tick(player, id, null);
    assertNull(player.nextMessage(), "waits until nobody is near");
    witness.teleport(new Location(fx.world, 500, 11, 500));
    OccultistHints.tick(player, id, BloodRecords.getOrCreate(id, "o"));
    assertTrue(lines("riddles").contains(plain(player.nextMessage())));
    long next = OccultistHints.nextRiddle(player.getUniqueId());
    assertTrue(next >= now.get() + 120 * MINUTE && next <= now.get() + 240 * MINUTE, "then every few hours");

    now.set(next);
    WhisperService.fastTraitOf = character -> false;
    OccultistHints.tick(player, id, null);
    assertNull(player.nextMessage(), "no fast trait, no riddle");
    assertTrue(OccultistHints.nextRiddle(player.getUniqueId()) > now.get(), "checked again an interval later");

    OccultistHints.tick(player, id, fx.record("occultist", BloodStage.WHISPERED));
    assertNull(OccultistHints.nextRiddle(player.getUniqueId()), "the voices woke");
    OccultistHints.tick(player, id, null);
    WhisperService.forget(player.getUniqueId());
    assertNull(OccultistHints.nextRiddle(player.getUniqueId()), "a quit or switch forgets the timer");
  }

  @Test
  void theMinuteTickRunsTheRiddles() {
    PlayerMock player = fx.player("occultist");
    WhisperService.tick(MagicTickContext.between(59, 60));
    assertNotNull(OccultistHints.nextRiddle(player.getUniqueId()));
    WhisperService.clear();
    assertNull(OccultistHints.nextRiddle(player.getUniqueId()));
  }

  @Test
  void settingsCanSwitchOffRecolourAndRetimeTheHints() throws Exception {
    java.util.List<String> warnings = new java.util.ArrayList<>();
    fx.logger.addHandler(new Handler() {
      @Override public void publish(LogRecord record) { warnings.add(record.getMessage()); }
      @Override public void flush() { }
      @Override public void close() { }
    });
    PlayerMock player = fx.player("occultist");
    String id = BloodFixture.id("occultist");

    load("hints:", "  colors: [red, '#112233']", "  sound: ''", "  deed_cooldown_minutes: 0",
        "  first_riddle_minutes: [5, 1]", "  riddle_interval_minutes: [3]",
        "  deeds:", "    robbery: [taken]", "  riddles: [hush]");
    assertTrue(warnings.stream().anyMatch(w -> w.contains("hints.colors")));
    WhisperService.addTo(id, "o", 1, player, OccultistHints.Deed.ROBBERY);
    WhisperService.addTo(id, "o", 1, player, OccultistHints.Deed.ROBBERY);
    assertEquals("taken", plain(player.nextMessage()));
    assertEquals("taken", plain(player.nextMessage()), "no cooldown");
    WhisperService.addTo(id, "o", 1, player, OccultistHints.Deed.CHARACTER_KILL);
    assertNull(player.nextMessage(), "a deed without lines says nothing");
    OccultistHints.tick(player, id, null);
    long due = OccultistHints.nextRiddle(player.getUniqueId());
    assertTrue(due >= now.get() + MINUTE && due <= now.get() + 5 * MINUTE, "reversed range is sorted");
    now.set(due);
    OccultistHints.tick(player, id, null);
    assertEquals("hush", plain(player.nextMessage()));
    long next = OccultistHints.nextRiddle(player.getUniqueId());
    assertTrue(next >= now.get() + 120 * MINUTE, "a range that isn't two numbers keeps the default");

    load("hints:", "  style: '§o'", "  deeds:", "    robbery: [plain]");
    WhisperService.addTo(id, "o", 1, player, OccultistHints.Deed.ROBBERY);
    String line = player.nextMessage();
    assertEquals("plain", plain(line));
    assertTrue(line.startsWith("§o"), "without colours the style leads: " + line);
    OccultistHints.tick(player, id, null);
    assertNull(OccultistHints.nextRiddle(player.getUniqueId()), "no riddles, no timer");

    load("hints:", "  enabled: false", "  deeds:", "    robbery: [off]", "  riddles: [off]");
    WhisperService.addTo(id, "o", 1, player, OccultistHints.Deed.ROBBERY);
    OccultistHints.tick(player, id, null);
    assertNull(player.nextMessage());
    assertNull(OccultistHints.nextRiddle(player.getUniqueId()));

    load("enabled: true");
    WhisperService.addTo(id, "o", 1, player, OccultistHints.Deed.ROBBERY);
    assertNull(player.nextMessage(), "no hints section: nothing to say");
  }
}
