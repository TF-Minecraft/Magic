package net.tfminecraft.magic;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.*;
import net.tfminecraft.magic.gear.*;
import net.tfminecraft.magic.listener.*;
import net.tfminecraft.magic.manager.ResonanceGuiManager;
import net.tfminecraft.magic.profile.MagicProfileService;
import net.tfminecraft.magic.session.ResonanceSessionManager;
import net.tfminecraft.rpcharacters.lifecycle.CharacterActivatedEvent;
import net.tfminecraft.rpcharacters.objects.RPCharacter;
import org.bukkit.Material;
import org.bukkit.block.DoubleChest;
import org.bukkit.entity.minecart.StorageMinecart;
import org.bukkit.entity.*;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.inventory.*;
import org.bukkit.event.player.*;
import org.bukkit.inventory.*;
import org.junit.jupiter.api.*;
import org.mockbukkit.mockbukkit.*;

class ListenerTest {
  ServerMock server;

  @BeforeEach
  void setup() {
    server = MockBukkit.mock();
    Magic.plugin = mock(Magic.class, RETURNS_DEEP_STUBS);
    when(Magic.plugin.getServer()).thenReturn(server);
    when(Magic.plugin.isEnabled()).thenReturn(true);
    GearCache.alignmentEnabled = true;
  }

  @AfterEach
  void cleanup() {
    Magic.plugin = null;
    MockBukkit.unmock();
    GearCache.alignmentEnabled = false;
  }

  @Test
  void heldAndDropRefreshWaitForSettledInventoryAndIgnoreOfflineOrRemovedItems() {
    var listener = new GearRefreshListener();
    var player = server.addPlayer();
    var input = new ItemStack(Material.STICK);
    var rebuilt = new ItemStack(Material.BLAZE_ROD);
    player.getInventory().setItem(2, input);
    try (var refresh = mockStatic(GearRefresher.class)) {
      refresh.when(() -> GearRefresher.refreshIfOutdated(input, player)).thenReturn(rebuilt);
      listener.onItemHeld(new PlayerItemHeldEvent(player, 0, 2));
      assertEquals(input, player.getInventory().getItem(2));
      server.getScheduler().performOneTick();
      assertEquals(rebuilt, player.getInventory().getItem(2));
      listener.onItemHeld(new PlayerItemHeldEvent(player, 2, 3));
      server.getScheduler().performOneTick();
      assertNull(player.getInventory().getItem(3));
      Item item = mock(Item.class);
      when(item.isValid()).thenReturn(true);
      when(item.getItemStack()).thenReturn(input);
      listener.onDrop(new PlayerDropItemEvent(player, item));
      server.getScheduler().performOneTick();
      verify(item).setItemStack(rebuilt);
      refresh.when(() -> GearRefresher.refreshIfOutdated(input, player)).thenReturn(null);
      listener.onDrop(new PlayerDropItemEvent(player, item));
      server.getScheduler().performOneTick();
      when(item.isValid()).thenReturn(false);
      listener.onDrop(new PlayerDropItemEvent(player, item));
      server.getScheduler().performOneTick();
      verify(item, times(1)).setItemStack(any());
      listener.onItemHeld(new PlayerItemHeldEvent(player, 2, 3));
      player.disconnect();
      server.getScheduler().performOneTick();
      Magic.plugin = null;
      listener.onItemHeld(new PlayerItemHeldEvent(player, 2, 3));
    }
  }

