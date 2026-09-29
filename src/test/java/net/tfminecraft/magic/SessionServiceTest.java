package net.tfminecraft.magic;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.nio.file.Path;
import java.util.*;
import java.util.logging.Logger;
import net.tfminecraft.magic.integration.RpCharactersBridge;
import net.tfminecraft.magic.model.ElementDef;
import net.tfminecraft.magic.profile.*;
import net.tfminecraft.magic.registry.ElementRegistry;
import net.tfminecraft.magic.service.*;
import net.tfminecraft.magic.session.*;
import net.tfminecraft.rpcharacters.objects.RPCharacter;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.mockbukkit.mockbukkit.*;

class SessionServiceTest {
  @TempDir Path temp;
  ServerMock server;
  ResonanceSessionManager sessions;
  MagicProfileStore store;
  MagicProfileService service;

  @BeforeEach
  void setup() {
    server = MockBukkit.mock();
    Magic.plugin = mock(Magic.class);
    when(Magic.plugin.getLogger()).thenReturn(Logger.getAnonymousLogger());
    sessions = new ResonanceSessionManager();
    store = new MagicProfileStore(temp.toFile());
    service = new MagicProfileService(store, sessions);
    when(Magic.plugin.getProfileService()).thenReturn(service);
    ElementRegistry.clear();
    ElementRegistry.register(DomainTest.element("fire"));
    Cache.defaultEquilibrium = 0;
    Cache.defaultCastMode = "flow";
    Cache.debug = false;
    Cache.secondsPerHour = 1;
    Cache.tickIntervalTicks = 20;
    GuiCache.equilibriumMin = -100;
    GuiCache.equilibriumMax = 100;
    Cache.equilibriumPassiveCorruptPerHour = .05;
    Cache.equilibriumCorruptCompoundStrength = 1.25;
    Cache.equilibriumTranquilityDecayPerHour = .03;
  }

  @AfterEach
  void cleanup() {
    Magic.plugin = null;
    MockBukkit.unmock();
    ElementRegistry.clear();
    Cache.debug = false;
    Cache.secondsPerHour = 3600;
  }

  RPCharacter character(String id) {
    RPCharacter character = mock(RPCharacter.class);
    when(character.getId()).thenReturn(id);
    return character;
  }

  @Test
  void sessionManagerTracksPlayersAndClearsCharacterIdentity() {
    var player = server.addPlayer();
    assertNull(sessions.get(null));
    assertNull(sessions.get(player));
    assertNull(sessions.getLoadedCharacterId(null));
    sessions.setLoadedCharacterId(null, "id");
    var first = sessions.getOrCreate(player);
    assertSame(first, sessions.getOrCreate(player));
    assertSame(first, sessions.get(player));
    sessions.setLoadedCharacterId(player, "id");
    assertEquals("id", sessions.getLoadedCharacterId(player));
    sessions.setLoadedCharacterId(player, null);
    assertNull(sessions.getLoadedCharacterId(player));
    sessions.setLoadedCharacterId(player, " ");
    assertNull(sessions.getLoadedCharacterId(player));
    List<Object> seen = new ArrayList<>();
    sessions.forEachOnlineSession(
        (p, s) -> {
          assertSame(player, p);
          assertSame(first, s);
          seen.add(s);
        });
    assertEquals(1, seen.size());
    player.disconnect();
    sessions.forEachOnlineSession((p, s) -> fail("Offline sessions must not tick"));
    sessions.remove(null);
    sessions.remove(player.getUniqueId());
    assertNull(sessions.get(player));
  }

  @Test
  void activationCreatesRestoresAndAvoidsOverwritingLiveSession() {
    var player = server.addPlayer();
    service.activate(null, character("x"));
    service.activate(player, null);
    service.activate(player, character(null));
    service.activate(player, character(" "));
    assertNull(sessions.get(player));
    Cache.debug = true;
    service.activate(player, character("one"));
    assertTrue(store.exists("one"));
    assertEquals("one", service.characterId(player));
    sessions.get(player).setResonance("fire", 25);
    service.activate(player, character("one"));
    assertEquals(25, sessions.get(player).getResonance("fire"));
    service.savePrevious(null, character("one"));
    service.savePrevious(player, null);
    service.savePrevious(player, character(null));
    service.savePrevious(player, character(" "));
    service.savePrevious(player, character("one"));
    assertEquals(25, store.load("one").getResonance().get("fire"));
    service.deactivate(null);
    service.deactivate(player);
    assertNull(sessions.get(player));
    service.activate(player, character("one"));
    assertEquals(25, sessions.get(player).getResonance("fire"));
    assertSame(player, service.findOnlineWithCharacter("one"));
    assertNull(service.findOnlineWithCharacter(null));
    assertNull(service.findOnlineWithCharacter(" "));
    assertNull(service.findOnlineWithCharacter("missing"));
    service.saveAllOnline();
    service.deactivate(player);
    service.savePrevious(player, character("one"));
  }

