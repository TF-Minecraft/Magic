package net.tfminecraft.magic;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.*;
import net.tfminecraft.magic.artifact.sacrifice.*;
import net.tfminecraft.magic.integration.RpCharactersBridge;
import net.tfminecraft.rpcharacters.RPCharacters;
import net.tfminecraft.rpcharacters.identity.DisplayIdentityService;
import net.tfminecraft.rpcharacters.mail.CharacterMailTarget;
import net.tfminecraft.rpcharacters.managers.PlayerManager;
import net.tfminecraft.rpcharacters.objects.*;
import net.tfminecraft.rpcharacters.permadeath.*;
import org.bukkit.Bukkit;
import org.bukkit.plugin.PluginManager;
import org.junit.jupiter.api.*;
import org.mockbukkit.mockbukkit.*;

class RpBridgeCoverageTest {
  ServerMock server;

  @BeforeEach
  void setup() {
    server = MockBukkit.mock();
  }

  @AfterEach
  void stop() {
    MockBukkit.unmock();
  }

  @Test
  void missingDependencyAndNullInputsHaveSafeFallback() {
    try (var b = mockStatic(Bukkit.class, CALLS_REAL_METHODS)) {
      var plugins = mock(PluginManager.class);
      b.when(Bukkit::getPluginManager).thenReturn(plugins);
      var p = server.addPlayer();
      assertFalse(RpCharactersBridge.isAvailable());
      assertNull(RpCharactersBridge.getActiveCharacter(p));
      assertEquals("", RpCharactersBridge.resolveDisplayTab(p));
      assertEquals("", RpCharactersBridge.resolveCharacterName("id"));
      when(plugins.isPluginEnabled("RPCharacters")).thenReturn(true);
      assertNull(RpCharactersBridge.getActiveCharacter(null));
      assertEquals("", RpCharactersBridge.resolveDisplayTab(null));
      assertEquals("", RpCharactersBridge.resolveCharacterName(null));
      assertEquals("", RpCharactersBridge.resolveCharacterName(" "));
    }
  }

  @Test
  void onlineCharacterNamesAndOfflineMailTargetsResolve() {
    var p = server.addPlayer();
    try (var b = mockStatic(Bukkit.class, CALLS_REAL_METHODS);
        var rp = mockStatic(RPCharacters.class);
        var pm = mockStatic(PlayerManager.class);
        var names = mockStatic(DisplayIdentityService.class)) {
      var plugins = mock(PluginManager.class);
      when(plugins.isPluginEnabled("RPCharacters")).thenReturn(true);
      b.when(Bukkit::getPluginManager).thenReturn(plugins);
      var character = mock(RPCharacter.class);
      rp.when(() -> RPCharacters.getActiveCharacter(p)).thenReturn(character);
      assertSame(character, RpCharactersBridge.getActiveCharacter(p));
      names.when(() -> DisplayIdentityService.resolveDisplayTab(p)).thenReturn("name");
      names.when(() -> DisplayIdentityService.ensureDisplayTabWhite("name")).thenReturn("§fName");
      assertEquals("§fName", RpCharactersBridge.resolveDisplayTab(p));
      assertEquals("", RpCharactersBridge.resolveCharacterName("id"));
      var data = mock(PlayerData.class);
      pm.when(() -> PlayerManager.get(p)).thenReturn(data);
      assertEquals("", RpCharactersBridge.resolveCharacterName("id"));
      when(data.getCharacterById("id")).thenReturn(character);
      assertEquals("", RpCharactersBridge.resolveCharacterName("id"));
      when(character.getName()).thenReturn(" ");
      assertEquals("", RpCharactersBridge.resolveCharacterName("id"));
      when(character.getName()).thenReturn("Online name");
      assertEquals("Online name", RpCharactersBridge.resolveCharacterName("id"));
      when(data.getCharacterById("id")).thenReturn(null);
      var wrong = mock(CharacterMailTarget.class);
      when(wrong.getCharacterId()).thenReturn("other");
      var unnamed = mock(CharacterMailTarget.class);
      when(unnamed.getCharacterId()).thenReturn("id");
      var blank = mock(CharacterMailTarget.class);
      when(blank.getCharacterId()).thenReturn("id");
      when(blank.getDisplayPlain()).thenReturn(" ");
      var match = mock(CharacterMailTarget.class);
      when(match.getCharacterId()).thenReturn("id");
      when(match.getDisplayPlain()).thenReturn("Offline name");
      rp.when(RPCharacters::listMailTargets)
          .thenReturn(Arrays.asList(null, wrong, unnamed, blank, match));
      assertEquals("Offline name", RpCharactersBridge.resolveCharacterName("id"));
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
    try (var b = mockStatic(Bukkit.class, CALLS_REAL_METHODS);
        var rp = mockStatic(RPCharacters.class);
        var registry = mockStatic(SacrificeRegistry.class);
        var consequences = mockStatic(PermadeathService.class)) {
      var plugins = mock(PluginManager.class);
      b.when(Bukkit::getPluginManager).thenReturn(plugins);
      registry.when(SacrificeRegistry::isRequireRpCharacters).thenReturn(true);
      assertFalse(RpCharactersBridge.applySacrificeTier(victim, caster, null));
      assertFalse(RpCharactersBridge.applySacrificeTier(victim, caster, healing));
      registry.when(SacrificeRegistry::isRequireRpCharacters).thenReturn(false);
      assertTrue(RpCharactersBridge.applySacrificeTier(victim, caster, null));
      assertTrue(RpCharactersBridge.applySacrificeTier(victim, caster, healing));
      when(plugins.isPluginEnabled("RPCharacters")).thenReturn(true);
      assertTrue(RpCharactersBridge.applySacrificeTier(victim, caster, healing));
      registry.when(SacrificeRegistry::isRequireRpCharacters).thenReturn(true);
      assertFalse(RpCharactersBridge.applySacrificeTier(victim, caster, healing));
      var character = mock(RPCharacter.class);
      rp.when(() -> RPCharacters.getActiveCharacter(victim)).thenReturn(character);
      assertTrue(RpCharactersBridge.applySacrificeTier(victim, caster, healing));
      consequences.verify(() -> PermadeathService.applyRandomInjury(victim, character));
      assertTrue(RpCharactersBridge.applySacrificeTier(victim, caster, permanent));
      consequences.verify(() -> PermadeathService.applyRandomPermanentInjury(victim, character));
      assertTrue(RpCharactersBridge.applySacrificeTier(victim, caster, fatal));
      consequences.verify(
          () ->
              PermadeathService.killCharacter(victim, character, PermakillCause.SACRIFICE, caster));
      assertTrue(RpCharactersBridge.applySacrificeTier(victim, caster, none));
    }
  }
}