  @Test
  void clickRefreshKeepsOriginalInventoryWhenPlayerClosesView() {
    var listener = new GearRefreshListener();
    var player = server.addPlayer();
    Inventory original = server.createInventory(null, 9);
    var old = new ItemStack(Material.STICK);
    var rebuilt = new ItemStack(Material.BLAZE_ROD);
    original.setItem(1, old);
    var click = mock(InventoryClickEvent.class);
    when(click.getWhoClicked()).thenReturn(player);
    when(click.getClickedInventory()).thenReturn(original);
    when(click.getSlot()).thenReturn(1);
    try (var refresh = mockStatic(GearRefresher.class)) {
      refresh.when(() -> GearRefresher.refreshIfOutdated(old, player)).thenReturn(rebuilt);
      listener.onClick(click);
      player.closeInventory();
      server.getScheduler().performOneTick();
      assertEquals(rebuilt, original.getItem(1));
      listener.onClick(click);
      server.getScheduler().performOneTick();
      player.getOpenInventory().setCursor(old);
      listener.onClick(click);
      server.getScheduler().performOneTick();
      assertEquals(rebuilt, player.getOpenInventory().getCursor());
      when(click.getClickedInventory()).thenReturn(null);
      listener.onClick(click);
      server.getScheduler().performOneTick();
      when(click.getClickedInventory()).thenReturn(original);
      for (int invalid : new int[] {-1, 9}) {
        when(click.getSlot()).thenReturn(invalid);
        listener.onClick(click);
        server.getScheduler().performOneTick();
      }
      when(click.getWhoClicked()).thenReturn(mock(HumanEntity.class));
      listener.onClick(click);
      when(click.getWhoClicked()).thenReturn(player);
      listener.onClick(click);
      player.disconnect();
      server.getScheduler().performOneTick();
    }
  }

  @Test
  void alignmentEventsDeferResyncAndRespectDisabledOfflineAndNonPlayerCases() {
    var listener = new GearAlignmentListener();
    var player = server.addPlayer();
    var sessions = new ResonanceSessionManager();
    var session = sessions.getOrCreate(player);
    when(Magic.plugin.getResonanceGuiManager().getSessionManager()).thenReturn(sessions);
    listener.onItemHeld(new PlayerItemHeldEvent(player, 0, 1));
    listener.onSwapHands(new PlayerSwapHandItemsEvent(player, null, null));
    listener.onDrop(new PlayerDropItemEvent(player, mock(Item.class)));
    var click = mock(InventoryClickEvent.class);
    when(click.getWhoClicked()).thenReturn(player);
    listener.onInventoryClick(click);
    var drag = mock(InventoryDragEvent.class);
    when(drag.getWhoClicked()).thenReturn(player);
    listener.onInventoryDrag(drag);
    listener.onPickup(new EntityPickupItemEvent(player, mock(Item.class), 0));
    verify(Magic.plugin, never()).syncSpellModifiers(any(), any());
    server.getScheduler().performOneTick();
    verify(Magic.plugin, times(6)).syncSpellModifiers(player, session);
    when(click.getWhoClicked()).thenReturn(mock(HumanEntity.class));
    listener.onInventoryClick(click);
    when(drag.getWhoClicked()).thenReturn(mock(HumanEntity.class));
    listener.onInventoryDrag(drag);
    listener.onPickup(new EntityPickupItemEvent(mock(LivingEntity.class), mock(Item.class), 0));
    GearCache.alignmentEnabled = false;
    listener.onItemHeld(new PlayerItemHeldEvent(player, 0, 1));
    GearCache.alignmentEnabled = true;
    listener.onItemHeld(new PlayerItemHeldEvent(player, 0, 1));
    player.disconnect();
    server.getScheduler().performOneTick();
    verify(Magic.plugin, times(6)).syncSpellModifiers(player, session);
    var event = mock(PlayerItemHeldEvent.class);
    listener.onItemHeld(event);
    Magic.plugin = null;
    listener.onItemHeld(new PlayerItemHeldEvent(player, 0, 1));
  }

  @Test
  void roleplayCharacterEventsSavePreviousThenActivateAndCloseOldGui() {
    var player = server.addPlayer();
    var profile = mock(MagicProfileService.class);
    var gui = mock(ResonanceGuiManager.class, RETURNS_DEEP_STUBS);
    var listener = new MagicSessionListener(profile, gui);
    var event = mock(CharacterActivatedEvent.class);
    listener.onCharacterActivated(event);
    when(event.getOwner()).thenReturn(player);
    listener.onCharacterActivated(event);
    var character = mock(RPCharacter.class);
    when(character.getId()).thenReturn("next");
    when(event.getCharacter()).thenReturn(character);
    listener.onCharacterActivated(event);
    verify(profile).activate(player, character);
    verify(gui).closeIfCharacterChanged(player, "next");
    var previous = mock(RPCharacter.class);
    when(event.getPrevious()).thenReturn(previous);
    listener.onCharacterActivated(event);
    verify(profile).savePrevious(player, previous);
    listener.onPlayerQuit(new PlayerQuitEvent(player, "quit"));
    verify(profile).deactivate(player);
    verify(Magic.plugin).clearSpellModifiers(player);
  }

