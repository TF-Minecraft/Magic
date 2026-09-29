package net.tfminecraft.magic;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.*;
import net.tfminecraft.magic.artifact.*;
import net.tfminecraft.magic.artifact.aura.*;
import net.tfminecraft.magic.artifact.config.*;
import net.tfminecraft.magic.artifact.sacrifice.SacrificeRegistry;
import net.tfminecraft.magic.artifact.shrine.*;
import net.tfminecraft.magic.charge.*;
import net.tfminecraft.tlibs.TLibs;
import net.tfminecraft.tlibs.objects.api.ItemAPI;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.junit.jupiter.api.*;
import org.mockbukkit.mockbukkit.MockBukkit;

class ChargeTest {
  @BeforeEach
  void setup() {
    MockBukkit.mock();
    Magic.plugin = mock(Magic.class);
    when(Magic.plugin.namespace()).thenReturn("magic");
    ChargeRegistry.clear();
    TierBands.clear();
    ArtifactTypeRegistry.clear();
    ShrineRegistry.clear();
    SacrificeRegistry.clear();
  }

  @AfterEach
  void cleanup() {
    Magic.plugin = null;
    MockBukkit.unmock();
    ChargeRegistry.clear();
    TierBands.clear();
    ArtifactTypeRegistry.clear();
    ShrineRegistry.clear();
    SacrificeRegistry.clear();
  }

  ItemStack stamped(int tier) {
    var item = new ItemStack(Material.STONE);
    var meta = item.getItemMeta();
    meta.getPersistentDataContainer()
        .set(ChargeKeys.chargeTier(), PersistentDataType.INTEGER, tier);
    item.setItemMeta(meta);
    return item;
  }

  @Test
  void registryRejectsInvalidTiersAndMatchesThroughRealMaterialFilter() {
    ChargeRegistry.register(null);
    ChargeRegistry.register(new ChargeDef(0, "stone", 10));
    ChargeRegistry.register(new ChargeDef(1, null, -1));
    assertTrue(ChargeRegistry.isEmpty());
    assertEquals("", new ChargeDef(1, null, -1).getItemPath());
    assertEquals(0, new ChargeDef(1, "stone", -1).getAuraCap());
    assertNull(ChargeRegistry.match(null));
    assertNull(ChargeRegistry.match(new ItemStack(Material.AIR)));
    assertNull(ChargeRegistry.match(new ItemStack(Material.STONE)));
    var def = new ChargeDef(1, " v.STONE ", 10);
    ChargeRegistry.register(def);
    assertEquals(1, ChargeRegistry.size());
    assertSame(def, ChargeRegistry.getByTier(1));
    assertEquals(List.of(def), new ArrayList<>(ChargeRegistry.getAll()));
    try (var libs = mockStatic(TLibs.class)) {
      ItemAPI api = mock(ItemAPI.class, RETURNS_DEEP_STUBS);
      libs.when(TLibs::getItemAPI).thenReturn(api);
      when(api.getCreator().getItemFromPath("v.STONE")).thenReturn(new ItemStack(Material.STONE));
      var stone = new ItemStack(Material.STONE);
      when(api.getChecker().checkItemWithPath(stone, "v.STONE")).thenReturn(true);
      assertNull(ChargeRegistry.match(new ItemStack(Material.APPLE)));
      assertSame(def, ChargeRegistry.match(stone));
      verify(api.getCreator(), times(1)).getItemFromPath("v.STONE");
      when(api.getChecker().checkItemWithPath(stone, "v.STONE")).thenReturn(false);
      assertNull(ChargeRegistry.match(stone));
      when(api.getChecker().checkItemWithPath(stone, "v.STONE"))
          .thenThrow(new IllegalArgumentException("bad path"));
      assertNull(ChargeRegistry.match(stone));
      ChargeRegistry.clear();
      ChargeRegistry.register(def);
      when(api.getCreator().getItemFromPath("v.STONE"))
          .thenThrow(new IllegalArgumentException("bad path"));
      assertNull(ChargeRegistry.match(stone));
    }
  }

  @Test
  void bandsUseElementSpecificThresholdsFallbackAndRomanNumerals() {
    assertTrue(TierBands.isEmpty());
    assertEquals(0, TierBands.bandOf("fire", 100));
    TierBands.register("fire", 0, 1);
    assertTrue(TierBands.isEmpty());
    TierBands.register(null, 1, 10);
    TierBands.register(" ", 2, 20);
    TierBands.register(" FIRE ", 1, 5);
    TierBands.register("fire", 3, 30);
    assertEquals(2, TierBands.size());
    assertEquals(0, TierBands.bandOf("fire", 4));
    assertEquals(1, TierBands.bandOf("fire", 5));
    assertEquals(3, TierBands.bandOf("fire", 30));
    assertEquals(2, TierBands.bandOf("water", 20));
    assertEquals("III", TierBands.numeralFor("fire", 30));
    assertEquals("", TierBands.numeral(0));
    assertEquals("", TierBands.numeral(9));
    assertEquals("VIII", TierBands.numeral(8));
  }

