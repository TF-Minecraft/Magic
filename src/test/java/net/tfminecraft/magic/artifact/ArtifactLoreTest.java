package net.tfminecraft.magic.artifact;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.*;
import net.tfminecraft.magic.*;
import net.tfminecraft.magic.artifact.config.*;
import net.tfminecraft.magic.artifact.sacrifice.SacrificeRegistry;
import net.tfminecraft.magic.artifact.shrine.ShrineRegistry;
import net.tfminecraft.magic.charge.ChargeRegistry;
import net.tfminecraft.magic.model.ElementDef;
import net.tfminecraft.magic.registry.ElementRegistry;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.junit.jupiter.api.*;
import org.mockbukkit.mockbukkit.MockBukkit;

class ArtifactLoreTest {
  @BeforeEach
  void setup() {
    MockBukkit.mock();
    Magic.plugin = mock(Magic.class);
    when(Magic.plugin.namespace()).thenReturn("magic");
    ElementRegistry.clear();
    ArtifactTypeRegistry.clear();
    ArtifactRarityRegistry.clear();
    ShrineRegistry.clear();
    SacrificeRegistry.clear();
    ChargeRegistry.clear();
    element("fire", "Fire", -.5);
    element("water", "Water", 0);
    Cache.artifactGlint = true;
  }

  @AfterEach
  void cleanup() {
    Magic.plugin = null;
    MockBukkit.unmock();
    ElementRegistry.clear();
    ArtifactRarityRegistry.clear();
    ChargeRegistry.clear();
  }

  void element(String id, String name, double decay) {
    var cfg = new YamlConfiguration();
    cfg.set("name", name);
    cfg.set("aura_decay_per_hour", decay);
    ElementRegistry.register(new ElementDef(id, cfg));
  }

  ItemStack item(List<String> lines) {
    var item = new ItemStack(Material.STONE);
    var meta = item.getItemMeta();
    meta.setLore(lines);
    item.setItemMeta(meta);
    return item;
  }

  ItemStack artifact() {
    var item = item(List.of("Original lore"));
    var a = Artifact.create();
    a.setCap("fire", 10);
    a.setFill("fire", 5);
    a.setCap("water", 20);
    a.setFill("water", 8.25);
    a.persistPdc(item);
    return item;
  }

  List<String> plain(ItemStack item) {
    return item.getItemMeta().getLore().stream().map(ChatColor::stripColor).toList();
  }

  @Test
  void applyBuildsAuraAndKeepsOnlyOriginalPrefixOnRefresh() {
    var item = artifact();
    ArtifactLore.apply(item);
    assertEquals(List.of("Original lore", "Aura", "Water 8.25 / 20", "Fire 5 / 10"), plain(item));
    assertTrue(item.getItemMeta().getEnchantmentGlintOverride());
    ArtifactLore.apply(item);
    assertEquals(4, plain(item).size());
    var meta = item.getItemMeta();
    meta.getPersistentDataContainer()
        .set(ArtifactKeys.artifactPrimary(), PersistentDataType.STRING, "fire");
    meta.getPersistentDataContainer()
        .set(ArtifactKeys.artifactRarity(), PersistentDataType.STRING, "unlisted");
    item.setItemMeta(meta);
    Cache.artifactGlint = false;
    assertSame(item, ArtifactLore.updateItem(item));
    assertEquals("unlisted", plain(item).get(1));
    assertEquals("Fire 5 / 10", plain(item).get(3));
    assertFalse(item.getItemMeta().getEnchantmentGlintOverride());
    ArtifactLore.apply(null);
    ArtifactLore.apply(new ItemStack(Material.STONE));
  }

  @Test
  void legacyLoreRestoresAuraAndMuffleWithoutTreatingOtherStatsAsAura() {
    assertNull(ArtifactLore.readAura(null));
    assertNull(ArtifactLore.readAura(new ItemStack(Material.AIR)));
    assertNull(ArtifactLore.readAura(new ItemStack(Material.STONE)));
    assertNull(ArtifactLore.readAura(item(List.of("Other line", "Fire resistance 5 / 10"))));
    var legacy =
        item(
            List.of(
                "Muffled: 50%",
                "Fire 2.5 / 10",
                "Water 1 / 5",
                "Fire resistance 3 / 10",
                "Other 10 / 20",
                "Fire 3 / bad",
                "Fire 3 / 0"));
    var recovered = ArtifactLore.readAura(legacy);
    assertNotNull(recovered);
    assertEquals(5, recovered.getFill("fire"));
    assertEquals(2, recovered.getFill("water"));
    assertEquals(10, recovered.getCap("fire"));
    var loaded = Artifact.fromItem(legacy);
    assertNotNull(loaded);
    assertTrue(legacy.getItemMeta().getPersistentDataContainer().has(ArtifactKeys.auraData()));
    for (String muffle : List.of("Muffled: bad%", "Muffled: 100%", "Muffled: 0%")) {
      assertEquals(
          2.5, ArtifactLore.readAura(item(List.of(muffle, "Fire 2.5 / 10"))).getFill("fire"));
    }
    assertNull(
        ArtifactLore.readAura(
            item(List.of("Fire / 10", "Fire .5 / 10", "Fire 5 5 / 10", "Fire notnumber / 10"))));
    element("blank", "", 0);
    assertEquals(2, ArtifactLore.readAura(item(List.of("blank 2 / 10"))).getFill("blank"));
  }

