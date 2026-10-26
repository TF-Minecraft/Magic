package net.tfminecraft.magic.blood;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import net.tfminecraft.tlibs.character.CharacterDeathEvent;
import net.tfminecraft.tlibs.character.CharacterRef;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.event.Event;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.world.EntitiesLoadEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

class BloodListenerTest {
  @TempDir Path temp;
  final BloodListener listener = new BloodListener();

  PlayerInteractEvent click(PlayerMock player, Action action, ItemStack item, Block block, BlockFace face,
      EquipmentSlot hand) {
    return new PlayerInteractEvent(player, action, item, block, face, hand);
  }

  @Test
  void rightClickWithAltarItemPlacesItAndTouchingAnAltarAnswers() {
    try (BloodFixture fx = new BloodFixture(temp)) {
      PlayerMock player = fx.player("mage");
      fx.record("mage", BloodStage.ANIMAL);
      Block floor = fx.world.getBlockAt(0, 9, 0);
      Block target = fx.pocket(0, 10, 0);
      ItemStack item = BloodItems.create(BloodItemKind.ALTAR, BloodFixture.id("mage"));

      PlayerInteractEvent offHand = click(player, Action.RIGHT_CLICK_BLOCK, item, floor, BlockFace.UP, EquipmentSlot.OFF_HAND);
      listener.onInteract(offHand);
      assertFalse(offHand.isCancelled());
      PlayerInteractEvent left = click(player, Action.LEFT_CLICK_BLOCK, item, floor, BlockFace.UP, EquipmentSlot.HAND);
      listener.onInteract(left);
      assertFalse(left.isCancelled());
      PlayerInteractEvent plain = click(player, Action.RIGHT_CLICK_BLOCK, new ItemStack(Material.STONE), floor,
          BlockFace.UP, EquipmentSlot.HAND);
      listener.onInteract(plain);
      assertFalse(plain.isCancelled());

      PlayerInteractEvent place = click(player, Action.RIGHT_CLICK_BLOCK, item, floor, BlockFace.UP, EquipmentSlot.HAND);
      listener.onInteract(place);
      assertTrue(place.isCancelled());
      assertEquals(Event.Result.DENY, place.useItemInHand());
      assertEquals("blood.altar.placed", player.nextMessage());
      assertNotNull(BloodAltars.at(target));

      PlayerInteractEvent touch = click(player, Action.RIGHT_CLICK_BLOCK, null, target, BlockFace.UP, EquipmentSlot.HAND);
      listener.onInteract(touch);
      assertTrue(touch.isCancelled());
      assertEquals("blood.altar.owner_touch", player.nextMessage());

      Block grass = fx.pocket(5, 10, 5);
      grass.setType(Material.SHORT_GRASS);
      fx.record("other", BloodStage.ANIMAL);
      PlayerMock other = fx.player("other");
      listener.onInteract(click(other, Action.RIGHT_CLICK_BLOCK,
          BloodItems.create(BloodItemKind.ALTAR, BloodFixture.id("other")), grass, BlockFace.UP, EquipmentSlot.HAND));
      assertEquals("blood.altar.placed", other.nextMessage());
      assertNotNull(BloodAltars.at(grass));
    }
  }

