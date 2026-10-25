package net.tfminecraft.magic.blood;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicLong;

import net.tfminecraft.magic.integration.RpCharactersBridge;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffectType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import org.mockito.MockedStatic;

class CarryTest {
  @TempDir Path temp;
  BloodFixture fx;
  MockedStatic<RpCharactersBridge> bridge;
  final AtomicLong now = new AtomicLong(1_000_000_000_000L);
  PlayerMock mage;
  PlayerMock victim;
  String mageId;
  BloodRecord record;
  Block altarBlock;

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
    bridge.when(() -> RpCharactersBridge.conversationCounts(any())).thenAnswer(i -> new java.util.HashMap<>());
    bridge.when(() -> RpCharactersBridge.moveDowned(any(), any())).thenAnswer(i -> {
      Player body = i.getArgument(0);
      return body.teleport((Location) i.getArgument(1));
    });
    mage = fx.player("mage");
    mageId = BloodFixture.id("mage");
    record = BloodRecords.getOrCreate(mageId, mage.getUniqueId().toString());
    record.setStage(BloodStage.HUNT);
    record.setPath(BloodPath.TRUE);
    altarBlock = fx.pocket(0, 10, 0);
    BloodAltars.add(BloodAltar.create(mageId, mage.getUniqueId().toString(), altarBlock, 0f, 1L));
    mage.getInventory().setItemInMainHand(BloodItems.create(BloodItemKind.BLOOD_KNIFE, mageId));
    mage.teleport(new Location(fx.world, 20.5, 11, 0.5));
    victim = fx.player("victim");
    victim.teleport(new Location(fx.world, 21.5, 11, 0.5));
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

  void walk(double dx) {
    mage.teleport(mage.getLocation().clone().add(dx, 0, 0));
    CarryService.tick();
  }

  @Test
  void takingHoldChecksTheMageAndTheBody() {
    PlayerMock nobody = fx.server.addPlayer();
    assertEquals(CharacterRiteService.Bind.NOT_STAGE, CarryService.start(nobody, victim));
    mage.getInventory().setItemInMainHand(new ItemStack(Material.STONE));
    assertEquals(CharacterRiteService.Bind.NOT_KNIFE, CarryService.start(mage, victim));
    mage.getInventory().setItemInMainHand(BloodItems.create(BloodItemKind.BLOOD_KNIFE, mageId));
    PlayerMock bare = fx.server.addPlayer();
    assertEquals(CharacterRiteService.Bind.NO_CHARACTER, CarryService.start(mage, bare));
    bridge.when(() -> RpCharactersBridge.isDown(victim)).thenReturn(false);
    assertEquals(CharacterRiteService.Bind.NOT_DOWN, CarryService.start(mage, victim));
    bridge.when(() -> RpCharactersBridge.isDown(victim)).thenReturn(true);

    assertNull(CarryService.carried(mage));
    assertEquals(CharacterRiteService.Bind.OK, CarryService.start(mage, victim));
    assertEquals("blood.carry.start", mage.nextMessage());
    assertEquals("blood.carry.dragged", victim.nextMessage());
    assertSame(victim, CarryService.carried(mage));
    assertEquals(mage.getUniqueId(), CarryService.carrier(victim.getUniqueId()));
    assertEquals(CharacterRiteService.Bind.BUSY, CarryService.start(mage, fx.player("other")));

    PlayerMock second = fx.player("second-mage");
    String secondId = BloodFixture.id("second-mage");
    BloodRecords.getOrCreate(secondId, "o").setStage(BloodStage.FINAL);
    second.getInventory().setItemInMainHand(BloodItems.create(BloodItemKind.BLOOD_KNIFE, secondId));
    assertEquals(CharacterRiteService.Bind.BUSY, CarryService.start(second, victim));

    CarryService.release(mage, true);
    assertEquals("blood.carry.released", mage.nextMessage());
    CarryService.release(mage, true);
    assertNull(mage.nextMessage());
    assertNull(CarryService.carrier(victim.getUniqueId()));

    PlayerMock onAltar = fx.player("on-altar");
    onAltar.teleport(new Location(fx.world, 1.5, 10, 0.5));
    assertEquals(CharacterRiteService.Bind.OK, CharacterRiteService.bind(mage, onAltar));
    assertEquals(CharacterRiteService.Bind.BUSY, CarryService.start(second, onAltar));

    assertEquals(CharacterRiteService.Bind.OK, CarryService.start(second, victim));
    assertNull(CarryService.carrier(onAltar.getUniqueId()));
    victim.disconnect();
    assertNull(CarryService.carried(second));
    second.nextMessage();
    CarryService.tick();
    assertEquals("blood.carry.broken", second.nextMessage());
    assertNull(CarryService.carrier(victim.getUniqueId()));
  }

