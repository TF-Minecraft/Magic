package net.tfminecraft.magic.blood;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.Levelled;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Hanging;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityPlaceEvent;
import org.bukkit.event.hanging.HangingPlaceEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerBucketFillEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import net.tfminecraft.interactiblefurniture.events.FurniturePlaceEvent;
import net.tfminecraft.interactiblefurniture.furniture.Furniture;

class RoomLockTest {
  @TempDir Path temp;
  BloodFixture fx;
  final BloodListener listener = new BloodListener();

  static final String TEMPLATE = String.join("\n",
      "placement:",
      "  min_cover: 1",
      "  cover_scan_height: 4",
      "  room:",
      "    template:",
      "      floor: [STONE_BRICKS]",
      "      walls: [COBBLESTONE, UNOBTAINIUM, WATER]",
      "      ceiling: [ANDESITE]",
      "      light: 40",
      "");

  @BeforeEach
  void setup() {
    fx = new BloodFixture(temp);
  }

  @AfterEach
  void cleanup() {
    fx.close();
  }

  void config(String yaml) throws Exception {
    Files.writeString(temp.resolve("r.yml"), "enabled: true\n" + yaml);
    assertTrue(BloodConfig.load(temp.resolve("r.yml").toFile(), fx.logger));
  }

  @Test
  void everyChamberIsLaidInTheTemplateWithItsWayInLeftOpen() throws Exception {
    config(TEMPLATE);
    assertEquals(15, RoomTemplate.light());
    Block target = fx.room(0, 10, 0);
    Block door = fx.world.getBlockAt(5, 10, 0);
    door.setType(Material.AIR);
    Block hole = fx.world.getBlockAt(2, 9, 2);
    hole.setType(Material.AIR);
    Block bedrock = fx.world.getBlockAt(-5, 11, 0);
    bedrock.setType(Material.BEDROCK);

    AltarRoom.Plan plan = AltarRoom.plan(target, null);
    assertEquals(AltarRoom.Verdict.OK, plan.verdict());
    assertTrue(plan.carve().isEmpty());
    assertEquals(4, plan.lights().size());
    assertEquals(Material.STONE_BRICKS, plan.lay().get(hole));
    assertFalse(plan.lay().containsKey(door));
    assertFalse(plan.lay().containsKey(bedrock));
    plan.apply();
    assertEquals(Material.STONE_BRICKS, fx.world.getBlockAt(0, 9, 3).getType());
    assertEquals(Material.STONE_BRICKS, hole.getType());
    assertEquals(Material.COBBLESTONE, fx.world.getBlockAt(5, 11, 0).getType());
    assertEquals(Material.COBBLESTONE, fx.world.getBlockAt(-5, 9, -5).getType());
    assertEquals(Material.ANDESITE, fx.world.getBlockAt(1, 13, 1).getType());
    assertEquals(Material.AIR, door.getType());
    assertEquals(Material.BEDROCK, bedrock.getType());
    Block corner = fx.world.getBlockAt(4, 12, -4);
    assertEquals(Material.LIGHT, corner.getType());
    assertEquals(15, ((Levelled) corner.getBlockData()).getLevel());
    assertEquals(4, AltarRoom.corners(target).size());
    assertTrue(AltarRoom.corners(target).contains(fx.world.getBlockAt(5, 10, 5)));
  }

