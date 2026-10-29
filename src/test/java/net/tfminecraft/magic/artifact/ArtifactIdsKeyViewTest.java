package net.tfminecraft.magic.artifact;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import net.tfminecraft.magic.Magic;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.junit.jupiter.api.*;
import org.mockbukkit.mockbukkit.MockBukkit;

/** The scan's artifact check reads the stack's data view and answers as the old full-meta read did. */
class ArtifactIdsKeyViewTest {

  @BeforeEach
  void setup() {
    MockBukkit.mock();
    Magic.plugin = mock(Magic.class);
    when(Magic.plugin.namespace()).thenReturn("magic");
  }

  @AfterEach
  void cleanup() {
    Magic.plugin = null;
    MockBukkit.unmock();
  }

  // origin/main ArtifactIds.hasKey
  private static boolean oldHasKey(ItemStack stack) {
    if (stack == null || stack.getType().isAir() || !stack.hasItemMeta()) {
      return false;
    }
    ItemMeta meta = stack.getItemMeta();
    return meta.getPersistentDataContainer().has(ArtifactKeys.artifactId(), PersistentDataType.STRING);
  }

  private static ItemStack stack(Material material, Consumer<PersistentDataContainer> edit) {
    ItemStack stack = new ItemStack(material);
    ItemMeta meta = stack.getItemMeta();
    edit.accept(meta.getPersistentDataContainer());
    stack.setItemMeta(meta);
    return stack;
  }

  @Test
  void hasKeyMatchesExactlyWhatTheFullMetaReadMatched() {
    Map<String, ItemStack> samples = new LinkedHashMap<>();
    samples.put("null", null);
    samples.put("air", new ItemStack(Material.AIR));
    for (Material material : List.of(Material.STONE, Material.AMETHYST_SHARD, Material.PAPER)) {
      String name = material.name().toLowerCase();
      samples.put(name + " without meta", new ItemStack(material));
      ItemStack unbreakable = new ItemStack(material);
      ItemMeta meta = unbreakable.getItemMeta();
      meta.setUnbreakable(true);
      unbreakable.setItemMeta(meta);
      samples.put(name + " with meta but no data", unbreakable);
      ItemStack written = new ItemStack(material);
      ArtifactIds.writeNew(written);
      samples.put(name + " artifact", written);
      samples.put(name + " blank id", stack(material,
          d -> d.set(ArtifactKeys.artifactId(), PersistentDataType.STRING, " ")));
      samples.put(name + " id stored as a number", stack(material,
          d -> d.set(ArtifactKeys.artifactId(), PersistentDataType.INTEGER, 4)));
      samples.put(name + " other artifact key only", stack(material,
          d -> d.set(ArtifactKeys.artifactSpent(), PersistentDataType.STRING, "old")));
      samples.put(name + " id under another plugin", stack(material,
          d -> d.set(new NamespacedKey("other", "artifact_id"), PersistentDataType.STRING, "x")));
    }

    List<String> matched = new ArrayList<>();
    var handler = new ArtifactAttuneScanHandler();
    samples.forEach((name, stack) -> {
      assertEquals(oldHasKey(stack), ArtifactIds.hasKey(stack), name);
      assertEquals(ArtifactIds.hasKey(stack), handler.matches(stack), name);
      if (ArtifactIds.hasKey(stack)) matched.add(name);
    });
    assertEquals(List.of("stone artifact", "stone blank id", "amethyst_shard artifact",
        "amethyst_shard blank id", "paper artifact", "paper blank id"), matched);
  }
}