  @Test
  void theBodyTrailsBehindAlongThePathWalked() {
    assertEquals(CharacterRiteService.Bind.OK, CarryService.start(mage, victim));
    CarryService.tick();
    assertEquals(21.5, victim.getLocation().getX(), 1e-9);
    assertTrue(mage.hasPotionEffect(PotionEffectType.SLOWNESS));
    walk(-0.1);
    walk(-0.5);
    walk(-0.5);
    walk(-0.5);
    assertEquals(20.5, victim.getLocation().getX(), 1e-9);
    CarryService.tick();
    assertEquals(20.5, victim.getLocation().getX(), 1e-9);
    for (int i = 0; i < 50; i++) {
      walk(-0.5);
    }
    assertTrue(mage.getLocation().getX() - victim.getLocation().getX() <= -1.2 + 1e-9);
    assertTrue(victim.getLocation().distance(mage.getLocation()) < 2);

    World other = fx.server.addSimpleWorld("other");
    mage.teleport(new Location(other, 0.5, 64, 0.5));
    victim.teleport(new Location(other, 0.5, 64, 1.5));
    CarryService.tick();
    assertSame(victim, CarryService.carried(mage));
    walk(-2);
    assertEquals(0.5, victim.getLocation().getX(), 1e-9);
    assertEquals(64, victim.getLocation().getY(), 1e-9);
  }

  @Test
  void noSlownessWhenTurnedOff() throws Exception {
    Files.writeString(temp.resolve("c.yml"), "enabled: true\nhunt:\n  carry: { slowness: 0, distance: 0.1, break_distance: 0 }\n");
    BloodConfig.load(temp.resolve("c.yml").toFile(), fx.logger);
    assertEquals(0.5, HuntConfig.carryDistance());
    assertEquals(1.5, HuntConfig.carryBreakDistance());
    assertEquals(0, HuntConfig.carrySlowness());
    assertEquals(CharacterRiteService.Bind.OK, CarryService.start(mage, victim));
    CarryService.tick();
    assertFalse(mage.hasPotionEffect(PotionEffectType.SLOWNESS));
  }

  @Test
  void theDragBreaksWhenAnythingChanges() {
    Runnable[] breakers = {
      () -> fx.characters.start(mage, "mage-alt"),
      () -> fx.characters.start(victim, "victim-alt"),
      () -> bridge.when(() -> RpCharactersBridge.isDown(victim)).thenReturn(false),
      () -> bridge.when(() -> RpCharactersBridge.isDown(mage)).thenReturn(true),
      () -> victim.teleport(new Location(fx.server.addSimpleWorld("w" + now.incrementAndGet()), 0, 64, 0)),
      () -> victim.teleport(new Location(fx.world, 40.5, 11, 0.5)),
    };
    for (Runnable breaker : breakers) {
      fx.characters.start(mage, "mage");
      fx.characters.start(victim, "victim");
      bridge.when(() -> RpCharactersBridge.isDown(any())).thenAnswer(i -> i.getArgument(0) != mage);
      victim.teleport(new Location(fx.world, 21.5, 11, 0.5));
      assertEquals(CharacterRiteService.Bind.OK, CarryService.start(mage, victim));
      mage.nextMessage();
      victim.nextMessage();
      breaker.run();
      CarryService.tick();
      assertNull(CarryService.carried(mage));
      assertEquals("blood.carry.broken", mage.nextMessage());
    }
    fx.characters.start(mage, "mage");
    fx.characters.start(victim, "victim");
    bridge.when(() -> RpCharactersBridge.isDown(any())).thenAnswer(i -> i.getArgument(0) != mage);
    victim.teleport(new Location(fx.world, 21.5, 11, 0.5));
    assertEquals(CharacterRiteService.Bind.OK, CarryService.start(mage, victim));
    mage.disconnect();
    CarryService.tick();
    assertNull(CarryService.carrier(victim.getUniqueId()));
  }

