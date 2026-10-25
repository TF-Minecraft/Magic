package net.tfminecraft.magic.blood;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import net.tfminecraft.magic.integration.RpCharactersBridge;
import net.tfminecraft.magic.tick.MagicTickContext;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import org.mockito.MockedStatic;

/** The fixes from the first review round: rite paths, quits, carry safety, protections and the off switch. */
class ReviewFixesTest {
  @TempDir Path temp;
  BloodFixture fx;
  MockedStatic<RpCharactersBridge> bridge;
  final AtomicLong now = new AtomicLong(1_000_000_000_000L);
  PlayerMock mage;
  PlayerMock victim;
  String mageId;
  BloodRecord record;

  @BeforeEach
  void setup() {
    fx = new BloodFixture(temp);
    BloodConfig.load(new File("src/main/resources/bloodmagic.yml"), fx.logger);
    BondService.init(new BloodStore(temp.resolve("bonds").toFile(), fx.logger));
    CharacterRiteService.clock = now::get;
    bridge = mockStatic(RpCharactersBridge.class);
    bridge.when(() -> RpCharactersBridge.isDown(any())).thenAnswer(i -> i.getArgument(0) != mage);
    bridge.when(() -> RpCharactersBridge.playtimeSeconds(any())).thenReturn(100L * 3600);
    bridge.when(() -> RpCharactersBridge.characterAgeSeconds(any())).thenReturn(100L * 86_400);
    bridge.when(() -> RpCharactersBridge.conversationCounts(any())).thenAnswer(i -> new HashMap<>());
    bridge.when(() -> RpCharactersBridge.sacrifice(any(), any())).thenReturn(true);
    bridge.when(() -> RpCharactersBridge.moveDowned(any(), any())).thenAnswer(i -> {
      Player body = i.getArgument(0);
      return body.teleport((Location) i.getArgument(1));
    });
    fx.open(-10, 10, -3, 20, 12, 4);
    mage = fx.player("mage");
    mageId = BloodFixture.id("mage");
    record = BloodRecords.getOrCreate(mageId, mage.getUniqueId().toString());
    record.setStage(BloodStage.HUNT);
    record.setPath(BloodPath.TRUE);
    BloodAltars.add(BloodAltar.create(mageId, mage.getUniqueId().toString(), fx.pocket(0, 10, 0), 0f, 1L));
    mage.getInventory().setItemInMainHand(BloodItems.create(BloodItemKind.BLOOD_KNIFE, mageId));
    mage.teleport(new Location(fx.world, 2.5, 10, 0.5));
    victim = fx.player("victim");
    victim.teleport(new Location(fx.world, 1.5, 10, 0.5));
  }

  @AfterEach
  void cleanup() {
    CarryService.clear();
    CharacterRiteService.clear();
    BondService.clear();
    CharacterRiteService.clock = System::currentTimeMillis;
    bridge.close();
    fx.close();
  }

  @Test
  void theRiteNeedsAClearLineAndAFreeBody() {
    victim.teleport(new Location(fx.world, 3.5, 10, 0.5));
    fx.world.getBlockAt(2, 10, 0).setType(Material.OAK_STAIRS);
    assertTrue(CharacterRiteService.clearPath(victim.getLocation(), BloodAltars.ofCharacter(mageId).center()));
    fx.world.getBlockAt(2, 11, 0).setType(Material.STONE);
    assertEquals(CharacterRiteService.Bind.BLOCKED, CharacterRiteService.bind(mage, victim));
    fx.world.getBlockAt(2, 11, 0).setType(Material.AIR);
    // A window: a full wall at floor level with only the layer above open is still a wall.
    fx.world.getBlockAt(2, 10, 0).setType(Material.STONE);
    assertEquals(CharacterRiteService.Bind.BLOCKED, CharacterRiteService.bind(mage, victim));
    fx.world.getBlockAt(2, 10, 0).setType(Material.OAK_SLAB);
    assertTrue(CharacterRiteService.clearPath(victim.getLocation(), BloodAltars.ofCharacter(mageId).center()));
    fx.world.getBlockAt(2, 10, 0).setType(Material.AIR);
    // A body lying a step below the altar: the line clips the altar's own block, which never counts.
    fx.world.getBlockAt(2, 9, 0).setType(Material.AIR);
    assertTrue(CharacterRiteService.clearPath(new Location(fx.world, 2.5, 9, 0.5), BloodAltars.ofCharacter(mageId).center()));
    bridge.when(() -> RpCharactersBridge.moveDowned(any(), any())).thenReturn(false);
    assertEquals(CharacterRiteService.Bind.BLOCKED, CharacterRiteService.bind(mage, victim));
    assertFalse(CharacterRiteService.hasRite(mage.getUniqueId()));
    bridge.when(() -> RpCharactersBridge.moveDowned(any(), any())).thenAnswer(i -> {
      Player body = i.getArgument(0);
      return body.teleport((Location) i.getArgument(1));
    });

    PlayerMock rival = fx.player("rival");
    String rivalId = BloodFixture.id("rival");
    BloodRecords.getOrCreate(rivalId, rival.getUniqueId().toString()).setStage(BloodStage.HUNT);
    rival.getInventory().setItemInMainHand(BloodItems.create(BloodItemKind.BLOOD_KNIFE, rivalId));
    rival.teleport(new Location(fx.world, 4.5, 10, 0.5));
    assertEquals(CharacterRiteService.Bind.OK, CarryService.start(rival, victim));
    assertEquals(CharacterRiteService.Bind.BUSY, CharacterRiteService.bind(mage, victim));
    CarryService.release(rival, false);
    assertEquals(CharacterRiteService.Bind.OK, CarryService.start(mage, victim));
    assertEquals(CharacterRiteService.Bind.OK, CharacterRiteService.bind(mage, victim));
  }

