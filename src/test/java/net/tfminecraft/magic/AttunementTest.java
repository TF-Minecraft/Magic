package net.tfminecraft.magic;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.google.gson.Gson;
import java.nio.file.*;
import java.util.*;
import java.util.function.Consumer;
import net.tfminecraft.interactiblefurniture.InteractibleFurniture;
import net.tfminecraft.interactiblefurniture.furniture.*;
import net.tfminecraft.magic.artifact.*;
import net.tfminecraft.magic.attunement.*;
import net.tfminecraft.magic.meditation.MeditationCache;
import org.bukkit.*;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.mockbukkit.mockbukkit.*;

class AttunementTest {
  @TempDir Path temp;
  ServerMock server;

  @BeforeEach
  void setup() {
    server = MockBukkit.mock();
    server.addSimpleWorld("display");
    Magic.plugin = mock(Magic.class);
    when(Magic.plugin.namespace()).thenReturn("magic");
    ArtifactDisplayIndex.rebuildFromSaved();
  }

  @AfterEach
  void cleanup() {
    ArtifactDisplayIndex.rebuildFromSaved();
    AuraLog.configure(false, false, null);
    Magic.plugin = null;
    MockBukkit.unmock();
  }

  @Test
  void displayIndexCountsCopiesAndEnsuresReloadsWithoutDoubleCounting() {
    var item = new ItemStack(Material.STONE);
    ArtifactIds.writeNew(item);
    var id = ArtifactIds.read(item);
    assertFalse(ArtifactDisplayIndex.isDisplayed((UUID) null));
    for (String bad : Arrays.asList(null, "", "unbound", "bad"))
      assertFalse(ArtifactDisplayIndex.isDisplayed(bad));
    ArtifactDisplayIndex.add((UUID) null);
    ArtifactDisplayIndex.ensure((UUID) null);
    ArtifactDisplayIndex.remove((UUID) null);
    ArtifactDisplayIndex.remove(id);
    ArtifactDisplayIndex.add(item);
    ArtifactDisplayIndex.add(id);
    assertTrue(ArtifactDisplayIndex.isDisplayed(id.toString()));
    ArtifactDisplayIndex.ensure(item);
    ArtifactDisplayIndex.remove(item);
    assertTrue(ArtifactDisplayIndex.isDisplayed(id));
    ArtifactDisplayIndex.remove(id);
    assertFalse(ArtifactDisplayIndex.isDisplayed(id));
    ArtifactDisplayIndex.ensure(id);
    assertTrue(ArtifactDisplayIndex.isDisplayed(id));
  }

  @Test
  void displayFurnitureRejectsCarriedObjectsAndTracksLoadedSavedSlots() {
    var furniture = mock(Furniture.class);
    var slot = mock(PlacedSlot.class);
    var item = new ItemStack(Material.STONE);
    ArtifactIds.writeNew(item);
    var id = ArtifactIds.read(item);
    when(slot.getCurrentItem()).thenReturn(item);
    when(furniture.getActiveSlots()).thenReturn(Map.of("main", slot));
    assertFalse(ArtifactDisplayIndex.isDisplayFurniture(null));
    assertFalse(ArtifactDisplayIndex.isDisplayFurniture(furniture));
    ArtifactDisplayIndex.indexFurniture(null, true);
    ArtifactDisplayIndex.removeFurniture(null);
    ArtifactDisplayIndex.removeFurniture(furniture);
    when(furniture.getId()).thenReturn("pedestal");
    MeditationCache.pedestalId = "pedestal";
    assertTrue(ArtifactDisplayIndex.isDisplayFurniture(furniture));
    when(furniture.isCarried()).thenReturn(true);
    assertFalse(ArtifactDisplayIndex.isDisplayFurniture(furniture));
    when(furniture.isCarried()).thenReturn(false);
    when(furniture.isPersistedCarried()).thenReturn(true);
    assertFalse(ArtifactDisplayIndex.isDisplayFurniture(furniture));
    when(furniture.isPersistedCarried()).thenReturn(false);
    when(furniture.getId()).thenReturn("shelf");
    ArtifactCareCache.displayFurnitureId = null;
    assertFalse(ArtifactDisplayIndex.isDisplayFurniture(furniture));
    ArtifactCareCache.displayFurnitureId = "";
    assertFalse(ArtifactDisplayIndex.isDisplayFurniture(furniture));
    ArtifactCareCache.displayFurnitureId = "display";
    assertFalse(ArtifactDisplayIndex.isDisplayFurniture(furniture));
    ArtifactCareCache.displayFurnitureId = "shelf";
    assertTrue(ArtifactDisplayIndex.isDisplayFurniture(furniture));
    ArtifactDisplayIndex.indexFurniture(furniture, true);
    assertTrue(ArtifactDisplayIndex.isDisplayed(id));
    ArtifactDisplayIndex.removeFurniture(furniture);
    assertFalse(ArtifactDisplayIndex.isDisplayed(id));
    var chunk = server.getWorlds().getFirst().getChunkAt(0, 0);
    ArtifactDisplayIndex.ensureChunk(null);
    ArtifactDisplayIndex.ensureChunk(chunk);
    var enabled = MockBukkit.createMockPlugin("InteractibleFurniture");
    try (var bridge = mockStatic(InteractibleFurniture.class)) {
      ArtifactDisplayIndex.rebuildFromSaved();
      ArtifactDisplayIndex.ensureChunk(chunk);
      var dependency = mock(InteractibleFurniture.class);
      bridge.when(InteractibleFurniture::getInstance).thenReturn(dependency);
      ArtifactDisplayIndex.rebuildFromSaved();
      ArtifactDisplayIndex.ensureChunk(chunk);
      var manager = mock(net.tfminecraft.interactiblefurniture.manager.FurnitureManager.class);
      when(dependency.getFurnitureManager()).thenReturn(manager);
      when(manager.getFurnitureInChunk(chunk)).thenReturn(Set.of(furniture));
      doAnswer(
              i -> {
                Consumer<Furniture> visit = i.getArgument(0);
                visit.accept(furniture);
                return null;
              })
          .when(manager)
          .visitSavedFurniture(any());
      ArtifactDisplayIndex.rebuildFromSaved();
      ArtifactDisplayIndex.ensureChunk(chunk);
      assertTrue(ArtifactDisplayIndex.isDisplayed(id));
      ArtifactDisplayIndex.remove(id);
      assertFalse(ArtifactDisplayIndex.isDisplayed(id));
      server.getPluginManager().disablePlugin(enabled);
      ArtifactDisplayIndex.rebuildFromSaved();
      ArtifactDisplayIndex.ensureChunk(chunk);
    }
  }

