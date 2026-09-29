package net.tfminecraft.magic;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.*;
import net.tfminecraft.interactiblefurniture.events.*;
import net.tfminecraft.interactiblefurniture.furniture.*;
import net.tfminecraft.magic.artifact.*;
import net.tfminecraft.magic.artifact.aura.*;
import net.tfminecraft.magic.artifact.fillchest.ArtifactFillChestService;
import net.tfminecraft.magic.artifact.sacrifice.*;
import net.tfminecraft.magic.artifact.shrine.ShrineChargeService;
import net.tfminecraft.magic.attunement.ArtifactDisplayIndex;
import net.tfminecraft.magic.listener.*;
import net.tfminecraft.magic.meditation.*;
import net.tfminecraft.rpcharacters.chat.CharacterChatEvent;
import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.*;
import org.bukkit.event.inventory.*;
import org.bukkit.event.player.*;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.inventory.*;
import org.junit.jupiter.api.*;
import org.mockbukkit.mockbukkit.*;

class ArtifactListenerTest {
  ServerMock server;

  @BeforeEach
  void setup() {
    server = MockBukkit.mock();
    server.addSimpleWorld("listeners");
    Magic.plugin = mock(Magic.class);
    when(Magic.plugin.namespace()).thenReturn("magic");
    when(Magic.plugin.getServer()).thenReturn(server);
    when(Magic.plugin.isEnabled()).thenReturn(true);
    SacrificeRegistry.clear();
    MeditationCache.pedestalId = "pedestal";
  }

  @AfterEach
  void cleanup() {
    ArtifactFillChestService.clearAll();
    SacrificeRegistry.clear();
    Magic.plugin = null;
    MockBukkit.unmock();
  }

  @Test
  void furnitureEventsStartStopDisplayTrackAndRespectMeditationLocks() {
    var listener = new ArtifactListener();
    var furniture = mock(Furniture.class);
    when(furniture.getEntityId()).thenReturn(UUID.randomUUID());
    var slot = mock(SlotDefinition.class);
    when(slot.getId()).thenReturn("main");
    var player = server.addPlayer();
    var item = new ItemStack(Material.STONE);
    var data = Artifact.create();
    data.setCap("fire", 10);
    data.persistPdc(item);
    var add = mock(FurnitureSlotItemAddEvent.class);
    var take = mock(FurnitureSlotItemTakeEvent.class);
    var broken = mock(FurnitureBreakEvent.class);
    try (var display = mockStatic(ArtifactDisplayIndex.class);
        var shrine = mockStatic(ShrineChargeService.class);
        var sacrifice = mockStatic(SacrificeRiteService.class);
        var meditation = mockStatic(MeditationService.class);
        var lore = mockStatic(VesselLore.class)) {
      listener.onFurnitureSlotAdd(add);
      when(add.getFurniture()).thenReturn(furniture);
      listener.onFurnitureSlotAdd(add);
      when(add.getSlot()).thenReturn(slot);
      listener.onFurnitureSlotAdd(add);
      when(furniture.getId()).thenReturn("pedestal");
      listener.onFurnitureSlotAdd(add);
      when(add.getItem()).thenReturn(item);
      when(add.getPlayer()).thenReturn(player);
      display.when(() -> ArtifactDisplayIndex.isDisplayFurniture(furniture)).thenReturn(true);
      listener.onFurnitureSlotAdd(add);
      display.verify(() -> ArtifactDisplayIndex.add(item));
      shrine.verifyNoInteractions();
      server.getScheduler().performOneTick();
      shrine.verify(() -> ShrineChargeService.tryStart(player, furniture, "main"));
      listener.onFurnitureSlotTake(take);
      when(take.getFurniture()).thenReturn(furniture);
      listener.onFurnitureSlotTake(take);
      when(take.getSlot()).thenReturn(slot);
      when(take.getItem()).thenReturn(item);
      lore.when(() -> VesselLore.updateItem(item)).thenReturn(item);
      listener.onFurnitureSlotTake(take);
      shrine.verify(() -> ShrineChargeService.stop(furniture.getEntityId(), "main"));
      sacrifice.verify(() -> SacrificeRiteService.stop(furniture.getEntityId(), "main"));
      verify(take).setItem(item);
      listener.onFurnitureSlotTakeDisplay(take);
      display.verify(() -> ArtifactDisplayIndex.remove(item));
      when(take.getFurniture()).thenReturn(null);
      listener.onFurnitureSlotTakeDisplay(take);
      when(take.getFurniture()).thenReturn(furniture);
      meditation.when(() -> MeditationService.locksFurniture(furniture)).thenReturn(true);
      listener.onFurnitureSlotTake(take);
      verify(take).setCancelled(true);
      listener.onFurnitureBreakLock(broken);
      listener.onFurnitureBreak(broken);
      when(broken.getFurniture()).thenReturn(furniture);
      listener.onFurnitureBreakLock(broken);
      verify(broken).setCancelled(true);
      listener.onFurnitureBreak(broken);
      shrine.verify(() -> ShrineChargeService.stopAll(furniture.getEntityId()));
      sacrifice.verify(() -> SacrificeRiteService.stopAll(furniture.getEntityId()));
      var chunk = server.getWorlds().getFirst().getChunkAt(0, 0);
      listener.onChunkLoad(new ChunkLoadEvent(chunk, false));
      display.verify(() -> ArtifactDisplayIndex.ensureChunk(chunk));
      listener.onPickup(new EntityPickupItemEvent(mock(Zombie.class), mock(Item.class), 0));
      listener.onPickup(new EntityPickupItemEvent(player, mock(Item.class), 0));
      server.getScheduler().performOneTick();
      listener.onJoin(new PlayerJoinEvent(player, "joined"));
      lore.verify(() -> VesselLore.updateInventory(player), times(2));
    }
  }