  @Test
  void protectionStopsBreakingPistonsAndExplosions() {
    try (BloodFixture fx = new BloodFixture(temp)) {
      PlayerMock player = fx.player("mage");
      Block target = fx.pocket(0, 10, 0);
      BloodAltars.add(BloodAltar.create("c", "o", target, 0f, 1L));
      Block other = fx.world.getBlockAt(3, 10, 3);

      BlockBreakEvent breakAltar = new BlockBreakEvent(target, player);
      listener.onBreak(breakAltar);
      assertTrue(breakAltar.isCancelled());
      assertEquals("blood.altar.unbreakable", player.nextMessage());
      BlockBreakEvent breakOther = new BlockBreakEvent(other, player);
      listener.onBreak(breakOther);
      assertFalse(breakOther.isCancelled());

      BlockPlaceEvent placeBound = new BlockPlaceEvent(other, other.getState(), other,
          BloodItems.create(BloodItemKind.ALTAR, "x"), player, true, EquipmentSlot.HAND);
      listener.onPlace(placeBound);
      assertTrue(placeBound.isCancelled());
      BlockPlaceEvent placePlain = new BlockPlaceEvent(other, other.getState(), other,
          new ItemStack(Material.STONE), player, true, EquipmentSlot.HAND);
      listener.onPlace(placePlain);
      assertFalse(placePlain.isCancelled());

      BlockPistonExtendEvent extend = new BlockPistonExtendEvent(other, List.of(other, target), BlockFace.UP);
      listener.onPistonExtend(extend);
      assertTrue(extend.isCancelled());
      BlockPistonExtendEvent extendFree = new BlockPistonExtendEvent(other, List.of(other), BlockFace.UP);
      listener.onPistonExtend(extendFree);
      assertFalse(extendFree.isCancelled());
      BlockPistonRetractEvent retract = new BlockPistonRetractEvent(other, List.of(target), BlockFace.UP);
      listener.onPistonRetract(retract);
      assertTrue(retract.isCancelled());
      BlockPistonRetractEvent retractFree = new BlockPistonRetractEvent(other, List.of(), BlockFace.UP);
      listener.onPistonRetract(retractFree);
      assertFalse(retractFree.isCancelled());

      List<Block> blown = new ArrayList<>(List.of(target, other));
      listener.onEntityExplode(new EntityExplodeEvent(player, target.getLocation(), blown, 1f, null));
      assertEquals(List.of(other), blown);
      List<Block> blockBlown = new ArrayList<>(List.of(target, other));
      listener.onBlockExplode(new BlockExplodeEvent(other, other.getState(), blockBlown, 1f, null));
      assertEquals(List.of(other), blockBlown);
    }
  }

  @Test
  void chunkLoadRedrawsAltarsAndDeathPurifies() {
    try (BloodFixture fx = new BloodFixture(temp)) {
      Block target = fx.pocket(0, 10, 0);
      String owner = UUID.randomUUID().toString();
      BloodAltar altar = BloodAltar.create(owner, "o", target, 0f, 1L);
      BloodAltars.add(altar);
      ItemDisplay stray = fx.world.spawn(target.getLocation(), ItemDisplay.class);
      stray.getPersistentDataContainer().set(AltarDisplays.key(),
          org.bukkit.persistence.PersistentDataType.STRING, "gone");
      List<Entity> entities = List.of(stray);
      listener.onEntitiesLoad(new EntitiesLoadEvent(target.getChunk(), entities));
      assertFalse(stray.isValid());
      assertNotNull(altar.getDisplayId());

      CharacterRef other = new CharacterRef(UUID.randomUUID(), UUID.randomUUID());
      listener.onCharacterDeath(new CharacterDeathEvent(other, UUID.randomUUID(), Instant.EPOCH, null, null, "x"));
      assertNotNull(BloodAltars.at(target));
      CharacterRef dead = new CharacterRef(UUID.randomUUID(), UUID.fromString(owner));
      listener.onCharacterDeath(new CharacterDeathEvent(dead, UUID.randomUUID(), Instant.EPOCH, null, null, "x"));
      assertNull(BloodAltars.at(target));
    }
  }

