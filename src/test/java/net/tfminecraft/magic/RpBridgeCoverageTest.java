package net.tfminecraft.magic;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.*;
import net.tfminecraft.magic.artifact.sacrifice.*;
import net.tfminecraft.magic.integration.RpCharactersBridge;
import net.tfminecraft.rpcharacters.RPCharacters;
import net.tfminecraft.rpcharacters.objects.RPCharacter;
import net.tfminecraft.rpcharacters.permadeath.*;
import net.tfminecraft.tlibs.character.*;
import org.bukkit.entity.Player;
import org.bukkit.plugin.ServicePriority;
import org.junit.jupiter.api.*;
import org.mockbukkit.mockbukkit.*;

class RpBridgeCoverageTest {
  ServerMock server;
  CharacterIdentity identity;

  @BeforeEach
  void setup() {
    server = MockBukkit.mock();
    identity = mock(CharacterIdentity.class);
  }

  @AfterEach
  void stop() {
    MockBukkit.unmock();
  }

  @Test
  void pluginPresenceWithoutAProviderAndNullInputsHaveSafeFallback() {
    var player = server.addPlayer();
    MockBukkit.createMockPlugin("RPCharacters");
    try (var rp = mockStatic(RPCharacters.class)) {
      assertFalse(RpCharactersBridge.isAvailable());
      assertNull(RpCharactersBridge.getActiveCharacter(player));
      assertEquals("", RpCharactersBridge.resolveRealName(null));
      registerProvider();
      assertTrue(RpCharactersBridge.isAvailable());
      assertNull(RpCharactersBridge.getActiveCharacter(null));
      when(identity.currentSession(player)).thenReturn(Optional.empty());
      assertNull(RpCharactersBridge.getActiveCharacter(player));
      rp.verifyNoInteractions();
    }
  }

  @Test
  void ownResonanceScreenShowsTheRealNameNotTheMaskOrAlias() {
    var player = server.addPlayer();
    registerProvider();
    when(identity.displayName(player)).thenReturn("Masked Stranger");
    var character = mock(RPCharacter.class);
    when(character.getName()).thenReturn("Real Name");
    when(character.getEffectiveDisplayPlain()).thenReturn("Nickname");
    String name = RpCharactersBridge.resolveRealName(character);
    assertTrue(name.startsWith("\u00a7f"), name);
    assertEquals("Real Name", org.bukkit.ChatColor.stripColor(name));
    verify(identity, never()).displayName(any());
  }

  @Test
  void rpcModelsMustMatchTheReadySessionAndAreRecheckedAfterSwitching() {
    var player = server.addPlayer();
    registerProvider();
    UUID firstId = UUID.randomUUID(), secondId = UUID.randomUUID();
    when(identity.currentSession(player)).thenReturn(Optional.of(session(player, firstId, 1)));
    try (var rp = mockStatic(RPCharacters.class)) {
      assertNull(RpCharactersBridge.getActiveCharacter(player));
      RPCharacter character = mock(RPCharacter.class);
      rp.when(() -> RPCharacters.getActiveCharacter(player)).thenReturn(character);
      assertNull(RpCharactersBridge.getActiveCharacter(player));
      when(character.getId()).thenReturn("invalid");
      assertNull(RpCharactersBridge.getActiveCharacter(player));
      when(character.getId()).thenReturn(secondId.toString());
      assertNull(RpCharactersBridge.getActiveCharacter(player));
      when(character.getId()).thenReturn(firstId.toString().toUpperCase(Locale.ROOT));
      assertSame(character, RpCharactersBridge.getActiveCharacter(player));
      when(identity.currentSession(player)).thenReturn(Optional.of(session(player, secondId, 2)));
      assertNull(RpCharactersBridge.getActiveCharacter(player));
      when(character.getId()).thenReturn(secondId.toString());
      assertSame(character, RpCharactersBridge.getActiveCharacter(player));
      when(identity.currentSession(player)).thenReturn(Optional.empty());
      assertNull(RpCharactersBridge.getActiveCharacter(player));
    }
  }