  @Test
  void inventoryCareRoutesClickedCursorHotbarAndDraggedSlots() {
    var listener = new ArtifactListener();
    var player = server.addPlayer();
    var item = new ItemStack(Material.STONE);
    ArtifactIds.writeNew(item);
    player.getInventory().setItem(2, item);
    var click = mock(InventoryClickEvent.class);
    when(click.getWhoClicked()).thenReturn(player);
    when(click.getHotbarButton()).thenReturn(2);
    when(click.getClickedInventory()).thenReturn(player.getInventory());
    when(click.getCurrentItem()).thenReturn(item);
    when(click.getCursor()).thenReturn(item);
    try (var care = mockStatic(ArtifactCareStore.class);
        var places = mockStatic(ArtifactCarePlaces.class)) {
      listener.onCareClick(click);
      care.verify(
          () ->
              ArtifactCareStore.apply(
                  eq(item), anyBoolean(), anyLong(), eq(ArtifactCareStore.Persist.ALWAYS)),
          times(3));
      when(click.getHotbarButton()).thenReturn(-1);
      when(click.getCurrentItem()).thenReturn(null);
      when(click.getCursor()).thenReturn(null);
      listener.onCareClick(click);
      when(click.getHotbarButton()).thenReturn(1);
      when(click.getWhoClicked()).thenReturn(mock(HumanEntity.class));
      listener.onCareClick(click);
      var drag = mock(InventoryDragEvent.class);
      when(drag.getWhoClicked()).thenReturn(player);
      when(drag.getNewItems()).thenReturn(Map.of(1, item));
      when(drag.getCursor()).thenReturn(item);
      var view = mock(InventoryView.class);
      when(view.getInventory(1)).thenReturn(player.getInventory());
      when(drag.getView()).thenReturn(view);
      listener.onCareDrag(drag);
      when(drag.getView()).thenReturn(null);
      when(drag.getWhoClicked()).thenReturn(mock(HumanEntity.class));
      listener.onCareDrag(drag);
      care.verify(
          () ->
              ArtifactCareStore.apply(
                  eq(item), anyBoolean(), anyLong(), eq(ArtifactCareStore.Persist.ALWAYS)),
          times(7));
    }
  }