  @Test
  void waterOrLavaInOrAgainstTheChamberRefusesIt() throws Exception {
    config(TEMPLATE);
    Block target = fx.room(0, 10, 0);
    Block door = fx.world.getBlockAt(5, 10, 0);
    door.setType(Material.AIR);
    fx.world.getBlockAt(6, 10, 0).setType(Material.WATER);
    assertEquals(AltarRoom.Verdict.BLOCKED, AltarRoom.plan(target, null).verdict());
    fx.world.getBlockAt(6, 10, 0).setType(Material.STONE);
    assertEquals(AltarRoom.Verdict.OK, AltarRoom.plan(target, null).verdict());
    fx.world.getBlockAt(-5, 11, 2).setType(Material.LAVA);
    assertEquals(AltarRoom.Verdict.BLOCKED, AltarRoom.plan(target, null).verdict());
    fx.world.getBlockAt(-5, 11, 2).setType(Material.STONE);

    BloodAltar rival = BloodAltar.create("r", "o", fx.pocket(30, 10, 30), 0f, 1L);
    rival.getCorrupted().add(new BloodAltar.CorruptedBlock(0, 13, 0, "minecraft:stone", "NETHERRACK"));
    BloodAltars.add(rival);
    fx.world.getBlockAt(0, 13, 0).setType(Material.NETHERRACK);
    assertEquals(AltarRoom.Verdict.BLOCKED, AltarRoom.plan(target, null).verdict());
    fx.world.getBlockAt(0, 13, 0).setType(Material.STONE);
    BloodAltars.remove(rival);
    BloodAltars.add(BloodAltar.create("r2", "o", fx.world.getBlockAt(-5, 10, -5), 0f, 1L));
    assertEquals(AltarRoom.Verdict.BLOCKED, AltarRoom.plan(target, null).verdict());
    BloodAltars.clear();
    BloodAltars.init(new BloodStore(temp.resolve("b2").toFile(), fx.logger));

    // A floor hole holding something that must not move.
    Block portal = fx.world.getBlockAt(1, 9, 1);
    portal.setType(Material.END_PORTAL);
    assertEquals(AltarRoom.Verdict.BLOCKED, AltarRoom.plan(target, null).verdict());
    portal.setType(Material.STONE);
    AltarRegions.setQuery(location -> location.getBlockX() == -5 ? List.of("claim") : List.of());
    assertEquals(AltarRoom.Verdict.BLOCKED, AltarRoom.plan(target, null).verdict());
  }

