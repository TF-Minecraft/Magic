package net.tfminecraft.magic.blood;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import net.tfminecraft.magic.integration.RpCharactersBridge;
import net.tfminecraft.tlibs.character.CharacterInfo;
import net.tfminecraft.tlibs.character.CharacterRef;
import net.tfminecraft.tlibs.character.CharacterStatus;
import net.tfminecraft.tlibs.character.Characters;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import org.mockito.MockedStatic;

class HuntAndFinalsTest {
  @TempDir Path temp;
  BloodFixture fx;
  MockedStatic<RpCharactersBridge> bridge;
  MockedStatic<Characters> chars;
  final AtomicLong now = new AtomicLong(1_000_000_000_000L);
  final Map<UUID, CharacterInfo> infos = new HashMap<>();
  final Map<String, Integer> talks = new HashMap<>();
  PlayerMock mage;
  String mageId;
  BloodRecord record;
  Block target;

  @BeforeEach
  void setup() {
    fx = new BloodFixture(temp);
    BloodConfig.load(new File("src/main/resources/bloodmagic.yml"), fx.logger);
    BondService.init(new BloodStore(temp.resolve("bonds-root").toFile(), fx.logger));
    CharacterRiteService.clock = now::get;
    FinalService.clock = now::get;
    ClassUnlockBridge.unlocksClass = ChoiceTest.FakeUnlocks.class.getName();
    ChoiceTest.FakeUnlocks.answer = true;
    ChoiceTest.FakeUnlocks.last = null;
    bridge = mockStatic(RpCharactersBridge.class);
    bridge.when(() -> RpCharactersBridge.isDown(any())).thenReturn(true);
    bridge.when(() -> RpCharactersBridge.playtimeSeconds(any())).thenReturn(100L * 3600);
    bridge.when(() -> RpCharactersBridge.characterAgeSeconds(any())).thenReturn(100L * 86_400);
    bridge.when(() -> RpCharactersBridge.sacrifice(any(), any())).thenReturn(true);
    bridge.when(() -> RpCharactersBridge.moveDowned(any(), any())).thenReturn(true);
    bridge.when(() -> RpCharactersBridge.conversationCounts(any())).thenAnswer(i -> new HashMap<>(talks));
    chars = mockStatic(Characters.class, CALLS_REAL_METHODS);
    chars.when(() -> Characters.info(any())).thenAnswer(i -> {
      UUID id = i.getArgument(0);
      return Optional.ofNullable(infos.get(id));
    });
    mage = fx.player("mage");
    mageId = BloodFixture.id("mage");
    alive(mage, "mage", "Mage");
    record = BloodRecords.getOrCreate(mageId, mage.getUniqueId().toString());
    record.setStage(BloodStage.HUNT);
    record.setPath(BloodPath.TRUE);
    fx.open(-30, 10, -3, 45, 12, 4);
    target = fx.pocket(0, 10, 0);
    BloodAltars.add(BloodAltar.create(mageId, mage.getUniqueId().toString(), target, 0f, 1L));
    mage.getInventory().setItemInMainHand(BloodItems.create(BloodItemKind.BLOOD_KNIFE, mageId));
  }

  @AfterEach
  void cleanup() {
    CharacterRiteService.clear();
    BondService.clear();
    chars.close();
    bridge.close();
    ClassUnlockBridge.unlocksClass = "net.tfminecraft.rpcharacters.classpick.ClassUnlocks";
    CharacterRiteService.clock = System::currentTimeMillis;
    FinalService.clock = System::currentTimeMillis;
    WhisperService.clear();
    fx.close();
  }

  void alive(PlayerMock player, String label, String name) {
    UUID id = UUID.fromString(BloodFixture.id(label));
    infos.put(id, new CharacterInfo(new CharacterRef(player.getUniqueId(), id), name, CharacterStatus.ALIVE, null,
        Set.of(), 0, Instant.ofEpochMilli(now.get())));
  }

