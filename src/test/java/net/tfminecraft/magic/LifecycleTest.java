package net.tfminecraft.magic;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import net.tfminecraft.magic.manager.MagicTickService;
import net.tfminecraft.magic.session.ResonanceSession;
import net.tfminecraft.tlibs.TLibs;
import net.tfminecraft.tlibs.objects.api.ItemAPI;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

class LifecycleTest {
  @Test
  void pluginLoadsReloadsAndStopsAllTimers() throws Exception {
    var server = MockBukkit.mock();
    try (var libs = mockStatic(TLibs.class)) {
      ItemAPI api = mock(ItemAPI.class, RETURNS_DEEP_STUBS);
      libs.when(TLibs::getItemAPI).thenReturn(api);
      String descriptor =
          "name: Magic\n"
              + "main: net.tfminecraft.magic.Magic\n"
              + "version: test\n"
              + "api-version: '1.21'\n"
              + "commands:\n"
              + "  magic: {}\n"
              + "  resonance: {}\n";
      Magic plugin =
          MockBukkit.loadWith(
              Magic.class, new ByteArrayInputStream(descriptor.getBytes(StandardCharsets.UTF_8)));
      assertSame(plugin, Magic.plugin);
      assertNotNull(plugin.getProfileService());
      assertNotNull(plugin.getResonanceGuiManager());
      assertNotNull(plugin.getArtifactCreateGuiManager());
      assertNotNull(Magic.getRevisionTracker());
      assertTrue(MagicTickService.isRunning());
      assertTrue(Files.exists(plugin.getDataFolder().toPath().resolve("artifacts/generator.yml")));
      assertTrue(plugin.reloadAll());
      plugin.syncSpellModifiers(null, new ResonanceSession());
      plugin.clearSpellModifiers(null);
      server.getScheduler().performTicks(40);
      Files.writeString(plugin.getDataFolder().toPath().resolve("config.yml"), "bad: [");
      assertFalse(plugin.reloadAll());
      server.getPluginManager().disablePlugin(plugin);
      assertFalse(MagicTickService.isRunning());
      verify(api).unregisterPathHandler("magic");
    } finally {
      MockBukkit.unmock();
      Magic.plugin = null;
    }
  }
}
