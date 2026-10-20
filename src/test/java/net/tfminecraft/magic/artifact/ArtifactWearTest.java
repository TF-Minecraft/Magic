package net.tfminecraft.magic.artifact;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.*;
import net.tfminecraft.magic.*;
import net.tfminecraft.magic.artifact.config.*;
import net.tfminecraft.magic.model.ElementDef;
import net.tfminecraft.magic.registry.ElementRegistry;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.junit.jupiter.api.*;
import org.mockbukkit.mockbukkit.MockBukkit;

class ArtifactWearTest {
  @BeforeEach
  void setup() {
    MockBukkit.mock();
    Magic.plugin = mock(Magic.class);
    when(Magic.plugin.namespace()).thenReturn("magic");
    when(Magic.plugin.getResource("messages.yml"))
        .thenReturn(getClass().getResourceAsStream("/messages.yml"));
    Messages.loadFromResources();
    ElementRegistry.clear();
    ArtifactRarityRegistry.clear();
    ArtifactRarityRegistry.register(
        new ArtifactRarityDef("rare", "Rare", null, 1, 1, 2, new CapRange(46, 75)));
    var cfg = new YamlConfiguration();
    cfg.set("name", "Fire");
    ElementRegistry.register(new ElementDef("fire", cfg));
    var water = new YamlConfiguration();
    water.set("name", "Water");
    ElementRegistry.register(new ElementDef("water", water));
    Cache.artifactWearMultiplier = 2.0;
    Cache.artifactGlint = true;
  }

  @AfterEach
  void cleanup() {
    Cache.artifactWearMultiplier = Cache.DEFAULT_ARTIFACT_WEAR_MULTIPLIER;
    Magic.plugin = null;
    MockBukkit.unmock();
    ElementRegistry.clear();
    ArtifactRarityRegistry.clear();
  }

  ItemStack artifact() {
    var item = new ItemStack(Material.STONE);
    var meta = item.getItemMeta();
    meta.setDisplayName("§6Ember Ring");
    meta.setLore(List.of("Original lore"));
    item.setItemMeta(meta);
    var a = Artifact.create();
    a.setCap("fire", 10);
    a.setFill("fire", 10);
    a.setCap("water", 4);
    a.setFill("water", 4);
    a.persistPdc(item);
    ArtifactIds.writeNew(item);
    var withRarity = item.getItemMeta();
    withRarity.getPersistentDataContainer()
        .set(ArtifactKeys.artifactRarity(), PersistentDataType.STRING, "rare");
    item.setItemMeta(withRarity);
    ArtifactCareStore.stampUser(item, "someone", 1000);
    ArtifactCareStore.setMuffle(item, .25, 1000);
    ArtifactLore.apply(item);
    return item;
  }

  List<String> plainLore(ItemStack item) {
    return item.getItemMeta().getLore().stream().map(ChatColor::stripColor).toList();
  }

  void setWear(ItemStack item, double value) {
    var meta = item.getItemMeta();
    meta.getPersistentDataContainer()
        .set(ArtifactKeys.artifactWear(), PersistentDataType.DOUBLE, value);
    item.setItemMeta(meta);
  }

  @Test
  void budgetIsTheMultipleOfTheHighestCapAndWearOffIgnoresEverything() {
    var item = artifact();
    var artifact = Artifact.fromItem(item);
    assertTrue(ArtifactWear.enabled());
    assertEquals(20, ArtifactWear.budget(artifact));
    assertEquals(0, ArtifactWear.budget(null));
    assertEquals(1, ArtifactWear.integrity(item, artifact));
    assertEquals(0, ArtifactWear.budget(Artifact.create()));
    assertEquals(1, ArtifactWear.integrity(item, Artifact.create()));

    assertEquals(20, ArtifactWear.remaining(item));
    setWear(item, 15);
    assertEquals(5, ArtifactWear.remaining(item));
    setWear(item, 25);
    assertEquals(0, ArtifactWear.remaining(item));
    setWear(item, 0);
    assertEquals(Double.POSITIVE_INFINITY, ArtifactWear.remaining(new ItemStack(Material.STONE)));
    Cache.artifactWearMultiplier = 0;
    assertEquals(Double.POSITIVE_INFINITY, ArtifactWear.remaining(item));
    assertFalse(ArtifactWear.enabled());
    assertEquals(0, ArtifactWear.budget(artifact));
    assertEquals(1, ArtifactWear.integrity(item, artifact));
    assertEquals(ArtifactWear.Result.NONE, ArtifactWear.wear(item, 5));
    assertEquals(0, ArtifactWear.readWear(item));
  }

  @Test
  void readWearIgnoresMissingAndBrokenValues() {
    assertEquals(0, ArtifactWear.readWear(null));
    assertEquals(0, ArtifactWear.readWear(new ItemStack(Material.AIR)));
    assertEquals(0, ArtifactWear.readWear(new ItemStack(Material.STONE)));
    var item = artifact();
    assertEquals(0, ArtifactWear.readWear(item));
    setWear(item, -3);
    assertEquals(0, ArtifactWear.readWear(item));
    setWear(item, Double.NaN);
    assertEquals(0, ArtifactWear.readWear(item));
    setWear(item, 30);
    assertEquals(30, ArtifactWear.readWear(item));
    assertEquals(0, ArtifactWear.integrity(item, Artifact.fromItem(item)));
  }