  String offline(String name, CharacterStatus status, Instant lastPlayed) {
    UUID id = UUID.randomUUID();
    infos.put(id, new CharacterInfo(new CharacterRef(UUID.randomUUID(), id), name, status, null, Set.of(), 0,
        lastPlayed));
    return id.toString();
  }

  PlayerMock victim(String label) {
    PlayerMock victim = fx.player(label);
    victim.teleport(new Location(fx.world, 1.5, 10, 0.5));
    alive(victim, label, label);
    return victim;
  }

  void runRite() {
    now.addAndGet(11_000L);
    CharacterRiteService.tick();
  }

  @Test
  void bondsGrowFromTimeTogetherAndPersist() throws Exception {
    PlayerMock friend = victim("friend");
    PlayerMock far = victim("far");
    far.teleport(new Location(fx.world, 500, 10, 500));
    PlayerMock elsewhere = victim("elsewhere");
    elsewhere.teleport(new Location(fx.server.addSimpleWorld("other"), 0, 64, 0));
    fx.server.addPlayer();
    BondService.sample();
    String friendId = BloodFixture.id("friend");
    assertEquals(5, BondService.minutes(mageId, friendId));
    assertEquals(5, BondService.minutes(friendId, mageId));
    assertEquals(0, BondService.minutes(mageId, BloodFixture.id("far")));
    assertEquals(0, BondService.minutes(mageId, BloodFixture.id("elsewhere")));
    BondService.flush();
    BondService.unload(mageId);
    BondService.unload(mageId);
    assertEquals(5, BondService.minutes(mageId, friendId));
    BondService.add(mageId, friendId, 1);
    BondService.unload(mageId);
    assertEquals(6, BondService.minutes(mageId, friendId));
    BondService.load(mageId);

    Path file = temp.resolve("bonds-root/bonds/" + mageId + ".json");
    Files.writeString(file, "{\"" + friendId + "\": 7, \"x\": null, \"y\": 0}");
    BondService.unload(mageId);
    assertEquals(7, BondService.minutes(mageId, friendId));
    BondService.clear();
    BondService.add(mageId, friendId, 1);
    BondService.flush();
    assertEquals(1, BondService.minutes(mageId, friendId));
    BondService.unload(mageId);
    assertEquals(0, BondService.minutes(mageId, friendId));
    BondService.add(mageId, friendId, 2);
    BondService.unload(mageId);
    BondService.init(new BloodStore(temp.resolve("bonds-root").toFile(), fx.logger));
    Files.writeString(file, "{broken");
    assertEquals(0, BondService.minutes(mageId, friendId));
  }

  @Test
  void rankingWeighsTalksTimeAndQualifiedHeirsButNotOwnAccountsOrTheDead() {
    String talker = offline("Talker", CharacterStatus.ALIVE, Instant.EPOCH);
    String sitter = offline("Sitter", CharacterStatus.ALIVE, Instant.EPOCH);
    String heir = offline("Heir", CharacterStatus.ALIVE, Instant.EPOCH);
    String dead = offline("Dead", CharacterStatus.DEAD, Instant.EPOCH);
    UUID ownAlt = UUID.randomUUID();
    infos.put(ownAlt, new CharacterInfo(new CharacterRef(mage.getUniqueId(), ownAlt), "Alt", CharacterStatus.ALIVE,
        null, Set.of(), 0, Instant.EPOCH));
    talks.put(talker.toUpperCase(), 10);
    talks.put(dead, 50);
    talks.put(ownAlt.toString(), 50);
    talks.put("not-a-uuid", 50);
    talks.put(mageId, 99);
    BondService.add(mageId, sitter, 600);
    BondService.add(mageId, heir, 60);
    bridge.when(() -> RpCharactersBridge.willHeir(mage)).thenReturn(heir);
    List<BondService.Ranked> ranking = BondService.ranking(mage);
    assertEquals(List.of(talker, sitter, heir), ranking.stream().map(BondService.Ranked::character).toList());
    assertEquals(30, ranking.get(0).score());
    BondService.add(mageId, heir, 600);
    assertEquals(heir, BondService.ranking(mage).get(0).character());
    assertEquals(1, BondService.rank(mage, heir.toUpperCase()));
    assertEquals(0, BondService.rank(mage, dead));
    String quietHeir = offline("Quiet", CharacterStatus.ALIVE, Instant.EPOCH);
    bridge.when(() -> RpCharactersBridge.willHeir(mage)).thenReturn(quietHeir);
    talks.put(quietHeir, 25);
    assertEquals(quietHeir, BondService.ranking(mage).get(0).character());
    String silent = offline("Silent", CharacterStatus.ALIVE, Instant.EPOCH);
    talks.put(silent, 0);
    assertEquals(0, BondService.rank(mage, silent));
    PlayerMock nobody = fx.server.addPlayer();
    assertTrue(BondService.ranking(nobody).isEmpty());
    assertFalse(BondService.livingOtherAccount("bad", mage.getUniqueId()));
    assertFalse(BondService.livingOtherAccount(UUID.randomUUID().toString(), mage.getUniqueId()));
  }

