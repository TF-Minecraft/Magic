package net.tfminecraft.magic.artifact;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import net.tfminecraft.magic.*;
import net.tfminecraft.magic.artifact.aura.VesselLore;
import net.tfminecraft.magic.attunement.*;
import net.tfminecraft.magic.model.ElementDef;
import net.tfminecraft.magic.registry.ElementRegistry;
import net.tfminecraft.magic.session.ResonanceSession;
import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.*;
import org.mockbukkit.mockbukkit.*;

class ArtifactScanTest {
  ServerMock server;

  @BeforeEach
  void setup() {
    server = MockBukkit.mock();
    Magic.plugin = mock(Magic.class);
    when(Magic.plugin.namespace()).thenReturn("magic");
    ElementRegistry.clear();
    AuraLog.configure(false, false, null);
  }

  @AfterEach
  void cleanup() {
    ElementRegistry.clear();
    Magic.plugin = null;
    MockBukkit.unmock();
  }

  @Test
  void scanPersistsChangedSnapshotsToTheirInventorySlot() {
    var handler = new ArtifactAttuneScanHandler();
    var player = server.addPlayer();
    var stack = new ItemStack(Material.STONE);
    ArtifactIds.writeNew(stack);
    var aura = Artifact.create();
    aura.setCap("fire", 10);
    aura.setFill("fire", 5);
    aura.persistPdc(stack);
    assertTrue(handler.matches(stack));
    assertFalse(handler.matches(null));
    var inv = server.createInventory(null, 9);
    inv.setItem(2, stack);
    try (var places = mockStatic(ArtifactCarePlaces.class);
        var care = mockStatic(ArtifactCareStore.class)) {
      care.when(() -> ArtifactCareStore.apply(eq(stack), anyBoolean(), anyLong(), any()))
          .thenReturn(true);
      handler.update(player, inv, 2, stack);
      assertEquals(stack, inv.getItem(2));
      handler.update(player, null, 0, stack);
      handler.update(player, inv, -1, stack);
      care.when(() -> ArtifactCareStore.apply(eq(stack), anyBoolean(), anyLong(), any()))
          .thenReturn(false);
      handler.update(player, inv, 2, stack);
    }
  }

  @Test
  void captureRejectsInvalidAndLockedGainsAndSupportsAnonymousCredit() {
    var session = new ResonanceSession();
    var player = server.addPlayer();
    for (String id : new String[] {null, " "})
      AttunementCaptureService.credit(player, session, null, id, 1);
    AttunementCaptureService.credit(player, null, null, "fire", 1);
    AttunementCaptureService.credit(player, session, null, "fire", 0);
    var cfg = new YamlConfiguration();
    cfg.set("permission", "secret.fire");
    ElementRegistry.register(new ElementDef("fire", cfg));
    AttunementCaptureService.credit(player, session, null, "fire", 1);
    assertEquals(0, session.getResonance("fire"));
    ElementRegistry.register(new ElementDef("fire", new YamlConfiguration()));
    AttunementCaptureService.credit(null, session, null, "fire", 1);
    assertEquals(1, session.getResonance("fire"));
  }

  @Test
  void displayNamesArePlainAndInventoryLoreHandlesAllSlots() {
    assertEquals("", AttunementCaptureService.displayNameOf(null));
    var stack = new ItemStack(Material.STONE);
    assertEquals("", AttunementCaptureService.displayNameOf(stack));
    var meta = stack.getItemMeta();
    meta.setLore(java.util.List.of("Only lore"));
    stack.setItemMeta(meta);
    assertEquals("", AttunementCaptureService.displayNameOf(stack));
    meta.setDisplayName(" ");
    stack.setItemMeta(meta);
    assertEquals("", AttunementCaptureService.displayNameOf(stack));
    meta.setDisplayName("§aCrystal");
    stack.setItemMeta(meta);
    assertEquals("Crystal", AttunementCaptureService.displayNameOf(stack));
    VesselLore.updateInventory(null);
    var player = server.addPlayer();
    player.getInventory().setItem(0, stack);
    player.getInventory().setHelmet(stack);
    player.getInventory().setItemInOffHand(stack);
    VesselLore.updateInventory(player);
    assertEquals(
        "Crystal", AttunementCaptureService.displayNameOf(player.getInventory().getItem(0)));
  }
}