  @Test
  void killsFeedTheKillersMeterAndSessionsResetVoiceTimers() {
    try (BloodFixture fx = new BloodFixture(temp)) {
      PlayerMock killer = fx.player("killer");
      String killerId = BloodFixture.id("killer");
      CharacterRef victim = new CharacterRef(UUID.randomUUID(), UUID.randomUUID());
      listener.onCharacterDeath(new CharacterDeathEvent(victim, UUID.randomUUID(), Instant.EPOCH, null, null, "x"));
      listener.onCharacterDeath(new CharacterDeathEvent(victim, UUID.randomUUID(), Instant.EPOCH, victim.ownerId(), victim, "x"));
      assertNull(BloodRecords.get(victim.characterId().toString()));
      CharacterRef killerRef = new CharacterRef(killer.getUniqueId(), UUID.fromString(killerId));
      listener.onCharacterDeath(new CharacterDeathEvent(victim, UUID.randomUUID(), Instant.EPOCH,
          killer.getUniqueId(), killerRef, "x"));
      assertEquals(15, BloodRecords.get(killerId).getWhisper());
      CharacterRef otherOfSameOwner = new CharacterRef(killer.getUniqueId(), UUID.randomUUID());
      listener.onCharacterDeath(new CharacterDeathEvent(victim, UUID.randomUUID(), Instant.EPOCH,
          killer.getUniqueId(), otherOfSameOwner, "x"));
      assertEquals(15, BloodRecords.get(otherOfSameOwner.characterId().toString()).getWhisper());
      CharacterRef offline = new CharacterRef(UUID.randomUUID(), UUID.randomUUID());
      listener.onCharacterDeath(new CharacterDeathEvent(victim, UUID.randomUUID(), Instant.EPOCH,
          offline.ownerId(), offline, "x"));
      assertEquals(15, BloodRecords.get(offline.characterId().toString()).getWhisper());

      BloodConfig.load(new java.io.File("src/main/resources/wildmagic/bloodmagic"), fx.logger);
      BloodConfig.setEnabled(true);
      var cow = fx.world.spawn(killer.getLocation(), org.bukkit.entity.Cow.class);
      killer.getInventory().setItemInMainHand(new ItemStack(Material.IRON_SWORD));
      listener.onAnimalDeath(new org.bukkit.event.entity.EntityDeathEvent(cow,
          org.bukkit.damage.DamageSource.builder(org.bukkit.damage.DamageType.GENERIC).build(), new ArrayList<>()));
      cow.setKiller(killer);
      fx.world.setTime(1000);
      listener.onAnimalDeath(new org.bukkit.event.entity.EntityDeathEvent(cow,
          org.bukkit.damage.DamageSource.builder(org.bukkit.damage.DamageType.GENERIC).build(), new ArrayList<>()));
      assertEquals(19, BloodRecords.get(killerId).getWhisper());

      WhisperService.voiceSoon(killer);
      listener.onQuit(new org.bukkit.event.player.PlayerQuitEvent(killer, "bye"));
      assertNull(WhisperService.nextVoice(killer.getUniqueId()));
      WhisperService.voiceSoon(killer);
      listener.onSessionStart(new net.tfminecraft.tlibs.character.CharacterSessionStartEvent(killer,
          new net.tfminecraft.tlibs.character.CharacterSession(killerRef, 99)));
      assertNull(WhisperService.nextVoice(killer.getUniqueId()));
      WhisperService.clear();
    }
  }