  @Test
  void joinSweepRefreshesInventoryOffhandAndEnderChestNextTick() {
    var listener = new GearRefreshListener();
    var player = server.addPlayer();
    var old = new ItemStack(Material.STICK);
    var rebuilt = new ItemStack(Material.BLAZE_ROD);
    player.getInventory().setItem(4, old);
    player.getInventory().setItemInOffHand(old);
    player.getInventory().setItem(5, new ItemStack(Material.DIRT));
    player.getEnderChest().setItem(1, old);
    try (var refresh = mockStatic(GearRefresher.class)) {
      refresh.when(() -> GearRefresher.refreshIfOutdated(old, player)).thenReturn(rebuilt);
      var join = mock(PlayerJoinEvent.class);
      when(join.getPlayer()).thenReturn(player);
      listener.onJoin(join);
      assertEquals(old, player.getInventory().getItem(4));
      server.getScheduler().performOneTick();
      assertEquals(rebuilt, player.getInventory().getItem(4));
      assertEquals(rebuilt, player.getInventory().getItemInOffHand());
      assertEquals(Material.DIRT, player.getInventory().getItem(5).getType());
      assertEquals(rebuilt, player.getEnderChest().getItem(1));
      Player gone = mock(Player.class);
      when(gone.isOnline()).thenReturn(false);
      when(join.getPlayer()).thenReturn(gone);
      listener.onJoin(join);
      server.getScheduler().performOneTick();
      verify(gone, never()).getInventory();
    }
  }

  @Test
  void openSweepOnlyTouchesWorldStorageOpenedByPlayers() {
    var listener = new GearRefreshListener();
    var player = server.addPlayer();
    var old = new ItemStack(Material.STICK);
    var rebuilt = new ItemStack(Material.BLAZE_ROD);
    try (var refresh = mockStatic(GearRefresher.class)) {
      refresh.when(() -> GearRefresher.refreshIfOutdated(old, player)).thenReturn(rebuilt);
      Inventory menu = mock(Inventory.class);
      var open = mock(InventoryOpenEvent.class);
      when(open.getInventory()).thenReturn(menu);
      when(open.getPlayer()).thenReturn(player);
      listener.onOpen(open);
      server.getScheduler().performOneTick();
      verify(menu, never()).getContents();
      Inventory chest = mock(Inventory.class);
      when(chest.getHolder(false)).thenReturn(mock(BlockInventoryHolder.class));
      when(chest.getContents()).thenReturn(new ItemStack[] {null, old});
      when(open.getInventory()).thenReturn(chest);
      when(open.getPlayer()).thenReturn(mock(HumanEntity.class));
      listener.onOpen(open);
      server.getScheduler().performOneTick();
      verify(chest, never()).getContents();
      when(open.getPlayer()).thenReturn(player);
      listener.onOpen(open);
      verify(chest, never()).setItem(anyInt(), any());
      server.getScheduler().performOneTick();
      verify(chest).setItem(1, rebuilt);
      verify(chest, never()).setItem(eq(0), any());
    }
  }

  @Test
  void worldStorageCoversBlocksDoubleChestsAndEntitiesButNotMenus() {
    assertTrue(GearRefreshListener.isWorldStorage(mock(BlockInventoryHolder.class)));
    assertTrue(GearRefreshListener.isWorldStorage(mock(DoubleChest.class)));
    assertTrue(GearRefreshListener.isWorldStorage(mock(StorageMinecart.class)));
    assertFalse(GearRefreshListener.isWorldStorage(mock(InventoryHolder.class)));
    assertFalse(GearRefreshListener.isWorldStorage(null));
  }
}
