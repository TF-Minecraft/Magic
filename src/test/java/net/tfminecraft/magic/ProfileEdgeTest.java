package net.tfminecraft.magic;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.google.gson.Gson;
import java.nio.file.*;
import java.util.*;
import java.util.logging.Logger;
import net.tfminecraft.magic.profile.*;
import net.tfminecraft.magic.registry.ElementRegistry;
import net.tfminecraft.magic.session.*;
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
  void profileServiceNeverFallsBackToAccountOrUnloadedCharacter() throws Exception {
    var sessions = new ResonanceSessionManager();
    var store = new MagicProfileStore(temp.toFile());
    var provider = new CharacterTestSupport();
    var service = new MagicProfileService(provider.plugin, store, sessions);
    var player = server.addPlayer();
    var token = provider.start(player, "one");
    assertNull(sessions.get(player));
    assertNull(sessions.getLoadedCharacterId(player));
    service.savePlayer(player);
    assertFalse(store.exists(CharacterTestSupport.id("one")));
    service.activate(player, token);
    sessions.get(player).setResonance("fire", 23);
    assertNull(service.findOnlineWithCharacter("missing"));
    provider.unregister();
    service.savePlayer(player);
    service.close();
    assertEquals(23, store.load(CharacterTestSupport.id("one")).getResonance().get("fire"));
    var nested = temp.resolve("nested");
    var nestedStore = new MagicProfileStore(nested.toFile());
    Files.delete(nested);
    nestedStore.save(MagicProfile.fromDefaults("saved", null));
    assertTrue(Files.exists(nested.resolve("saved.json")));
  }
  @Test
  void sharedUuidLoadsAndSavesTheExistingLegacyCaseSpelling() throws Exception {
    var player = server.addPlayer();
    String id = CharacterTestSupport.id("legacy-case");
    String legacyId = id.toUpperCase(Locale.ROOT);
    var store = new MagicProfileStore(temp.toFile());
    var profile = MagicProfile.fromDefaults(legacyId, player.getUniqueId());
    profile.setResonance(new HashMap<>(Map.of("fire", 43d)));
    store.save(profile);
    String original = Files.readString(temp.resolve(legacyId + ".json"));
    var provider = new CharacterTestSupport();
    var sessions = new ResonanceSessionManager();
    var service = new MagicProfileService(provider.plugin, store, sessions);
    service.activate(player, provider.start(player, "legacy-case"));
    assertEquals(43, sessions.get(player).getResonance("fire"));
    assertEquals(original, Files.readString(temp.resolve(legacyId + ".json")));
    sessions.get(player).setResonance("fire", 57);
    service.close();
    assertFalse(Files.exists(temp.resolve(id + ".json")));
    assertEquals(57, store.load(legacyId).getResonance().get("fire"));
    assertTrue(store.exists(id));
  }

  @Test
  void ambiguousLegacySpellingFailsWithoutReplacingEitherProfile() throws Exception {
    String id = "abcdefab-abcd-abcd-abcd-abcdefabcdef";
    var first = temp.resolve(id.toUpperCase(Locale.ROOT) + ".json");
    var second = temp.resolve("Abcdefab-abcd-abcd-abcd-abcdefabcdef.json");
    Files.writeString(first, "first profile");
    Files.writeString(second, "second profile");
    var store = new MagicProfileStore(temp.toFile());
    assertThrows(IllegalStateException.class, () -> store.load(id));
    assertThrows(IllegalStateException.class, () -> store.save(MagicProfile.fromDefaults(id, null)));
    assertEquals("first profile", Files.readString(first));
    assertEquals("second profile", Files.readString(second));
    assertFalse(Files.exists(temp.resolve(id + ".json")));
  }

  @Test
  void unreadableProfileDirectoryDoesNotClaimACharacterFileExists() throws Exception {
    Path blocked = temp.resolve("blocked-directory");
    Files.writeString(blocked, "preserved file");
    var store = new MagicProfileStore(blocked.toFile());
    assertFalse(store.exists(CharacterTestSupport.id("absent")));
    assertNull(store.load(CharacterTestSupport.id("absent")));
    assertEquals("preserved file", Files.readString(blocked));
  }

}
