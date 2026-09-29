package net.tfminecraft.magic;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.*;
import net.tfminecraft.magic.artifact.*;
import net.tfminecraft.magic.artifact.aura.*;
import net.tfminecraft.magic.artifact.shrine.*;
import net.tfminecraft.magic.attunement.*;
import net.tfminecraft.magic.charge.*;
import net.tfminecraft.magic.model.ElementVisibility;
import net.tfminecraft.magic.registry.ElementRegistry;
import net.tfminecraft.tlibs.TLibs;
import net.tfminecraft.tlibs.objects.api.ItemAPI;
import org.bukkit.*;
import org.bukkit.entity.ItemFrame;
import org.bukkit.inventory.*;
import org.bukkit.persistence.PersistentDataType;
import org.junit.jupiter.api.*;

class ChargeEdgeCoverageTest extends GearCoverageSupport {
  @BeforeEach
  void registries() {
    ChargeRegistry.clear();
    TierBands.clear();
    ElementRegistry.clear();
    net.tfminecraft.magic.artifact.config.ArtifactTypeRegistry.clear();
    ShrineRegistry.clear();
    net.tfminecraft.magic.artifact.sacrifice.SacrificeRegistry.clear();
  }

  @AfterEach
  void cleanup() {
    registries();
    ArtifactDisplayIndex.rebuildFromSaved();
  }

  ItemStack chargeItem() {
    var s = item();
    tag(s, ChargeKeys.chargeTier(), PersistentDataType.INTEGER, 1);
    return s;
  }

  @Test
  void identifiersRejectEmptyMetadataMalformedIdsAndZeroTiers() {
    var noMeta = mock(ItemStack.class);
    when(noMeta.getType()).thenReturn(Material.STONE);
    assertFalse(ArtifactIds.hasKey(noMeta));
    assertFalse(ArtifactIds.hasKey(new ItemStack(Material.AIR)));
    assertFalse(ChargeIds.isCharge(noMeta));
    assertNull(Charge.fromItem(noMeta));
    assertEquals("", AttunementCaptureService.displayNameOf(noMeta));
    assertNull(ArtifactIds.read(new ItemStack(Material.AIR)));
    ArtifactIds.writeNew(null);
    ArtifactIds.writeNew(new ItemStack(Material.AIR));
    assertFalse(ArtifactIds.hasId(item()));
    var s = item();
    tag(s, ArtifactKeys.artifactId(), PersistentDataType.STRING, " ");
    assertNull(ArtifactIds.read(s));
    tag(s, ArtifactKeys.artifactId(), PersistentDataType.STRING, "invalid");
    assertNull(ArtifactIds.read(s));
    ArtifactIds.writeNew(s);
    assertTrue(ArtifactIds.hasId(s));
    tag(s, ChargeKeys.chargeTier(), PersistentDataType.INTEGER, 0);
    assertFalse(ChargeIds.hasKey(s));
    assertFalse(Artifact.create().isCharge());
    assertFalse(Charge.fromItem(chargeItem()).isArtifact());
    var inventory = mock(Inventory.class);
    when(inventory.getHolder())
        .thenReturn(mock(InventoryHolder.class, withSettings().extraInterfaces(ItemFrame.class)));
    assertTrue(ArtifactCarePlaces.accepted(inventory, s));
    ArtifactDisplayIndex.add(s);
    assertTrue(ArtifactDisplayIndex.isDisplayed(ArtifactIds.read(s).toString()));
  }

  @Test
  void fallbackMatchingSupportsUnstampedChargesAndUnresolvableMaterials() {
    var def = new ChargeDef(1, "v.STONE", 10);
    ChargeRegistry.register(def);
    var s = item();
    try (var libs = mockStatic(TLibs.class)) {
      var api = mock(ItemAPI.class, RETURNS_DEEP_STUBS);
      libs.when(TLibs::getItemAPI).thenReturn(api);
      when(api.getCreator().getItemFromPath("v.STONE")).thenReturn(new ItemStack(Material.AIR));
      when(api.getChecker().checkItemWithPath(s, "v.STONE")).thenReturn(true);
      assertTrue(ChargeIds.isCharge(s));
      var charge = Charge.fromItem(s);
      assertEquals(1, charge.getTier());
      tag(s, ArtifactKeys.artifactPrimary(), PersistentDataType.STRING, " ");
      assertEquals("", Charge.fromItem(s).primaryElementId());
      tag(s, ArtifactKeys.artifactPrimary(), PersistentDataType.STRING, " FIRE ");
      assertEquals("fire", Charge.fromItem(s).primaryElementId());
    }
  }