  @Test
  void bindingTheVictimChecksEveryRule() throws Exception {
    PlayerMock victim = victim("victim");
    String victimId = BloodFixture.id("victim");
    PlayerMock stranger = fx.player("no-record");
    assertEquals(CharacterRiteService.Bind.NOT_STAGE, CharacterRiteService.bind(stranger, victim));
    record.setStage(BloodStage.ANIMAL);
    assertEquals(CharacterRiteService.Bind.NOT_STAGE, CharacterRiteService.bind(mage, victim));
    record.setStage(BloodStage.HUNT);
    mage.getInventory().setItemInMainHand(BloodItems.create(BloodItemKind.RITUAL_KNIFE, mageId));
    assertEquals(CharacterRiteService.Bind.NOT_KNIFE, CharacterRiteService.bind(mage, victim));
    mage.getInventory().setItemInMainHand(BloodItems.create(BloodItemKind.BLOOD_KNIFE, "x"));
    assertEquals(CharacterRiteService.Bind.NOT_KNIFE, CharacterRiteService.bind(mage, victim));
    mage.getInventory().setItemInMainHand(BloodItems.create(BloodItemKind.BLOOD_KNIFE, mageId));
    BloodAltar altar = BloodAltars.ofCharacter(mageId);
    BloodAltars.remove(altar);
    assertEquals(CharacterRiteService.Bind.NO_ALTAR, CharacterRiteService.bind(mage, victim));
    BloodAltars.add(altar);
    PlayerMock bare = fx.server.addPlayer();
    assertEquals(CharacterRiteService.Bind.NO_CHARACTER, CharacterRiteService.bind(mage, bare));
    bridge.when(() -> RpCharactersBridge.isDown(victim)).thenReturn(false);
    assertEquals(CharacterRiteService.Bind.NOT_DOWN, CharacterRiteService.bind(mage, victim));
    bridge.when(() -> RpCharactersBridge.isDown(victim)).thenReturn(true);
    victim.teleport(new Location(fx.world, 40, 10, 40));
    assertEquals(CharacterRiteService.Bind.TOO_FAR, CharacterRiteService.bind(mage, victim));
    victim.teleport(new Location(fx.server.addSimpleWorld("away"), 0, 10, 0));
    assertEquals(CharacterRiteService.Bind.TOO_FAR, CharacterRiteService.bind(mage, victim));
    victim.teleport(new Location(fx.world, 1.5, 10, 0.5));
    bridge.when(() -> RpCharactersBridge.playtimeSeconds(victim)).thenReturn(60L);
    assertEquals(CharacterRiteService.Bind.TOO_NEW, CharacterRiteService.bind(mage, victim));
    bridge.when(() -> RpCharactersBridge.playtimeSeconds(victim)).thenReturn(100L * 3600);
    bridge.when(() -> RpCharactersBridge.characterAgeSeconds(victim)).thenReturn(60L);
    assertEquals(CharacterRiteService.Bind.TOO_NEW, CharacterRiteService.bind(mage, victim));
    bridge.when(() -> RpCharactersBridge.characterAgeSeconds(victim)).thenReturn(100L * 86_400);
    record.setLastVictimAccount(victim.getUniqueId().toString());
    assertEquals(CharacterRiteService.Bind.REPEAT, CharacterRiteService.bind(mage, victim));
    record.setLastVictimAccount(null);
    talks.put(victimId, 5);
    assertEquals(CharacterRiteService.Bind.RESERVED, CharacterRiteService.bind(mage, victim));
    talks.clear();

    assertEquals(CharacterRiteService.Bind.OK, CharacterRiteService.bind(mage, victim));
    assertEquals("blood.rite.dread", victim.nextMessage());
    bridge.verify(() -> RpCharactersBridge.moveDowned(eq(victim), argThat(at -> at.getY() == 11.0)));
    assertTrue(CharacterRiteService.hasRite(mage.getUniqueId()));
    assertEquals(CharacterRiteService.Bind.BUSY, CharacterRiteService.bind(mage, victim));
    PlayerMock second = fx.player("second-mage");
    String secondId = BloodFixture.id("second-mage");
    BloodRecords.getOrCreate(secondId, "o").setStage(BloodStage.HUNT);
    BloodAltars.add(BloodAltar.create(secondId, "o", fx.pocket(2, 10, 0), 0f, 1L));
    second.getInventory().setItemInMainHand(BloodItems.create(BloodItemKind.BLOOD_KNIFE, secondId));
    assertEquals(CharacterRiteService.Bind.BUSY, CharacterRiteService.bind(second, victim));
    PlayerMock otherVictim = victim("other-victim");
    otherVictim.teleport(new Location(fx.world, 2.5, 10, 0.5));
    assertEquals(CharacterRiteService.Bind.OK, CharacterRiteService.bind(second, otherVictim));
    Files.writeString(temp.resolve("nr.yml"), "enabled: true\nhunt:\n  victims: { no_repeat_player: false }\n");
    BloodConfig.load(temp.resolve("nr.yml").toFile(), fx.logger);
    CharacterRiteService.clear();
    record.setLastVictimAccount(victim.getUniqueId().toString());
    victim.teleport(new Location(fx.world, 1.5, 10, 0.5));
    assertEquals(CharacterRiteService.Bind.OK, CharacterRiteService.bind(mage, victim));
    BloodConfig.load(new File("src/main/resources/bloodmagic.yml"), fx.logger);
    record.setLastVictimAccount(null);

    record.setStage(BloodStage.FINAL);
    CharacterRiteService.clear();
    record.setNextFinalAt(now.get() + 1000);
    assertEquals(CharacterRiteService.Bind.WAITING, CharacterRiteService.bind(mage, victim));
    record.setNextFinalAt(0);
    assertEquals(CharacterRiteService.Bind.NOT_TARGET, CharacterRiteService.bind(mage, victim));
    record.getFinalTargets().add(victimId);
    assertEquals(CharacterRiteService.Bind.OK, CharacterRiteService.bind(mage, victim));
  }

