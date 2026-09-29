package net.tfminecraft.magic;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.io.File;
import java.security.*;
import java.util.*;
import net.tfminecraft.magic.model.*;
import net.tfminecraft.magic.modifier.*;
import net.tfminecraft.magic.util.*;
import net.tfminecraft.tlibs.TLibs;
import net.tfminecraft.tlibs.objects.api.ItemAPI;
import net.tfminecraft.tlibs.objects.api.subapi.StringFormatter;
import org.bukkit.*;
import org.bukkit.inventory.*;
import org.junit.jupiter.api.*;
import org.mockbukkit.mockbukkit.*;

class UtilityEdgeCoverageTest {
  @BeforeEach
  void setup() {
    MockBukkit.mock();
    Magic.plugin = mock(Magic.class);
    when(Magic.plugin.namespace()).thenReturn("magic");
    when(Magic.plugin.getLogger()).thenReturn(java.util.logging.Logger.getLogger("MagicTests"));
  }

  @AfterEach
  void cleanup() {
    Magic.plugin = null;
    MockBukkit.unmock();
  }

  @Test
  void itemReferencesNormalizeCloneAndHandleUnavailableProviders() {
    assertEquals("", ItemRef.normalize(null));
    assertEquals("", ItemRef.normalize(" "));
    assertEquals("v.stone", ItemRef.normalize(" VANILLA.STONE "));
    assertEquals("m.SWORD.TEST", ItemRef.normalize(" m.SWORD.TEST "));
    assertNull(ItemRef.build(" "));
    var api = mock(ItemAPI.class, RETURNS_DEEP_STUBS);
    var stone = new ItemStack(Material.STONE);
    try (var libs = mockStatic(TLibs.class)) {
      libs.when(TLibs::getItemAPI).thenReturn(api);
      when(api.getCreator().getItemFromPath("missing")).thenReturn(null);
      assertNull(ItemRef.build("missing"));
      when(api.getCreator().getItemFromPath("v.air")).thenReturn(new ItemStack(Material.AIR));
      assertNull(ItemRef.build("vanilla.air"));
      when(api.getCreator().getItemFromPath("v.stone")).thenReturn(stone);
      var result = ItemRef.build("vanilla.stone");
      assertEquals(stone, result);
      assertNotSame(stone, result);
      assertEquals(stone, ItemRef.buildOrFallback("vanilla.stone", Material.BARRIER));
      assertEquals(
          Material.BARRIER, ItemRef.buildOrFallback("missing", Material.BARRIER).getType());
      when(api.getCreator().getItemFromPath("broken"))
          .thenThrow(new IllegalStateException("unavailable"));
      assertNull(ItemRef.build("broken"));
    }
    ItemRef.applyBlankDisplay(null);
    ItemRef.applyBlankDisplay(new ItemStack(Material.AIR));
    ItemRef.applyBlankDisplay(stone);
    assertEquals(" ", stone.getItemMeta().getDisplayName());
    assertFalse(stone.getItemMeta().hasLore());
    assertTrue(stone.getItemMeta().hasItemFlag(ItemFlag.HIDE_ATTRIBUTES));
  }

  @Test
  void costNamesFallBackWhenItemsOrNamesAreMissing() {
    var api = mock(ItemAPI.class, RETURNS_DEEP_STUBS);
    var stone = new ItemStack(Material.STONE);
    try (var libs = mockStatic(TLibs.class);
        var formatter = mockStatic(StringFormatter.class, CALLS_REAL_METHODS)) {
      libs.when(TLibs::getItemAPI).thenReturn(api);
      when(api.getCreator().getItemFromPath("missing")).thenReturn(null);
      assertEquals(
          List.of("§7- §fmissing §e1"), CostFormatter.getCostsFormatted(Map.of("missing", 1)));
      when(api.getCreator().getItemFromPath("air")).thenReturn(new ItemStack(Material.AIR));
      assertEquals(List.of("§7- §fair §e1"), CostFormatter.getCostsFormatted(Map.of("air", 1)));
      when(api.getCreator().getItemFromPath("stone")).thenReturn(stone);
      formatter.when(() -> StringFormatter.getName(stone)).thenReturn(null, " ", "Stone");
      assertEquals(List.of("§7- §fstone §e2"), CostFormatter.getCostsFormatted(Map.of("stone", 2)));
      assertEquals(List.of("§7- §fstone §e2"), CostFormatter.getCostsFormatted(Map.of("stone", 2)));
      assertEquals(List.of("§7- §fStone §e2"), CostFormatter.getCostsFormatted(Map.of("stone", 2)));
    }
  }

  @Test
  void unavailableDirectoryAndHashProviderFailPredictably() {
    var directory = mock(File.class);
    when(directory.exists()).thenReturn(true);
    when(directory.isDirectory()).thenReturn(true);
    assertTrue(YamlFolder.listYamlFiles(directory).isEmpty());
    var tracker = new RevisionTracker();
    tracker.resolvePart("staff", "hash");
    assertDoesNotThrow(tracker::flush);
    try (var hashes = mockStatic(MessageDigest.class)) {
      hashes
          .when(() -> MessageDigest.getInstance("SHA-256"))
          .thenThrow(new NoSuchAlgorithmException("provider unavailable"));
      assertInstanceOf(
          NoSuchAlgorithmException.class,
          assertThrows(IllegalStateException.class, () -> RevisionTracker.sha256("x")).getCause());
    }
  }

  @Test
  void gridRejectsRowsPastLastRow() {
    int old = GuiCache.elementRow;
    try {
      GuiCache.elementRow = -1;
      assertFalse(GridLayout.validateSlots());
      GuiCache.elementRow = 6;
      assertFalse(GridLayout.validateSlots());
    } finally {
      GuiCache.elementRow = old;
    }
  }

  @Test
  void zeroRoundingAndItemAlignmentRemainNeutral() {
    assertEquals("0", MagicNumbers.format(-0.0));
    assertEquals("0", MagicNumbers.format(-0.001));
    assertTrue(SpellModifiers.alignment(new ItemStack(Material.STICK), "missing").isZero());
  }
}