  @Test
  void ritualKnifeClicksBindAnimalsAndLeadsAreRemembered() {
    try (BloodFixture fx = new BloodFixture(temp)) {
      PlayerMock mage = fx.player("mage");
      var cow = fx.world.spawn(mage.getLocation(), org.bukkit.entity.Cow.class);
      var stand = fx.world.spawn(mage.getLocation(), org.bukkit.entity.ArmorStand.class);
      PlayerMock other = fx.server.addPlayer();
      var offHand = new org.bukkit.event.player.PlayerInteractEntityEvent(mage, cow, EquipmentSlot.OFF_HAND);
      listener.onInteractEntity(offHand);
      assertFalse(offHand.isCancelled());
      var plain = new org.bukkit.event.player.PlayerInteractEntityEvent(mage, cow, EquipmentSlot.HAND);
      listener.onInteractEntity(plain);
      assertFalse(plain.isCancelled());
      mage.getInventory().setItemInMainHand(BloodItems.create(BloodItemKind.RITUAL_KNIFE, BloodFixture.id("mage")));
      var atPlayer = new org.bukkit.event.player.PlayerInteractEntityEvent(mage, other, EquipmentSlot.HAND);
      listener.onInteractEntity(atPlayer);
      assertFalse(atPlayer.isCancelled());
      var item = fx.world.dropItem(mage.getLocation(), new ItemStack(Material.STONE));
      var atItem = new org.bukkit.event.player.PlayerInteractEntityEvent(mage, item, EquipmentSlot.HAND);
      listener.onInteractEntity(atItem);
      assertFalse(atItem.isCancelled());
      var atCow = new org.bukkit.event.player.PlayerInteractEntityEvent(mage, cow, EquipmentSlot.HAND);
      listener.onInteractEntity(atCow);
      assertTrue(atCow.isCancelled());
      assertEquals("blood.animal.bind.not_stage", mage.nextMessage());
      assertNotNull(stand);

      listener.onLeash(new org.bukkit.event.entity.PlayerLeashEntityEvent(cow, mage, mage, EquipmentSlot.HAND));
      assertTrue(StolenClues.remembers(cow.getUniqueId()));
      StolenClues.clear();

      var trace = fx.world.spawn(mage.getLocation(), ItemDisplay.class);
      trace.getPersistentDataContainer().set(StolenClues.traceKey(), org.bukkit.persistence.PersistentDataType.LONG, 0L);
      listener.onEntitiesLoad(new EntitiesLoadEvent(fx.world.getChunkAt(0, 0), List.of(trace)));
      assertFalse(trace.isValid());

      BloodNotes.add(new BloodNotes.Note(mage.getUniqueId().toString(), null, "taken", Long.MAX_VALUE));
      listener.onSessionStart(new net.tfminecraft.tlibs.character.CharacterSessionStartEvent(mage,
          new net.tfminecraft.tlibs.character.CharacterSession(
              new CharacterRef(mage.getUniqueId(), UUID.fromString(BloodFixture.id("mage"))), 5)));
      assertEquals("taken", mage.nextMessage());
      BloodNotes.clear();
    }
  }