  @Test
  void huntSacrificesAddPointsAndTheSeventhPointOpensTheFinals() throws Exception {
    Files.writeString(temp.resolve("p.yml"), "enabled: true\nhunt:\n  points_required: 2\n  points: { stranger: 1 }\nfinals:\n  count: 2\nclasses:\n  'true': sanguinar\n");
    BloodConfig.load(temp.resolve("p.yml").toFile(), fx.logger);
    String close = offline("Close", CharacterStatus.ALIVE, Instant.ofEpochMilli(now.get()));
    String closer = offline("Closer", CharacterStatus.ALIVE, Instant.ofEpochMilli(now.get()));
    talks.put(close, 3);
    talks.put(closer, 9);
    PlayerMock first = victim("first");
    assertEquals(CharacterRiteService.Bind.OK, CharacterRiteService.bind(mage, first));
    CharacterRiteService.tick();
    assertTrue(CharacterRiteService.hasRite(mage.getUniqueId()));
    runRite();
    assertEquals(1, record.getHuntPoints());
    assertEquals("blood.rite.taken", mage.nextMessage());
    assertEquals(first.getUniqueId().toString(), record.getLastVictimAccount());
    assertEquals(1, record.getSacrifices().size());
    assertEquals(CharacterRiteService.Bind.REPEAT, CharacterRiteService.bind(mage, first));

    PlayerMock secondVictim = victim("second");
    talks.put(BloodFixture.id("second"), 1);
    assertEquals(CharacterRiteService.Bind.OK, CharacterRiteService.bind(mage, secondVictim));
    runRite();
    assertEquals(BloodStage.FINAL, record.getStage());
    assertEquals("sanguinar", ChoiceTest.FakeUnlocks.last);
    assertEquals(List.of(close, closer), record.getFinalTargets());
    assertEquals("blood.hunt.complete", mage.nextMessage());
    assertNotNull(mage.nextMessage());
  }

