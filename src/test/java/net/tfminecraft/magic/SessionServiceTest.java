package net.tfminecraft.magic;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.logging.Logger;
import net.tfminecraft.magic.integration.RpCharactersBridge;
import net.tfminecraft.magic.model.ElementDef;
import net.tfminecraft.magic.profile.*;
import net.tfminecraft.magic.registry.ElementRegistry;
import net.tfminecraft.magic.service.*;
import net.tfminecraft.magic.session.*;
import net.tfminecraft.tlibs.character.*;
import net.tfminecraft.tlibs.character.CharacterSessionEndEvent.Reason;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockbukkit.mockbukkit.*;

class SessionServiceTest {
  @TempDir Path temp;
  ServerMock server;
  Magic magic;
  Plugin plugin;
  ResonanceSessionManager sessions;
  MagicProfileStore store;
  MagicProfileService service;

  @BeforeEach
  void setup() {
    server = MockBukkit.mock();
    plugin = MockBukkit.createMockPlugin("MagicProfiles");
    magic = mock(Magic.class);
    Magic.plugin = magic;
    when(magic.getLogger()).thenReturn(Logger.getAnonymousLogger());
    sessions = new ResonanceSessionManager();
    store = spy(new MagicProfileStore(temp.toFile()));
    service = new MagicProfileService(plugin, store, sessions);
    when(magic.getProfileService()).thenReturn(service);
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
    try {
      Magic.plugin = magic;
      service.close();
    } finally {
      Magic.plugin = null;
      MockBukkit.unmock();
      ElementRegistry.clear();
      Cache.debug = false;
      Cache.secondsPerHour = 3600;
    }
  }

  @Test
  void standaloneGameplayIsEphemeralAndOnlyOnlineSessionsTick() {
    var player = server.addPlayer();
    server.addPlayer();
    assertNull(sessions.get(null));
    assertNull(sessions.get(player));
    assertNull(sessions.getLoadedCharacterId(null));
    var first = sessions.getOrCreate(player);
    assertSame(first, sessions.getOrCreate(player));
    assertSame(first, sessions.get(player));
    assertNull(sessions.getLoadedCharacterId(player));
    List<Object> seen = new ArrayList<>();
    sessions.forEachOnlineSession((online, value) -> {
      assertSame(player, online);
      assertSame(first, value);
      seen.add(value);
    });
    assertEquals(List.of(first), seen);
    player.disconnect();
    sessions.forEachOnlineSession((online, value) -> fail("Offline sessions must not tick"));
    sessions.removeStandalone(null);
    sessions.removeStandalone(player.getUniqueId());
    assertNull(sessions.get(player));
    var unstarted = new ResonanceSessionManager();
    assertNull(unstarted.getSession(new CharacterSession(
        new CharacterRef(player.getUniqueId(), UUID.randomUUID()), 1)));
    unstarted.close();
  }

  @Test
  void providerRequiresAReadyLoadedProfileAndManagerCanOnlyStartOnce() {
    var player = server.addPlayer();
    var ephemeral = sessions.getOrCreate(player);
    ephemeral.setResonance("fire", 70);
    CharacterTestSupport characters = new CharacterTestSupport();
    assertNull(sessions.getOrCreate(player));
    var token = characters.start(player, "one");
    assertNull(sessions.getOrCreate(player));
    assertNull(sessions.getLoadedCharacterId(player));
    service.activate(player, token);
    assertNotSame(ephemeral, sessions.get(player));
    assertEquals(0, sessions.get(player).getResonance("fire"));
    assertEquals(CharacterTestSupport.id("one"), sessions.getLoadedCharacterId(player));
    assertSame(sessions.get(player), sessions.getOrCreate(player));
    assertSame(sessions.get(player), sessions.getSession(token));
    assertThrows(IllegalStateException.class, () -> sessions.start(plugin, (session, value) -> {}));
    characters.unregister();
    assertNull(sessions.get(player), "Binding a character must discard its account-only temporary state");
  }

