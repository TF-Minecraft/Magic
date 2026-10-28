package net.tfminecraft.magic.blood;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import net.tfminecraft.magic.integration.RpCharactersBridge;
import net.tfminecraft.tlibs.TLibs;
import net.tfminecraft.tlibs.character.CharacterInfo;
import net.tfminecraft.tlibs.character.CharacterRef;
import net.tfminecraft.tlibs.character.CharacterStatus;
import net.tfminecraft.tlibs.character.Characters;
import net.tfminecraft.tlibs.objects.api.ItemAPI;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import org.mockito.MockedStatic;

class ChoiceTest {
  @TempDir Path temp;
  BloodFixture fx;
  PlayerMock mage;
  String id;
  BloodRecord record;
  MockedStatic<RpCharactersBridge> bridge;

  /** Stands in for RPCharacters' ClassUnlocks. */
  public static final class FakeUnlocks {
    static boolean answer = true;
    static String last;

    private FakeUnlocks() {}

    public static boolean assign(Player player, String classId) {
      last = classId;
      return answer;
    }
  }

  @BeforeEach
  void setup() {
    fx = new BloodFixture(temp);
    BloodConfig.load(new File("src/main/resources/wildmagic/bloodmagic"), fx.logger);
    mage = fx.player("mage");
    id = BloodFixture.id("mage");
    record = fx.record("mage", BloodStage.CHOICE);
    record.addSpecies("cow");
    BloodAltars.add(BloodAltar.create(id, "o", fx.pocket(0, 10, 0), 0f, 1L));
    ClassUnlockBridge.unlocksClass = FakeUnlocks.class.getName();
    FakeUnlocks.answer = true;
    FakeUnlocks.last = null;
    bridge = mockStatic(RpCharactersBridge.class);
    bridge.when(() -> RpCharactersBridge.applyTrait(any(), any())).thenReturn(true);
  }

  @AfterEach
  void cleanup() {
    bridge.close();
    ClassUnlockBridge.unlocksClass = "net.tfminecraft.rpcharacters.classpick.ClassUnlocks";
    WhisperService.clear();
    QuestionService.clear();
    QuestionService.clock = System::currentTimeMillis;
    fx.close();
  }

  final java.util.concurrent.atomic.AtomicLong now = new java.util.concurrent.atomic.AtomicLong(1_000_000L);

  /** The god's question in the mage's chamber: altar in the middle of an open room. */
  void chamber() {
    BloodAltars.remove(BloodAltars.ofCharacter(id));
    BloodAltars.add(BloodAltar.create(id, "o", fx.room(0, 10, 0), 0f, 1L));
    mage.teleport(new org.bukkit.Location(fx.world, 2.5, 10, 2.5));
    QuestionService.clock = now::get;
  }

  /** Lets the dialogue run a while, one tick a second. */
  void wait(int seconds) {
    for (int i = 0; i < seconds; i++) {
      now.addAndGet(1000L);
      QuestionService.tick();
    }
  }

  java.util.List<String> heard() {
    java.util.List<String> out = new java.util.ArrayList<>();
    for (String m = mage.nextMessage(); m != null; m = mage.nextMessage()) {
      out.add(WhisperServiceTest.plain(m));
    }
    return out;
  }

  void say(String words) {
    QuestionService.onChat(mage, "rp", words);
  }

  @Test
  void bundledQuestionClassesAndArmourLoad() {
    assertEquals(5, ChoiceConfig.ask().size());
    assertEquals(WhisperConfig.Speaker.SELF, ChoiceConfig.ask().get(3).speaker());
    assertNotNull(ChoiceConfig.unclear());
    assertNotNull(ChoiceConfig.walkedAway());
    assertEquals(90, ChoiceConfig.answerSeconds());
    assertEquals(5, ChoiceConfig.lineSeconds());
    assertEquals("tfmc_blood:rite.question", ChoiceConfig.music());
    assertEquals(91, ChoiceConfig.musicLoopSeconds());
    assertTrue(ChoiceConfig.answer(BloodPath.DEVOTEE).words().contains("i kneel"));
    assertEquals(2, ChoiceConfig.answer(BloodPath.TRUE).sealed().size());
    assertEquals("manaless", ChoiceConfig.devoteeTrait());
    assertEquals("bloodsworn", ChoiceConfig.classFor(BloodPath.DEVOTEE));
    assertEquals("sanguinar", ChoiceConfig.classFor(BloodPath.TRUE));
    assertNull(ChoiceConfig.classFor(BloodPath.NONE));
    assertEquals(4, ChoiceConfig.armourFor(BloodPath.TRUE).size());
    assertTrue(ChoiceConfig.armourFor(BloodPath.NONE).isEmpty());
  }