  @Test
  void finalsTakeTheNamedTargetThenTheNextUntilAscension() throws Exception {
    Files.writeString(temp.resolve("f.yml"), "enabled: true\nfinals:\n  count: 2\n");
    BloodConfig.load(temp.resolve("f.yml").toFile(), fx.logger);
    PlayerMock lesser = victim("lesser");
    PlayerMock dearest = victim("dearest");
    String lesserId = BloodFixture.id("lesser");
    String dearestId = BloodFixture.id("dearest");
    talks.put(lesserId, 2);
    talks.put(dearestId, 20);
    record.setPath(BloodPath.DEVOTEE);
    FinalService.begin(mage, record);
    assertNull(ChoiceTest.FakeUnlocks.last);
    assertEquals(List.of(lesserId, dearestId), record.getFinalTargets());
    assertNotNull(mage.nextMessage());

    assertEquals(CharacterRiteService.Bind.NOT_TARGET, CharacterRiteService.bind(mage, dearest));
    assertEquals(CharacterRiteService.Bind.OK, CharacterRiteService.bind(mage, lesser));
    runRite();
    assertEquals(1, record.getFinalsDone());
    assertEquals(List.of(dearestId), record.getFinalTargets());
    assertEquals("blood.final.taken", mage.nextMessage());
    assertNotNull(mage.nextMessage());
    assertEquals(CharacterRiteService.Bind.OK, CharacterRiteService.bind(mage, dearest));
    runRite();
    assertEquals(BloodStage.ASCENDED, record.getStage());
    assertEquals("blood.final.ascended", mage.nextMessage());
    assertTrue(record.getFinalTargets().isEmpty());
  }