  @Test
  void aliceBobAliceUsesFreshTokensAndFlushesTheOutgoingStoredValueAfterInvalidation() {
    var player = server.addPlayer();
    CharacterTestSupport characters = new CharacterTestSupport();
    var alice = characters.start(player, "alice");
    service.activate(player, alice);
    var aliceValue = sessions.get(player);
    aliceValue.setResonance("fire", 25);
    List<Boolean> currentDuringEnd = new ArrayList<>();
    List<ResonanceSession> readableDuringEnd = new ArrayList<>();
    server.getPluginManager().registerEvent(CharacterSessionEndEvent.class, new Listener() {},
        EventPriority.NORMAL, (listener, event) -> {
          CharacterSession outgoing = ((CharacterSessionEndEvent) event).getSession();
          currentDuringEnd.add(Characters.isCurrent(outgoing));
          readableDuringEnd.add(sessions.getSession(outgoing));
        }, plugin);
    var bob = characters.start(player, "bob");
    assertEquals(List.of(false), currentDuringEnd);
    assertEquals(1, readableDuringEnd.size());
    assertNull(readableDuringEnd.getFirst());
    assertEquals(25, store.load(CharacterTestSupport.id("alice")).getResonance().get("fire"));
    assertNull(sessions.getSession(alice));
    service.activate(player, bob);
    sessions.get(player).setResonance("fire", 7);
    var aliceAgain = characters.start(player, "alice");
    assertEquals(alice.ref(), aliceAgain.ref());
    assertNotEquals(alice.sessionId(), aliceAgain.sessionId());
    assertFalse(Characters.isCurrent(alice));
    assertFalse(Characters.isCurrent(bob));
    service.activate(player, aliceAgain);
    var restored = sessions.get(player);
    assertNotSame(aliceValue, restored);
    assertEquals(25, restored.getResonance("fire"));
    assertEquals(7, store.load(CharacterTestSupport.id("bob")).getResonance().get("fire"));
    sessions.put(alice, new ResonanceSession());
    service.activate(player, alice);
    assertSame(restored, sessions.get(player));
    assertEquals(aliceAgain.ref().characterId().toString(), service.characterId(player));
  }

  @ParameterizedTest
  @EnumSource(value = Reason.class, names = {"QUIT", "DEATH", "SHUTDOWN"})
  void terminalEventsFlushTheEndedValueExactlyOnce(Reason reason) {
    var player = server.addPlayer();
    CharacterTestSupport characters = new CharacterTestSupport();
    var token = characters.start(player, "ended");
    service.activate(player, token);
    sessions.get(player).setResonance("fire", 32);
    clearInvocations(store);
    characters.end(player, reason);
    assertFalse(Characters.isCurrent(token));
    assertNull(sessions.get(player));
    assertNull(sessions.getSession(token));
    assertNull(service.characterId(player));
    assertEquals(32, store.load(CharacterTestSupport.id("ended")).getResonance().get("fire"));
    characters.end(player, reason);
    service.close();
    verify(store, times(1)).save(any());
  }

  @Test
  void closeFlushesEveryStoredValueAfterProviderUnregistration() {
    var first = server.addPlayer();
    var second = server.addPlayer();
    CharacterTestSupport characters = new CharacterTestSupport();
    service.activate(first, characters.start(first, "first"));
    service.activate(second, characters.start(second, "second"));
    sessions.get(first).setResonance("fire", 31);
    sessions.get(second).setResonance("fire", 42);
    clearInvocations(store);
    characters.unregister();
    assertFalse(Characters.available());
    assertNull(sessions.get(first));
    service.close();
    assertEquals(31, store.load(CharacterTestSupport.id("first")).getResonance().get("fire"));
    assertEquals(42, store.load(CharacterTestSupport.id("second")).getResonance().get("fire"));
    service.close();
    verify(store, times(2)).save(any());
  }

  @Test
  void duplicateActivationDoesNotReloadWhileNullMismatchedAndStaleTokensAreIgnored() {
    var player = server.addPlayer();
    var other = server.addPlayer();
    CharacterTestSupport characters = new CharacterTestSupport();
    var token = characters.start(player, "one");
    service.activate(null, token);
    service.activate(player, null);
    service.activate(other, token);
    service.activate(player, new CharacterSession(token.ref(), token.sessionId() + 100));
    assertNull(sessions.get(player));
    verify(store, never()).load(anyString());
    Cache.debug = true;
    service.activate(player, token);
    assertTrue(store.exists(CharacterTestSupport.id("one")));
    sessions.get(player).setResonance("fire", 25);
    clearInvocations(store);
    service.activate(player, token);
    assertEquals(25, sessions.get(player).getResonance("fire"));
    verify(store, never()).load(anyString());
    characters.end(player, Reason.QUIT);
    clearInvocations(store);
    service.activate(player, token);
    verifyNoInteractions(store);
  }