  @Test
  void missingSectionsFallBack() throws Exception {
    Files.writeString(temp.resolve("e.yml"), String.join("\n", "enabled: true", "choice:", "  devotee_trait: ' '",
        "  answer_seconds: 1", "  line_seconds: 0", "  music: { sound: ' x:y ', category: nope, volume: -1 }",
        "  ask: [plain, { god: ' ' }, { who: hi }, { god: a, self: b }, { self: mine }]",
        "classes:", "  devotee: ' '", "  'true': [x]", ""));
    BloodConfig.load(temp.resolve("e.yml").toFile(), fx.logger);
    assertEquals("", ChoiceConfig.devoteeTrait());
    assertEquals(5, ChoiceConfig.answerSeconds());
    assertEquals(1, ChoiceConfig.lineSeconds());
    assertEquals("x:y", ChoiceConfig.music());
    assertEquals(org.bukkit.SoundCategory.RECORDS, ChoiceConfig.musicCategory());
    assertEquals(0f, ChoiceConfig.musicVolume());
    assertEquals(1, ChoiceConfig.ask().size());
    assertNull(ChoiceConfig.unclear());
    assertTrue(ChoiceConfig.answer(BloodPath.DEVOTEE).words().isEmpty());
    assertNull(ChoiceConfig.classFor(BloodPath.DEVOTEE));
    assertNull(ChoiceConfig.classFor(BloodPath.TRUE));
    assertTrue(ChoiceConfig.armourFor(BloodPath.DEVOTEE).isEmpty());
    Files.writeString(temp.resolve("w.yml"), "enabled: true\nchoice:\n  kneel:\n    words: [' ', 'Yes!']\n");
    BloodConfig.load(temp.resolve("w.yml").toFile(), fx.logger);
    assertEquals(java.util.List.of("yes"), ChoiceConfig.answer(BloodPath.DEVOTEE).words());
    BloodConfig.reset();
    assertTrue(ChoiceConfig.ask().isEmpty());
  }

  @Test
  void answersAreReadFromWhatIsSaid() {
    assertEquals(BloodPath.TRUE, QuestionService.classify("I kneel to no one."));
    assertEquals(BloodPath.TRUE, QuestionService.classify("I will NOT kneel"));
    assertEquals(BloodPath.DEVOTEE, QuestionService.classify("Yes, father... I kneel."));
    assertNull(QuestionService.classify("I know what you are"));
    assertNull(QuestionService.classify("hm"));
    assertEquals("i'm yours", QuestionService.normalize("  I'M   yours!! "));
  }

  @Test
  void kneelingIsAskedBackThenSealed() {
    chamber();
    QuestionService.start(mage);
    assertTrue(QuestionService.asking(mage.getUniqueId()));
    QuestionService.start(mage);
    say("I kneel");
    wait(30);
    java.util.List<String> lines = heard();
    assertEquals(ChoiceConfig.ask().size() + 1, lines.size());
    assertEquals(ChoiceConfig.ask().get(0).text(), lines.get(0));
    assertEquals("blood.choice.prompt", lines.get(lines.size() - 1));
    say("hm?");
    say("what?");
    assertEquals(java.util.List.of(ChoiceConfig.unclear().text()), heard());
    QuestionService.onChat(mage, "ooc", "I kneel");
    QuestionService.onChat(mage, null, "I kneel");
    say("Yes, I kneel");
    wait(3);
    assertEquals(ChoiceConfig.answer(BloodPath.DEVOTEE).confirm().get(0).text(), heard().get(0));
    say("I kneel. I am yours.");
    wait(20);
    assertEquals(BloodPath.DEVOTEE, record.getPath());
    assertFalse(QuestionService.asking(mage.getUniqueId()));
    assertTrue(heard().contains(ChoiceConfig.answer(BloodPath.DEVOTEE).sealed().get(0).text()));
  }

  @Test
  void refusingCanBeChangedUntilItIsSaidTwice() throws Exception {
    chamber();
    QuestionService.start(mage);
    wait(30);
    heard();
    say("No.");
    wait(8);
    say("Yes, father");
    wait(8);
    say("No. Never.");
    wait(12);
    say("I refuse you");
    wait(20);
    assertEquals(BloodPath.TRUE, record.getPath());
    assertEquals(BloodStage.HUNT, record.getStage());
  }