  @Test
  void registeringAnotherTierInvalidatesMaterialPrefilter() {
    var stone = new ChargeDef(1, "v.STONE", 10);
    var diamond = new ChargeDef(2, "v.DIAMOND", 20);
    ChargeRegistry.register(stone);
    var first = new ItemStack(Material.STONE);
    var second = new ItemStack(Material.DIAMOND);
    try (var libs = mockStatic(TLibs.class)) {
      var api = mock(ItemAPI.class, RETURNS_DEEP_STUBS);
      libs.when(TLibs::getItemAPI).thenReturn(api);
      when(api.getCreator().getItemFromPath("v.STONE")).thenReturn(first);
      when(api.getCreator().getItemFromPath("v.DIAMOND")).thenReturn(second);
      when(api.getChecker().checkItemWithPath(first, "v.STONE")).thenReturn(true);
      when(api.getChecker().checkItemWithPath(second, "v.DIAMOND")).thenReturn(true);
      assertSame(stone, ChargeRegistry.match(first));
      ChargeRegistry.register(diamond);
      assertSame(diamond, ChargeRegistry.match(second));
    }
  }

  @Test
  void publicImprintCallsTolerateAirDestination() {
    ChargeRegistry.register(new ChargeDef(1, "v.STONE", 10));
    var charge = Charge.fromItem(chargeItem());
    assertTrue(charge.imprintElement(new ItemStack(Material.AIR), "fire"));
  }

  @Test
  void loreFiltersHiddenElementsAndSortsLatePrimaryFirst() throws Exception {
    for (String id : List.of("alpha", "beta", "gamma", "hidden"))
      ElementRegistry.register(DomainTest.element(id));
    var stack = chargeItem();
    var charge = Charge.fromItem(stack);
    for (String id : List.of("alpha", "beta", "hidden")) {
      charge.setCap(id, 10);
      charge.setFill(id, 5);
    }
    charge.persistPdc(stack);
    tag(stack, ArtifactKeys.artifactPrimary(), PersistentDataType.STRING, "beta");
    try (var visibility = mockStatic(ElementVisibility.class)) {
      visibility.when(() -> ElementVisibility.shownOnCharge("alpha")).thenReturn(true);
      visibility.when(() -> ElementVisibility.shownOnCharge("beta")).thenReturn(true);
      ChargeLore.apply(stack);
    }
    var lore = stack.getItemMeta().getLore();
    assertTrue(ChatColor.stripColor(lore.get(1)).toLowerCase().contains("beta"));
    assertEquals(3, lore.size());
    assertEquals(
        Arrays.asList(null, ""),
        invoke(
            ChargeLore.class,
            "stripChargeBlock",
            new Class[] {List.class},
            Arrays.asList(null, "")));
    assertEquals(
        List.of("flavor"),
        invoke(
            ChargeLore.class,
            "stripChargeBlock",
            new Class[] {List.class},
            List.of("flavor", "§0§1§3§rCharge", "obsolete")));
  }

  @Test
  void displayReferenceCounterCannotOverflowAndHideVisibleArtifacts() throws Exception {
    var id = UUID.randomUUID();
    var field = ArtifactDisplayIndex.class.getDeclaredField("displayed");
    field.setAccessible(true);
    @SuppressWarnings("unchecked")
    var counts =
        (Map<UUID, Integer>)
            field.get(null); // Fast-forward a reachable positive counter without billions of
    // registrations.
    counts.put(id, Integer.MAX_VALUE);
    ArtifactDisplayIndex.add(id);
    assertTrue(ArtifactDisplayIndex.isDisplayed(id));
  }

  @Test
  void nonfiniteShrineScoreCannotImprintACharge() {
    ChargeRegistry.register(new ChargeDef(1, "v.STONE", 10));
    for (double invalid : new double[] {Double.NaN, Double.POSITIVE_INFINITY}) {
      var s = chargeItem();
      var charge = Charge.fromItem(s);
      assertFalse(
          charge.imprint(
              s,
              new ShrineScore(
                  Map.of("fire", new ShrineElementScore("fire", invalid, 1, null, null)))));
      assertTrue(charge.isBlank());
    }
  }

  @Test
  void emptyInputsAndDefaultVesselHelpersRemainConsistent() throws Exception {
    assertNull(ArtifactIds.read(null));
    assertFalse(ChargeIds.isCharge(null));
    assertFalse(ChargeIds.isCharge(new ItemStack(Material.AIR)));
    assertFalse(ChargeIds.hasKey(null));
    assertFalse(ChargeIds.hasKey(new ItemStack(Material.AIR)));
    assertTrue(Artifact.create().isArtifact());
    assertTrue(Charge.fromItem(chargeItem()).isCharge());
    assertFalse(ArtifactDisplayIndex.isDisplayed((String) null));
    assertFalse(ArtifactDisplayIndex.isDisplayed("invalid"));
    assertFalse(ArtifactDisplayIndex.isDisplayed(UUID.randomUUID().toString()));
    var session = new net.tfminecraft.magic.session.ResonanceSession();
    AttunementCaptureService.credit(
        server.addPlayer(), session, UUID.randomUUID().toString(), "unknown", 1);
    assertEquals(0, session.getResonance("unknown"));
    assertEquals(
        List.of("flavor"),
        invoke(
            ChargeLore.class,
            "stripChargeBlock",
            new Class[] {List.class},
            List.of("flavor", "§0§1§3Old", "obsolete")));
  }
}
