package net.tfminecraft.magic;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.nio.file.*;
import java.util.*;
import net.tfminecraft.magic.artifact.sacrifice.*;
import net.tfminecraft.magic.registry.ElementRegistry;
import net.tfminecraft.tlibs.TLibs;
import net.tfminecraft.tlibs.objects.api.ItemAPI;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.mockbukkit.mockbukkit.MockBukkit;

class SacrificeConfigEdgeTest {
  @TempDir Path temp;

  @BeforeEach
  void setup() {
    MockBukkit.mock();
    Magic.plugin = mock(Magic.class);
    when(Magic.plugin.getLogger()).thenReturn(java.util.logging.Logger.getAnonymousLogger());
    SacrificeRegistry.clear();
    ElementRegistry.clear();
    ElementRegistry.register(DomainTest.element("fire"));
  }

  @AfterEach
  void cleanup() {
    SacrificeRegistry.clear();
    ElementRegistry.clear();
    Magic.plugin = null;
    MockBukkit.unmock();
  }

  @Test
  void blankDaggerFallsBackAndCaseSensitiveWordsAndBlankSoundsLoad() throws Exception {
    var file = temp.resolve("sacrifice.yml");
    Files.writeString(
        file,
        "dagger: ''\n"
            + "case_insensitive: false\n"
            + "pedestal_id: ' '\n"
            + "elements:\n"
            + "  fire: {words: [Word], min_scenery_aura: -1}\n"
            + "fx: {surge_sound: ' ', boom_sound: ' '}\n");
    assertTrue(SacrificeConfigLoader.load(file.toFile()));
    assertEquals(SacrificeDaggerDef.DEFAULT_PATH, SacrificeRegistry.getDagger().getPath());
    assertEquals(50, SacrificeRegistry.minSceneryAura("fire"));
    assertEquals(SacrificeDaggerDef.DEFAULT_PATH, new SacrificeDaggerDef(" ").getPath());
    assertEquals(SacrificeDaggerDef.DEFAULT_PATH, new SacrificeDaggerDef(null).getPath());
    Files.writeString(file, "elements: {}\nfx: {}\n");
    assertTrue(SacrificeConfigLoader.load(file.toFile()));
  }

  @Test
  void fxDefinitionsFilterBlankTiersAndExposeDefensiveLists() {
    var fx =
        new SacrificeFxDef(
            new LinkedHashSet<>(Arrays.asList(null, " ", " SOUL ")), null, 1, 1, null, 1, 1);
    assertEquals(List.of("soul"), fx.getSurgeTiers());
    assertFalse(fx.surges(null));
    assertFalse(fx.surges(" "));
    assertFalse(fx.surges("pain"));
    assertTrue(fx.surges("SOUL"));
    assertFalse(new SacrificeFxDef(null, null, 1, 1, null, 1, 1).surges("soul"));
  }

  @Test
  void daggerMatcherUsesConfiguredItemPath() {
    var item = new ItemStack(Material.GOLDEN_SWORD);
    try (var libs = mockStatic(TLibs.class)) {
      var api = mock(ItemAPI.class, RETURNS_DEEP_STUBS);
      libs.when(TLibs::getItemAPI).thenReturn(api);
      when(api.getChecker().checkItemWithPath(item, SacrificeDaggerDef.DEFAULT_PATH))
          .thenReturn(true);
      assertTrue(SacrificeDaggerMatcher.matches(item));
      assertFalse(SacrificeDaggerMatcher.matches(null));
    }
  }
}
