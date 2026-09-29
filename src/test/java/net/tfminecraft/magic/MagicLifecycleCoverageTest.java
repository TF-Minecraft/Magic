package net.tfminecraft.magic;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import net.tfminecraft.magic.integration.*;
import net.tfminecraft.magic.loader.ConfigLoader;
import net.tfminecraft.magic.manager.MagicTickService;
import net.tfminecraft.magic.session.ResonanceSession;
import net.tfminecraft.tlibs.TLibs;
import net.tfminecraft.tlibs.itemscan.ItemScanService;
import net.tfminecraft.tlibs.objects.api.ItemAPI;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.mockbukkit.mockbukkit.MockBukkit;

class MagicLifecycleCoverageTest {
  @TempDir Path temp;

  @Test
  void optionalIntegrationsRunWithConfigErrorsAndAbsentCommandEntries() throws Exception {
    var server = MockBukkit.mock();
    boolean oldDebug = Cache.debug;
    try (var libs = mockStatic(TLibs.class);
        var bridge = mockStatic(RpCharactersBridge.class);
        var modifiers = mockStatic(SpellModifierApplyService.class);
        var scans = mockStatic(ItemScanService.class);
        var configs =
            mockConstruction(
                ConfigLoader.class,
                (mock, context) -> when(mock.loadSafe(any())).thenReturn(false))) {
      var api = mock(ItemAPI.class, RETURNS_DEEP_STUBS);
      libs.when(TLibs::getItemAPI).thenReturn(api);
      var scan = mock(ItemScanService.class);
      scans.when(ItemScanService::get).thenReturn(scan);
      bridge.when(RpCharactersBridge::isAvailable).thenReturn(true);
      MockBukkit.createMockPlugin("MythicLib");
      MockBukkit.createMockPlugin("MMOCore");
      Cache.debug = true;
      String descriptor =
          "name: Magic\nmain: net.tfminecraft.magic.Magic\nversion: test\napi-version: '1.21'\n";
      var plugin =
          MockBukkit.loadWith(
              Magic.class, new ByteArrayInputStream(descriptor.getBytes(StandardCharsets.UTF_8)));
      verify(scan).subscribe(any());
      var session = new ResonanceSession();
      plugin.syncSpellModifiers(null, session);
      modifiers.verify(() -> SpellModifierApplyService.sync(null, session));
      plugin.clearSpellModifiers(null);
      modifiers.verify(() -> SpellModifierApplyService.clear(null));
      server.getScheduler().performTicks(40);
      modifiers.verify(() -> SpellModifierApplyService.syncChangedOnline(any()), atLeastOnce());
      MagicTickService.stop();
      assertFalse(plugin.reloadAll());
      server.getPluginManager().disablePlugin(plugin);
      verify(scan).unsubscribe(any());
      for (String field : java.util.List.of("profileService", "meditationService")) {
        var f = Magic.class.getDeclaredField(field);
        f.setAccessible(true);
        f.set(plugin, null);
      }
      plugin.onDisable();
    } finally {
      Cache.debug = oldDebug;
      MockBukkit.unmock();
      Magic.plugin = null;
    }
  }

  @Test
  void resourcesPreserveExistingFilesAndLogReadFailures() throws Exception {
    MockBukkit.mock();
    try (var libs = mockStatic(TLibs.class);
        var bridge = mockStatic(RpCharactersBridge.class)) {
      bridge.when(RpCharactersBridge::isAvailable).thenReturn(true);
      var api = mock(ItemAPI.class, RETURNS_DEEP_STUBS);
      libs.when(TLibs::getItemAPI).thenReturn(api);
      String descriptor =
          "name: Magic\nmain: net.tfminecraft.magic.Magic\nversion: test\napi-version: '1.21'\n";
      var loaded =
          MockBukkit.loadWith(
              Magic.class, new ByteArrayInputStream(descriptor.getBytes(StandardCharsets.UTF_8)));
      var plugin = spy(loaded);
      doReturn(temp.resolve("plugin").toFile()).when(plugin).getDataFolder();
      invoke(plugin, "createFolders", new Class[] {});
      invoke(plugin, "createFolders", new Class[] {});
      invoke(plugin, "copyResourceIfMissing", new Class[] {String.class}, "config.yml");
      invoke(plugin, "copyResourceIfMissing", new Class[] {String.class}, "config.yml");
      invoke(plugin, "copyResourceIfMissing", new Class[] {String.class}, "missing-resource.yml");
      InputStream broken =
          new InputStream() {
            @Override
            public int read() throws IOException {
              throw new IOException("unreadable");
            }
          };
      doReturn(broken).when(plugin).getResource("broken.yml");
      invoke(plugin, "copyResourceIfMissing", new Class[] {String.class}, "broken.yml");
      assertFalse(
          Files.exists(temp.resolve("plugin/broken.yml")), "failed copies must remain retryable");
      assertTrue(Files.exists(temp.resolve("plugin/config.yml")));
      InputStream racing =
          new ByteArrayInputStream("default".getBytes(StandardCharsets.UTF_8)) {
            @Override
            public long transferTo(OutputStream output) throws IOException {
              Files.writeString(temp.resolve("plugin/raced.yml"), "user config");
              return super.transferTo(output);
            }
          };
      doReturn(racing).when(plugin).getResource("raced.yml");
      invoke(plugin, "copyResourceIfMissing", new Class[] {String.class}, "raced.yml");
      assertEquals("user config", Files.readString(temp.resolve("plugin/raced.yml")));
      try (var files = Files.list(temp.resolve("plugin"))) {
        assertFalse(
            files.anyMatch(path -> path.getFileName().toString().startsWith(".magic-default-")));
      }
      MockBukkit.createMockPlugin("MythicLib");
      assertFalse(
          (boolean) GearCoverageSupport.invoke(Magic.class, "isMmoStackPresent", new Class[] {}));
      loaded.getServer().getPluginManager().disablePlugin(loaded);
    } finally {
      MockBukkit.unmock();
      Magic.plugin = null;
    }
  }

  static Object invoke(Magic plugin, String name, Class<?>[] types, Object... args)
      throws Exception {
    var method = Magic.class.getDeclaredMethod(name, types);
    method.setAccessible(true);
    return method.invoke(plugin, args);
  }
}
