package net.tfminecraft.magic;

import static org.mockito.Mockito.*;

import io.lumine.mythic.lib.MythicLib;
import net.Indyuce.mmoitems.MMOItems;
import org.junit.jupiter.api.*;

abstract class GearMmoCoverageSupport extends GearCoverageSupport {
  MMOItems oldMmo;
  MythicLib oldLib;

  @BeforeEach
  void setupMmo() {
    oldMmo = MMOItems.plugin;
    oldLib = MythicLib.plugin;
    MMOItems.plugin = mock(MMOItems.class, RETURNS_DEEP_STUBS);
    when(MMOItems.plugin.namespace()).thenReturn("mmoitems");
    MythicLib.plugin = mock(MythicLib.class, RETURNS_DEEP_STUBS);
    when(MythicLib.plugin.namespace()).thenReturn("mythiclib");
  }

  @AfterEach
  void cleanupMmo() {
    MMOItems.plugin = oldMmo;
    MythicLib.plugin = oldLib;
  }
}
