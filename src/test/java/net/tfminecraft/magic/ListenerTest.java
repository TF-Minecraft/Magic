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
}
