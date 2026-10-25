package net.tfminecraft.magic.blood;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.logging.Logger;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mockito;

class BloodStoreTest {
  @TempDir Path temp;
  final Logger log = Logger.getAnonymousLogger();

  @AfterEach
  void clear() {
    BloodRecords.clear();
    BloodAltars.clear();
  }

  File characters() {
    return temp.resolve("characters").toFile();
  }

  @Test
  void recordsRoundTripAndEmptyRecordsLeaveDisk() throws Exception {
    BloodStore store = new BloodStore(temp.toFile(), log);
    assertTrue(store.loadRecords().isEmpty());
    BloodRecord record = BloodRecord.create("c1", "o1");
    record.setStage(BloodStage.ANIMAL);
    record.addSpecies("cow");
    assertTrue(store.save(record));
    List<BloodRecord> loaded = store.loadRecords();
    assertEquals(1, loaded.size());
    assertEquals(BloodStage.ANIMAL, loaded.get(0).getStage());
    assertTrue(loaded.get(0).hasSpecies("cow"));
    assertTrue(store.save(BloodRecord.create("c1", "o1")));
    assertFalse(new File(characters(), "c1.json").exists());
    assertTrue(store.save(BloodRecord.create("never", "o1")));
  }

  @Test
  void recordWithoutIdTakesTheFileNameAndUnreadableFilesMoveAside() throws Exception {
    Files.createDirectories(characters().toPath());
    Files.writeString(characters().toPath().resolve("named.json"), "{\"stage\":\"HUNT\"}");
    Files.writeString(characters().toPath().resolve("blank.json"), "{\"characterId\":\" \",\"stage\":\"ANIMAL\"}");
    Files.writeString(characters().toPath().resolve("broken.json"), "{not json");
    Files.writeString(characters().toPath().resolve("null.json"), "null");
    Files.writeString(characters().toPath().resolve("skip.txt"), "x");
    BloodStore store = new BloodStore(temp.toFile(), log);
    List<BloodRecord> loaded = store.loadRecords();
    assertEquals(2, loaded.size());
    loaded.sort(java.util.Comparator.comparing(BloodRecord::getCharacterId));
    assertEquals("blank", loaded.get(0).getCharacterId());
    assertEquals("named", loaded.get(1).getCharacterId());
    assertEquals(BloodStage.HUNT, loaded.get(1).getStage());
    String[] names = characters().list();
    assertEquals(2, java.util.Arrays.stream(names).filter(n -> n.contains(".rejected-")).count());
  }

  @Test
  void anUnmovableUnreadableFileIsReportedAndSkipped() throws Exception {
    Files.createDirectories(characters().toPath());
    Files.writeString(characters().toPath().resolve("broken.json"), "{");
    BloodStore store = new BloodStore(temp.toFile(), log);
    try (var files = mockStatic(Files.class, Mockito.CALLS_REAL_METHODS)) {
      files.when(() -> Files.move(any(Path.class), any(Path.class))).thenThrow(new IOException("locked"));
      assertTrue(store.loadRecords().isEmpty());
    }
    assertTrue(new File(characters(), "broken.json").exists());
  }

  @Test
  void writeAndDeleteFailuresReturnFalse() throws Exception {
    BloodStore store = new BloodStore(temp.toFile(), log);
    Path blocker = characters().toPath().resolve("c2.json");
    Files.createDirectories(blocker);
    Files.writeString(blocker.resolve("inside"), "x");
    BloodRecord record = BloodRecord.create("c2", "o");
    assertFalse(store.save(record));
    record.setStage(BloodStage.WHISPERED);
    assertFalse(store.save(record));
  }

  @Test
  void altarsRoundTripSkipIncompleteEntriesAndSurviveBadFiles() throws Exception {
    try (BloodFixture fx = new BloodFixture(temp.resolve("fx"))) {
      BloodStore store = new BloodStore(temp.toFile(), log);
      assertTrue(store.loadAltars().isEmpty());
      BloodAltar altar = BloodAltar.create("c", "o", fx.world.getBlockAt(1, 2, 3), 0f, 1L);
      assertTrue(store.saveAltars(List.of(altar)));
      Path file = temp.resolve("altars.json");
      String json = Files.readString(file);
      Files.writeString(file, json.replaceFirst("\\[", "[null, {\"id\":\"x\"},"));
      List<BloodAltar> loaded = store.loadAltars();
      assertEquals(1, loaded.size());
      assertEquals("cave,1,2,3", loaded.get(0).blockKey());
      Files.writeString(file, "[");
      assertTrue(store.loadAltars().isEmpty());
      assertFalse(file.toFile().exists());
    }
  }

  @Test
  void registriesIndexAndForget() throws Exception {
    try (BloodFixture fx = new BloodFixture(temp.resolve("fx"))) {
      BloodStore store = new BloodStore(temp.resolve("reg").toFile(), log);
      BloodRecords.init(store);
      BloodAltars.init(store);
      assertNull(BloodRecords.get(null));
      BloodRecord record = BloodRecords.getOrCreate("ABC", "o");
      assertSame(record, BloodRecords.get("abc"));
      assertTrue(BloodRecords.save(record));
      assertNull(BloodRecords.get("abc"));
      record = BloodRecords.getOrCreate("abc", "o");
      record.setStage(BloodStage.ANIMAL);
      assertTrue(BloodRecords.save(record));
      assertEquals(1, BloodRecords.all().size());
      BloodRecords.init(store);
      assertEquals(BloodStage.ANIMAL, BloodRecords.get("abc").getStage());
      assertTrue(BloodRecords.delete("abc"));
      assertTrue(BloodRecords.delete("abc"));
      assertTrue(store.loadRecords().isEmpty());

      var block = fx.world.getBlockAt(40, 5, 40);
      BloodAltar altar = BloodAltar.create("Owner", "o", block, 0f, 1L);
      assertTrue(BloodAltars.add(altar));
      assertSame(altar, BloodAltars.at(block));
      assertNull(BloodAltars.at(null));
      assertSame(altar, BloodAltars.byId(altar.getId()));
      assertNull(BloodAltars.byId(null));
      assertSame(altar, BloodAltars.ofCharacter("owner"));
      assertNull(BloodAltars.ofCharacter(null));
      assertEquals(List.of(altar), BloodAltars.inChunk("cave", 2, 2));
      assertTrue(BloodAltars.inChunk("cave", 0, 0).isEmpty());
      BloodAltars.init(store);
      assertEquals(1, BloodAltars.all().size());
      assertTrue(BloodAltars.remove(BloodAltars.all().get(0)));
      assertTrue(BloodAltars.all().isEmpty());

      BloodRecords.clear();
      BloodAltars.clear();
      record = BloodRecords.getOrCreate("x", "o");
      record.setStage(BloodStage.ANIMAL);
      assertTrue(BloodRecords.save(record));
      assertTrue(BloodRecords.delete("x"));
      assertTrue(BloodAltars.add(altar));
      assertTrue(BloodAltars.save());

      Path failing = temp.resolve("failing");
      BloodStore broken = new BloodStore(failing.toFile(), log);
      BloodRecords.init(broken);
      BloodAltars.init(broken);
      Files.createDirectories(failing.resolve("altars.json/inside"));
      assertFalse(BloodAltars.add(altar));
      record = BloodRecords.getOrCreate("y", "o");
      record.setStage(BloodStage.ANIMAL);
      Files.createDirectories(failing.resolve("characters/y.json/inside"));
      assertFalse(BloodRecords.delete("y"));
    }
  }
}