  @Test
  void wearAddsUpAndShowsIntegrityOnceWorn() {
    var item = artifact();
    assertTrue(plainLore(item).stream().noneMatch(l -> l.startsWith("Integrity")));
    assertEquals(ArtifactWear.Result.NONE, ArtifactWear.wear(item, 0));
    assertEquals(ArtifactWear.Result.NONE, ArtifactWear.wear(new ItemStack(Material.STONE), 1));
    assertEquals(ArtifactWear.Result.WORN, ArtifactWear.wear(item, 5));
    assertEquals(ArtifactWear.Result.WORN, ArtifactWear.wear(item, 0.1));
    assertEquals(5.1, ArtifactWear.readWear(item), 1e-9);
    assertEquals(.745, ArtifactWear.integrity(item, Artifact.fromItem(item)), 1e-9);
    assertTrue(plainLore(item).contains("Integrity: 75%"));
    assertTrue(plainLore(item).contains("Muffled: 25%"));
    Cache.artifactWearMultiplier = 0;
    ArtifactLore.apply(item);
    assertTrue(plainLore(item).stream().noneMatch(l -> l.startsWith("Integrity")));
    Cache.artifactWearMultiplier = 2;
    assertEquals("Integrity: 1%", ChatColor.stripColor(ArtifactLore.integrityLine(.001)));
    assertEquals("Integrity: 0%", ChatColor.stripColor(ArtifactLore.integrityLine(-1)));
    assertEquals("Integrity: 100%", ChatColor.stripColor(ArtifactLore.integrityLine(2)));
  }

  @Test
  void reachingTheBudgetCrumblesIntoARecyclableHusk() {
    var item = artifact();
    var id = ArtifactIds.read(item);
    setWear(item, 19.99995);
    assertEquals(ArtifactWear.Result.CRUMBLED, ArtifactWear.wear(item, 0.00001));
    assertTrue(ArtifactWear.isHusk(item));
    assertNull(Artifact.fromItem(item));
    var pdc = item.getItemMeta().getPersistentDataContainer();
    assertEquals(id.toString(), pdc.get(ArtifactKeys.artifactSpent(), PersistentDataType.STRING));
    assertEquals("rare", pdc.get(ArtifactKeys.artifactRarity(), PersistentDataType.STRING));
    for (var key :
        List.of(
            ArtifactKeys.auraCap(),
            ArtifactKeys.auraFill(),
            ArtifactKeys.auraData(),
            ArtifactKeys.careUsers(),
            ArtifactKeys.careMuffle(),
            ArtifactKeys.careLastTick(),
            ArtifactKeys.attuneStart(),
            ArtifactKeys.attuneCount(),
            ArtifactKeys.artifactId(),
            ArtifactKeys.artifactWear())) assertFalse(pdc.has(key), key.toString());
    var lore = plainLore(item);
    assertEquals("Original lore", lore.getFirst());
    assertEquals("Rare", lore.get(1));
    assertEquals("Its aura is spent. Only a recycler has a use for it now.", lore.get(2));
    assertEquals(3, lore.size());
    assertEquals("Spent Ember Ring", ChatColor.stripColor(item.getItemMeta().getDisplayName()));
    assertEquals(Boolean.FALSE, item.getItemMeta().getEnchantmentGlintOverride());
    assertEquals(ArtifactWear.Result.NONE, ArtifactWear.wear(item, 1));
  }

  @Test
  void crumbleWithoutIdNameLoreOrRarityStillMarksTheHusk() {
    ArtifactWear.crumble(null);
    ArtifactWear.crumble(new ItemStack(Material.AIR));
    var bare = new ItemStack(Material.STONE);
    var a = Artifact.create();
    a.setCap("fire", 3);
    a.persistPdc(bare);
    ArtifactWear.crumble(bare);
    assertEquals(
        "-",
        bare.getItemMeta().getPersistentDataContainer()
            .get(ArtifactKeys.artifactSpent(), PersistentDataType.STRING));
    assertEquals(
        List.of("Its aura is spent. Only a recycler has a use for it now."), plainLore(bare));
    assertFalse(bare.getItemMeta().hasDisplayName());

    var blankId = new ItemStack(Material.STONE);
    a.persistPdc(blankId);
    var meta = blankId.getItemMeta();
    meta.getPersistentDataContainer()
        .set(ArtifactKeys.artifactId(), PersistentDataType.STRING, " ");
    blankId.setItemMeta(meta);
    ArtifactWear.crumble(blankId);
    assertEquals(
        "-",
        blankId.getItemMeta().getPersistentDataContainer()
            .get(ArtifactKeys.artifactSpent(), PersistentDataType.STRING));
  }

  @Test
  void huskCheckIgnoresEmptyStacksAndHuskLoreIsNeverReadBack() {
    assertFalse(ArtifactWear.isHusk(null));
    assertFalse(ArtifactWear.isHusk(new ItemStack(Material.AIR)));
    assertFalse(ArtifactWear.isHusk(new ItemStack(Material.STONE)));
    var item = new ItemStack(Material.STONE);
    var meta = item.getItemMeta();
    meta.setLore(List.of("Fire 5 / 10"));
    meta.getPersistentDataContainer()
        .set(ArtifactKeys.artifactSpent(), PersistentDataType.STRING, "-");
    item.setItemMeta(meta);
    assertTrue(ArtifactWear.isHusk(item));
    assertNull(Artifact.fromItem(item));
  }
}
