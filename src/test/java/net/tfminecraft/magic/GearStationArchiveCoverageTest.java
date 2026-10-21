package net.tfminecraft.magic;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.logging.*;
import net.tfminecraft.magic.gear.*;
import net.tfminecraft.magic.gear.ownership.GearFiles;
import org.junit.jupiter.api.*;

class GearStationArchiveCoverageTest extends GearStationCoverageSupport {
  final List<String> logged = new ArrayList<>();
  Handler handler;

  @BeforeEach
  void captureLog() {
    handler = new Handler() {
      @Override public void publish(LogRecord record) { logged.add(record.getLevel() + " " + record.getMessage()); }
      @Override public void flush() {}
      @Override public void close() {}
    };
    Magic.plugin.getLogger().addHandler(handler);
  }

  @AfterEach
  void releaseLog() { Magic.plugin.getLogger().removeHandler(handler); }

  Path data() { return temp.resolve("data"); }

  @Test
  void anAcknowledgedReleaseLeavesTheLiveFileQuietly() throws Exception {
    var location = station();
    var id = seed(location, attuned(), UUID.randomUUID(), Map.of()).generation();
    assertNotNull(eject(location));
    assertTrue(Files.exists(data().resolve("gear-stations-archive/" + id + ".yml")));
    assertFalse(Files.readString(data().resolve("gear-stations.yml")).contains(id.toString()));
    assertTrue(logged.stream().noneMatch(line -> line.contains("Archived")), logged.toString());
  }

  @Test
  void startupReportsArchivedRowsDifferingCopiesAndFailures() throws Exception {
    var location = station();
    var first = seed(location, attuned(), UUID.randomUUID(), Map.of()).generation();
    var second = seed(location, attuned(), UUID.randomUUID(), Map.of()).generation(); // seeding marks the first released
    GearStationStore.load();
    assertTrue(logged.contains("INFO [Magic] Archived 1 released gear station rows to data/gear-stations-archive."), logged.toString());
    assertTrue(Files.exists(data().resolve("gear-stations-archive/" + first + ".yml")));
    assertNotNull(GearStationStore.get(location));

    logged.clear();
    GearStationStore.load();
    assertTrue(logged.stream().noneMatch(line -> line.contains("Archived")), logged.toString());

    var copy = data().resolve("gear-stations-archive/" + second + ".yml");
    GearFiles.write(copy, "stations: {}\n".getBytes(StandardCharsets.UTF_8), false);
    seed(location, attuned(), UUID.randomUUID(), Map.of());
    GearStationStore.load();
    assertTrue(logged.contains("WARNING [Magic] Kept released gear station rows in the live file: " + second + " (archive copy differs)"), logged.toString());
    assertTrue(Files.readString(data().resolve("gear-stations.yml")).contains(second.toString()));

    logged.clear();
    GearFiles.write(data().resolve("gear-ownership/estates/broken.json"), "{\"entries\":{}}".getBytes(StandardCharsets.UTF_8), true);
    GearStationStore.load();
    assertTrue(logged.contains("WARNING [Magic] Could not archive released gear stations: Ownership evidence needs review before archiving"), logged.toString());
    assertNotNull(GearStationStore.get(location));
    assertFalse((Boolean) field("unreadableStore"));
  }
}
