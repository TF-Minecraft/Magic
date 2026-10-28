package net.tfminecraft.magic.blood;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.io.File;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import io.lumine.mythic.lib.api.player.MMOPlayerData;
import io.lumine.mythic.lib.api.stat.StatInstance;
import io.lumine.mythic.lib.api.stat.StatMap;
import io.lumine.mythic.lib.api.stat.modifier.StatModifier;
import net.tfminecraft.magic.integration.RpCharactersBridge;
import net.tfminecraft.tlibs.character.CharacterInfo;
import net.tfminecraft.tlibs.character.CharacterRef;
import net.tfminecraft.tlibs.character.CharacterSession;
import net.tfminecraft.tlibs.character.CharacterSessionEndEvent;
import net.tfminecraft.tlibs.character.CharacterStatus;
import net.tfminecraft.tlibs.character.Characters;
import net.tfminecraft.tlibs.character.CharacterDeathEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.command.ConsoleCommandSenderMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import org.mockito.MockedConstruction;
import org.mockito.MockedStatic;

class BoonsAndToolsTest {
  @TempDir Path temp;
  BloodFixture fx;
  PlayerMock mage;
  String mageId;
  BloodRecord record;

  @BeforeEach
  void setup() {
    fx = new BloodFixture(temp);
    BloodConfig.load(new File("src/main/resources/wildmagic/bloodmagic"), fx.logger);
    BondService.init(new BloodStore(temp.resolve("b").toFile(), fx.logger));
    mage = fx.server.addPlayer("Mage");
    fx.characters.start(mage, "mage");
    mageId = BloodFixture.id("mage");
    record = BloodRecords.getOrCreate(mageId, mage.getUniqueId().toString());
    record.setStage(BloodStage.FINAL);
    record.setPath(BloodPath.TRUE);
  }

  @AfterEach
  void cleanup() {
    BondService.clear();
    CharacterRiteService.clear();
    fx.close();
  }

  @Test
  void boonsNeedMythicLibAndReplaceEachOther() {
    record.setFinalsDone(4); // health and spell damage: two stats
    BloodBoons.apply(mage);
    BloodBoons.clear(mage);
    MockBukkit.createMockPlugin("MythicLib");
    try (MockedStatic<MMOPlayerData> players = mockStatic(MMOPlayerData.class);
        MockedConstruction<StatModifier> modifiers = mockConstruction(StatModifier.class)) {
      BloodBoons.apply(mage);
      assertTrue(modifiers.constructed().isEmpty());
      MMOPlayerData data = mock(MMOPlayerData.class);
      StatMap map = mock(StatMap.class);
      StatInstance instance = mock(StatInstance.class);
      when(data.getStatMap()).thenReturn(map);
      when(map.getInstances()).thenReturn(List.of(instance));
      players.when(() -> MMOPlayerData.getOrNull(mage)).thenReturn(data);
      BloodBoons.apply(mage);
      assertEquals(2, modifiers.constructed().size());
      verify(modifiers.constructed().get(0)).register(data);
      BloodBoons.apply(mage);
      verify(instance, times(2)).remove(MythicBoons.KEY);
      BloodBoons.clear(mage);
      BloodBoons.clear(mage);
      record.setFinalsDone(0);
      BloodBoons.apply(mage);
      assertEquals(4, modifiers.constructed().size());
      PlayerMock bare = fx.server.addPlayer();
      BloodBoons.apply(bare);
      record.setFinalsDone(1);
      BloodBoons.apply(mage);
      players.when(() -> MMOPlayerData.getOrNull(mage)).thenReturn(null);
      BloodBoons.clear(mage);
      BloodBoons.apply(mage);
    }
  }

  @Test
  void theDevoteesMarkTakesAllManaWhileTheCharacterBearsIt() throws Exception {
    MockBukkit.createMockPlugin("MythicLib");
    List<List<?>> made = new java.util.ArrayList<>();
    try (MockedStatic<MMOPlayerData> players = mockStatic(MMOPlayerData.class);
        MockedConstruction<StatModifier> modifiers = mockConstruction(StatModifier.class,
            (modifier, context) -> made.add(context.arguments()));
        MockedStatic<RpCharactersBridge> bridge = mockStatic(RpCharactersBridge.class)) {
      record.setFinalsDone(0);
      bridge.when(() -> RpCharactersBridge.hasTrait(mage, "manaless")).thenReturn(true);
      BloodBoons.apply(mage);
      assertTrue(made.isEmpty(), "No MythicLib data yet");
      MMOPlayerData data = mock(MMOPlayerData.class);
      StatMap map = mock(StatMap.class);
      when(data.getStatMap()).thenReturn(map);
      when(map.getInstances()).thenReturn(List.of());
      players.when(() -> MMOPlayerData.getOrNull(mage)).thenReturn(data);
      BloodBoons.apply(mage);
      assertEquals(List.of(List.of(MythicBoons.KEY, "MAX_MANA", -100.0,
          io.lumine.mythic.lib.player.modifier.ModifierType.RELATIVE)), made);
      verify(modifiers.constructed().get(0)).register(data);
      java.nio.file.Files.writeString(temp.resolve("off.yml"), "enabled: true\nchoice:\n  devotee_trait: ''\n");
      BloodConfig.load(temp.resolve("off.yml").toFile(), fx.logger);
      BloodBoons.apply(mage);
      assertEquals(1, made.size(), "No mark configured: mana stays");
    }
  }