  @Test
  void theChamberIsLockedToAllButStaff() throws Exception {
    config("corruption:\n  levels:\n    0: { radius: 2, max_blocks: 4 }\n    5: { radius: 7, max_blocks: 70 }\n");
    assertEquals(7, CorruptionConfig.maxRadius());
    assertEquals(7, AltarRoom.lockRadius());
    Block target = fx.room(0, 10, 0);
    BloodAltar altar = BloodAltar.create(BloodFixture.id("mage"), "o", target, 0f, 1L);
    BloodAltars.add(altar);
    assertSame(altar, AltarRoom.lockedBy(fx.world.getBlockAt(7, 7, -7)));
    assertSame(altar, AltarRoom.lockedBy(fx.world.getBlockAt(-7, 13, 7)));
    assertNull(AltarRoom.lockedBy(fx.world.getBlockAt(8, 10, 0)));
    assertNull(AltarRoom.lockedBy(fx.world.getBlockAt(0, 10, -8)));
    assertNull(AltarRoom.lockedBy(fx.world.getBlockAt(0, 6, 0)));
    assertNull(AltarRoom.lockedBy(fx.world.getBlockAt(0, 14, 0)));
    org.mockbukkit.mockbukkit.world.WorldMock elsewhere = new org.mockbukkit.mockbukkit.world.WorldMock();
    elsewhere.setName("elsewhere");
    fx.server.addWorld(elsewhere);
    assertNull(AltarRoom.lockedBy(elsewhere.getBlockAt(0, 10, 0)));

    PlayerMock mage = fx.player("mage");
    Block wall = fx.world.getBlockAt(5, 11, 0);
    BlockBreakEvent breakWall = new BlockBreakEvent(wall, mage);
    listener.onBreak(breakWall);
    assertTrue(breakWall.isCancelled());
    assertEquals("blood.altar.room_locked", mage.nextMessage());
    Block air = fx.world.getBlockAt(2, 10, 2);
    BlockPlaceEvent torch = new BlockPlaceEvent(air, air.getState(), air.getRelative(BlockFace.DOWN),
        new ItemStack(Material.TORCH), mage, true, EquipmentSlot.HAND);
    listener.onPlace(torch);
    assertTrue(torch.isCancelled());
    assertEquals("blood.altar.room_locked", mage.nextMessage());

    PlayerBucketEmptyEvent pour = mock(PlayerBucketEmptyEvent.class);
    when(pour.getPlayer()).thenReturn(mage);
    when(pour.getBlock()).thenReturn(air);
    listener.onBucketEmpty(pour);
    verify(pour).setCancelled(true);
    PlayerBucketFillEvent scoop = mock(PlayerBucketFillEvent.class);
    when(scoop.getPlayer()).thenReturn(mage);
    when(scoop.getBlock()).thenReturn(air);
    listener.onBucketFill(scoop);
    verify(scoop).setCancelled(true);
    Entity frame = mock(Hanging.class);
    when(frame.getLocation()).thenReturn(air.getLocation());
    HangingPlaceEvent hang = mock(HangingPlaceEvent.class);
    when(hang.getEntity()).thenReturn((Hanging) frame);
    listener.onHang(hang);
    verify(hang).setCancelled(true);
    ArmorStand stand = fx.world.spawn(air.getLocation(), ArmorStand.class);
    EntityPlaceEvent standPlace = mock(EntityPlaceEvent.class);
    when(standPlace.getEntity()).thenReturn(stand);
    when(standPlace.getPlayer()).thenReturn(mage);
    listener.onEntityPlace(standPlace);
    verify(standPlace).setCancelled(true);
    FurniturePlaceEvent furniture = new FurniturePlaceEvent(new Furniture("chair", air.getLocation(), java.util.UUID.randomUUID()), mage);
    listener.onFurniturePlace(furniture);
    assertTrue(furniture.isCancelled());
    mage.nextMessage();
    mage.nextMessage();
    mage.nextMessage();
    mage.nextMessage();

    Block outside = fx.world.getBlockAt(20, 10, 20);
    BlockPlaceEvent away = new BlockPlaceEvent(outside, outside.getState(), outside.getRelative(BlockFace.DOWN),
        new ItemStack(Material.TORCH), mage, true, EquipmentSlot.HAND);
    listener.onPlace(away);
    assertFalse(away.isCancelled());
    FurniturePlaceEvent farFurniture = new FurniturePlaceEvent(new Furniture("chair", outside.getLocation(), java.util.UUID.randomUUID()), mage);
    listener.onFurniturePlace(farFurniture);
    assertFalse(farFurniture.isCancelled());
    HangingPlaceEvent farHang = mock(HangingPlaceEvent.class);
    Entity farFrame = mock(Hanging.class);
    when(farFrame.getLocation()).thenReturn(outside.getLocation());
    when(farHang.getEntity()).thenReturn((Hanging) farFrame);
    listener.onHang(farHang);
    verify(farHang, never()).setCancelled(true);
    PlayerBucketEmptyEvent farPour = mock(PlayerBucketEmptyEvent.class);
    when(farPour.getBlock()).thenReturn(outside);
    listener.onBucketEmpty(farPour);
    verify(farPour, never()).setCancelled(true);
    PlayerBucketFillEvent farScoop = mock(PlayerBucketFillEvent.class);
    when(farScoop.getBlock()).thenReturn(outside);
    listener.onBucketFill(farScoop);
    verify(farScoop, never()).setCancelled(true);
    EntityPlaceEvent farStand = mock(EntityPlaceEvent.class);
    when(farStand.getEntity()).thenReturn(fx.world.spawn(outside.getLocation(), ArmorStand.class));
    listener.onEntityPlace(farStand);
    verify(farStand, never()).setCancelled(true);

    mage.addAttachment(MockBukkit.createMockPlugin(), BloodListener.BYPASS, true);
    BlockBreakEvent staffBreak = new BlockBreakEvent(wall, mage);
    listener.onBreak(staffBreak);
    assertFalse(staffBreak.isCancelled());
    assertNull(mage.nextMessage());
    assertTrue(BloodListener.locked(null, wall));
  }

  @Test
  void claimsAtTheChambersCornersRefuseThePlacement() {
    PlayerMock player = fx.player("mage");
    fx.record("mage", BloodStage.ANIMAL);
    Block target = fx.room(0, 10, 0);
    Set<Integer> denied = Set.of(5);
    fx.server.getPluginManager().registerEvents(new Listener() {
      @EventHandler
      public void onPlace(BlockPlaceEvent event) {
        if (denied.contains(event.getBlock().getX()) && event.getBlock().getZ() == -5) {
          event.setCancelled(true);
        }
      }
    }, MockBukkit.createMockPlugin());
    assertEquals("blood.altar.place.region",
        AltarService.place(player, BloodItems.create(BloodItemKind.ALTAR, BloodFixture.id("mage")), target));
    assertNull(BloodAltars.at(target));
  }

  @Test
  void badTemplateEntriesAreSkipped() throws Exception {
    config("placement:\n  room:\n    template:\n      floor: [AIR]\n      light: -3\n");
    assertFalse(RoomTemplate.hasFloor());
    assertEquals(0, RoomTemplate.light());
    assertNull(RoomTemplate.wall(new java.util.Random()));
    assertNull(RoomTemplate.ceiling(new java.util.Random()));
  }
}