  @Test
  void leavingLetsGoAndTheTimerRuns() {
    assertEquals(CharacterRiteService.Bind.OK, CarryService.start(mage, victim));
    mage.nextMessage();
    CarryService.drop(victim.getUniqueId());
    assertEquals("blood.carry.broken", mage.nextMessage());
    assertEquals(CharacterRiteService.Bind.OK, CarryService.start(mage, victim));
    CarryService.drop(mage.getUniqueId());
    assertNull(CarryService.carrier(victim.getUniqueId()));
    CarryService.drop(mage.getUniqueId());
    PlayerMock gone = fx.player("gone");
    assertEquals(CharacterRiteService.Bind.OK, CarryService.start(mage, gone));
    gone.disconnect();
    while (mage.nextMessage() != null) {
      // drain earlier lines
    }
    CarryService.release(mage, true);
    assertEquals("blood.carry.released", mage.nextMessage());
    assertEquals(CharacterRiteService.Bind.OK, CarryService.start(mage, victim));
    mage.disconnect();
    CarryService.drop(victim.getUniqueId());
    assertNull(CarryService.carrier(victim.getUniqueId()));

    var plugin = org.mockbukkit.mockbukkit.MockBukkit.createMockPlugin("CarryTicks");
    CarryService.startTicking(plugin);
    CarryService.startTicking(plugin);
    PlayerMock again = fx.player("again");
    String againId = BloodFixture.id("again");
    BloodRecords.getOrCreate(againId, "o").setStage(BloodStage.HUNT);
    again.getInventory().setItemInMainHand(BloodItems.create(BloodItemKind.BLOOD_KNIFE, againId));
    again.teleport(new Location(fx.world, 21.5, 11, 2.5));
    assertEquals(CharacterRiteService.Bind.OK, CarryService.start(again, victim));
    bridge.when(() -> RpCharactersBridge.isDown(again)).thenReturn(false);
    fx.server.getScheduler().performTicks(4);
    assertTrue(again.hasPotionEffect(PotionEffectType.SLOWNESS));
    CarryService.clear();
    CarryService.clear();
  }