  @Test
  void aNamedFinalTargetIsNeverBlockedByTheRepeatRule() {
    record.setStage(BloodStage.FINAL);
    record.getFinalTargets().add(BloodFixture.id("victim"));
    record.setLastVictimAccount(victim.getUniqueId().toString());
    assertEquals(CharacterRiteService.Bind.OK, CharacterRiteService.bind(mage, victim));
  }

  @Test
  void loggingOutOnTheAltarDoesNotSaveTheVictim() {
    PlayerMock bystander = fx.player("bystander");
    assertEquals(CharacterRiteService.Bind.OK, CharacterRiteService.bind(mage, victim));
    CharacterRiteService.onQuit(bystander);
    assertTrue(CharacterRiteService.hasRite(mage.getUniqueId()));
    CharacterRiteService.onQuit(victim);
    assertFalse(CharacterRiteService.hasRite(mage.getUniqueId()));
    assertEquals(1, record.getSacrifices().size());
    bridge.verify(() -> RpCharactersBridge.sacrifice(victim, mage));

    PlayerMock second = fx.player("second");
    second.teleport(new Location(fx.world, 1.5, 10, 0.5));
    record.setLastVictimAccount(null);
    assertEquals(CharacterRiteService.Bind.OK, CharacterRiteService.bind(mage, second));
    CharacterRiteService.onQuit(mage);
    assertFalse(CharacterRiteService.hasRite(mage.getUniqueId()));

    PlayerMock third = fx.player("third");
    third.teleport(new Location(fx.world, 1.5, 10, 0.5));
    assertEquals(CharacterRiteService.Bind.OK, CharacterRiteService.bind(mage, third));
    mage.disconnect();
    CharacterRiteService.onQuit(third);
    assertEquals(1, record.getSacrifices().size());

    PlayerMock fled = fx.player("fled");
    fled.teleport(new Location(fx.world, 1.5, 10, 0.5));
    PlayerMock back = fx.player("mage");
    back.getInventory().setItemInMainHand(BloodItems.create(BloodItemKind.BLOOD_KNIFE, mageId));
    assertEquals(CharacterRiteService.Bind.OK, CharacterRiteService.bind(back, fled));
    fled.teleport(new Location(fx.world, 9.5, 10, 0.5));
    CharacterRiteService.onQuit(fled);
    assertEquals(1, record.getSacrifices().size());
    PlayerMock late = fx.player("late");
    late.teleport(new Location(fx.world, 1.5, 10, 0.5));
    assertEquals(CharacterRiteService.Bind.OK, CharacterRiteService.bind(back, late));
    BloodConfig.setEnabled(false);
    CharacterRiteService.onQuit(late);
    BloodConfig.setEnabled(true);
    assertEquals(1, record.getSacrifices().size());
    back.disconnect();

    PlayerMock again = fx.player("mage");
    again.getInventory().setItemInMainHand(BloodItems.create(BloodItemKind.BLOOD_KNIFE, mageId));
    PlayerMock fourth = fx.player("fourth");
    fourth.teleport(new Location(fx.world, 1.5, 10, 0.5));
    assertEquals(CharacterRiteService.Bind.OK, CharacterRiteService.bind(again, fourth));
    BloodAltars.remove(BloodAltars.ofCharacter(mageId));
    CharacterRiteService.onQuit(fourth);
    assertEquals(1, record.getSacrifices().size());
  }