  @Test
  void requiredCharacterConsequencesFailClosedAndApplyConfiguredInjuries() {
    var victim = server.addPlayer();
    var caster = server.addPlayer();
    var healing = new SacrificeTierDef("heal", 0, 0, 0, "healing", false);
    var permanent = new SacrificeTierDef("permanent", 0, 0, 0, "permanent", false);
    var fatal = new SacrificeTierDef("death", 0, 0, 0, "none", true);
    var none = new SacrificeTierDef("none", 0, 0, 0, null, false);
    try (var rp = mockStatic(RPCharacters.class);
        var registry = mockStatic(SacrificeRegistry.class);
        var consequences = mockStatic(PermadeathService.class)) {
      registry.when(SacrificeRegistry::isRequireRpCharacters).thenReturn(true);
      assertFalse(RpCharactersBridge.applySacrificeTier(victim, caster, null));
      assertFalse(RpCharactersBridge.applySacrificeTier(victim, caster, healing));
      registry.when(SacrificeRegistry::isRequireRpCharacters).thenReturn(false);
      assertTrue(RpCharactersBridge.applySacrificeTier(victim, caster, null));
      assertTrue(RpCharactersBridge.applySacrificeTier(victim, caster, healing));
      registerProvider();
      assertTrue(RpCharactersBridge.applySacrificeTier(victim, caster, healing));
      registry.when(SacrificeRegistry::isRequireRpCharacters).thenReturn(true);
      assertFalse(RpCharactersBridge.applySacrificeTier(victim, caster, healing));
      UUID id = UUID.randomUUID();
      when(identity.currentSession(victim)).thenReturn(Optional.of(session(victim, id, 1)));
      var character = mock(RPCharacter.class);
      when(character.getId()).thenReturn(id.toString());
      rp.when(() -> RPCharacters.getActiveCharacter(victim)).thenReturn(character);
      assertTrue(RpCharactersBridge.applySacrificeTier(victim, caster, healing));
      consequences.verify(() -> PermadeathService.applyRandomInjury(victim, character));
      assertTrue(RpCharactersBridge.applySacrificeTier(victim, caster, permanent));
      consequences.verify(() -> PermadeathService.applyRandomPermanentInjury(victim, character));
      consequences.when(() -> PermadeathService.killCharacter(victim, character, PermakillCause.SACRIFICE, caster))
          .thenReturn(true);
      assertTrue(RpCharactersBridge.applySacrificeTier(victim, caster, fatal));
      consequences.verify(() -> PermadeathService.killCharacter(victim, character, PermakillCause.SACRIFICE, caster));
      assertTrue(RpCharactersBridge.applySacrificeTier(victim, caster, none));
    }
  }

  @Test
  void refusedPermanentDeathNeverReportsSuccessfulSacrificeEvenWhenRpcIsOptional() {
    var victim = server.addPlayer();
    var caster = server.addPlayer();
    registerProvider();
    UUID id = UUID.randomUUID();
    when(identity.currentSession(victim)).thenReturn(Optional.of(session(victim, id, 1)));
    var fatal = new SacrificeTierDef("death", 0, 0, 0, "none", true);
    var character = mock(RPCharacter.class);
    when(character.getId()).thenReturn(id.toString());
    try (var rp = mockStatic(RPCharacters.class);
        var registry = mockStatic(SacrificeRegistry.class);
        var consequences = mockStatic(PermadeathService.class)) {
      rp.when(() -> RPCharacters.getActiveCharacter(victim)).thenReturn(character);
      consequences.when(() -> PermadeathService.killCharacter(victim, character, PermakillCause.SACRIFICE, caster))
          .thenReturn(false);
      registry.when(SacrificeRegistry::isRequireRpCharacters).thenReturn(true);
      assertFalse(RpCharactersBridge.applySacrificeTier(victim, caster, fatal));
      registry.when(SacrificeRegistry::isRequireRpCharacters).thenReturn(false);
      assertFalse(RpCharactersBridge.applySacrificeTier(victim, caster, fatal));
      consequences.verify(() -> PermadeathService.killCharacter(victim, character, PermakillCause.SACRIFICE, caster), times(2));
    }
  }

  private void registerProvider() {
    server.getServicesManager().register(CharacterIdentity.class, identity,
        MockBukkit.createMockPlugin("CharacterIdentityProvider"), ServicePriority.Normal);
  }

  private CharacterSession session(Player player, UUID characterId, long sessionId) {
    return new CharacterSession(new CharacterRef(player.getUniqueId(), characterId), sessionId);
  }
}