  @Test
  void standaloneAndFallbackCharacterStateAreNeverPersisted() throws Exception {
    var player = server.addPlayer();
    sessions.getOrCreate(player).setResonance("fire", 73);
    try (var bridge = mockStatic(RpCharactersBridge.class)) {
      service.savePlayer(null);
      service.savePlayer(player);
      service.loadOnlineCharacters();
      assertNull(service.characterId(player));
      assertNull(service.findOnlineWithCharacter("untracked-rpc-character"));
      service.close();
      bridge.verifyNoInteractions();
    }
    verify(store, never()).save(any());
    assertFalse(store.exists(player.getUniqueId().toString()));
    try (var files = Files.list(temp)) {
      assertEquals(0, files.count());
    }
  }

  @Test
  void bootstrapLoadsOnlyReadySessionsAndFindsOnlyLoadedCharacterIdentities() {
    var ready = server.addPlayer();
    var pending = server.addPlayer();
    CharacterTestSupport characters = new CharacterTestSupport();
    characters.start(ready, "ready");
    assertNull(service.findOnlineWithCharacter(CharacterTestSupport.id("ready")));
    try (var bridge = mockStatic(RpCharactersBridge.class)) {
      service.loadOnlineCharacters();
      bridge.verifyNoInteractions();
    }
    assertEquals(CharacterTestSupport.id("ready"), service.characterId(ready));
    assertNull(service.characterId(pending));
    assertNull(sessions.getOrCreate(pending));
    assertSame(ready, service.findOnlineWithCharacter(CharacterTestSupport.id("ready").toUpperCase(Locale.ROOT)));
    assertNull(service.findOnlineWithCharacter(null));
    assertNull(service.findOnlineWithCharacter(" "));
    assertNull(service.findOnlineWithCharacter(CharacterTestSupport.id("unknown")));
  }

  @Test
  void savePlayerSavesOnlyTheCurrentBoundTokenNeverLatePreviousValuesOrPendingProfiles() {
    var player = server.addPlayer();
    var empty = server.addPlayer();
    CharacterTestSupport characters = new CharacterTestSupport();
    var alice = characters.start(player, "alice");
    service.activate(player, alice);
    var oldValue = sessions.get(player);
    oldValue.setResonance("fire", 12);
    var bob = characters.start(player, "bob");
    clearInvocations(store);
    service.savePlayer(player);
    service.savePlayer(empty);
    verify(store, never()).save(any());
    service.activate(player, bob);
    sessions.get(player).setResonance("fire", 34);
    oldValue.setResonance("fire", 99);
    service.savePlayer(player);
    assertEquals(34, store.load(CharacterTestSupport.id("bob")).getResonance().get("fire"));
    assertEquals(12, store.load(CharacterTestSupport.id("alice")).getResonance().get("fire"));
    characters.end(player, Reason.QUIT);
    clearInvocations(store);
    service.savePlayer(player);
    verifyNoInteractions(store);
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
    CharacterTestSupport characters = new CharacterTestSupport();
    service.activate(player, characters.start(player, "one"));
    var s = sessions.get(player);
    s.setResonance("constant", 10);
    s.setResonance("decay", 10);
    ResonanceService.tickOnlineSessions(sessions);
    assertEquals(9.5, s.getResonance("decay"));
    assertEquals(0, s.getResonance("grow"));
    assertEquals(10, s.getResonance("constant"));
    assertEquals(9.5, store.load(CharacterTestSupport.id("one")).getResonance().get("decay"));
    player.addAttachment(plugin, "magic.grow", true);
    Cache.debug = true;
    ResonanceService.tickOnlineSessions(sessions);
    assertEquals(.5, s.getResonance("grow"));
    s.setResonance("decay", 0);
    ResonanceService.tickOnlineSessions(sessions);
    assertEquals(0, s.getResonance("decay"));
  }

  @Test
  void realTimeDriftBuildsUpToWholeHundredthsAndOnlySavesWhenTheValueMoves() {
    Cache.secondsPerHour = 3600;
    ElementRegistry.clear();
    var cfg = new YamlConfiguration();
    cfg.set("decay_per_hour", -.02);
    ElementRegistry.register(new ElementDef("fire", cfg));
    var player = server.addPlayer();
    service.activate(player, new CharacterTestSupport().start(player, "slow"));
    var s = sessions.get(player);
    s.setResonance("fire", 100);
    s.setEquilibrium(20);
    clearInvocations(store);
    for (int i = 0; i < 1199; i++) EquilibriumService.tickOnlineSessions(sessions);
    assertEquals(20, s.getEquilibrium());
    EquilibriumService.tickOnlineSessions(sessions);
    assertEquals(19.99, s.getEquilibrium());
    for (int i = 0; i < 1799; i++) ResonanceService.tickOnlineSessions(sessions);
    assertEquals(100, s.getResonance("fire"));
    verify(store, never()).save(any());
    ResonanceService.tickOnlineSessions(sessions);
    assertEquals(99.99, s.getResonance("fire"));
    verify(store).save(any());
    for (int i = 0; i < 1800; i++) ResonanceService.tickOnlineSessions(sessions);
    assertEquals(99.98, s.getResonance("fire"));
  }