  @Test
  void ritesBreakWhenTheMageLeavesOrAnyoneSwitchesAndRefusedDeathsCountNothing() {
    PlayerMock victim = victim("victim");
    CharacterRiteService.bind(mage, victim);
    mage.teleport(new Location(fx.world, 60, 10, 60));
    CharacterRiteService.tick();
    assertFalse(CharacterRiteService.hasRite(mage.getUniqueId()));
    assertEquals("blood.rite.broken", mage.nextMessage());
    mage.teleport(new Location(fx.world, 0.5, 11, 0.5));

    victim.teleport(new Location(fx.world, 1.5, 10, 0.5));
    CharacterRiteService.bind(mage, victim);
    fx.characters.start(victim, "victim-alt");
    CharacterRiteService.tick();
    assertFalse(CharacterRiteService.hasRite(mage.getUniqueId()));
    fx.characters.start(victim, "victim");

    victim.teleport(new Location(fx.world, 1.5, 10, 0.5));
    CharacterRiteService.bind(mage, victim);
    fx.characters.start(mage, "mage-alt");
    CharacterRiteService.tick();
    assertFalse(CharacterRiteService.hasRite(mage.getUniqueId()));
    fx.characters.start(mage, "mage");

    victim.teleport(new Location(fx.world, 1.5, 10, 0.5));
    CharacterRiteService.bind(mage, victim);
    BloodAltar altar = BloodAltars.ofCharacter(mageId);
    BloodAltars.remove(altar);
    CharacterRiteService.tick();
    assertFalse(CharacterRiteService.hasRite(mage.getUniqueId()));
    BloodAltars.add(altar);

    victim.teleport(new Location(fx.world, 1.5, 10, 0.5));
    CharacterRiteService.bind(mage, victim);
    mage.teleport(new Location(fx.server.addSimpleWorld("void"), 0, 10, 0));
    CharacterRiteService.tick();
    assertFalse(CharacterRiteService.hasRite(mage.getUniqueId()));
    mage.teleport(new Location(fx.world, 0.5, 11, 0.5));

    victim.teleport(new Location(fx.world, 1.5, 10, 0.5));
    CharacterRiteService.bind(mage, victim);
    victim.disconnect();
    CharacterRiteService.tick();
    assertFalse(CharacterRiteService.hasRite(mage.getUniqueId()));

    PlayerMock other = victim("other");
    CharacterRiteService.bind(mage, other);
    mage.disconnect();
    CharacterRiteService.tick();

    PlayerMock again = fx.player("mage");
    again.getInventory().setItemInMainHand(BloodItems.create(BloodItemKind.BLOOD_KNIFE, mageId));
    other.teleport(new Location(fx.world, 1.5, 10, 0.5));
    bridge.when(() -> RpCharactersBridge.sacrifice(any(), any())).thenReturn(false);
    assertEquals(CharacterRiteService.Bind.OK, CharacterRiteService.bind(again, other));
    runRite();
    assertEquals("blood.rite.refused", again.nextMessage());
    assertEquals(0, record.getHuntPoints());

    bridge.when(() -> RpCharactersBridge.sacrifice(any(), any())).thenAnswer(i -> {
      assertTrue(CharacterRiteService.isBeingSacrificed(BloodFixture.id("other")));
      new BloodListener().onCharacterDeath(new net.tfminecraft.tlibs.character.CharacterDeathEvent(
          new CharacterRef(other.getUniqueId(), UUID.fromString(BloodFixture.id("other"))), UUID.randomUUID(),
          Instant.EPOCH, null, null, "SACRIFICE"));
      return true;
    });
    other.teleport(new Location(fx.world, 1.5, 10, 0.5));
    CharacterRiteService.bind(again, other);
    BloodRecords.delete(mageId);
    runRite();
    bridge.when(() -> RpCharactersBridge.sacrifice(any(), any())).thenReturn(true);
    assertFalse(CharacterRiteService.isBeingSacrificed(BloodFixture.id("other")));
    assertFalse(CharacterRiteService.isBeingSacrificed(null));

    BloodRecord fresh = fx.record("mage", BloodStage.FINAL);
    fresh.getFinalTargets().add(BloodFixture.id("other"));
    PlayerMock third = victim("third");
    CharacterRiteService.bind(again, third);
    fresh.getFinalTargets().add(0, BloodFixture.id("third"));
    assertEquals(CharacterRiteService.Bind.OK, CharacterRiteService.bind(again, third));
    fresh.getFinalTargets().remove(0);
    runRite();
    assertEquals(0, fresh.getFinalsDone());
    assertEquals(1, fresh.getSacrifices().size());
    PlayerMock fourth = victim("fourth");
    fresh.getFinalTargets().add(0, BloodFixture.id("fourth"));
    fresh.setLastVictimAccount(null);
    assertEquals(CharacterRiteService.Bind.OK, CharacterRiteService.bind(again, fourth));
    fresh.setStage(BloodStage.ASCENDED);
    runRite();
    assertEquals(2, fresh.getSacrifices().size());
  }

