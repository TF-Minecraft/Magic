package net.tfminecraft.magic;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.*;
import net.tfminecraft.magic.artifact.config.*;
import net.tfminecraft.magic.artifact.fillchest.ArtifactFillChestService;
import net.tfminecraft.magic.artifact.fillchest.ArtifactFillChestService.*;
import net.tfminecraft.magic.artifact.generate.*;
import org.bukkit.*;
import org.bukkit.block.*;
import org.bukkit.inventory.*;
import org.junit.jupiter.api.*;
import org.mockbukkit.mockbukkit.*;

class FillChestTest {
  ServerMock server;
  Block block;
  Inventory inventory;

  @BeforeEach
  void setup() {
    server = MockBukkit.mock();
    var world = server.addSimpleWorld("chests");
    block = world.getBlockAt(0, 60, 0);
    block.setType(Material.CHEST);
    inventory = ((Chest) block.getState()).getInventory();
    ArtifactTypeRegistry.clear();
  }

  @AfterEach
  void cleanup() {
    ArtifactFillChestService.clearAll();
    ArtifactTypeRegistry.clear();
    MockBukkit.unmock();
  }

  @Test
  void pendingRequestsAndModesCanBeArmedConsumedAndCleared() {
    var id = UUID.randomUUID();
    assertNull(ArtifactFillChestService.get(null));
    assertNull(ArtifactFillChestService.get(id));
    for (var mode : Mode.values()) {
      ArtifactFillChestService.arm(id, mode, "fire");
      var pending = ArtifactFillChestService.get(id);
      assertEquals(mode, pending.mode());
      assertEquals("fire", pending.typeId());
      assertTrue(pending.expireAtMs() > System.currentTimeMillis());
      ArtifactFillChestService.consume(id);
      assertNull(ArtifactFillChestService.get(id));
    }
    ArtifactFillChestService.arm(id, Mode.ALL, null);
    ArtifactFillChestService.clear(null);
    ArtifactFillChestService.clear(id);
    assertNull(ArtifactFillChestService.get(id));
    assertNull(ArtifactFillChestService.parseMode(null));
    assertNull(ArtifactFillChestService.parseMode(""));
    assertNull(ArtifactFillChestService.parseMode("missing"));
    ArtifactTypeRegistry.register(ArtifactGenerationTest.type("fire", true, 1));
    ArtifactTypeRegistry.register(ArtifactGenerationTest.type("water", false, 1));
    assertEquals(Mode.TYPE, ArtifactFillChestService.parseMode("FIRE"));
    assertNull(ArtifactFillChestService.parseMode("water"));
    assertEquals(Mode.ALL, ArtifactFillChestService.parseMode("ALL"));
    assertEquals(Mode.RANDOM, ArtifactFillChestService.parseMode("random"));
    assertFalse(ArtifactFillChestService.isChestBlock(null));
    assertTrue(ArtifactFillChestService.isChestBlock(block));
    block.setType(Material.TRAPPED_CHEST);
    assertTrue(ArtifactFillChestService.isChestBlock(block));
    block.setType(Material.STONE);
    assertFalse(ArtifactFillChestService.isChestBlock(block));
    assertEquals(0, ArtifactFillChestService.fill(block, new Pending(Mode.ALL, null, 0)));
  }

  @Test
  void generationFailurePreservesExistingChestContents() {
    var original = new ItemStack(Material.DIAMOND, 12);
    inventory.setItem(0, original);
    try (var rolls =
            mockConstruction(
                ArtifactRoller.class,
                (m, c) ->
                    when(m.roll(anyString(), anyString()))
                        .thenReturn(ArtifactRoll.error("failure")));
        var builders = mockConstruction(ArtifactItemBuilder.class)) {
      assertEquals(0, ArtifactFillChestService.fill(block, new Pending(Mode.ALL, null, 0)));
      assertEquals(original, inventory.getItem(0));
    }
  }

  @Test
  void successfulFillReplacesChestAndClosesViewersAcrossAllModes() {
    var roll = ArtifactRoll.ok("fire", "common", List.of(new ArtifactAuraSlot("fire", 10)));
    var item = new ItemStack(Material.DIAMOND);
    var viewer = server.addPlayer();
    viewer.openInventory(inventory);
    try (var rolls =
            mockConstruction(
                ArtifactRoller.class,
                (m, c) -> when(m.roll(anyString(), anyString())).thenReturn(roll));
        var builders =
            mockConstruction(
                ArtifactItemBuilder.class,
                (m, c) -> when(m.build(roll, null, null, false)).thenReturn(item))) {
      for (var mode : Mode.values()) {
        assertEquals(27, ArtifactFillChestService.fill(block, new Pending(mode, "fire", 0)));
        for (var actual : inventory.getContents()) assertEquals(item, actual);
      }
      assertTrue(inventory.getViewers().isEmpty());
    }
  }

  @Test
  void invalidGeneratedItemsNeverReplaceTheChest() {
    var roll = ArtifactRoll.ok("fire", "common", List.of());
    ItemStack[] output = {null};
    ArtifactRoll[] generated = {ArtifactRoll.error("generation-failed")};
    try (var rolls =
            mockConstruction(
                ArtifactRoller.class,
                (m, c) -> when(m.roll(anyString(), anyString())).thenAnswer(i -> generated[0]));
        var builders =
            mockConstruction(
                ArtifactItemBuilder.class,
                (m, c) ->
                    when(m.build(any(), isNull(), isNull(), eq(false)))
                        .thenAnswer(i -> output[0]))) {
      assertEquals(0, ArtifactFillChestService.fill(block, new Pending(Mode.ALL, null, 0)));
      assertEquals(0, ArtifactFillChestService.fill(block, new Pending(Mode.TYPE, null, 0)));
      generated[0] = roll;
      assertEquals(0, ArtifactFillChestService.fill(block, new Pending(Mode.RANDOM, null, 0)));
      output[0] = new ItemStack(Material.AIR);
      assertEquals(0, ArtifactFillChestService.fill(block, new Pending(Mode.TYPE, "fire", 0)));
    }
  }
}
