package net.tfminecraft.magic;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.nio.file.*;
import java.util.*;
import java.util.logging.Logger;
import net.tfminecraft.magic.profile.*;
import net.tfminecraft.magic.registry.ElementRegistry;
import net.tfminecraft.magic.util.RevisionTracker;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

class PersistenceTest {
  @TempDir Path temp;

  @BeforeEach
  void setup() {
    Magic.plugin = mock(Magic.class);
    when(Magic.plugin.getLogger()).thenReturn(Logger.getAnonymousLogger());
    ElementRegistry.clear();
    ElementRegistry.register(DomainTest.element("fire"));
  }

  @AfterEach
  void cleanup() {
    Magic.plugin = null;
    ElementRegistry.clear();
  }

  @Test
  void profileFilesRoundTripAndIgnoreMissingIds() throws Exception {
    var store = new MagicProfileStore(temp.resolve("characters").toFile());
    assertFalse(store.exists(null));
    assertFalse(store.exists(" "));
    assertFalse(store.exists("one"));
    assertNull(store.load(null));
    assertNull(store.load(" "));
    assertNull(store.load("one"));
    store.save(null);
    store.save(new MagicProfile());
    var profile = MagicProfile.fromDefaults("one", UUID.randomUUID());
    profile.setEquilibrium(2);
    profile.setResonance(new HashMap<>(Map.of("fire", 12.3)));
    store.save(profile);
    assertTrue(store.exists("one"));
    var loaded = store.load("one");
    assertEquals("one", loaded.getCharacterId());
    assertEquals(profile.getOwnerUuid(), loaded.getOwnerUuid());
    assertEquals(Map.of("fire", 12.3), loaded.copyResonance());
    assertEquals(2, loaded.getEquilibrium());
    Files.writeString(temp.resolve("characters/one.json"), "{}");
    assertEquals("one", store.load("one").getCharacterId());
    Files.writeString(temp.resolve("characters/one.json"), "{\"characterId\":\" \"}");
    assertEquals("one", store.load("one").getCharacterId());
    Files.writeString(temp.resolve("characters/one.json"), "null");
    assertNull(store.load("one"));
    Files.writeString(temp.resolve("characters/one.json"), "{bad json");
    assertNull(store.load("one"));
    profile.setCharacterId(" ");
    store.save(profile);
  }

  @Test
  void oldClaimsFoldIntoResonanceOnce() throws Exception {
    var store = new MagicProfileStore(temp.toFile());
    Files.writeString(
        temp.resolve("one.json"),
        """
{"characterId":"one","resonance":{"fire":2},"claims":{"old":{"byElement":{" FIRE ":3," ":8,"water":null}},"bad":null}}
""");
    var profile = store.load("one");
    assertNotNull(profile);
    assertEquals(5, profile.getResonance().get("fire"));
    store.save(profile);
    assertFalse(Files.readString(temp.resolve("one.json")).contains("claims"));
    assertEquals(5, store.load("one").getResonance().get("fire"));
    Files.writeString(temp.resolve("two.json"), "{\"resonance\":null}");
    assertTrue(store.load("two").copyResonance().isEmpty());
  }

  @Test
  void profileWriteFailuresLeaveExistingDataUntouched() throws Exception {
    var store = new MagicProfileStore(temp.toFile());
    var profile = MagicProfile.fromDefaults("one", null);
    store.save(profile);
    String original = Files.readString(temp.resolve("one.json"));
    Files.createDirectory(temp.resolve("one.json.tmp"));
    profile.setEquilibrium(50);
    store.save(profile);
    assertEquals(original, Files.readString(temp.resolve("one.json")));
    Files.delete(temp.resolve("one.json.tmp"));
    Files.delete(temp.resolve("one.json"));
    Files.createDirectory(temp.resolve("one.json"));
    Files.writeString(temp.resolve("one.json/keep"), "keep");
    store.save(profile);
    assertEquals("keep", Files.readString(temp.resolve("one.json/keep")));
    assertNull(store.load("one"));
  }