  @Test
  void sacrificeChatOnlyStartsMatchingRpWordsAndHonorsVisibility() {
    var listener = new SacrificeListener();
    var player = server.addPlayer();
    var chat = mock(CharacterChatEvent.class);
    when(chat.getSender()).thenReturn(player);
    try (var rites = mockStatic(SacrificeRiteService.class)) {
      listener.onCharacterChat(chat);
      SacrificeRegistry.setEnabled(true);
      listener.onCharacterChat(chat);
      SacrificeRegistry.register(
          new SacrificeElementDef("fire", true, List.of("ignite"), "", "", "", 0));
      listener.onCharacterChat(chat);
      when(chat.getChannel()).thenReturn("");
      listener.onCharacterChat(chat);
      when(chat.getChannel()).thenReturn("global");
      listener.onCharacterChat(chat);
      when(chat.getChannel()).thenReturn(" RP ");
      when(chat.getMessage()).thenReturn("hello");
      listener.onCharacterChat(chat);
      when(chat.getMessage()).thenReturn("ignite");
      listener.onCharacterChat(chat);
      verify(chat, never()).setCancelled(true);
      SacrificeRegistry.setHideIncantation(true);
      listener.onCharacterChat(chat);
      verify(chat).setCancelled(true);
      rites.verify(() -> SacrificeRiteService.tryStart(player, "fire"), times(2));
      var death = mock(PlayerDeathEvent.class);
      listener.onDeath(death);
      when(death.getEntity()).thenReturn(player);
      listener.onDeath(death);
      rites.verify(() -> SacrificeRiteService.handleDeath(player));
      var quit = mock(PlayerQuitEvent.class);
      listener.onQuit(quit);
      when(quit.getPlayer()).thenReturn(player);
      listener.onQuit(quit);
      rites.verify(() -> SacrificeRiteService.cancelForPlayer(player.getUniqueId()));
    }
  }

  @Test
  void fillChestInteractionConsumesOnlyMainHandChestClicksAndExpiresRequests() {
    var listener = new ArtifactFillChestListener();
    var player = server.addPlayer();
    var event = mock(PlayerInteractEvent.class);
    when(event.getPlayer()).thenReturn(player);
    listener.onInteract(event);
    when(event.getAction()).thenReturn(Action.RIGHT_CLICK_BLOCK);
    listener.onInteract(event);
    when(event.getHand()).thenReturn(EquipmentSlot.HAND);
    listener.onInteract(event);
    var block = player.getWorld().getBlockAt(0, 60, 0);
    ArtifactFillChestService.arm(player.getUniqueId(), ArtifactFillChestService.Mode.ALL, null);
    when(event.getClickedBlock()).thenReturn(block);
    listener.onInteract(event);
    block.setType(Material.CHEST);
    try (var service = mockStatic(ArtifactFillChestService.class);
        var messages = mockStatic(Messages.class, i -> i.getArgument(0))) {
      service.when(() -> ArtifactFillChestService.isChestBlock(block)).thenReturn(true);
      var expired =
          new ArtifactFillChestService.Pending(ArtifactFillChestService.Mode.ALL, null, 0);
      service.when(() -> ArtifactFillChestService.get(player.getUniqueId())).thenReturn(expired);
      listener.onInteract(event);
      assertEquals("fillchest.expired", player.nextMessage());
      service.verify(() -> ArtifactFillChestService.clear(player.getUniqueId()));
      var ready =
          new ArtifactFillChestService.Pending(
              ArtifactFillChestService.Mode.ALL, null, Long.MAX_VALUE);
      service.when(() -> ArtifactFillChestService.get(player.getUniqueId())).thenReturn(ready);
      service.when(() -> ArtifactFillChestService.fill(block, ready)).thenReturn(27);
      listener.onInteract(event);
      verify(event).setCancelled(true);
      assertEquals("fillchest.done", player.nextMessage());
      service.verify(() -> ArtifactFillChestService.consume(player.getUniqueId()));
      listener.onQuit(new PlayerQuitEvent(player, "quit"));
      service.verify(() -> ArtifactFillChestService.clear(player.getUniqueId()), times(2));
    }
  }
}