  @Test
  void staffTargetAndBondTools() {
    ConsoleCommandSenderMock console = (ConsoleCommandSenderMock) fx.server.getConsoleSender();
    UUID friend = UUID.randomUUID();
    CharacterInfo friendInfo = new CharacterInfo(new CharacterRef(UUID.randomUUID(), friend), "Friend",
        CharacterStatus.ALIVE, null, Set.of(), 0, Instant.now());
    CharacterInfo deadInfo = new CharacterInfo(new CharacterRef(UUID.randomUUID(), UUID.randomUUID()), "Friend",
        CharacterStatus.DEAD, null, Set.of(), 0, Instant.now());
    try (MockedStatic<Characters> chars = mockStatic(Characters.class, CALLS_REAL_METHODS);
        MockedStatic<RpCharactersBridge> bridge = mockStatic(RpCharactersBridge.class)) {
      chars.when(() -> Characters.info(friend)).thenReturn(Optional.of(friendInfo));
      chars.when(() -> Characters.findByName("Friend")).thenReturn(List.of(deadInfo, friendInfo));
      chars.when(() -> Characters.findByName("Nobody")).thenReturn(List.of(deadInfo));
      bridge.when(() -> RpCharactersBridge.conversationCounts(any())).thenReturn(Map.of(friend.toString(), 4));

      assertTrue(BloodCommand.execute(console, new String[] {"blood", "target", "Mage"}));
      assertEquals("blood.admin.usage", console.nextMessage());
      BloodCommand.execute(console, new String[] {"blood", "target", "Mage", "dance"});
      assertEquals("blood.admin.usage", console.nextMessage());
      BloodCommand.execute(console, new String[] {"blood", "target", "Mage", "set"});
      assertEquals("blood.admin.usage", console.nextMessage());
      BloodCommand.execute(console, new String[] {"blood", "target", "Mage", "list"});
      assertEquals("blood.admin.targets", console.nextMessage());
      BloodCommand.execute(console, new String[] {"blood", "target", "Mage", "reroll"});
      assertEquals("blood.admin.targets", console.nextMessage());
      assertEquals(List.of(friend.toString()), record.getFinalTargets());
      BloodCommand.execute(console, new String[] {"blood", "target", "Mage", "set", "Nobody"});
      assertEquals("blood.admin.no_character_named", console.nextMessage());
      BloodCommand.execute(console, new String[] {"blood", "target", "Mage", "set", "Friend"});
      assertEquals("blood.admin.targets", console.nextMessage());
      BloodCommand.execute(console, new String[] {"blood", "target", "Mage", "skip"});
      assertEquals("blood.admin.targets", console.nextMessage());
      assertEquals(1, record.getFinalsDone());
      record.setStage(BloodStage.HUNT);
      BloodCommand.execute(console, new String[] {"blood", "target", "Mage", "list"});
      assertEquals("blood.admin.not_final", console.nextMessage());
      BloodRecords.delete(mageId);
      BloodCommand.execute(console, new String[] {"blood", "target", "Mage", "list"});
      assertEquals("blood.admin.not_final", console.nextMessage());

      BloodCommand.execute(console, new String[] {"blood", "bonds", "Mage"});
      assertEquals("blood.admin.bonds_header", console.nextMessage());
      assertEquals("blood.admin.bonds_line", console.nextMessage());
    }
    assertEquals(List.of("list", "reroll", "set", "skip"),
        BloodCommand.complete(new String[] {"blood", "target", "Mage", ""}));
  }

  @Test
  void listenerRoutesBloodKnifeClicksSessionsAndDeaths() {
    BloodListener listener = new BloodListener();
    PlayerMock victim = fx.server.addPlayer();
    fx.characters.start(victim, "victim");
    mage.getInventory().setItemInMainHand(BloodItems.create(BloodItemKind.BLOOD_KNIFE, mageId));
    PlayerInteractEntityEvent click = new PlayerInteractEntityEvent(mage, victim, EquipmentSlot.HAND);
    listener.onInteractEntity(click);
    assertTrue(click.isCancelled());
    assertEquals("blood.rite.bind.no_altar", mage.nextMessage());
    PlayerInteractEntityEvent offHand = new PlayerInteractEntityEvent(mage, victim, EquipmentSlot.OFF_HAND);
    listener.onInteractEntity(offHand);
    assertFalse(offHand.isCancelled());

    String target = BloodFixture.id("victim");
    record.getFinalTargets().add(target);
    try (MockedStatic<RpCharactersBridge> bridge = mockStatic(RpCharactersBridge.class)) {
      listener.onCharacterDeath(new CharacterDeathEvent(new CharacterRef(victim.getUniqueId(), UUID.fromString(target)),
          UUID.randomUUID(), Instant.EPOCH, null, null, "x"));
      assertTrue(record.getFinalTargets().isEmpty());
      bridge.verify(() -> RpCharactersBridge.applyHealingInjury(mage));
    }

    BondService.add(mageId, target, 5);
    CharacterSession session = new CharacterSession(new CharacterRef(mage.getUniqueId(), UUID.fromString(mageId)), 7);
    listener.onSessionEnd(new CharacterSessionEndEvent(mage, session, CharacterSessionEndEvent.Reason.SWITCH));
    assertEquals(5, BondService.minutes(mageId, target));
  }

  @Test
  void magicTicksSampleFlushAndCheckFinals() {
    PlayerMock friend = fx.server.addPlayer();
    fx.characters.start(friend, "friend");
    friend.teleport(mage.getLocation());
    var original = WhisperService.equilibrium;
    WhisperService.equilibrium = p -> Double.NaN;
    try {
      BloodMagic.tick(net.tfminecraft.magic.tick.MagicTickContext.between(599, 600));
    } finally {
      WhisperService.equilibrium = original;
    }
    assertEquals(5, BondService.minutes(mageId, BloodFixture.id("friend")));
  }
}