  @Test
  void revisionHashesPersistAndIncrementOnlyForContentChanges() throws Exception {
    var tracker = new RevisionTracker();
    tracker.flush();
    tracker.load(temp.toFile());
    tracker.flush();
    assertEquals(0, tracker.resolvePart(null, "h"));
    assertEquals(0, tracker.resolvePart(" ", "h"));
    assertEquals(0, tracker.partRevision(null));
    assertEquals(0, tracker.partRevision(" "));
    assertEquals(0, tracker.partRevision("unknown"));
    assertEquals(1, tracker.resolvePart(" Part ", "first"));
    assertEquals(1, tracker.resolvePart("part", "first"));
    assertEquals(2, tracker.resolvePart("part", "second"));
    assertEquals(2, tracker.partRevision(" PART "));
    assertEquals(1, tracker.resolveArchetype("wand", null));
    assertEquals(1, tracker.resolveArchetype("wand", ""));
    assertEquals(1, tracker.archetypeRevision("wand"));
    tracker.flush();
    var loaded = new RevisionTracker();
    loaded.load(temp.toFile());
    assertEquals(2, loaded.partRevision("part"));
    assertEquals(1, loaded.archetypeRevision("wand"));
    assertEquals(2, loaded.resolvePart("part", "second"));
    assertEquals(
        "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
        RevisionTracker.sha256(null));
    assertEquals(RevisionTracker.sha256(""), RevisionTracker.sha256(null));
    assertNotEquals(RevisionTracker.sha256("abc"), RevisionTracker.sha256("ABC"));
  }

  @Test
  void revisionLoaderHandlesMissingSectionsDefaultsAndMalformedFiles() throws Exception {
    Files.createDirectories(temp.resolve("data"));
    Path file = temp.resolve("data/revisions.json");
    var tracker = new RevisionTracker();
    Files.writeString(file, "{\"parts\":{\"LEGACY\":{}},\"archetypes\":false}");
    tracker.load(temp.toFile());
    assertEquals(1, tracker.partRevision("legacy"));
    assertEquals(1, tracker.resolvePart("legacy", null));
    Files.writeString(file, "{}");
    tracker.load(temp.toFile());
    assertEquals(0, tracker.partRevision("legacy"));
    Files.writeString(file, "broken");
    assertDoesNotThrow(() -> tracker.load(temp.toFile()));
    Files.delete(file);
    Files.createDirectory(file);
    Files.writeString(file.resolve("keep"), "keep");
    tracker.resolvePart("a", "hash");
    assertDoesNotThrow(tracker::flush);
    assertEquals("keep", Files.readString(file.resolve("keep")));
  }

  @Test
  void failedQuarantineBlocksOverwriteUntilOriginalCanBePreserved() throws Exception {
    Path folder = Files.createDirectory(temp.resolve("protected"));
    Path file = folder.resolve("one.json");
    String original = "{invalid profile";
    Files.writeString(file, original);
    var store = new MagicProfileStore(folder.toFile());
    var permissions = Files.getPosixFilePermissions(folder);
    try {
      Files.setPosixFilePermissions(
          folder, java.nio.file.attribute.PosixFilePermissions.fromString("r-xr-xr-x"));
      assertNull(store.load("one"));
    } finally {
      Files.setPosixFilePermissions(folder, permissions);
    }
    store.save(MagicProfile.fromDefaults("one", null));
    assertEquals(original, Files.readString(file));
    assertNull(store.load("one"));
    store.save(MagicProfile.fromDefaults("one", null));
    assertNotNull(store.load("one"));
    try (var files = Files.list(folder)) {
      var preserved =
          files.filter(p -> p.getFileName().toString().startsWith("one.json.rejected-")).toList();
      assertEquals(1, preserved.size());
      assertEquals(original, Files.readString(preserved.getFirst()));
    }
  }
}