  @Test
  void theBloodKnifeOpensTheQuestionAndArmourIsPoliced() {
    try (BloodFixture fx = new BloodFixture(temp)) {
      PlayerMock mage = fx.player("mage");
      String id = BloodFixture.id("mage");
      Block target = fx.pocket(0, 10, 0);
      BloodAltars.add(BloodAltar.create(id, "o", target, 0f, 1L));
      fx.record("mage", BloodStage.CHOICE);
      PlayerInteractEvent touch = click(mage, Action.RIGHT_CLICK_BLOCK, null, target, BlockFace.UP, EquipmentSlot.HAND);
      listener.onInteract(touch);
      assertEquals("blood.altar.owner_touch", mage.nextMessage());
      mage.getInventory().setItemInMainHand(BloodItems.create(BloodItemKind.BLOOD_KNIFE, "someone"));
      listener.onInteract(click(mage, Action.RIGHT_CLICK_BLOCK, null, target, BlockFace.UP, EquipmentSlot.HAND));
      assertEquals("blood.altar.owner_touch", mage.nextMessage());
      mage.getInventory().setItemInMainHand(BloodItems.create(BloodItemKind.RITUAL_KNIFE, id));
      listener.onInteract(click(mage, Action.RIGHT_CLICK_BLOCK, null, target, BlockFace.UP, EquipmentSlot.HAND));
      assertEquals("blood.altar.owner_touch", mage.nextMessage());
      mage.getInventory().setItemInMainHand(BloodItems.create(BloodItemKind.BLOOD_KNIFE, id));
      BloodRecords.get(id).setStage(BloodStage.HUNT);
      listener.onInteract(click(mage, Action.RIGHT_CLICK_BLOCK, null, target, BlockFace.UP, EquipmentSlot.HAND));
      assertEquals("blood.altar.owner_touch", mage.nextMessage());
      BloodRecords.get(id).setStage(BloodStage.CHOICE);
      listener.onInteract(click(mage, Action.RIGHT_CLICK_BLOCK, null, target, BlockFace.UP, EquipmentSlot.HAND));
      assertInstanceOf(ChoiceMenu.class, mage.getOpenInventory().getTopInventory().getHolder());
      PlayerMock stranger = fx.player("stranger");
      stranger.getInventory().setItemInMainHand(BloodItems.create(BloodItemKind.BLOOD_KNIFE, BloodFixture.id("stranger")));
      listener.onInteract(click(stranger, Action.RIGHT_CLICK_BLOCK, null, target, BlockFace.UP, EquipmentSlot.HAND));
      assertEquals("blood.altar.stranger_touch", stranger.nextMessage());
      PlayerMock bare = fx.server.addPlayer();
      listener.onInteract(click(bare, Action.RIGHT_CLICK_BLOCK, null, target, BlockFace.UP, EquipmentSlot.HAND));
      assertEquals("blood.altar.stranger_touch", bare.nextMessage());

      var view = mage.getOpenInventory();
      var menuClick = new org.bukkit.event.inventory.InventoryClickEvent(view,
          org.bukkit.event.inventory.InventoryType.SlotType.CONTAINER, 0,
          org.bukkit.event.inventory.ClickType.LEFT, org.bukkit.event.inventory.InventoryAction.PICKUP_ALL);
      listener.onMenuClick(menuClick);
      assertTrue(menuClick.isCancelled());
      var drag = mock(org.bukkit.event.inventory.InventoryDragEvent.class);
      when(drag.getInventory()).thenReturn(view.getTopInventory());
      listener.onMenuDrag(drag);
      verify(drag).setCancelled(true);

      ItemStack foreign = BloodArmour.bind(new ItemStack(Material.LEATHER_HELMET), id);
      stranger.getInventory().setHelmet(foreign);
      listener.onArmourChange(new com.destroystokyo.paper.event.player.PlayerArmorChangeEvent(stranger,
          com.destroystokyo.paper.event.player.PlayerArmorChangeEvent.SlotType.HEAD, new ItemStack(Material.AIR), foreign));
      when(net.tfminecraft.magic.Magic.plugin.isEnabled()).thenReturn(true);
      fx.server.getScheduler().performOneTick();
      listener.onArmourChange(new com.destroystokyo.paper.event.player.PlayerArmorChangeEvent(stranger,
          com.destroystokyo.paper.event.player.PlayerArmorChangeEvent.SlotType.HEAD, foreign, new ItemStack(Material.AIR)));

      var dropped = fx.world.dropItem(stranger.getLocation(), foreign);
      var pickup = new org.bukkit.event.entity.EntityPickupItemEvent(stranger, dropped, 0);
      listener.onPickup(pickup);
      assertFalse(pickup.isCancelled());
      String dead = UUID.randomUUID().toString();
      var relic = fx.world.dropItem(stranger.getLocation(), BloodArmour.bind(new ItemStack(Material.LEATHER_BOOTS), dead));
      try (var chars = mockStatic(net.tfminecraft.tlibs.character.Characters.class, CALLS_REAL_METHODS)) {
        chars.when(() -> net.tfminecraft.tlibs.character.Characters.info(UUID.fromString(dead)))
            .thenReturn(AltarServiceTest.info(dead, net.tfminecraft.tlibs.character.CharacterStatus.DEAD));
        var relicPickup = new org.bukkit.event.entity.EntityPickupItemEvent(stranger, relic, 0);
        listener.onPickup(relicPickup);
        assertTrue(relicPickup.isCancelled());
        assertFalse(relic.isValid());
      }
    }
  }
}
