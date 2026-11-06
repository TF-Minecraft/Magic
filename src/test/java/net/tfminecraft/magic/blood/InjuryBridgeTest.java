package net.tfminecraft.magic.blood;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import net.tfminecraft.magic.integration.RpCharactersBridge;
import net.tfminecraft.rpcharacters.RPCharacters;
import net.tfminecraft.rpcharacters.loaders.TraitLoader;
import net.tfminecraft.rpcharacters.objects.RPCharacter;
import net.tfminecraft.rpcharacters.objects.trait.Trait;
import net.tfminecraft.rpcharacters.utils.TraitChangeService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class InjuryBridgeTest {
  @TempDir Path temp;

  @Test
  void aTraitGoesToTheActiveCharacterOnceAndIsSeenThere() {
    Trait manaless = mock(Trait.class);
    when(manaless.getId()).thenReturn("manaless");
    Trait other = mock(Trait.class);
    when(other.getId()).thenReturn("one_handed");
    List<Trait> owned = new ArrayList<>(List.of(other));
    try (BloodFixture fx = new BloodFixture(temp);
        var rp = mockStatic(RPCharacters.class);
        var changes = mockStatic(TraitChangeService.class)) {
      TraitLoader.oList.add(manaless);
      var player = fx.player("mage");
      assertFalse(RpCharactersBridge.applyTrait(player, "manaless"), "No active character");
      assertFalse(RpCharactersBridge.hasTrait(player, "manaless"));
      RPCharacter character = mock(RPCharacter.class);
      when(character.getId()).thenReturn(BloodFixture.id("mage"));
      when(character.getTraits()).thenReturn(owned);
      rp.when(() -> RPCharacters.getActiveCharacter(player)).thenReturn(character);
      assertFalse(RpCharactersBridge.applyTrait(player, "nonexistent"));
      assertFalse(RpCharactersBridge.applyTrait(player, null));
      assertFalse(RpCharactersBridge.hasTrait(player, null));
      assertFalse(RpCharactersBridge.hasTrait(player, "manaless"));
      assertTrue(RpCharactersBridge.applyTrait(player, "manaless"));
      changes.verify(() -> TraitChangeService.addTrait(player, character, manaless));
      changes.verify(() -> TraitChangeService.sendGainedMessage(player, manaless));
      owned.add(manaless);
      assertTrue(RpCharactersBridge.hasTrait(player, "MANALESS"));
      assertTrue(RpCharactersBridge.applyTrait(player, "manaless"), "Already marked");
      changes.verify(() -> TraitChangeService.addTrait(any(), any(), any()), times(1));
    } finally {
      TraitLoader.oList.remove(manaless);
    }
  }
}
