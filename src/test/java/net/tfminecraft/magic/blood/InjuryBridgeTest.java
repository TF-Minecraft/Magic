package net.tfminecraft.magic.blood;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.nio.file.Path;

import net.tfminecraft.magic.integration.RpCharactersBridge;
import net.tfminecraft.rpcharacters.RPCharacters;
import net.tfminecraft.rpcharacters.objects.RPCharacter;
import net.tfminecraft.rpcharacters.permadeath.PermadeathService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class InjuryBridgeTest {
  @TempDir Path temp;

  @Test
  void permanentInjuryNeedsTheActiveCharacter() {
    try (BloodFixture fx = new BloodFixture(temp);
        var rp = mockStatic(RPCharacters.class);
        var permadeath = mockStatic(PermadeathService.class)) {
      var player = fx.player("mage");
      assertFalse(RpCharactersBridge.applyPermanentInjury(player));
      RPCharacter character = mock(RPCharacter.class);
      when(character.getId()).thenReturn(BloodFixture.id("mage"));
      rp.when(() -> RPCharacters.getActiveCharacter(player)).thenReturn(character);
      assertFalse(RpCharactersBridge.applyPermanentInjury(player));
      permadeath.when(() -> PermadeathService.applyRandomPermanentInjury(player, character)).thenReturn(true);
      assertTrue(RpCharactersBridge.applyPermanentInjury(player));
    }
  }
}
