package net.tfminecraft.magic;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.google.gson.Gson;
import java.nio.file.*;
import java.util.*;
import java.util.logging.Logger;
import net.tfminecraft.magic.integration.RpCharactersBridge;
import net.tfminecraft.magic.profile.*;
import net.tfminecraft.magic.registry.ElementRegistry;
import net.tfminecraft.magic.session.*;
import net.tfminecraft.rpcharacters.objects.RPCharacter;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.mockbukkit.mockbukkit.*;

class ProfileEdgeTest {
  @TempDir Path temp;
  ServerMock server;

  @BeforeEach
  void setup() {
    server = MockBukkit.mock();
    Magic.plugin = mock(Magic.class);
    when(Magic.plugin.getLogger()).thenReturn(Logger.getAnonymousLogger());
    ElementRegistry.clear();
    ElementRegistry.register(DomainTest.element("fire"));
  }

  @AfterEach
  void cleanup() {
    Magic.plugin = null;
    MockBukkit.unmock();
    ElementRegistry.clear();
  }

  @Test
  void persistedNullMapsAndLegacyMalformedClaimsAreRepaired() {
    var gson = new Gson();
    var profile = gson.fromJson("{\"resonance\":null}", MagicProfile.class);
    assertTrue(profile.copyResonance().isEmpty());
    profile.pruneEmptyResonance();
    assertTrue(profile.getResonance().isEmpty());
    profile = gson.fromJson("{\"resonance\":null}", MagicProfile.class);
    assertTrue(profile.getResonance().isEmpty());
    profile =
        gson.fromJson(
            "{\"claims\":{\"bad\":{\"byElement\":[[\"fire\",null],[\"\",2],[\" FIRE"
                + " \",4]]}},\"resonance\":null}",
            MagicProfile.class);
    profile.migrateClaims();
    assertEquals(Map.of("fire", 4d), profile.copyResonance());
    var values = new HashMap<String, Double>();
    values.put(null, 2d);
    values.put("water", null);
    values.put("zero", 0d);
    values.put("fire", .001);
    profile.setResonance(values);
    assertEquals(Map.of("fire", .001), profile.copyResonance());
    profile.pruneEmptyResonance();
    assertTrue(profile.copyResonance().isEmpty());
  }

  @Test
  void sessionMapImportRejectsInvalidEntriesAndCopiesStayIndependent() {
    var session = new ResonanceSession();
    session.setResonance("fire", 8);
    var values = new HashMap<String, Double>();
    values.put(null, 2d);
    values.put("water", null);
    values.put("fire", 4d);
    session.setResonanceMap(values);
    assertEquals(Map.of("fire", 4d), session.copyResonance());
    var copied = session.copyResonance();
    copied.put("fire", 9d);
    assertEquals(4, session.getResonance("fire"));
    session.setResonanceMap(Map.of());
    assertTrue(session.copyResonance().isEmpty());
    session.setResonanceMap(null);
    assertTrue(session.copyResonance().isEmpty());
  }

  @Test
  void profileServiceHandlesLoadedIdWithoutSessionAndBlankBridgeCharacter() throws Exception {
    var sessions = new ResonanceSessionManager();
    var store = new MagicProfileStore(temp.toFile());
    var service = new MagicProfileService(store, sessions);
    var player = server.addPlayer();
    var active = mock(RPCharacter.class);
    when(active.getId()).thenReturn("one");
    sessions.setLoadedCharacterId(player, "one");
    try (var bridge = mockStatic(RpCharactersBridge.class)) {
      service.activate(player, active);
      assertNotNull(sessions.get(player));
      assertTrue(store.exists("one"));
      var unmatched = mock(RPCharacter.class);
      when(unmatched.getId()).thenReturn("other");
      bridge.when(() -> RpCharactersBridge.getActiveCharacter(player)).thenReturn(unmatched);
      assertNull(service.findOnlineWithCharacter("missing"));
      sessions.remove(player.getUniqueId());
      service.saveAllOnline();
      when(unmatched.getId()).thenReturn("");
      service.saveAllOnline();
      service.loadOnlineCharacters();
      assertNull(sessions.get(player));
    }
    var nested = temp.resolve("nested");
    var nestedStore = new MagicProfileStore(nested.toFile());
    Files.delete(nested);
    nestedStore.save(MagicProfile.fromDefaults("saved", null));
    assertTrue(Files.exists(nested.resolve("saved.json")));
  }
}
