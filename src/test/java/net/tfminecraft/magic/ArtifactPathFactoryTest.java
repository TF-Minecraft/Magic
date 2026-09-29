package net.tfminecraft.magic;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.logging.Logger;
import net.tfminecraft.magic.artifact.Artifact;
import net.tfminecraft.magic.artifact.config.*;
import net.tfminecraft.magic.artifact.generate.*;
import net.tfminecraft.magic.artifact.path.ArtifactPathFactory;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.*;
import org.mockbukkit.mockbukkit.MockBukkit;

class ArtifactPathFactoryTest {
  @BeforeEach
  void setup() {
    MockBukkit.mock();
    Magic.plugin = mock(Magic.class);
    when(Magic.plugin.namespace()).thenReturn("magic");
    when(Magic.plugin.getLogger()).thenReturn(Logger.getAnonymousLogger());
    ArtifactTypeRegistry.clear();
    ArtifactRarityRegistry.clear();
  }

  @AfterEach
  void cleanup() {
    Magic.plugin = null;
    MockBukkit.unmock();
    ArtifactTypeRegistry.clear();
    ArtifactRarityRegistry.clear();
  }

  @Test
  void invalidPathsAndUnconfiguredRollsDoNotCreateItems() {
    var factory = ArtifactPathFactory.INSTANCE;
    assertNull(factory.create("invalid"));
    assertNull(factory.create("magic.artifact"));
    Magic.plugin = null;
    assertNull(factory.create("magic.artifact"));
  }

  @Test
  void buildFailuresArePropagatedAndSuccessfulItemsAreSingleStacks() {
    ArtifactTypeRegistry.register(ArtifactGenerationTest.type("fire", true, 1));
    ArtifactRarityRegistry.register(ArtifactGenerationTest.rarity("common", 1, 1, 1));
    assertNull(
        net.tfminecraft.magic.artifact.path.ArtifactPathParser.parse("magic.(fire=)")
            .getExtras()
            .get("fire"));
    for (ItemStack result :
        new ItemStack[] {null, new ItemStack(Material.AIR), new ItemStack(Material.STONE, 8)}) {
      try (var builder =
          mockConstruction(
              ArtifactItemBuilder.class, (m, c) -> when(m.build(any())).thenReturn(result))) {
        var item = ArtifactPathFactory.INSTANCE.create("magic.(primary=fire;rarity=common)");
        if (result == null || result.getType() == Material.AIR) assertNull(item);
        else {
          assertSame(result, item);
          assertEquals(1, item.getAmount());
        }
      }
    }
  }

  @Test
  void matchesRequiresStoredAuraRatherThanAnArbitraryItem() {
    var factory = ArtifactPathFactory.INSTANCE;
    assertFalse(factory.matches(null, "magic.artifact"));
    assertFalse(factory.matches(new ItemStack(Material.AIR), "magic.artifact"));
    var stack = new ItemStack(Material.STONE);
    assertFalse(factory.matches(stack, "magic.artifact"));
    var artifact = Artifact.create();
    artifact.setCap("fire", 10);
    artifact.persistPdc(stack);
    assertFalse(factory.matches(stack, "magic.artifact"));
    artifact.setFill("fire", 5);
    artifact.persistPdc(stack);
    assertTrue(factory.matches(stack, "magic.artifact"));
  }
}
