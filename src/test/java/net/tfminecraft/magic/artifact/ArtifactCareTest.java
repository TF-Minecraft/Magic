package net.tfminecraft.magic.artifact;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.*;
import net.tfminecraft.magic.Cache;
import net.tfminecraft.magic.Magic;
import net.tfminecraft.magic.attunement.ArtifactCareCache;
import net.tfminecraft.magic.charge.*;
import net.tfminecraft.magic.model.ElementDef;
import net.tfminecraft.magic.registry.ElementRegistry;
import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.junit.jupiter.api.*;
import org.mockbukkit.mockbukkit.MockBukkit;

class ArtifactCareTest {
  @BeforeEach
  void setup() {
    MockBukkit.mock();
    Magic.plugin = mock(Magic.class);
    when(Magic.plugin.namespace()).thenReturn("magic");
    ChargeRegistry.clear();
    ElementRegistry.clear();
    ArtifactCareCache.muffledEnabled = true;
    ArtifactCareCache.muffledOffPerHour = .1;
    ArtifactCareCache.muffledRecoverPerHour = .2;
    Cache.secondsPerHour = 1;
  }

  @AfterEach
  void cleanup() {
    Magic.plugin = null;
    MockBukkit.unmock();
    ChargeRegistry.clear();
    ElementRegistry.clear();
    ArtifactCareCache.muffledEnabled = false;
    Cache.secondsPerHour = 3600;
  }

  ItemStack artifact() {
    var item = new ItemStack(Material.STONE);
    var a = Artifact.create();
    a.setCap("fire", 10);
    a.setFill("fire", 5);
    a.persistPdc(item);
    return item;
  }

  void users(ItemStack item, String value) {
    var meta = item.getItemMeta();
    meta.getPersistentDataContainer()
        .set(ArtifactKeys.careUsers(), PersistentDataType.STRING, value);
    item.setItemMeta(meta);
  }

  @Test
  void userStampsExpirePruneAndIgnoreMalformedEntries() {
    assertEquals(Map.of(), ArtifactCareStore.readUsers(null));
    ArtifactCareStore.pruneUsers(null, 0);
    assertEquals(0, ArtifactCareStore.readMuffle(null));
    assertEquals(0, ArtifactCareStore.readLastTickMs(null));
    var item = artifact();
    users(
        item,
        "missing;bad:;:1; :3;broken:nope;expired:10;active:100;active2:200; ;id:with:colon:300");
    assertEquals(4, ArtifactCareStore.readUsers(item).size());
    assertEquals(3, ArtifactCareStore.activeUserCount(item, 50));
    ArtifactCareStore.pruneUsers(item, 50);
    assertEquals(3, ArtifactCareStore.readUsers(item).size());
    ArtifactCareStore.pruneUsers(item, 50);
    ArtifactCareStore.pruneUsers(item, 300);
    assertTrue(ArtifactCareStore.readUsers(item).isEmpty());
    assertFalse(item.getItemMeta().getPersistentDataContainer().has(ArtifactKeys.careUsers()));
    ArtifactCareStore.stampUser(item, null, 0);
    ArtifactCareStore.stampUser(item, " ", 0);
    ArtifactCareStore.stampUser(null, "id", 0);
    ArtifactCareStore.stampUser(new ItemStack(Material.AIR), "id", 0);
    ArtifactCareCache.usersTtlDays = 0;
    ArtifactCareStore.stampUser(item, " user ", 10);
    assertEquals(Map.of("user", 86400010L), ArtifactCareStore.readUsers(item));
    ArtifactCareCache.usersTtlDays = 7;
    ArtifactCareStore.stampUser(item, "second", 10);
    assertEquals(2, ArtifactCareStore.readUsers(item).size());
  }