  @Test
  void bodiesAreNeverLaidInLiquidFireOrWalls() {
    Block feet = fx.world.getBlockAt(5, 10, 0);
    Location spot = feet.getLocation().clone().add(0.5, 0, 0.5);
    assertTrue(CarryService.safe(spot));
    feet.setType(Material.STONE);
    assertFalse(CarryService.safe(spot));
    feet.setType(Material.WATER);
    assertFalse(CarryService.safe(spot));
    feet.setType(Material.AIR);
    feet.getRelative(org.bukkit.block.BlockFace.DOWN).setType(Material.LAVA);
    assertFalse(CarryService.safe(spot));
    feet.getRelative(org.bukkit.block.BlockFace.DOWN).setType(Material.MAGMA_BLOCK);
    assertFalse(CarryService.safe(spot));
    feet.getRelative(org.bukkit.block.BlockFace.DOWN).setType(Material.STONE);
    feet.setType(Material.STONE_SLAB);
    Location onSlab = spot.clone().add(0, 0.5, 0);
    assertTrue(CarryService.safe(onSlab));
    org.bukkit.block.data.type.Slab wet = (org.bukkit.block.data.type.Slab) Material.STONE_SLAB.createBlockData();
    wet.setWaterlogged(true);
    feet.setBlockData(wet);
    assertFalse(CarryService.safe(onSlab));
    feet.setType(Material.KELP_PLANT);
    assertFalse(CarryService.safe(spot));
    feet.setType(Material.AIR);

    fx.world.getBlockAt(7, 9, 0).setType(Material.LAVA);
    victim.teleport(new Location(fx.world, 6.5, 10, 0.5));
    mage.teleport(new Location(fx.world, 7.5, 10, 0.5));
    assertEquals(CharacterRiteService.Bind.OK, CarryService.start(mage, victim));
    CarryService.tick();
    mage.teleport(new Location(fx.world, 6.0, 10, 0.5));
    CarryService.tick();
    mage.teleport(new Location(fx.world, 5.5, 10, 0.5));
    CarryService.tick();
    assertEquals(6.5, victim.getLocation().getX(), 1e-9);
    mage.teleport(new Location(fx.world, 4.0, 10, 0.5));
    CarryService.tick();
    assertEquals(5.5, victim.getLocation().getX(), 1e-9);
  }

  @Test
  void protectionsCanRefuseTheAltar() {
    PlayerMock builder = fx.player("builder");
    String builderId = BloodFixture.id("builder");
    fx.record("builder", BloodStage.ANIMAL);
    var plugin = MockBukkit.createMockPlugin("Claims");
    Listener deny = new Listener() {
      @EventHandler public void place(BlockPlaceEvent event) { event.setCancelled(true); }
    };
    fx.server.getPluginManager().registerEvents(deny, plugin);
    assertEquals("blood.altar.place.region",
        AltarService.place(builder, BloodItems.create(BloodItemKind.ALTAR, builderId), fx.pocket(30, 10, 30)));
    org.bukkit.event.HandlerList.unregisterAll(deny);
    Listener noBuild = new Listener() {
      @EventHandler public void place(BlockPlaceEvent event) { event.setBuild(false); }
    };
    fx.server.getPluginManager().registerEvents(noBuild, plugin);
    assertEquals("blood.altar.place.region",
        AltarService.place(builder, BloodItems.create(BloodItemKind.ALTAR, builderId), fx.pocket(30, 10, 30)));
    org.bukkit.event.HandlerList.unregisterAll(noBuild);
  }

  @Test
  void corruptionStaysOutOfProtectedLand() {
    BloodAltar altar = BloodAltars.ofCharacter(mageId);
    AltarRegions.setQuery(location -> List.of("town"));
    assertEquals(0, AltarCorruption.spread(altar, 5));
    AltarRegions.setQuery(location -> List.of());
  }

  @Test
  void theOffSwitchStopsTheTickAndStopClearsBoons() throws Exception {
    Files.writeString(temp.resolve("off.yml"), "enabled: false\n");
    BloodConfig.load(temp.resolve("off.yml").toFile(), fx.logger);
    assertEquals(CharacterRiteService.Bind.NOT_STAGE, CharacterRiteService.bind(mage, victim));
    BloodConfig.setEnabled(true);
    assertEquals(CharacterRiteService.Bind.OK, CharacterRiteService.bind(mage, victim));
    assertEquals(CharacterRiteService.Bind.OK, CarryService.start(mage, fx.player("dragged")));
    BloodConfig.setEnabled(false);
    BloodMagic.tick(MagicTickContext.between(599, 600));
    assertFalse(CharacterRiteService.hasRite(mage.getUniqueId()));
    CarryService.tick();
    assertNull(CarryService.carried(mage));
    BloodMagic.stop();
    assertNull(BloodRecords.get(mageId));
  }
}
