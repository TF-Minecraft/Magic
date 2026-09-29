package net.tfminecraft.magic;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.io.*;
import java.lang.reflect.*;
import java.nio.file.*;
import java.util.*;
import net.tfminecraft.magic.artifact.shrine.*;
import net.tfminecraft.magic.loader.GuiLoader;
import net.tfminecraft.magic.registry.ElementRegistry;
import org.bukkit.*;
import org.bukkit.configuration.*;
import org.junit.jupiter.api.*;

class GuiShrineConfigCoverageTest extends GearCoverageSupport {
  Map<Field, Object> saved = new LinkedHashMap<>();
  boolean debug;

  @BeforeEach
  void configSetup() throws Exception {
    for (Class<?> type : List.of(GuiCache.class, ArtifactCreateCache.class)) {
      for (Field field : type.getFields()) {
        if (Modifier.isStatic(field.getModifiers()) && !Modifier.isFinal(field.getModifiers()))
          saved.put(field, field.get(null));
      }
    }
    for (String name : List.of("config", "bundled")) {
      var f = Messages.class.getDeclaredField(name);
      f.setAccessible(true);
      saved.put(f, f.get(null));
    }
    debug = Cache.debug;
    ShrineRegistry.clear();
    ElementRegistry.clear();
  }

  @AfterEach
  void configCleanup() throws Exception {
    for (var entry : saved.entrySet()) entry.getKey().set(null, entry.getValue());
    Cache.debug = debug;
    ShrineRegistry.clear();
    ElementRegistry.clear();
  }

  File write(String content) throws Exception {
    var p = temp.resolve("case.yml");
    Files.writeString(p, content);
    return p.toFile();
  }

  @Test
  void guiPartialModesButtonsAndInvalidSlotsAreValidated() throws Exception {
    GuiCache.characterHeadSlot = 4;
    GuiCache.castModeLeftSlot = 47;
    GuiCache.castModeRightSlot = 51;
    ArtifactCreateCache.previewSlot = 4;
    ArtifactCreateCache.capMinusSlot = 12;
    ArtifactCreateCache.capPlusSlot = 14;
    ArtifactCreateCache.confirmSlot = 48;
    ArtifactCreateCache.cancelSlot = 50;
    ArtifactCreateCache.raritySlots = List.of(10, 11, 13, 15, 16);
    var loader = new GuiLoader();
    loader.load(
        write(
            "cast_modes: {}\n"
                + "artifact_create:\n"
                + "  items: {}\n"
                + "  slots: {}\n"
                + "colors:\n"
                + "  blank: ''\n"
                + "  good: '#ffffff'\n"));
    assertEquals("#ffffff", GuiCache.colors.get("good"));
    assertFalse(GuiCache.colors.containsKey("blank"));
    assertTrue(loader.loadSafe(write("artifact_create: {}\n")));
    ArtifactCreateCache.raritySlots = Arrays.asList(null, -1, 54);
    assertFalse(loader.loadSafe(write("")));
    ArtifactCreateCache.raritySlots = List.of(10);
    GuiCache.castModeLeftSlot = -1;
    assertFalse(loader.loadSafe(write("")));
  }

  @Test
  void shrineFxParserAndBlockTagsHandleMalformedAndEmptyOptionalValues() throws Exception {
    Cache.debug = false;
    assertTrue(
        ShrineConfigLoader.load(
            write("fx:\n  ' ': {}\n  scalar: invalid\n  fire: {}\nelements: {}\n")));
    var logger = Magic.plugin.getLogger();
    assertNotNull(
        invoke(
            ShrineConfigLoader.class,
            "readFx",
            new Class[] {
              ConfigurationSection.class, ShrineFxDef.class, java.util.logging.Logger.class
            },
            null,
            null,
            logger));
    var materials = new LinkedHashSet<Material>();
    var types =
        new Class[] {
          Set.class, String.class, String.class, String.class, java.util.logging.Logger.class
        };
    invoke(ShrineConfigLoader.class, "addBlock", types, materials, null, "fire", "family", logger);
    invoke(ShrineConfigLoader.class, "addTag", types, materials, null, "fire", "family", logger);
    var tag = mock(org.bukkit.Tag.class);
    when(tag.getValues())
        .thenReturn(new LinkedHashSet<>(Arrays.asList(null, Material.STICK, Material.STONE)));
    try (var bukkit = mockStatic(Bukkit.class, CALLS_REAL_METHODS)) {
      bukkit
          .when(
              () ->
                  Bukkit.getTag(
                      eq(org.bukkit.Tag.REGISTRY_BLOCKS),
                      eq(NamespacedKey.minecraft("test")),
                      eq(Material.class)))
          .thenReturn(tag);
      invoke(
          ShrineConfigLoader.class, "addTag", types, materials, "test", "fire", "family", logger);
    }
    assertEquals(Set.of(Material.STONE), materials);
  }

  @Test
  void bundledMessageReadAndCloseFailuresPreserveFallbackBehavior() throws Exception {
    var input =
        new InputStream() {
          public int read() throws IOException {
            throw new IOException("read failed");
          }

          public void close() throws IOException {
            throw new IOException("close failed");
          }
        };
    when(Magic.plugin.getResource("messages.yml")).thenReturn(input);
    Messages.loadFromResources();
    assertEquals("", invoke(Messages.class, "format", new Class[] {String.class}, (Object) null));
  }

  @Test
  void bundledStreamsHandleReadFailureWithSuccessfulCloseAndSuccessfulReadWithCloseFailure()
      throws Exception {
    var readFailure =
        new InputStream() {
          public int read() throws IOException {
            throw new IOException("read failed");
          }
        };
    when(Magic.plugin.getResource("messages.yml")).thenReturn(readFailure);
    assertNull(invoke(Messages.class, "readBundled", new Class[] {}));
    var closeFailure =
        new ByteArrayInputStream(
            "hello: world\n".getBytes(java.nio.charset.StandardCharsets.UTF_8)) {
          public void close() throws IOException {
            throw new IOException("close failed");
          }
        };
    when(Magic.plugin.getResource("messages.yml")).thenReturn(closeFailure);
    assertNull(invoke(Messages.class, "readBundled", new Class[] {}));
  }

  @Test
  void missingMessageConfigurationReturnsKeyWithoutThrowing() throws Exception {
    for (String name : List.of("config", "bundled")) {
      var field = Messages.class.getDeclaredField(name);
      field.setAccessible(true);
      field.set(null, null);
    }
    assertEquals("missing", Messages.getRaw("missing"));
  }
}