  @Test
  void housedArtifactsRecoverAndUnhousedArtifactsMuffleOnlyAfterFirstTick() {
    var item = artifact();
    try (var lore = mockStatic(ArtifactLore.class)) {
      assertTrue(ArtifactCareStore.tick(item, false, 1000));
      assertEquals(0, ArtifactCareStore.readMuffle(item));
      assertEquals(1000, ArtifactCareStore.readLastTickMs(item));
      assertTrue(ArtifactCareStore.tick(item, false, 2000));
      assertEquals(.1, ArtifactCareStore.readMuffle(item), 1e-10);
      assertTrue(ArtifactCareStore.tick(item, true, 3000));
      assertEquals(0, ArtifactCareStore.readMuffle(item));
      assertFalse(ArtifactCareStore.apply(item, false, 4000, ArtifactCareStore.Persist.IF_VISIBLE));
      assertEquals(3000, ArtifactCareStore.readLastTickMs(item));
      assertTrue(ArtifactCareStore.tick(item, false, 20000));
      assertEquals(1, ArtifactCareStore.readMuffle(item));
      assertTrue(ArtifactCareStore.tick(item, false, 1000));
      assertEquals(1, ArtifactCareStore.readMuffle(item));
      ArtifactCareCache.muffledEnabled = false;
      Cache.secondsPerHour = 0;
      assertTrue(ArtifactCareStore.tick(item, true, 30000));
      assertEquals(1, ArtifactCareStore.readMuffle(item));
    }
    assertEquals(5, ArtifactCareStore.usableMax(10, .5));
    assertEquals(5, ArtifactCareStore.usableFill(8, 10, .5));
    assertEquals(0, ArtifactCareStore.usableFill(-1, 10, -1));
    assertEquals(0, ArtifactCareStore.usableMax(10, 2));
  }

  @Test
  void setMuffleClampsTheValueAndResetsTheCareClock() {
    var item = artifact();
    try (var lore = mockStatic(ArtifactLore.class)) {
      assertTrue(ArtifactCareStore.setMuffle(item, -1, 2000));
      assertEquals(0, ArtifactCareStore.readMuffle(item));
      assertEquals(2000, ArtifactCareStore.readLastTickMs(item));
      lore.verify(() -> ArtifactLore.apply(item));

      assertTrue(ArtifactCareStore.setMuffle(item, 2, 4000));
      assertEquals(1, ArtifactCareStore.readMuffle(item));
      assertEquals(4000, ArtifactCareStore.readLastTickMs(item));
      assertFalse(ArtifactCareStore.setMuffle(new ItemStack(Material.STONE), .5, 5000));
    }
  }

  @Test
  void chargeItemsNeverAcquireArtifactCareState() {
    assertFalse(ArtifactCareStore.tick(null, false, 1000));
    assertFalse(ArtifactCareStore.tick(new ItemStack(Material.AIR), false, 1000));
    var item = artifact();
    var meta = item.getItemMeta();
    meta.getPersistentDataContainer().set(ChargeKeys.chargeTier(), PersistentDataType.INTEGER, 1);
    item.setItemMeta(meta);
    assertTrue(ChargeIds.isCharge(item));
    assertFalse(ArtifactCareStore.tick(item, false, 1000));
    ArtifactCareStore.stampUser(item, "user", 1000);
    assertTrue(ArtifactCareStore.readUsers(item).isEmpty());
    assertNull(Artifact.fromItem(item));
    assertFalse(ChargeIds.isCharge(null));
    assertFalse(ChargeIds.hasKey(null));
    assertFalse(ChargeIds.hasKey(new ItemStack(Material.AIR)));
  }