  @Test
  void walkingAwayOrSilenceEndsTheQuestion() throws Exception {
    chamber();
    mage.teleport(new org.bukkit.Location(fx.world, 40.5, 10, 40.5));
    QuestionService.start(mage);
    assertFalse(QuestionService.asking(mage.getUniqueId()));
    mage.teleport(new org.bukkit.Location(fx.world, 2.5, 10, 2.5));
    QuestionService.start(mage);
    wait(2);
    mage.teleport(new org.bukkit.Location(fx.world, 40.5, 10, 40.5));
    say("I kneel");
    wait(1);
    assertFalse(QuestionService.asking(mage.getUniqueId()));
    assertTrue(heard().contains(ChoiceConfig.walkedAway().text()));

    mage.teleport(new org.bukkit.Location(fx.world, 2.5, 10, 2.5));
    QuestionService.start(mage);
    wait(30 + ChoiceConfig.answerSeconds());
    assertFalse(QuestionService.asking(mage.getUniqueId()));
    assertEquals(BloodPath.NONE, record.getPath());

    // A sealed answer stands even if the mage steps out while the god speaks.
    QuestionService.start(mage);
    wait(30);
    say("I refuse");
    wait(8);
    say("I refuse");
    mage.teleport(new org.bukkit.Location(fx.world, 40.5, 10, 40.5));
    wait(20);
    assertEquals(BloodPath.TRUE, record.getPath());

    record.setPath(BloodPath.NONE);
    record.setStage(BloodStage.CHOICE);
    mage.teleport(new org.bukkit.Location(fx.world, 2.5, 10, 2.5));
    Files.writeString(temp.resolve("m.yml"), "enabled: true\nchoice:\n  music: { sound: 'tfmc_blood:x', loop_seconds: 3 }\n");
    BloodConfig.load(temp.resolve("m.yml").toFile(), fx.logger);
    assertEquals(3, ChoiceConfig.musicLoopSeconds());
    QuestionService.start(mage);
    wait(4);
    mage.teleport(new org.bukkit.Location(fx.world, 40.5, 10, 40.5));
    wait(1);
    assertFalse(QuestionService.asking(mage.getUniqueId()));
    mage.teleport(new org.bukkit.Location(fx.world, 2.5, 10, 2.5));
    QuestionService.start(mage);
    say("anything");
    wait(2);
    mage.disconnect();
    wait(1);
    assertFalse(QuestionService.asking(mage.getUniqueId()));
    QuestionService.forget(mage.getUniqueId());
    record.setStage(BloodStage.HUNT);
    QuestionService.start(mage);
    assertFalse(QuestionService.asking(mage.getUniqueId()));
  }

  @Test
  void devoteesGiveThemselvesAndGetClassAndArmour() {
    try (MockedStatic<TLibs> libs = mockStatic(TLibs.class)) {
      ItemAPI api = mock(ItemAPI.class, RETURNS_DEEP_STUBS);
      libs.when(TLibs::getItemAPI).thenReturn(api);
      when(api.getCreator().getItemFromPath(anyString())).thenAnswer(i -> new ItemStack(Material.LEATHER_HELMET));
      when(api.getArmorMerger().merge(any(), any(), anyString())).thenAnswer(i -> i.getArgument(0));
      assertTrue(BloodChoice.answer(mage, BloodPath.DEVOTEE));
    }
    bridge.verify(() -> RpCharactersBridge.applyTrait(mage, "manaless"));
    assertEquals(BloodPath.DEVOTEE, record.getPath());
    assertEquals(BloodStage.FINAL, record.getStage());
    assertEquals("bloodsworn", FakeUnlocks.last);
    assertEquals(4, java.util.Arrays.stream(mage.getInventory().getContents()).filter(BloodArmour::is).count());
    assertEquals("blood.choice.devoted", mage.nextMessage());
    assertNotNull(WhisperService.nextVoice(mage.getUniqueId()));
    assertFalse(BloodChoice.answer(mage, BloodPath.TRUE));
    assertEquals(BloodConfig.level(BloodProgress.of(record)), BloodAltars.ofCharacter(id).getLevel());
  }

  @Test
  void refusingKeepsTheClassAndStartsTheHunt() {
    assertFalse(BloodChoice.answer(mage, BloodPath.NONE));
    assertTrue(BloodChoice.answer(mage, BloodPath.TRUE));
    assertEquals(BloodStage.HUNT, record.getStage());
    assertNull(FakeUnlocks.last);
    assertEquals("blood.choice.refused", mage.nextMessage());
    bridge.verify(() -> RpCharactersBridge.applyTrait(any(), any()), never());
  }

  @Test
  void aMarkThatCannotBeGivenOrIsSwitchedOffStillLetsTheDevoteeKneel() throws Exception {
    bridge.when(() -> RpCharactersBridge.applyTrait(any(), any())).thenReturn(false);
    assertTrue(BloodChoice.answer(mage, BloodPath.DEVOTEE));
    assertEquals(BloodStage.FINAL, record.getStage());
    java.nio.file.Path copy = temp.resolve("nomark");
    java.nio.file.Files.createDirectories(copy);
    for (java.io.File part : new java.io.File("src/main/resources/wildmagic/bloodmagic").listFiles()) {
      java.nio.file.Files.copy(part.toPath(), copy.resolve(part.getName()));
    }
    java.nio.file.Files.writeString(copy.resolve("zz-nomark.yml"), "choice:\n  devotee_trait: ''\n");
    BloodConfig.load(copy.toFile(), fx.logger);
    record.setPath(BloodPath.NONE);
    record.setStage(BloodStage.CHOICE);
    bridge.clearInvocations();
    assertTrue(BloodChoice.answer(mage, BloodPath.DEVOTEE));
    bridge.verify(() -> RpCharactersBridge.applyTrait(any(), any()), never());
  }