  @Test
  void legacyMarkersAndRarityLinesDoNotAccumulate() {
    ArtifactRarityRegistry.register(
        new ArtifactRarityDef("rare", "Rare name", "#ff0000", 1, 1, 1, new CapRange(1, 10)));
    for (List<String> old :
        List.of(
            List.of("Prefix", "magic.aura.begin", "old"),
            List.of("Prefix", "magic.aura.end"),
            List.of("Prefix", "\u2063old"),
            List.of("Prefix", "\u200bold"),
            List.of("Prefix", "Rare name", "Aura", "Fire 2 / 10"),
            List.of("Prefix", "rare", "Fire 2 / 10"),
            List.of("Prefix", "Aura", "Fire 2 / 10"),
            List.of("Prefix", "Fire 2 / 10"))) {
      var item = artifact();
      var meta = item.getItemMeta();
      meta.setLore(old);
      meta.getPersistentDataContainer()
          .set(ArtifactKeys.artifactRarity(), PersistentDataType.STRING, "rare");
      item.setItemMeta(meta);
      ArtifactLore.apply(item);
      assertEquals("Prefix", plain(item).getFirst());
      assertEquals(5, plain(item).size());
      assertEquals("Rare name", plain(item).get(1));
    }
  }

  @Test
  void refreshAttunementRebuildsMissingIndexesThenOnlyReplacesMuffleLine() {
    var item = artifact();
    ArtifactLore.refreshAttune(item);
    var meta = item.getItemMeta();
    meta.getPersistentDataContainer()
        .set(ArtifactKeys.artifactId(), PersistentDataType.STRING, UUID.randomUUID().toString());
    item.setItemMeta(meta);
    ArtifactLore.refreshAttune(item);
    assertEquals(4, plain(item).size());
    ArtifactLore.refreshAttune(item);
    meta = item.getItemMeta();
    meta.getPersistentDataContainer()
        .set(ArtifactKeys.careMuffle(), PersistentDataType.DOUBLE, .25);
    item.setItemMeta(meta);
    ArtifactLore.refreshAttune(item);
    assertEquals("Muffled: 25%", plain(item).getLast());
    ArtifactLore.refreshAttune(item);
    assertEquals(5, plain(item).size());
    meta = item.getItemMeta();
    meta.getPersistentDataContainer().set(ArtifactKeys.careMuffle(), PersistentDataType.DOUBLE, 0.);
    item.setItemMeta(meta);
    ArtifactLore.refreshAttune(item);
    assertEquals(4, plain(item).size());
    for (int start : new int[] {-1, 999}) {
      meta = item.getItemMeta();
      meta.getPersistentDataContainer()
          .set(ArtifactKeys.attuneStart(), PersistentDataType.INTEGER, start);
      item.setItemMeta(meta);
      ArtifactLore.refreshAttune(item);
      assertEquals(4, plain(item).size());
    }
    meta = item.getItemMeta();
    meta.setLore(null);
    item.setItemMeta(meta);
    ArtifactLore.refreshAttune(item);
    assertEquals(3, plain(item).size());
  }

  @Test
  void careVisibilityTracksRenderedChangesRatherThanTinyNumericDrift() {
    var item = artifact();
    assertFalse(ArtifactLore.careVisibleWouldChange(item, 0, 0));
    assertFalse(ArtifactLore.careVisibleWouldChange(new ItemStack(Material.STONE), 0, 1));
    assertTrue(ArtifactLore.careVisibleWouldChange(item, .1, 0));
    assertTrue(ArtifactLore.careVisibleWouldChange(item, 0, 1));
    assertFalse(ArtifactLore.careVisibleWouldChange(item, 0, .0001));
    var a = Artifact.fromItem(item);
    a.setFill("fire", 0);
    a.persistPdc(item);
    assertFalse(ArtifactLore.careVisibleWouldChange(item, 0, 100));
    a.setFill("fire", 1);
    a.persistPdc(item);
    assertTrue(ArtifactLore.careVisibleWouldChange(item, 0, 100));
  }

  @Test
  void malformedNegativeAttunementCountRebuildsLore() {
    var item = artifact();
    ArtifactIds.writeNew(item);
    ArtifactLore.apply(item);
    var meta = item.getItemMeta();
    meta.getPersistentDataContainer()
        .set(ArtifactKeys.attuneCount(), PersistentDataType.INTEGER, -1);
    item.setItemMeta(meta);
    assertDoesNotThrow(() -> ArtifactLore.refreshAttune(item));
    assertEquals(4, plain(item).size());
    assertEquals(
        0,
        item.getItemMeta()
            .getPersistentDataContainer()
            .get(ArtifactKeys.attuneCount(), PersistentDataType.INTEGER));
  }
}