  @Test
  void visibleCareTickDecaysOnlyConfiguredAuraAndWritesUpdatedLore() {
    var item = artifact();
    var data = Artifact.fromItem(item);
    data.setCap("water", 10);
    data.setFill("water", 0);
    data.setCap("unknown", 10);
    data.setCap("constant", 10);
    data.persistPdc(item);
    var cfg = new YamlConfiguration();
    cfg.set("aura_decay_per_hour", -.5);
    ElementRegistry.register(new ElementDef("fire", cfg));
    ElementRegistry.register(new ElementDef("water", cfg));
    ElementRegistry.register(new ElementDef("constant", new YamlConfiguration()));
    try (var lore = mockStatic(ArtifactLore.class)) {
      lore.when(() -> ArtifactLore.careVisibleWouldChange(any(), anyDouble(), anyDouble()))
          .thenReturn(true);
      ArtifactCareStore.tick(item, true, 1000);
      ArtifactCareStore.tick(item, true, 2000);
      assertEquals(4.5, Artifact.fromItem(item).getFill("fire"));
      lore.verify(() -> ArtifactLore.updateItem(item));
      var depleted = Artifact.fromItem(item);
      depleted.setFill("fire", 0);
      depleted.persistPdc(item);
      ArtifactCareStore.tick(item, true, 3000);
      assertEquals(0, Artifact.fromItem(item).getFill("fire"));
      var ordinary = new ItemStack(Material.STONE);
      ArtifactCareStore.tick(ordinary, true, 1000);
      ArtifactCareStore.tick(ordinary, true, 2000);
    }
  }

  @Test
  void artifactAccessorsRoundTripPrimaryAndAura() {
    assertNull(Artifact.fromItem(null));
    assertNull(Artifact.fromItem(new ItemStack(Material.AIR)));
    var item = artifact();
    var a = Artifact.fromItem(item);
    assertEquals(net.tfminecraft.magic.artifact.aura.VesselKind.ARTIFACT, a.kind());
    assertTrue(a.hasStoredAura());
    assertEquals(5, a.totalFill());
    assertEquals(10, a.getCap("fire"));
    assertEquals(Set.of("fire"), a.getCappedElementIds());
    assertEquals("fire", a.primaryElementId());
    var meta = item.getItemMeta();
    meta.getPersistentDataContainer()
        .set(ArtifactKeys.artifactPrimary(), PersistentDataType.STRING, " WATER ");
    item.setItemMeta(meta);
    assertEquals("water", Artifact.fromItem(item).primaryElementId());
    try (var lore = mockStatic(ArtifactLore.class)) {
      lore.when(() -> ArtifactLore.updateItem(item)).thenReturn(item);
      assertSame(item, a.write(item));
    }
  }

  @Test
  void visibleOnlyCareWritesWhenChangedAndStampsPruneExpiredUsers() {
    var item = artifact();
    users(item, " ");
    assertTrue(ArtifactCareStore.readUsers(item).isEmpty());
    users(item, "old:1;active:5000");
    ArtifactCareStore.stampUser(item, "new", 1000);
    assertFalse(ArtifactCareStore.readUsers(item).containsKey("old"));
    try (var lore = mockStatic(ArtifactLore.class)) {
      lore.when(() -> ArtifactLore.careVisibleWouldChange(eq(item), anyDouble(), anyDouble()))
          .thenReturn(true);
      assertTrue(ArtifactCareStore.apply(item, true, 1000, ArtifactCareStore.Persist.IF_VISIBLE));
    }
  }

  @Test
  void positiveDecayAtCapAndNegligibleNegativeFillAvoidUnnecessaryRewrites() {
    var item = artifact();
    var aura = Artifact.fromItem(item);
    aura.setFill("fire", 10);
    aura.persistPdc(item);
    var config = new YamlConfiguration();
    config.set("aura_decay_per_hour", 1);
    ElementRegistry.register(new ElementDef("fire", config));
    try (var lore = mockStatic(ArtifactLore.class)) {
      lore.when(() -> ArtifactLore.careVisibleWouldChange(eq(item), anyDouble(), anyDouble()))
          .thenReturn(true);
      ArtifactCareStore.tick(item, true, 1000);
      ArtifactCareStore.tick(item, true, 2000);
      assertEquals(10, Artifact.fromItem(item).getFill("fire"));
      config.set("aura_decay_per_hour", -1);
      ElementRegistry.register(new ElementDef("fire", config));
      aura = Artifact.fromItem(item);
      aura.setFill("fire", 0);
      aura.persistPdc(item);
      ArtifactCareStore.tick(item, true, 3000);
      assertEquals(0, Artifact.fromItem(item).getFill("fire"));
    }
  }
}