  @Test
  void onlineLoadingAndFallbackSavingUseActiveRoleplayCharacter() {
    var player = server.addPlayer();
    var empty = server.addPlayer();
    try (var bridge = mockStatic(RpCharactersBridge.class)) {
      var active = character("active");
      bridge.when(() -> RpCharactersBridge.getActiveCharacter(player)).thenReturn(active);
      assertSame(player, service.findOnlineWithCharacter("active"));
      service.loadOnlineCharacters();
      assertEquals("active", service.characterId(player));
      assertNull(service.characterId(empty));
      sessions.setLoadedCharacterId(player, null);
      sessions.get(player).setResonance("fire", 17);
      service.savePlayer(player);
      assertEquals(17, store.load("active").getResonance().get("fire"));
      service.savePlayer(empty);
      sessions.remove(player.getUniqueId());
      service.savePlayer(player);
      assertEquals(17, store.load("active").getResonance().get("fire"));
    }
  }

  @Test
  void equilibriumTickClampsCorruptionAndStopsTranquilityAtZero() {
    var player = server.addPlayer();
    var s = sessions.getOrCreate(player);
    EquilibriumService.tickOnlineSessions(sessions);
    assertEquals(0, s.getEquilibrium());
    s.setEquilibrium(-99.99);
    EquilibriumService.tickOnlineSessions(sessions);
    assertEquals(-100, s.getEquilibrium());
    s.setEquilibrium(.01);
    EquilibriumService.tickOnlineSessions(sessions);
    assertEquals(0, s.getEquilibrium());
    Cache.debug = true;
    s.setEquilibrium(20);
    EquilibriumService.tickOnlineSessions(sessions);
    assertEquals(19.97, s.getEquilibrium());
  }

  @Test
  void resonanceTicksDecayGrowUnlockedElementsAndPersistChanges() {
    ElementRegistry.clear();
    var cfg = new YamlConfiguration();
    cfg.set("decay_per_hour", 0);
    ElementRegistry.register(new ElementDef("constant", cfg));
    cfg.set("decay_per_hour", -.5);
    ElementRegistry.register(new ElementDef("decay", cfg));
    cfg.set("decay_per_hour", .5);
    cfg.set("permission", "magic.grow");
    ElementRegistry.register(new ElementDef("grow", cfg));
    var player = server.addPlayer();
    service.activate(player, character("one"));
    var s = sessions.get(player);
    s.setResonance("constant", 10);
    s.setResonance("decay", 10);
    ResonanceService.tickOnlineSessions(sessions);
    assertEquals(9.5, s.getResonance("decay"));
    assertEquals(0, s.getResonance("grow"));
    assertEquals(10, s.getResonance("constant"));
    assertEquals(9.5, store.load("one").getResonance().get("decay"));
    player.addAttachment(MockBukkit.createMockPlugin(), "magic.grow", true);
    Cache.debug = true;
    ResonanceService.tickOnlineSessions(sessions);
    assertEquals(.5, s.getResonance("grow"));
    s.setResonance("decay", 0);
    ResonanceService.tickOnlineSessions(sessions);
    assertEquals(0, s.getResonance("decay"));
  }

  @Test
  void activatingCorruptProfilePreservesOriginalBytes() throws Exception {
    var corrupt = temp.resolve("corrupt.json");
    String original = "{broken profile";
    java.nio.file.Files.writeString(corrupt, original);
    var player = server.addPlayer();
    service.activate(player, character("corrupt"));
    assertEquals("corrupt", service.characterId(player));
    try (var files = java.nio.file.Files.list(temp)) {
      var backup =
          files
              .filter(p -> p.getFileName().toString().startsWith("corrupt.json.rejected-"))
              .findFirst()
              .orElseThrow();
      assertEquals(original, java.nio.file.Files.readString(backup));
    }
    assertNotNull(store.load("corrupt"));
  }

  @Test
  void decayContinuesWhenPersistenceIsUnavailableAndCleanTicksDoNotSave() {
    var player = server.addPlayer();
    var session = sessions.getOrCreate(player);
    var profiles = mock(MagicProfileService.class);
    when(Magic.plugin.getProfileService()).thenReturn(profiles);
    ElementRegistry.clear();
    ResonanceService.tickOnlineSessions(sessions);
    verifyNoInteractions(profiles);
    var cfg = new YamlConfiguration();
    cfg.set("decay_per_hour", -.5);
    ElementRegistry.register(new ElementDef("decay", cfg));
    session.setResonance("decay", 10);
    when(Magic.plugin.getProfileService()).thenReturn(null);
    ResonanceService.tickOnlineSessions(sessions);
    assertEquals(9.5, session.getResonance("decay"));
    Magic.plugin = null;
    ResonanceService.tickOnlineSessions(sessions);
    assertEquals(9, session.getResonance("decay"));
  }
}
