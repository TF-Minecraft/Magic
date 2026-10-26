package net.tfminecraft.magic.blood;

import static org.junit.jupiter.api.Assertions.*;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.logging.Logger;

import org.bukkit.Material;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class BloodConfigTest {
  @TempDir Path temp;
  final Logger log = Logger.getAnonymousLogger();

  @AfterEach
  void reset() {
    BloodConfig.reset();
  }

  File write(String yaml) throws Exception {
    File file = temp.resolve("bloodmagic.yml").toFile();
    Files.writeString(file.toPath(), yaml);
    return file;
  }

  @Test
  void aFolderMergesItsFilesByNameAndSkipsMessages() throws Exception {
    java.nio.file.Path dir = temp.resolve("bm");
    Files.createDirectories(dir);
    Files.writeString(dir.resolve("a.yml"), "enabled: true\naltar:\n  display_scale: 2.0\n");
    Files.writeString(dir.resolve("b.yml"), "altar:\n  display_scale: 3.0\n");
    Files.writeString(dir.resolve("messages.yml"), "enabled: false\n");
    Files.writeString(dir.resolve("notes.txt"), "enabled: false\n");
    assertTrue(BloodConfig.load(dir.toFile(), log));
    assertTrue(BloodConfig.isEnabled());
    assertEquals(3.0f, BloodConfig.displayScale());
    Files.writeString(dir.resolve("c.yml"), "altar: [unclosed\n");
    assertFalse(BloodConfig.load(dir.toFile(), log));
    assertFalse(BloodConfig.isEnabled());
    File odd = org.mockito.Mockito.mock(File.class);
    org.mockito.Mockito.when(odd.listFiles(org.mockito.ArgumentMatchers.any(java.io.FilenameFilter.class))).thenReturn(null);
    assertTrue(BloodConfig.parts(odd).isEmpty());
    assertTrue(BloodConfig.load(null, log));
    assertTrue(BloodConfig.load(temp.resolve("nowhere").toFile(), log));
    assertFalse(BloodConfig.isEnabled());
  }

  @Test
  void bundledFileLoadsEverySection() {
    File bundled = new File("src/main/resources/wildmagic/bloodmagic");
    assertTrue(BloodConfig.load(bundled, log));
    assertTrue(BloodConfig.isEnabled());
    assertEquals("m.lore_weapons.blood_knife", BloodConfig.item(BloodItemKind.BLOOD_KNIFE).path());
    assertFalse(BloodConfig.item(BloodItemKind.ALTAR).lore().isEmpty());
    assertEquals(Material.BARRIER, BloodConfig.altarBlock());
    assertEquals("ia.tfmc_blood:altar_0", BloodConfig.model(0));
    assertEquals("ia.tfmc_blood:altar_5", BloodConfig.model(9));
    assertEquals(1.0f, BloodConfig.displayScale());
    assertEquals(0, BloodConfig.level(1));
    assertEquals(2, BloodConfig.level(5));
    assertEquals(5, BloodConfig.level(40));
    assertTrue(BloodConfig.worldAllowed("anything"));
    assertTrue(BloodConfig.requireUnderground());
    assertEquals(3, BloodConfig.minCover());
    assertEquals(48, BloodConfig.coverScanHeight());
    assertTrue(BloodConfig.denyRegions());
    assertFalse(BloodConfig.regionAllowed("spawn"));
    assertEquals(10, BloodConfig.purifyCheckMinutes());
    assertEquals(7, BloodConfig.huntPointsRequired());
  }

  @Test
  void missingOrBrokenFilesLeaveTheFeatureDisabled() throws Exception {
    assertTrue(BloodConfig.load(null, log));
    assertTrue(BloodConfig.load(temp.resolve("none.yml").toFile(), log));
    assertFalse(BloodConfig.isEnabled());
    assertFalse(BloodConfig.load(write("enabled: [unclosed"), log));
    assertFalse(BloodConfig.isEnabled());
  }

  @Test
  void emptyFileFallsBackToSafeDefaults() throws Exception {
    assertTrue(BloodConfig.load(write("enabled: true\n"), log));
    assertTrue(BloodConfig.isEnabled());
    for (BloodItemKind kind : BloodItemKind.values()) {
      assertEquals(kind.fallbackPath(), BloodConfig.item(kind).path());
      assertEquals("", BloodConfig.item(kind).name());
    }
    assertEquals("v.chiseled_stone_bricks", BloodConfig.model(3));
    assertEquals(0, BloodConfig.level(100));
  }

  @Test
  void badValuesAreReportedAndReplaced() throws Exception {
    String yaml = String.join("\n",
        "enabled: true",
        "items:",
        "  altar:",
        "    name: plain",
        "altar:",
        "  block: NOT_A_BLOCK",
        "  models:",
        "    x: v.stone",
        "    2: v.dirt",
        "  levels:",
        "    1: three",
        "    2: 4",
        "  display_scale: 0",
        "placement:",
        "  worlds: [Cave, ' ']",
        "  allowed_regions: [Wilds]",
        "  min_cover: -4",
        "  cover_scan_height: 0",
        "  purify_check_minutes: 0",
        "hunt:",
        "  points_required: 0",
        "");
    assertFalse(BloodConfig.load(write(yaml), log));
    assertEquals("v.chiseled_stone_bricks", BloodConfig.item(BloodItemKind.ALTAR).path());
    assertEquals("plain", BloodConfig.item(BloodItemKind.ALTAR).name());
    assertEquals(Material.BARRIER, BloodConfig.altarBlock());
    assertEquals("v.dirt", BloodConfig.model(0));
    assertEquals("v.dirt", BloodConfig.model(5));
    assertEquals(0.1f, BloodConfig.displayScale(), 1e-6);
    assertEquals(2, BloodConfig.level(4));
    assertTrue(BloodConfig.worldAllowed("cave"));
    assertFalse(BloodConfig.worldAllowed("other"));
    assertFalse(BloodConfig.worldAllowed(null));
    assertTrue(BloodConfig.regionAllowed("WILDS"));
    assertFalse(BloodConfig.regionAllowed(null));
    assertEquals(0, BloodConfig.minCover());
    assertEquals(1, BloodConfig.coverScanHeight());
    assertEquals(1, BloodConfig.purifyCheckMinutes());
    assertEquals(1, BloodConfig.huntPointsRequired());

    assertFalse(BloodConfig.load(write("altar:\n  block: DIAMOND\n"), log));
    assertEquals(Material.BARRIER, BloodConfig.altarBlock());
    assertTrue(BloodConfig.load(write("altar:\n  block: obsidian\n"), log));
    assertEquals(Material.OBSIDIAN, BloodConfig.altarBlock());
    assertFalse(BloodConfig.isEnabled());
    BloodConfig.setEnabled(true);
    assertTrue(BloodConfig.isEnabled());
  }
}