  @Test
  void targetsDyingElsewhereFailTheSacrificeAndLaterOnesJustLeave() {
    String a = offline("A", CharacterStatus.ALIVE, Instant.ofEpochMilli(now.get()));
    String b = offline("B", CharacterStatus.ALIVE, Instant.ofEpochMilli(now.get()));
    record.setStage(BloodStage.FINAL);
    record.getFinalTargets().addAll(List.of(a, b));
    BloodRecord other = BloodRecords.getOrCreate(UUID.randomUUID().toString(), UUID.randomUUID().toString());
    other.setStage(BloodStage.HUNT);
    FinalService.onDeath(b);
    assertEquals(List.of(a), record.getFinalTargets());
    assertFalse(record.isPendingBacklash());
    FinalService.onDeath(a.toUpperCase());
    assertTrue(record.getFinalTargets().isEmpty());
    assertEquals(now.get() + 72 * 3_600_000L, record.getNextFinalAt());
    bridge.verify(() -> RpCharactersBridge.applyHealingInjury(mage));
    assertFalse(record.isPendingBacklash());
    assertNotNull(mage.nextMessage());
    FinalService.onDeath(UUID.randomUUID().toString());

    BloodRecord offlineMage = BloodRecords.getOrCreate(UUID.randomUUID().toString(), UUID.randomUUID().toString());
    offlineMage.setStage(BloodStage.FINAL);
    offlineMage.getFinalTargets().add(a);
    FinalService.onDeath(a);
    assertTrue(offlineMage.isPendingBacklash());
    BloodRecord ownerless = BloodRecords.getOrCreate(UUID.randomUUID().toString(), null);
    ownerless.setStage(BloodStage.FINAL);
    ownerless.getFinalTargets().add(b);
    FinalService.onDeath(b);
    assertTrue(ownerless.isPendingBacklash());
    BloodRecord switched = BloodRecords.getOrCreate(UUID.randomUUID().toString(), mage.getUniqueId().toString());
    switched.setStage(BloodStage.FINAL);
    switched.getFinalTargets().add(b);
    FinalService.onDeath(b);
    assertTrue(switched.isPendingBacklash());
  }

  @Test
  void checksCatchMissedDeathsAbsenceWaitsAndRefill() throws Exception {
    Files.writeString(temp.resolve("c.yml"), "enabled: true\nfinals:\n  count: 2\n  fail_cooldown_hours: 1\n  inactive_days: 1\n");
    BloodConfig.load(temp.resolve("c.yml").toFile(), fx.logger);
    PlayerMock nobody = fx.server.addPlayer();
    FinalService.check(nobody);
    FinalService.check(mage);
    record.setStage(BloodStage.FINAL);
    record.setPendingBacklash(true);
    String dead = offline("Dead", CharacterStatus.DEAD, Instant.ofEpochMilli(now.get()));
    String gone = UUID.randomUUID().toString();
    String absent = offline("Absent", CharacterStatus.ALIVE, Instant.ofEpochMilli(now.get() - 2 * 86_400_000L));
    String unseen = offline("Unseen", CharacterStatus.ALIVE, null);
    record.getFinalTargets().addAll(List.of(dead, gone, absent, unseen, "not-a-uuid"));
    FinalService.check(mage);
    bridge.verify(() -> RpCharactersBridge.applyHealingInjury(mage), times(2));
    assertEquals(List.of(unseen), record.getFinalTargets());
    assertEquals(now.get() + 3_600_000L, record.getNextFinalAt());

    String fresh = offline("Fresh", CharacterStatus.ALIVE, Instant.ofEpochMilli(now.get()));
    talks.put(fresh, 4);
    FinalService.check(mage);
    assertEquals(List.of(unseen), record.getFinalTargets());
    now.addAndGet(2 * 3_600_000L);
    FinalService.check(mage);
    assertEquals(2, record.getFinalTargets().size());
    FinalService.check(mage);

    record.getFinalTargets().clear();
    record.getFinalTargets().add(gone);
    FinalService.check(mage);
    assertEquals(List.of(fresh), record.getFinalTargets());
    assertEquals(fresh, record.currentTarget());
  }