  @Test
  void onlyACharacterAtTheQuestionWithAnAltarMayAnswer() {
    BloodAltars.remove(BloodAltars.ofCharacter(id));
    assertFalse(BloodChoice.canAnswer(mage));
    record.setStage(BloodStage.ANIMAL);
    assertFalse(BloodChoice.canAnswer(mage));
    PlayerMock nobody = fx.server.addPlayer();
    assertFalse(BloodChoice.canAnswer(nobody));
  }

  @Test
  void classUnlockBridgeReportsFailures() {
    assertFalse(ClassUnlockBridge.assign(mage, null));
    FakeUnlocks.answer = false;
    assertFalse(ClassUnlockBridge.assign(mage, "x"));
    FakeUnlocks.answer = true;
    assertTrue(ClassUnlockBridge.assign(mage, "x"));
    ClassUnlockBridge.unlocksClass = "no.such.Unlocks";
    assertFalse(ClassUnlockBridge.assign(mage, "x"));
  }

  @Test
  void armourIsBoundUnbreakableAndOnlyItsOwnerWearsIt() {
    ItemStack helmet = BloodArmour.bind(new ItemStack(Material.LEATHER_HELMET), id);
    assertTrue(BloodArmour.is(helmet));
    assertTrue(helmet.getItemMeta().isUnbreakable());
    assertTrue(helmet.getItemMeta().hasItemFlag(org.bukkit.inventory.ItemFlag.HIDE_UNBREAKABLE));
    assertFalse(BloodArmour.is(null));
    assertFalse(BloodArmour.is(new ItemStack(Material.AIR)));
    assertFalse(BloodArmour.is(new ItemStack(Material.STONE)));
    assertFalse(BloodArmour.is(BloodItems.create(BloodItemKind.ALTAR, id)));
    assertTrue(BloodArmour.create(BloodPath.DEVOTEE, id).isEmpty());
    ItemStack bareItem = mock(ItemStack.class);
    when(bareItem.getType()).thenReturn(Material.LEATHER_HELMET);
    assertFalse(BloodArmour.is(bareItem));
    ItemStack ownerless = new ItemStack(Material.LEATHER_HELMET);
    var meta = ownerless.getItemMeta();
    meta.getPersistentDataContainer().set(BloodItems.kindKey(), org.bukkit.persistence.PersistentDataType.STRING, "armour");
    ownerless.setItemMeta(meta);
    PlayerMock wearer = fx.player("wearer");
    wearer.getInventory().setHelmet(ownerless);
    assertEquals(1, BloodArmour.enforce(wearer));

    mage.getInventory().setHelmet(helmet);
    mage.getInventory().setChestplate(new ItemStack(Material.LEATHER_CHESTPLATE));
    assertEquals(0, BloodArmour.enforce(mage));
    PlayerMock thief = fx.player("thief");
    thief.getInventory().setHelmet(helmet.clone());
    assertEquals(1, BloodArmour.enforce(thief));
    assertNull(thief.getInventory().getHelmet());
    assertEquals("blood.armour.refuses", thief.nextMessage());
    assertTrue(thief.getInventory().containsAtLeast(helmet, 1));
    PlayerMock bare = fx.server.addPlayer();
    bare.getInventory().setBoots(BloodArmour.bind(new ItemStack(Material.LEATHER_BOOTS), id));
    assertEquals(1, BloodArmour.enforce(bare));

    String dead = UUID.randomUUID().toString();
    ItemStack relic = BloodArmour.bind(new ItemStack(Material.LEATHER_LEGGINGS), dead);
    try (MockedStatic<Characters> chars = mockStatic(Characters.class, CALLS_REAL_METHODS)) {
      chars.when(() -> Characters.info(UUID.fromString(dead))).thenReturn(Optional.of(new CharacterInfo(
          new CharacterRef(UUID.randomUUID(), UUID.fromString(dead)), "N", CharacterStatus.DEAD, null, Set.of(), 0,
          Instant.EPOCH)));
      assertTrue(BloodArmour.crumbles(relic));
      assertFalse(BloodArmour.crumbles(helmet));
      assertFalse(BloodArmour.crumbles(new ItemStack(Material.STONE)));
      thief.getInventory().setLeggings(relic);
      assertEquals(1, BloodArmour.enforce(thief));
      assertNull(thief.getInventory().getLeggings());
      assertEquals("blood.armour.crumbles", thief.nextMessage());
    }
  }
}
