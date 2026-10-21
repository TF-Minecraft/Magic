package net.tfminecraft.magic.listener;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.Map;
import java.util.UUID;
import net.tfminecraft.magic.artifact.sealed.SealedArtifactDef;
import net.tfminecraft.magic.artifact.sealed.SealedArtifactRegistry;
import net.tfminecraft.magic.artifact.sealed.SealedArtifactService;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.*;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;

class SealedArtifactListenerTest {
  final SealedArtifactDef seal = new SealedArtifactDef("random", "v.ECHO_SHARD", null, Map.of("common", 1.0));
  final ItemStack sealed = new ItemStack(Material.ECHO_SHARD);
  final ItemStack plain = new ItemStack(Material.STONE);
  ServerMock server;
  SealedArtifactService service;
  SealedArtifactListener listener;
  Player player;

  @BeforeEach
  void setup() {
    server = MockBukkit.mock();
    service = mock(SealedArtifactService.class);
    listener = new SealedArtifactListener(() -> service);
    player = mock(Player.class);
    when(player.getUniqueId()).thenReturn(UUID.randomUUID());
  }

  @AfterEach
  void cleanup() {
    MockBukkit.unmock();
  }

  PlayerInteractEvent event(Action action, EquipmentSlot hand, ItemStack item, Material block) {
    var event = mock(PlayerInteractEvent.class);
    when(event.getAction()).thenReturn(action);
    when(event.getHand()).thenReturn(hand);
    when(event.getItem()).thenReturn(item);
    when(event.getPlayer()).thenReturn(player);
    when(event.useItemInHand()).thenReturn(Event.Result.DEFAULT);
    if (block != null) {
      var clicked = mock(Block.class);
      when(clicked.getType()).thenReturn(block);
      when(event.getClickedBlock()).thenReturn(clicked);
    }
    return event;
  }

  void assertOpened(PlayerInteractEvent event, EquipmentSlot hand) {
    server.getScheduler().performOneTick();
    listener.onInteract(event);
    verify(event).setUseItemInHand(Event.Result.DENY);
    verify(event).setUseInteractedBlock(Event.Result.DENY);
    verify(service).open(player, hand, seal);
    clearInvocations(service);
  }

  void assertIgnored(PlayerInteractEvent event) {
    listener.onInteract(event);
    verify(event, never()).setUseItemInHand(any());
    verifyNoInteractions(service);
  }

  @Test
  void rightClickBreaksTheSealInTheClickingHand() {
    assertNotNull(new SealedArtifactListener());
    try (var registry = mockStatic(SealedArtifactRegistry.class)) {
      registry.when(() -> SealedArtifactRegistry.match(sealed)).thenReturn(seal);
      assertOpened(event(Action.RIGHT_CLICK_AIR, EquipmentSlot.HAND, sealed, null), EquipmentSlot.HAND);
      assertOpened(event(Action.RIGHT_CLICK_BLOCK, EquipmentSlot.HAND, sealed, Material.STONE), EquipmentSlot.HAND);
      // A block in the event of an air click is not used.
      assertOpened(event(Action.RIGHT_CLICK_AIR, EquipmentSlot.HAND, sealed, Material.CHEST), EquipmentSlot.HAND);
      // Sneaking opens it even on a usable block.
      when(player.isSneaking()).thenReturn(true);
      assertOpened(event(Action.RIGHT_CLICK_BLOCK, EquipmentSlot.HAND, sealed, Material.CHEST), EquipmentSlot.HAND);
      when(player.isSneaking()).thenReturn(false);
      assertOpened(event(Action.RIGHT_CLICK_AIR, EquipmentSlot.OFF_HAND, sealed, null), EquipmentSlot.OFF_HAND);
    }
  }

  @Test
  void oneClickBreaksOneSealEvenWhenItFiresSeveralEvents() {
    try (var registry = mockStatic(SealedArtifactRegistry.class)) {
      registry.when(() -> SealedArtifactRegistry.match(sealed)).thenReturn(seal);
      assertOpened(event(Action.RIGHT_CLICK_BLOCK, EquipmentSlot.HAND, sealed, Material.STONE), EquipmentSlot.HAND);
      // The same click's air and off-hand events, still in this tick, are denied but break nothing.
      for (var extra :
          new PlayerInteractEvent[] {
            event(Action.RIGHT_CLICK_AIR, EquipmentSlot.HAND, sealed, null),
            event(Action.RIGHT_CLICK_AIR, EquipmentSlot.OFF_HAND, sealed, null)
          }) {
        listener.onInteract(extra);
        verify(extra).setUseItemInHand(Event.Result.DENY);
        verifyNoInteractions(service);
      }
      // Another player in the same tick still gets theirs.
      var other = player;
      player = mock(Player.class);
      when(player.getUniqueId()).thenReturn(UUID.randomUUID());
      var theirs = event(Action.RIGHT_CLICK_AIR, EquipmentSlot.HAND, sealed, null);
      listener.onInteract(theirs);
      verify(service).open(player, EquipmentSlot.HAND, seal);
      clearInvocations(service);
      // And the first player's next click, a tick later, works again.
      player = other;
      assertOpened(event(Action.RIGHT_CLICK_AIR, EquipmentSlot.HAND, sealed, null), EquipmentSlot.HAND);
    }
  }

  @Test
  void otherClicksItemsAndBlockedUsesAreLeftAlone() {
    try (var registry = mockStatic(SealedArtifactRegistry.class)) {
      registry.when(() -> SealedArtifactRegistry.match(sealed)).thenReturn(seal);
      assertIgnored(event(Action.LEFT_CLICK_AIR, EquipmentSlot.HAND, sealed, null));
      assertIgnored(event(Action.PHYSICAL, EquipmentSlot.HAND, sealed, Material.STONE));
      assertIgnored(event(Action.RIGHT_CLICK_AIR, EquipmentSlot.HAND, plain, null));
      assertIgnored(event(Action.RIGHT_CLICK_AIR, null, sealed, null));
      // Chests, doors and stations still open normally.
      assertIgnored(event(Action.RIGHT_CLICK_BLOCK, EquipmentSlot.HAND, sealed, Material.CHEST));
      var denied = event(Action.RIGHT_CLICK_AIR, EquipmentSlot.HAND, sealed, null);
      when(denied.useItemInHand()).thenReturn(Event.Result.DENY);
      assertIgnored(denied);
    }
  }
}