  @Test
  void staffCanRerollSetAndSkipTargets() {
    String a = offline("A", CharacterStatus.ALIVE, Instant.ofEpochMilli(now.get()));
    String b = offline("B", CharacterStatus.ALIVE, Instant.ofEpochMilli(now.get()));
    talks.put(a, 2);
    talks.put(b, 1);
    record.setStage(BloodStage.FINAL);
    record.getFinalTargets().add(a);
    FinalService.reroll(mage, record);
    assertEquals(List.of(b), record.getFinalTargets());
    record.getFinalTargets().clear();
    FinalService.reroll(mage, record);
    assertEquals(List.of(b, a), record.getFinalTargets());
    FinalService.set(mage, record, a.toUpperCase());
    assertEquals(List.of(a, b), record.getFinalTargets());
    FinalService.skip(mage, record);
    assertEquals(1, record.getFinalsDone());
    assertEquals(List.of(b), record.getFinalTargets());
    record.getFinalTargets().clear();
    talks.clear();
    FinalService.skip(mage, record);
    assertEquals(2, record.getFinalsDone());
    FinalService.name(mage, record);
    record.setNextFinalAt(0);
    FinalService.check(mage);
    assertNull(record.currentTarget());
    assertEquals("?", FinalService.nameOf("bad"));
    assertEquals("?", FinalService.nameOf(UUID.randomUUID().toString()));
    assertEquals("A", FinalService.nameOf(a));
    FinalService.backlash(mage, record);
  }

  @Test
  void bridgeHelpersReadTheActiveCharacter() {
    bridge.close();
    try (var rp = mockStatic(net.tfminecraft.rpcharacters.RPCharacters.class);
        var permadeath = mockStatic(net.tfminecraft.rpcharacters.permadeath.PermadeathService.class)) {
      assertEquals(Map.of(), RpCharactersBridge.conversationCounts(mage));
      assertNull(RpCharactersBridge.willHeir(mage));
      assertEquals(0, RpCharactersBridge.characterAgeSeconds(mage));
      assertEquals(0, RpCharactersBridge.playtimeSeconds(mage));
      assertFalse(RpCharactersBridge.sacrifice(mage, mage));
      assertFalse(RpCharactersBridge.applyHealingInjury(mage));
      assertFalse(RpCharactersBridge.isDown(mage));
      var knockouts = mock(net.tfminecraft.rpcharacters.pvp.PvpKnockoutManager.class);
      rp.when(net.tfminecraft.rpcharacters.RPCharacters::getPvpKnockoutManager).thenReturn(knockouts);
      when(knockouts.isDown(mage)).thenReturn(true);
      assertTrue(RpCharactersBridge.isDown(mage));
      var character = mock(net.tfminecraft.rpcharacters.objects.RPCharacter.class);
      when(character.getId()).thenReturn(mageId);
      when(character.getConversationCounts()).thenReturn(Map.of("x", 2));
      when(character.getWill()).thenReturn(net.tfminecraft.rpcharacters.will.CharacterWill.NONE);
      when(character.getOnlinePlaytimeSeconds()).thenReturn(50);
      rp.when(() -> net.tfminecraft.rpcharacters.RPCharacters.getActiveCharacter(mage)).thenReturn(character);
      rp.when(() -> net.tfminecraft.rpcharacters.RPCharacters.getCharacterAgeSeconds(character)).thenReturn(70);
      assertEquals(Map.of("x", 2), RpCharactersBridge.conversationCounts(mage));
      assertNull(RpCharactersBridge.willHeir(mage));
      UUID heir = UUID.randomUUID();
      when(character.getWill()).thenReturn(net.tfminecraft.rpcharacters.will.CharacterWill.NONE.change(heir, 1L, "r"));
      assertEquals(heir.toString(), RpCharactersBridge.willHeir(mage));
      assertEquals(70, RpCharactersBridge.characterAgeSeconds(mage));
      assertEquals(50, RpCharactersBridge.playtimeSeconds(mage));
      permadeath.when(() -> net.tfminecraft.rpcharacters.permadeath.PermadeathService.killCharacter(mage, character,
          net.tfminecraft.rpcharacters.permadeath.PermakillCause.SACRIFICE, mage)).thenReturn(true);
      assertTrue(RpCharactersBridge.sacrifice(mage, mage));
      permadeath.when(() -> net.tfminecraft.rpcharacters.permadeath.PermadeathService.applyRandomInjury(mage, character))
          .thenReturn(true);
      assertTrue(RpCharactersBridge.applyHealingInjury(mage));
    }
    bridge = mockStatic(RpCharactersBridge.class);
  }
}