  @Test
  void chargeImprintPersistsTierPrimaryAndAuraWithoutArtifactIdentity() {
    ChargeRegistry.register(new ChargeDef(2, "v.STONE", 25));
    var item = stamped(2);
    var charge = Charge.fromItem(item);
    assertNotNull(charge);
    assertTrue(charge.isBlank());
    assertEquals(2, charge.getTier());
    assertEquals(25, charge.tierCap());
    assertEquals(VesselKind.CHARGE, charge.kind());
    assertFalse(charge.hasStoredAura());
    assertEquals("", charge.primaryElementId());
    assertFalse(charge.imprintElement(null, "fire"));
    assertFalse(charge.imprintElement(item, null));
    assertFalse(charge.imprintElement(item, " "));
    assertTrue(charge.imprintElement(item, "fire"));
    assertEquals("fire", charge.primaryElementId());
    assertTrue(charge.imprintElement(item, "water"));
    assertEquals("fire", charge.primaryElementId());
    charge.setFill("fire", 8);
    assertEquals(8, charge.getFill("fire"));
    assertEquals(8, charge.totalFill());
    assertTrue(charge.hasStoredAura());
    assertEquals(25, charge.getCap("fire"));
    assertEquals(Set.of("fire", "water"), charge.getCappedElementIds());
    charge.persistPdc(item);
    assertEquals(8, Charge.fromItem(item).getFill("fire"));
    assertEquals("fire", Charge.fromItem(item).primaryElementId());
    assertFalse(item.getItemMeta().getPersistentDataContainer().has(ArtifactKeys.artifactId()));
    assertInstanceOf(Charge.class, AuraVessels.fromItem(item));
    assertNull(AuraVessels.fromItem(null));
    assertNull(AuraVessels.fromItem(new ItemStack(Material.AIR)));
    assertNull(Charge.fromItem(null));
    assertNull(Charge.fromItem(new ItemStack(Material.AIR)));
    assertNull(Charge.fromItem(new ItemStack(Material.STONE)));
    try (var lore = mockStatic(ChargeLore.class)) {
      lore.when(() -> ChargeLore.updateItem(item)).thenReturn(item);
      assertSame(item, charge.write(item));
    }
  }

  @Test
  void shrineImprintSelectsHighestScoreAndSkipsDisabledElements() {
    ChargeRegistry.register(new ChargeDef(1, "v.STONE", 10));
    ArtifactTypeRegistry.register(ArtifactGenerationTest.type("disabled", false, 1));
    var item = stamped(1);
    var charge = Charge.fromItem(item);
    assertFalse(charge.imprint(null, new ShrineScore(null)));
    assertFalse(charge.imprint(item, null));
    assertFalse(charge.imprint(item, new ShrineScore(null)));
    assertFalse(charge.imprintElement(item, "disabled"));
    var scores = new LinkedHashMap<String, ShrineElementScore>();
    scores.put("missing", null);
    scores.put("zero", new ShrineElementScore("zero", 0, 0, null, null));
    scores.put("disabled", new ShrineElementScore("disabled", 50, 1, null, null));
    scores.put("fire", new ShrineElementScore("fire", 10, 1, List.of("family"), List.of()));
    scores.put("water", new ShrineElementScore("water", 20, 2, null, null));
    scores.put("earth", new ShrineElementScore("earth", 5, 1, null, null));
    var score = new ShrineScore(scores);
    assertEquals("fire", score.get("fire").getElementId());
    assertEquals(1, score.get("fire").getAuraPerSecond());
    assertEquals(List.of("family"), score.get("fire").getActiveFamilyIds());
    assertEquals(List.of(), score.get("fire").getContributingBlocks());
    assertTrue(charge.imprint(item, score));
    assertEquals("water", charge.primaryElementId());
    assertEquals(Set.of("fire", "water", "earth"), charge.getCappedElementIds());
    ChargeRegistry.clear();
    assertEquals(0, charge.tierCap());
    assertFalse(charge.imprint(item, score));
    assertFalse(charge.imprintElement(item, "fire"));
  }
}
