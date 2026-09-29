package net.tfminecraft.magic;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.*;
import net.tfminecraft.magic.artifact.ArtifactKeys;
import net.tfminecraft.magic.artifact.aura.AuraData;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.junit.jupiter.api.*;
import org.mockbukkit.mockbukkit.MockBukkit;

class AuraDataTest {
  @BeforeEach
  void setup() {
    MockBukkit.mock();
    Magic.plugin = mock(Magic.class);
    when(Magic.plugin.getName()).thenReturn("Magic");
    when(Magic.plugin.namespace()).thenReturn("magic");
  }

  @AfterEach
  void cleanup() {
    Magic.plugin = null;
    MockBukkit.unmock();
  }

  @Test
  void removingAllAuraDoesNotResurrectBackupOnReload() {
    var item = new ItemStack(Material.STONE);
    var data = new AuraData();
    data.setCap("fire", 10);
    data.setFill("fire", 8);
    data.persistPdc(item);
    data.setCap("fire", 0);
    data.persistPdc(item);
    assertTrue(AuraData.fromPersistentData(item).isEmpty());
    assertFalse(
        item.getItemMeta()
            .getPersistentDataContainer()
            .has(ArtifactKeys.auraData(), PersistentDataType.STRING));
  }

  @Test
  void capsNormalizeClampAndRoundTripAcrossItemRebuild() {
    var item = new ItemStack(Material.STONE);
    var data = new AuraData();
    assertTrue(data.isEmpty());
    assertFalse(data.hasStoredAura());
    assertEquals("", data.highestCapElementId());
    data.setCap(null, 4);
    data.setCap(" ", 4);
    data.setFill(null, 4);
    data.setFill("missing", 4);
    assertTrue(data.isEmpty());
    data.setCap(" FIRE ", 10);
    data.setCap("water", 5);
    data.setFill("fire", 15);
    data.setFill("water", -1);
    assertEquals(10, data.getFill("fire"));
    assertEquals(0, data.getFill("water"));
    assertEquals(10, data.totalFill());
    assertTrue(data.hasStoredAura());
    assertEquals("fire", data.highestCapElementId());
    assertEquals(Set.of("fire", "water"), data.getCappedElementIds());
    assertThrows(UnsupportedOperationException.class, () -> data.getCappedElementIds().clear());
    data.persistPdc(item);
    var restored = AuraData.fromPersistentData(item);
    assertEquals(10, restored.getCap("fire"));
    assertEquals(10, restored.getFill("fire"));
    assertEquals(5, restored.getCap("water"));
    var meta = item.getItemMeta();
    meta.getPersistentDataContainer().remove(ArtifactKeys.auraCap());
    meta.getPersistentDataContainer().remove(ArtifactKeys.auraFill());
    item.setItemMeta(meta);
    restored = AuraData.fromPersistentData(item);
    assertEquals(10, restored.getFill("fire"));
    assertEquals(5, restored.getCap("water"));
    data.setCap("fire", 2);
    assertEquals(2, data.getFill("fire"));
    data.setCap("water", -1);
    assertEquals(Set.of("fire"), data.getCappedElementIds());
  }

  @Test
  void legacyFillOnlyItemsRecoverTheirCap() {
    var item = new ItemStack(Material.STONE);
    var meta = item.getItemMeta();
    var root = meta.getPersistentDataContainer();
    var fill = root.getAdapterContext().newPersistentDataContainer();
    fill.set(ArtifactKeys.element("fire"), PersistentDataType.DOUBLE, 3.);
    fill.set(ArtifactKeys.element("water"), PersistentDataType.DOUBLE, 0.);
    fill.set(ArtifactKeys.element("wrong"), PersistentDataType.STRING, "wrong-type");
    root.set(ArtifactKeys.auraFill(), PersistentDataType.TAG_CONTAINER, fill);
    item.setItemMeta(meta);
    var data = AuraData.fromPersistentData(item);
    assertEquals(3, data.getCap("fire"));
    assertEquals(3, data.totalFill());
    assertEquals(0, data.getCap("water"));
  }

