package net.tfminecraft.magic.blood;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.nio.file.Path;

import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDismountEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

class AltarSeatTest {
  @TempDir Path temp;
  BloodFixture fx;
  final BloodListener listener = new BloodListener();

  @BeforeEach
  void setup() {
    fx = new BloodFixture(temp);
  }

  @AfterEach
  void cleanup() {
    AltarSeat.clearAll();
    fx.close();
  }

  PlayerInteractEvent click(PlayerMock player, Block block) {
    return new PlayerInteractEvent(player, Action.RIGHT_CLICK_BLOCK, player.getInventory().getItemInMainHand(),
        block, BlockFace.UP, EquipmentSlot.HAND);
  }

  @Test
  void theOwnerSitsOnTheAltarWithAnEmptyHandAndTheSeatGoesWhenLeft() {
    PlayerMock mage = fx.player("mage");
    Block target = fx.room(0, 10, 0);
    BloodAltar altar = BloodAltar.create(BloodFixture.id("mage"), mage.getUniqueId().toString(), target, 0f, 1L);
    BloodAltars.add(altar);

    mage.getInventory().setItemInMainHand(new ItemStack(Material.STONE));
    listener.onInteract(click(mage, target));
    assertEquals("blood.altar.owner_touch", mage.nextMessage());
    assertFalse(mage.isInsideVehicle());
    PlayerMock stranger = fx.player("stranger");
    listener.onInteract(click(stranger, target));
    assertEquals("blood.altar.stranger_touch", stranger.nextMessage());
    PlayerMock nobody = fx.server.addPlayer();
    listener.onInteract(click(nobody, target));
    assertEquals("blood.altar.stranger_touch", nobody.nextMessage());

    mage.getInventory().setItemInMainHand(null);
    listener.onInteract(click(mage, target));
    assertEquals("blood.altar.sit", mage.nextMessage());
    Entity seat = mage.getVehicle();
    assertInstanceOf(ItemDisplay.class, seat);
    assertTrue(AltarSeat.isSeat(seat));
    assertFalse(seat.isPersistent());
    assertEquals(target.getY() + 1.0, seat.getLocation().getY(), 1e-9);

    listener.onInteract(click(mage, target));
    assertEquals("blood.altar.owner_touch", mage.nextMessage());

    listener.onDismount(new EntityDismountEvent(mage, seat));
    assertTrue(seat.isDead());
    AltarSeat.left(mage);
    assertFalse(AltarSeat.isSeat(null));

    mage.leaveVehicle();
    assertTrue(AltarSeat.sit(mage, altar));
    Entity second = mage.getVehicle();
    mage.leaveVehicle();
    assertTrue(AltarSeat.sit(mage, altar));
    assertTrue(second.isDead());
    AltarService.purify(altar, "test");
    assertFalse(mage.isInsideVehicle());
    AltarSeat.clear(altar);
  }

  @Test
  void seatsThatAreGoneOrAltarsNotLoadedAreSkipped() {
    PlayerMock mage = fx.player("mage");
    BloodAltar altar = BloodAltar.create(BloodFixture.id("mage"), "o", fx.room(0, 10, 0), 0f, 1L);
    BloodAltar unloaded = spy(altar);
    doReturn(null).when(unloaded).center();
    assertFalse(AltarSeat.sit(mage, unloaded));

    assertTrue(AltarSeat.sit(mage, altar));
    Entity seat = mage.getVehicle();
    mage.leaveVehicle();
    seat.remove();
    AltarSeat.clear(altar);

    assertTrue(AltarSeat.sit(mage, altar));
    BloodAltar other = BloodAltar.create("other", "o", fx.room(20, 10, 20), 0f, 1L);
    PlayerMock rival = fx.player("rival");
    assertTrue(AltarSeat.sit(rival, other));
    rival.getVehicle().remove();
    AltarSeat.clearAll();
    assertFalse(mage.isInsideVehicle());
  }
}
