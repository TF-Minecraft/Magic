package net.tfminecraft.magic.integration;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

class KnockoutMoveTest {
  ServerMock server;
  PlayerMock player;
  Location to;

  /** Stands in for RPCharacters' knockout manager with moveDowned. */
  public static final class FakeKnockouts {
    boolean answer;
    Location moved;

    public boolean moveDowned(Player player, Location to) {
      moved = to;
      return answer;
    }
  }

  @BeforeEach
  void setup() {
    server = MockBukkit.mock();
    player = server.addPlayer();
    to = new Location(player.getWorld(), 7.5, 70, 3.5);
  }

  @AfterEach
  void cleanup() {
    RpCharactersBridge.knockoutManager = net.tfminecraft.rpcharacters.RPCharacters::getPvpKnockoutManager;
    MockBukkit.unmock();
  }

  @Test
  void movesThroughRpCharactersWhenItCan() {
    FakeKnockouts knockouts = new FakeKnockouts();
    RpCharactersBridge.knockoutManager = () -> knockouts;
    assertFalse(RpCharactersBridge.moveDowned(player, to));
    assertSame(to, knockouts.moved);
    knockouts.answer = true;
    assertTrue(RpCharactersBridge.moveDowned(player, to));
    assertNotEquals(7.5, player.getLocation().getX());
  }

  @Test
  void downedSacrificeAndHealingInjuryNeedRpCharacters() {
    try (var rp = mockStatic(net.tfminecraft.rpcharacters.RPCharacters.class);
        var bridge = mockStatic(RpCharactersBridge.class, CALLS_REAL_METHODS);
        var death = mockStatic(net.tfminecraft.rpcharacters.permadeath.PermadeathService.class)) {
      assertFalse(RpCharactersBridge.isDown(player));
      var knockouts = mock(net.tfminecraft.rpcharacters.pvp.PvpKnockoutManager.class);
      rp.when(net.tfminecraft.rpcharacters.RPCharacters::getPvpKnockoutManager).thenReturn(knockouts);
      assertFalse(RpCharactersBridge.isDown(player));
      when(knockouts.isDown(player)).thenReturn(true);
      assertTrue(RpCharactersBridge.isDown(player));

      bridge.when(() -> RpCharactersBridge.getActiveCharacter(any())).thenReturn(null);
      assertFalse(RpCharactersBridge.sacrifice(player, player));
      assertFalse(RpCharactersBridge.applyHealingInjury(player));
      var character = mock(net.tfminecraft.rpcharacters.objects.RPCharacter.class);
      bridge.when(() -> RpCharactersBridge.getActiveCharacter(any())).thenReturn(character);
      assertFalse(RpCharactersBridge.sacrifice(player, player));
      assertFalse(RpCharactersBridge.applyHealingInjury(player));
      death.when(() -> net.tfminecraft.rpcharacters.permadeath.PermadeathService.killCharacter(any(), any(), any(), any()))
          .thenReturn(true);
      death.when(() -> net.tfminecraft.rpcharacters.permadeath.PermadeathService.applyRandomInjury(any(), any()))
          .thenReturn(true);
      assertTrue(RpCharactersBridge.sacrifice(player, player));
      assertTrue(RpCharactersBridge.applyHealingInjury(player));
    }
  }

  @Test
  void olderRpCharactersOrNoneOnlyTeleports() {
    RpCharactersBridge.knockoutManager = Object::new;
    assertTrue(RpCharactersBridge.moveDowned(player, to));
    assertEquals(7.5, player.getLocation().getX());
    RpCharactersBridge.knockoutManager = () -> null;
    Location other = to.clone().add(1, 0, 0);
    assertTrue(RpCharactersBridge.moveDowned(player, other));
    assertEquals(8.5, player.getLocation().getX());
  }
}