  @Test
  void malformedBackupTokensAreSkippedAndFillIsBounded() {
    var item = new ItemStack(Material.STONE);
    var meta = item.getItemMeta();
    meta.getPersistentDataContainer()
        .set(
            ArtifactKeys.auraData(),
            PersistentDataType.STRING,
            "bad,:2:1,bad:number:1,fire:5:9,water:3,earth:-1:2,fire:8:1");
    item.setItemMeta(meta);
    var data = AuraData.fromPersistentData(item);
    assertEquals(5, data.getCap("fire"));
    assertEquals(5, data.getFill("fire"));
    assertEquals(3, data.getCap("water"));
    assertEquals(0, data.getFill("water"));
    assertTrue(AuraData.fromPersistentData(null).isEmpty());
    assertTrue(AuraData.fromPersistentData(new ItemStack(Material.AIR)).isEmpty());
    data.persistPdc(null);
    data.persistPdc(new ItemStack(Material.AIR));
    data.setCap("invalid key!", 3);
    assertDoesNotThrow(() -> data.persistPdc(item));
    assertEquals(0, AuraData.clampFill(-1, 10));
    assertEquals(0, AuraData.clampFill(1, -1));
  }

  @Test
  void backupRecoversZeroedFillAndInvalidNestedCapsAreIgnored() {
    var item = new ItemStack(Material.STONE);
    var meta = item.getItemMeta();
    var root = meta.getPersistentDataContainer();
    var caps = root.getAdapterContext().newPersistentDataContainer();
    var fills = root.getAdapterContext().newPersistentDataContainer();
    caps.set(ArtifactKeys.element("fire"), PersistentDataType.DOUBLE, 10d);
    caps.set(ArtifactKeys.element("zero"), PersistentDataType.DOUBLE, 0d);
    caps.set(ArtifactKeys.element("bad"), PersistentDataType.STRING, "wrong");
    fills.set(ArtifactKeys.element("fire"), PersistentDataType.DOUBLE, 0d);
    root.set(ArtifactKeys.auraCap(), PersistentDataType.TAG_CONTAINER, caps);
    root.set(ArtifactKeys.auraFill(), PersistentDataType.TAG_CONTAINER, fills);
    root.set(ArtifactKeys.auraData(), PersistentDataType.STRING, "fire:10:4");
    item.setItemMeta(meta);
    var recovered = AuraData.fromPersistentData(item);
    assertEquals(Set.of("fire"), recovered.getCappedElementIds());
    assertEquals(4, recovered.getFill("fire"));
    root.set(ArtifactKeys.auraData(), PersistentDataType.STRING, " ");
    item.setItemMeta(meta);
    assertEquals(0, AuraData.fromPersistentData(item).getFill("fire"));
  }

  @Test
  void nonFiniteAuraNeverPoisonsLiveOrPersistedValues() {
    for (double invalid :
        new double[] {Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY}) {
      var data = new AuraData();
      data.setCap("fire", 10);
      data.setFill("fire", 4);
      data.setCap("fire", invalid);
      data.setFill("fire", invalid);
      assertEquals(10, data.getCap("fire"));
      assertEquals(4, data.getFill("fire"));
      assertEquals(0, AuraData.clampFill(invalid, 10));
      assertEquals(0, AuraData.clampFill(4, invalid));
      var item = new ItemStack(Material.STONE);
      var meta = item.getItemMeta();
      var root = meta.getPersistentDataContainer();
      var caps = root.getAdapterContext().newPersistentDataContainer();
      var fills = root.getAdapterContext().newPersistentDataContainer();
      caps.set(ArtifactKeys.element("badcap"), PersistentDataType.DOUBLE, invalid);
      caps.set(ArtifactKeys.element("fire"), PersistentDataType.DOUBLE, 10d);
      fills.set(ArtifactKeys.element("fire"), PersistentDataType.DOUBLE, invalid);
      root.set(ArtifactKeys.auraCap(), PersistentDataType.TAG_CONTAINER, caps);
      root.set(ArtifactKeys.auraFill(), PersistentDataType.TAG_CONTAINER, fills);
      root.set(
          ArtifactKeys.auraData(),
          PersistentDataType.STRING,
          "badblob:" + invalid + ":2,water:10:" + invalid);
      item.setItemMeta(meta);
      var restored = AuraData.fromPersistentData(item);
      assertEquals(Set.of("fire", "water"), restored.getCappedElementIds());
      assertEquals(0, restored.getFill("fire"));
      assertEquals(0, restored.getFill("water"));
      assertTrue(Double.isFinite(restored.totalFill()));
    }
  }
}