  @Test
  void driftStopsAtTheElementLimitsAndIgnoresUnknownElements() {
    ElementRegistry.clear();
    var cfg = new YamlConfiguration();
    cfg.set("decay_per_hour", .5);
    ElementRegistry.register(new ElementDef("grow", cfg));
    var player = server.addPlayer();
    service.activate(player, new CharacterTestSupport().start(player, "full"));
    var s = sessions.get(player);
    s.setResonance("grow", 100);
    clearInvocations(store);
    ResonanceService.tickOnlineSessions(sessions);
    assertEquals(100, s.getResonance("grow"));
    verify(store, never()).save(any());
    s.setResonance("grow", 99.8);
    assertTrue(s.driftResonance("grow", .5));
    assertEquals(100, s.getResonance("grow"));
    assertFalse(s.driftResonance("grow", -.004));
    assertEquals(100, s.getResonance("grow"));
    assertTrue(s.driftResonance("grow", -.006));
    assertEquals(99.99, s.getResonance("grow"));
    assertFalse(s.driftResonance("grow", -.004));
    assertFalse(s.driftResonance("missing", -1));
    s.clearResonanceDrift("missing");
    s.clearResonanceDrift("grow");
    assertFalse(s.driftResonance("grow", -.006));
    assertEquals(99.99, s.getResonance("grow"));
  }

  @Test
  void activatingCorruptProfilePreservesOriginalBytesBeforeCreatingFreshDefaults() throws Exception {
    String id = CharacterTestSupport.id("corrupt");
    var corrupt = temp.resolve(id + ".json");
    String original = "{broken profile";
    Files.writeString(corrupt, original);
    var player = server.addPlayer();
    CharacterTestSupport characters = new CharacterTestSupport();
    service.activate(player, characters.start(player, "corrupt"));
    assertEquals(id, service.characterId(player));
    try (var files = Files.list(temp)) {
      var backup = files.filter(path -> path.getFileName().toString().startsWith(id + ".json.rejected-"))
          .findFirst().orElseThrow();
      assertEquals(original, Files.readString(backup));
    }
    assertNotNull(store.load(id));
  }

  @Test
  void anUnpreservableCorruptProfileRemainsQuarantinedThroughSaveAndEndFlush() throws Exception {
    String id = CharacterTestSupport.id("quarantined");
    Path corrupt = temp.resolve(id + ".json");
    String original = "{unreadable profile";
    Files.writeString(corrupt, original);
    var player = server.addPlayer();
    CharacterTestSupport characters = new CharacterTestSupport();
    var token = characters.start(player, "quarantined");
    try (var files = mockStatic(Files.class, invocation -> {
      if (invocation.getMethod().getName().equals("move") && corrupt.equals(invocation.getArgument(0))) {
        throw new IOException("cannot preserve original");
      }
      return invocation.callRealMethod();
    })) {
      service.activate(player, token);
      sessions.get(player).setResonance("fire", 60);
      service.savePlayer(player);
      characters.end(player, Reason.QUIT);
    }
    assertEquals(original, Files.readString(corrupt));
    try (var files = Files.list(temp)) {
      assertEquals(List.of(corrupt), files.toList());
    }
  }

  @Test
  void decayContinuesWhenPersistenceIsUnavailableAndCleanTicksDoNotSave() {
    var player = server.addPlayer();
    var session = sessions.getOrCreate(player);
    var profiles = mock(MagicProfileService.class);
    when(magic.getProfileService()).thenReturn(profiles);
    ElementRegistry.clear();
    ResonanceService.tickOnlineSessions(sessions);
    verifyNoInteractions(profiles);
    var cfg = new YamlConfiguration();
    cfg.set("decay_per_hour", -.5);
    ElementRegistry.register(new ElementDef("decay", cfg));
    session.setResonance("decay", 10);
    when(magic.getProfileService()).thenReturn(null);
    ResonanceService.tickOnlineSessions(sessions);
    assertEquals(9.5, session.getResonance("decay"));
    Magic.plugin = null;
    ResonanceService.tickOnlineSessions(sessions);
    assertEquals(9, session.getResonance("decay"));
  }
}