  @Test
  void listenerTakesDragsAndLaysTheBodyDown() {
    BloodListener listener = new BloodListener();
    mage.setSneaking(true);
    PlayerInteractEntityEvent grab = new PlayerInteractEntityEvent(mage, victim, EquipmentSlot.HAND);
    listener.onInteractEntity(grab);
    assertTrue(grab.isCancelled());
    assertSame(victim, CarryService.carried(mage));
    assertEquals("blood.carry.start", mage.nextMessage());
    PlayerMock other = fx.player("other");
    listener.onInteractEntity(new PlayerInteractEntityEvent(mage, other, EquipmentSlot.HAND));
    assertEquals("blood.rite.bind.busy", mage.nextMessage());
    listener.onInteractEntity(new PlayerInteractEntityEvent(mage, victim, EquipmentSlot.HAND));
    assertEquals("blood.carry.released", mage.nextMessage());
    assertNull(CarryService.carried(mage));

    listener.onInteractEntity(new PlayerInteractEntityEvent(mage, victim, EquipmentSlot.HAND));
    mage.nextMessage();
    mage.setSneaking(false);
    listener.onInteractEntity(new PlayerInteractEntityEvent(mage, victim, EquipmentSlot.HAND));
    assertEquals("blood.rite.bind.too_far", mage.nextMessage());
    assertSame(victim, CarryService.carried(mage));

    mage.getInventory().setItemInMainHand(new ItemStack(Material.STONE));
    PlayerInteractEvent touch = new PlayerInteractEvent(mage, Action.RIGHT_CLICK_BLOCK, mage.getInventory().getItemInMainHand(),
        altarBlock, BlockFace.UP, EquipmentSlot.HAND);
    listener.onInteract(touch);
    assertNotEquals("blood.rite.bind.ok", mage.nextMessage());
    mage.getInventory().setItemInMainHand(BloodItems.create(BloodItemKind.BLOOD_KNIFE, mageId));
    victim.teleport(new Location(fx.world, 2.5, 10, 0.5));
    PlayerInteractEvent lay = new PlayerInteractEvent(mage, Action.RIGHT_CLICK_BLOCK, mage.getInventory().getItemInMainHand(),
        altarBlock, BlockFace.UP, EquipmentSlot.HAND);
    listener.onInteract(lay);
    assertEquals("blood.rite.bind.ok", mage.nextMessage());
    assertNull(CarryService.carried(mage));
    assertTrue(CharacterRiteService.hasRite(mage.getUniqueId()));
    assertEquals(0.5, victim.getLocation().getX(), 1e-9);
    assertEquals(11.0, victim.getLocation().getY(), 1e-9);
    CharacterRiteService.clear();

    PlayerMock third = fx.player("third");
    third.teleport(new Location(fx.world, 1.5, 10, 1.5));
    assertEquals(CharacterRiteService.Bind.OK, CarryService.start(mage, third));
    mage.nextMessage();
    listener.onInteractEntity(new PlayerInteractEntityEvent(mage, third, EquipmentSlot.HAND));
    assertEquals("blood.rite.bind.ok", mage.nextMessage());
    CharacterRiteService.clear();

    try {
      BloodConfig.load(writeCarryOff(), fx.logger);
    } catch (Exception ex) {
      fail(ex);
    }
    mage.setSneaking(true);
    PlayerMock fourth = fx.player("fourth");
    fourth.teleport(new Location(fx.world, 30.5, 11, 0.5));
    listener.onInteractEntity(new PlayerInteractEntityEvent(mage, fourth, EquipmentSlot.HAND));
    assertEquals("blood.rite.bind.too_far", mage.nextMessage());
    assertNull(CarryService.carried(mage));
    assertFalse(HuntConfig.carryEnabled());

    BloodConfig.load(new File("src/main/resources/bloodmagic.yml"), fx.logger);
    assertEquals(CharacterRiteService.Bind.OK, CarryService.start(mage, fourth));
    assertEquals("blood.carry.start", mage.nextMessage());
    listener.onQuit(new PlayerQuitEvent(fourth, net.kyori.adventure.text.Component.empty(),
        PlayerQuitEvent.QuitReason.DISCONNECTED));
    assertNull(CarryService.carrier(fourth.getUniqueId()));
    assertEquals("blood.carry.broken", mage.nextMessage());
  }

  File writeCarryOff() throws Exception {
    Path file = temp.resolve("off.yml");
    Files.writeString(file, "enabled: true\nhunt:\n  carry: { enabled: false }\n");
    return file.toFile();
  }

  @Test
  void theRiteBreaksWhenTheBodyGetsAway() {
    victim.teleport(new Location(fx.world, 1.5, 10, 0.5));
    mage.teleport(new Location(fx.world, 2.5, 10, 0.5));
    assertEquals(CharacterRiteService.Bind.OK, CharacterRiteService.bind(mage, victim));
    victim.teleport(new Location(fx.world, 5.5, 10, 0.5));
    CharacterRiteService.tick();
    assertFalse(CharacterRiteService.hasRite(mage.getUniqueId()));
    assertEquals("blood.rite.broken", mage.nextMessage());
    victim.teleport(new Location(fx.world, 1.5, 10, 0.5));
    record.setLastVictimAccount(null);
    assertEquals(CharacterRiteService.Bind.OK, CharacterRiteService.bind(mage, victim));
    victim.teleport(new Location(fx.server.addSimpleWorld("away"), 0.5, 64, 0.5));
    CharacterRiteService.tick();
    assertFalse(CharacterRiteService.hasRite(mage.getUniqueId()));
  }
}
