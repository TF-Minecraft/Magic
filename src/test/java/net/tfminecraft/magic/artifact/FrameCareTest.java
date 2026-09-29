package net.tfminecraft.magic.artifact;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.*;
import net.tfminecraft.magic.Magic;
import net.tfminecraft.magic.tick.MagicTickContext;
import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.*;
import org.mockbukkit.mockbukkit.*;

class FrameCareTest {
  ServerMock server;

  @BeforeEach
  void setup() {
    server = MockBukkit.mock();
    Magic.plugin = mock(Magic.class);
    when(Magic.plugin.namespace()).thenReturn("magic");
  }

  @AfterEach
  void cleanup() {
    Magic.plugin = null;
    MockBukkit.unmock();
  }

  @Test
  void framesOnlyRewriteValidArtifactItemsWhenCareChanged() {
    ArtifactFrameCareTicker.tickFrame(null, ArtifactCareStore.Persist.ALWAYS);
    var frame = mock(ItemFrame.class);
    ArtifactFrameCareTicker.tickFrame(frame, ArtifactCareStore.Persist.ALWAYS);
    when(frame.isValid()).thenReturn(true);
    ArtifactFrameCareTicker.tickFrame(frame, ArtifactCareStore.Persist.ALWAYS);
    var item = new ItemStack(Material.STONE);
    ArtifactIds.writeNew(item);
    when(frame.getItem()).thenReturn(item);
    try (var care = mockStatic(ArtifactCareStore.class)) {
      ArtifactFrameCareTicker.tickFrame(frame, ArtifactCareStore.Persist.ALWAYS);
      verify(frame, never()).setItem(any(), anyBoolean());
      care.when(
              () ->
                  ArtifactCareStore.apply(
                      eq(item), eq(true), anyLong(), eq(ArtifactCareStore.Persist.ALWAYS)))
          .thenReturn(true);
      ArtifactFrameCareTicker.tickFrame(frame, ArtifactCareStore.Persist.ALWAYS);
      verify(frame).setItem(item, false);
    }
  }

  @Test
  void tickingCyclesOnlinePlayersAndClicksOnlyInspectFrames() {
    var ticker = new ArtifactFrameCareTicker();
    ticker.onTick(MagicTickContext.of(1));
    var player = mock(Player.class);
    var frame = mock(ItemFrame.class);
    when(frame.isValid()).thenReturn(true);
    when(player.getNearbyEntities(anyDouble(), anyDouble(), anyDouble()))
        .thenReturn(List.of(frame, mock(Zombie.class)));
    try (var bukkit = mockStatic(Bukkit.class, CALLS_REAL_METHODS)) {
      bukkit.when(Bukkit::getOnlinePlayers).thenReturn(List.of(player));
      ticker.onTick(MagicTickContext.of(2));
      ticker.onTick(MagicTickContext.of(3));
      verify(player, times(2)).getNearbyEntities(48, 48, 48);
      ticker.onFrameInteract(new PlayerInteractEntityEvent(player, frame));
      ticker.onFrameInteract(new PlayerInteractEntityEvent(player, mock(Zombie.class)));
      verify(frame, times(3)).getItem();
      bukkit.when(Bukkit::getOnlinePlayers).thenReturn(List.of());
      ticker.onTick(MagicTickContext.of(4));
    }
  }
}