  @Test
  void legacyClaimJsonRepairsNullMapsAndCopiesWithoutSharing() {
    var gson = new Gson();
    var claim = gson.fromJson("{\"byElement\":null,\"displayName\":null}", ArtifactClaim.class);
    assertTrue(claim.isEmpty());
    assertEquals(0, claim.getAmount("fire"));
    assertEquals("", claim.getDisplayName());
    var copy = claim.copy();
    assertTrue(copy.getByElement().isEmpty());
    assertTrue(claim.getByElement().isEmpty());
    claim = gson.fromJson("{\"byElement\":null}", ArtifactClaim.class);
    claim.setAmount(null, 2);
    claim.setAmount(" ", 2);
    claim.setAmount(" FIRE ", 1.234567);
    assertEquals(1.234567, claim.getAmount("fire"), .000001);
    copy = claim.copy();
    copy.setAmount("fire", 3);
    assertNotEquals(copy.getAmount("fire"), claim.getAmount("fire"));
    claim.setAmount("fire", 0);
    assertTrue(claim.isEmpty());
    claim.setByElement(null);
    var amounts = new HashMap<String, Double>();
    amounts.put("empty", null);
    amounts.put("zero", 0d);
    claim.setByElement(amounts);
    assertFalse(claim.hasAnyAmount());
    assertEquals(0, claim.getAmount("empty"));
    claim.setAmount("fire", 2);
    assertTrue(claim.hasAnyAmount());
    claim.setOffDisplaySeconds(-1);
    assertEquals(0, claim.getOffDisplaySeconds());
    claim.setOffDisplaySeconds(3);
    assertEquals(3, claim.getOffDisplaySeconds());
    claim.setWaneWarned(true);
    assertTrue(claim.copy().isWaneWarned());
    claim.setDisplayName(null);
    assertEquals("", claim.getDisplayName());
    claim.setDisplayName("test");
    assertEquals("test", claim.copy().getDisplayName());
    assertEquals(0, claim.getAmount(null));
  }

  @Test
  void auraLoggingAppendsFormatsWipesAndToleratesFilesystemFailures() throws Exception {
    AuraLog.configure(false, true, temp.toFile());
    assertFalse(AuraLog.isEnabled());
    AuraLog.append("disabled");
    AuraLog.append("disabled %s", "arg");
    var file = temp.resolve("logs/aura.log");
    assertFalse(Files.exists(file));
    AuraLog.configure(true, false, temp.toFile());
    AuraLog.append((String) null);
    AuraLog.append("plain");
    AuraLog.append("value %s", AuraLog.n(1.25));
    var lines = Files.readAllLines(file);
    assertEquals(2, lines.size());
    assertTrue(lines.get(0).endsWith(" plain"));
    assertTrue(lines.get(1).endsWith(" value 1.25"));
    AuraLog.configure(true, true, null);
    assertFalse(Files.exists(file));
    Files.createDirectory(file);
    Files.writeString(file.resolve("child"), "keep");
    AuraLog.configure(true, true, temp.toFile());
    assertTrue(Files.exists(file.resolve("child")));
    AuraLog.append("cannot write directory");
  }

  @Test
  void careAcceptsPlayerInventoryAndIndexedDisplaysButNotOrdinaryStorage() {
    var player = server.addPlayer();
    var item = new ItemStack(Material.STONE);
    ArtifactIds.writeNew(item);
    var chest = server.createInventory(null, 9);
    assertFalse(ArtifactCarePlaces.accepted(null, item));
    assertFalse(ArtifactCarePlaces.accepted(chest, item));
    assertTrue(ArtifactCarePlaces.accepted(player.getInventory(), item));
    ArtifactDisplayIndex.add(item);
    assertTrue(ArtifactCarePlaces.accepted(chest, item));
  }
}
