package net.tfminecraft.magic.artifact.shrine;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.*;
import net.tfminecraft.interactiblefurniture.InteractibleFurniture;
import net.tfminecraft.interactiblefurniture.furniture.*;
import net.tfminecraft.magic.*;
import net.tfminecraft.magic.artifact.*;
import net.tfminecraft.magic.artifact.aura.*;
import net.tfminecraft.magic.artifact.sacrifice.*;
import net.tfminecraft.magic.meditation.*;
import net.tfminecraft.magic.tick.MagicTickContext;
import org.bukkit.*;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.*;
import org.mockbukkit.mockbukkit.*;
import org.mockito.MockedStatic;

class ShrineServiceTest {
  ServerMock server;
  Furniture furniture;
  PlacedSlot slot;
  ItemStack item;
  ShrineScore score;
  MockedStatic<ShrineScorer> scoring;
  MockedStatic<ShrineChargeFx> fx;
  MockedStatic<MeditationCircle> circle;
  MockedStatic<InteractibleFurniture> bridge;
  InteractibleFurniture dependency;

  @BeforeEach
  void setup() {
    server = MockBukkit.mock();
    var world = server.addSimpleWorld("shrine");
    Magic.plugin = mock(Magic.class);
    when(Magic.plugin.namespace()).thenReturn("magic");
    furniture = mock(Furniture.class);
    slot = mock(PlacedSlot.class);
    when(furniture.getEntityId()).thenReturn(UUID.randomUUID());
    when(furniture.getLoc()).thenReturn(new Location(world, 0, 64, 0));
    when(furniture.getActiveSlot("main")).thenReturn(Optional.of(slot));
    when(furniture.getActiveSlots()).thenReturn(Map.of("main", slot));
    when(slot.getId()).thenReturn("main");
    when(furniture.getId()).thenReturn("pedestal");
    when(slot.getCurrentItem()).thenAnswer(i -> item);
    doAnswer(
            i -> {
              item = i.getArgument(0);
              return null;
            })
        .when(slot)
        .setCurrentItem(any());
    score =
        new ShrineScore(Map.of("fire", new ShrineElementScore("fire", 5, 2, List.of(), List.of())));
    scoring = mockStatic(ShrineScorer.class);
    scoring.when(() -> ShrineScorer.score(any())).thenAnswer(i -> score);
    fx = mockStatic(ShrineChargeFx.class);
    circle = mockStatic(MeditationCircle.class);
    bridge = mockStatic(InteractibleFurniture.class);
    dependency = mock(InteractibleFurniture.class, RETURNS_DEEP_STUBS);
    bridge.when(InteractibleFurniture::getInstance).thenReturn(dependency);
    ShrineRegistry.clear();
    SacrificeRegistry.clear();
    MeditationCache.pedestalId = "pedestal";
    Cache.artifactAuraCap = 30;
  }

  @AfterEach
  void cleanup() {
    ShrineChargeService.clearAll();
    bridge.close();
    circle.close();
    fx.close();
    scoring.close();
    Magic.plugin = null;
    MockBukkit.unmock();
  }

  void artifact(double fill) {
    item = new ItemStack(Material.STONE);
    var a = Artifact.create();
    a.setCap("fire", 10);
    a.setFill("fire", fill);
    a.persistPdc(item);
    ArtifactIds.writeNew(item);
  }

  double fill() {
    return AuraVessels.fromItem(item).getFill("fire");
  }

  @Test
  void naturalChargeCannotOvershootTheSceneryLimit() {
    artifact(4);
    ShrineChargeService.tryStart(null, furniture, "main");
    assertTrue(ShrineChargeService.hasSessions());
    ShrineChargeService.tick(MagicTickContext.of(1));
    assertEquals(5, fill());
    ShrineChargeService.tick(MagicTickContext.of(2));
    assertFalse(ShrineChargeService.hasSessions());
  }

  @Test
  void naturalSessionsPersistProgressAndStopOnRemovalCarryOrCircle() {
    artifact(0);
    ShrineChargeService.tryStart(null, furniture, "main");
    assertTrue(ShrineChargeService.hasSessions());
    ShrineChargeService.tick(MagicTickContext.of(5));
    assertEquals(2, fill());
    verify(dependency.getFurnitureManager()).persistFurniture(furniture);
    ShrineChargeService.stop(furniture.getEntityId(), "main");
    assertFalse(ShrineChargeService.hasSessions());
    ShrineChargeService.tryStart(null, furniture, "main");
    when(furniture.isCarried()).thenReturn(true);
    ShrineChargeService.tick(null);
    assertFalse(ShrineChargeService.hasSessions());
    when(furniture.isCarried()).thenReturn(false);
    ShrineChargeService.tryStart(null, furniture, "main");
    circle.when(() -> MeditationCircle.containing(furniture)).thenReturn(true);
    ShrineChargeService.tick(null);
    assertFalse(ShrineChargeService.hasSessions());
    circle.when(() -> MeditationCircle.containing(furniture)).thenReturn(false);
    ShrineChargeService.tryStart(null, furniture, "main");
    item = null;
    ShrineChargeService.tick(null);
    assertFalse(ShrineChargeService.hasSessions());
    artifact(0);
    ShrineChargeService.tryStart(null, furniture, "main");
    item = new ItemStack(Material.STONE);
    ShrineChargeService.tick(null);
    assertFalse(ShrineChargeService.hasSessions());
  }

  @Test
  void invalidOrFullItemsDoNotStartAndStopAllOnlyStopsMatchingFurniture() {
    ShrineChargeService.tryStart(null, null, "main");
    ShrineChargeService.tryStart(null, furniture, null);
    ShrineChargeService.tryStart(null, furniture, "");
    ShrineChargeService.tryStart(null, furniture, "missing");
    ShrineChargeService.tryStart(null, furniture, "main");
    item = new ItemStack(Material.AIR);
    ShrineChargeService.tryStart(null, furniture, "main");
    item = new ItemStack(Material.STONE);
    ShrineChargeService.tryStart(null, furniture, "main");
    assertFalse(ShrineChargeService.hasSessions());
    artifact(6);
    var player = server.addPlayer();
    try (var messages = mockStatic(Messages.class, i -> i.getArgument(0))) {
      ShrineChargeService.tryStart(player, furniture, "main");
      assertEquals("shrine.full", player.nextMessage());
    }
    artifact(0);
    circle.when(() -> MeditationCircle.containing(furniture)).thenReturn(true);
    ShrineChargeService.tryStart(null, furniture, "main");
    assertFalse(ShrineChargeService.hasSessions());
    circle.when(() -> MeditationCircle.containing(furniture)).thenReturn(false);
    ShrineChargeService.tryStart(null, furniture, "main");
    ShrineChargeService.stopAll(null);
    ShrineChargeService.stopAll(UUID.randomUUID());
    assertTrue(ShrineChargeService.hasSessions());
    ShrineChargeService.stopAll(furniture.getEntityId());
    assertFalse(ShrineChargeService.hasSessions());
    assertEquals(
        furniture.getEntityId() + "|", ShrineChargeService.key(furniture.getEntityId(), null));
  }

  @Test
  void adminTargetsNearestEligiblePedestalAndClampsRequestedFill() {
    var player = server.addPlayer();
    player.teleport(furniture.getLoc());
    assertEquals(
        ShrineChargeService.AdminStart.NO_PEDESTAL,
        ShrineChargeService.startAdmin(null, "fire", 5));
    assertEquals(
        ShrineChargeService.AdminStart.NO_PEDESTAL,
        ShrineChargeService.startAdmin(player, null, 5));
    assertEquals(
        ShrineChargeService.AdminStart.NO_PEDESTAL, ShrineChargeService.startAdmin(player, "", 5));
    try (var targeting = mockStatic(SacrificeTargeting.class)) {
      targeting
          .when(() -> SacrificeTargeting.collectNearby(any(), anyInt(), anyInt()))
          .thenReturn(List.of());
      assertEquals(
          ShrineChargeService.AdminStart.NO_PEDESTAL,
          ShrineChargeService.startAdmin(player, "fire", 5));
      targeting
          .when(() -> SacrificeTargeting.collectNearby(any(), anyInt(), anyInt()))
          .thenReturn(List.of(furniture));
      targeting
          .when(() -> SacrificeTargeting.originCenter(furniture))
          .thenAnswer(i -> furniture.getLoc());
      assertEquals(
          ShrineChargeService.AdminStart.NO_ARTIFACT,
          ShrineChargeService.startAdmin(player, "fire", 5));
      artifact(0);
      assertEquals(
          ShrineChargeService.AdminStart.NO_CAP,
          ShrineChargeService.startAdmin(player, "water", 5));
      artifact(5);
      assertEquals(
          ShrineChargeService.AdminStart.ALREADY_FULL,
          ShrineChargeService.startAdmin(player, "fire", 5));
      artifact(0);
      assertEquals(
          ShrineChargeService.AdminStart.STARTED,
          ShrineChargeService.startAdmin(player, "FIRE", 3));
      assertTrue(ShrineChargeService.hasSessions());
      ShrineChargeService.tick(MagicTickContext.of(5));
      assertEquals(2, fill());
      ShrineChargeService.tick(MagicTickContext.of(6));
      assertEquals(3, fill());
      assertFalse(ShrineChargeService.hasSessions());
      artifact(0);
      assertEquals(
          ShrineChargeService.AdminStart.STARTED,
          ShrineChargeService.startAdmin(player, "fire", 15));
      ShrineChargeService.tick(null);
      assertEquals(2, fill());
      Cache.artifactAuraCap = 0;
      ShrineChargeService.tick(null);
      assertFalse(ShrineChargeService.hasSessions());
    }
  }

  @Test
  void chargeSelectionUsesValidAllowedSceneryAndPrefersPrimaryOnTies() {
    assertFalse(ShrineChargeService.hasChargeable(null, null, score));
    assertFalse(ShrineChargeService.shrineCanOffer(null, null, score));
    assertNull(ShrineChargeService.chargeFxElement(null, null, null, false));
    assertTrue(ShrineChargeService.allowedElements(null, null).isEmpty());
    artifact(0);
    var vessel = AuraVessels.fromItem(item);
    assertEquals(Set.of("fire"), ShrineChargeService.allowedElements(item, vessel));
    assertEquals("fire", ShrineChargeService.chargeFxElement(item, vessel, score, false));
    assertTrue(ShrineChargeService.hasChargeable(vessel, item, score));
    assertTrue(ShrineChargeService.shrineCanOffer(vessel, item, score));
    for (ShrineScore invalid :
        List.of(
            new ShrineScore(null),
            new ShrineScore(Map.of("fire", new ShrineElementScore("fire", 0, 1, null, null))),
            new ShrineScore(Map.of("fire", new ShrineElementScore("fire", 5, 0, null, null))))) {
      assertFalse(ShrineChargeService.hasChargeable(vessel, item, invalid));
      assertFalse(ShrineChargeService.shrineCanOffer(vessel, item, invalid));
      assertEquals("fire", ShrineChargeService.chargeFxElement(item, vessel, invalid, false));
    }
    assertFalse(ShrineChargeService.hasChargeable(vessel, item, null));
    assertFalse(ShrineChargeService.shrineCanOffer(vessel, item, null));
    assertEquals("fire", ShrineChargeService.chargeFxElement(item, vessel, null, false));
  }

  @Test
  void slotFallbackReadsDisplayAndSessionAccessorsPreserveInputs() {
    assertNull(ShrineChargeService.itemFromSlot(null));
    assertNull(ShrineChargeService.itemFromSlot(slot));
    var display = new org.mockbukkit.mockbukkit.entity.ItemDisplayMock(server, UUID.randomUUID());
    display.teleport(furniture.getLoc());
    server.registerEntity(display);
    var shown = new ItemStack(Material.DIAMOND);
    display.setItemStack(shown);
    when(slot.getDisplayStandId()).thenReturn(display.getUniqueId());
    assertEquals(shown, ShrineChargeService.itemFromSlot(slot));
    verify(slot).setModel(shown);
    var session = new ShrineChargeSession(furniture, "main", score, "fire", 8, true);
    assertSame(furniture, session.getFurniture());
    assertEquals("main", session.getSlotId());
    assertSame(score, session.getScore());
    assertEquals("fire", session.getForcedElement());
    assertEquals(8, session.getTargetFill());
    assertTrue(session.isAdminForced());
    session.setLastPersistSeconds(9);
    assertEquals(9, session.getLastPersistSeconds());
  }

  @Test
  void blankChargesImprintFromSceneryAndAdminsCanImprintNamedSchool() {
    net.tfminecraft.magic.charge.ChargeRegistry.clear();
    net.tfminecraft.magic.charge.ChargeRegistry.register(
        new net.tfminecraft.magic.charge.ChargeDef(1, "v.STONE", 10));
    item = new ItemStack(Material.STONE);
    var meta = item.getItemMeta();
    meta.getPersistentDataContainer()
        .set(
            net.tfminecraft.magic.charge.ChargeKeys.chargeTier(),
            org.bukkit.persistence.PersistentDataType.INTEGER,
            1);
    item.setItemMeta(meta);
    var player = server.addPlayer();
    player.teleport(furniture.getLoc());
    var saved = score;
    score = new ShrineScore(null);
    try (var messages = mockStatic(Messages.class, i -> i.getArgument(0))) {
      ShrineChargeService.tryStart(player, furniture, "main");
      assertEquals("charge.imprint.no_elements", player.nextMessage());
      score = saved;
      ShrineChargeService.tryStart(player, furniture, "main");
      assertEquals("charge.imprint.done", player.nextMessage());
      assertTrue(ShrineChargeService.hasSessions());
      assertEquals(Set.of("fire"), AuraVessels.fromItem(item).getCappedElementIds());
      ShrineChargeService.clearAll();
      try (var targeting = mockStatic(SacrificeTargeting.class)) {
        targeting
            .when(() -> SacrificeTargeting.collectNearby(any(), anyInt(), anyInt()))
            .thenReturn(List.of(furniture));
        targeting
            .when(() -> SacrificeTargeting.originCenter(furniture))
            .thenAnswer(i -> furniture.getLoc());
        assertEquals(
            ShrineChargeService.AdminStart.STARTED,
            ShrineChargeService.startAdmin(player, "water", 5));
        assertTrue(AuraVessels.fromItem(item).getCap("water") > 0);
        ShrineChargeService.tick(null);
        ShrineChargeService.stopAll(furniture.getEntityId());
      }
    }
  }

  @Test
  void adminSearchSkipsCarriedForeignFarAndWrongFurniture() {
    var player = server.addPlayer();
    player.teleport(furniture.getLoc());
    artifact(0);
    try (var targeting = mockStatic(SacrificeTargeting.class)) {
      targeting
          .when(() -> SacrificeTargeting.collectNearby(any(), anyInt(), anyInt()))
          .thenReturn(List.of(furniture));
      targeting
          .when(() -> SacrificeTargeting.originCenter(furniture))
          .thenAnswer(i -> furniture.getLoc());
      when(furniture.isCarried()).thenReturn(true);
      assertEquals(
          ShrineChargeService.AdminStart.NO_PEDESTAL,
          ShrineChargeService.startAdmin(player, "fire", 5));
      when(furniture.isCarried()).thenReturn(false);
      when(furniture.getId()).thenReturn("chair");
      assertEquals(
          ShrineChargeService.AdminStart.NO_PEDESTAL,
          ShrineChargeService.startAdmin(player, "fire", 5));
      when(furniture.getId()).thenReturn("pedestal");
      when(furniture.getLoc()).thenReturn(null);
      assertEquals(
          ShrineChargeService.AdminStart.NO_PEDESTAL,
          ShrineChargeService.startAdmin(player, "fire", 5));
      when(furniture.getLoc()).thenReturn(new Location(server.addSimpleWorld("away"), 0, 64, 0));
      assertEquals(
          ShrineChargeService.AdminStart.NO_PEDESTAL,
          ShrineChargeService.startAdmin(player, "fire", 5));
      var at = player.getLocation();
      when(furniture.getLoc()).thenReturn(at);
      targeting.when(() -> SacrificeTargeting.chebyshevBlocks(any(), any())).thenReturn(99);
      assertEquals(
          ShrineChargeService.AdminStart.NO_PEDESTAL,
          ShrineChargeService.startAdmin(player, "fire", 5));
      targeting.when(() -> SacrificeTargeting.chebyshevBlocks(any(), any())).thenReturn(0);
      item = new ItemStack(Material.STONE);
      assertEquals(
          ShrineChargeService.AdminStart.NO_ARTIFACT,
          ShrineChargeService.startAdmin(player, "fire", 5));
    }
  }

  @Test
  void sacrificeHintsDistinguishWeakAndReadySceneryAndBlockNaturalCharging() {
    artifact(0);
    var player = server.addPlayer();
    SacrificeRegistry.setEnabled(true);
    SacrificeRegistry.register(
        new SacrificeElementDef("fire", true, List.of("word"), "", "", "", 10));
    SacrificeRegistry.setGlobals(
        10, 4, null, true, true, 10, 2, true, true, true, null, null, null);
    try (var messages = mockStatic(Messages.class, i -> i.getArgument(0))) {
      ShrineChargeService.tryStart(player, furniture, "main");
      assertEquals("sacrifice.place.weak", player.nextMessage());
      ShrineChargeService.clearAll();
      score = new ShrineScore(Map.of("fire", new ShrineElementScore("fire", 15, 1, null, null)));
      ShrineChargeService.tryStart(player, furniture, "main");
      assertEquals("sacrifice.place.ready", player.nextMessage());
      ShrineChargeService.clearAll();
      SacrificeRegistry.setSceneryCharge(false);
      ShrineChargeService.tryStart(player, furniture, "main");
      assertFalse(ShrineChargeService.hasSessions());
      assertFalse(ShrineChargeService.hasChargeable(AuraVessels.fromItem(item), item, score));
      assertFalse(ShrineChargeService.shrineCanOffer(AuraVessels.fromItem(item), item, score));
      assertEquals(
          "fire",
          ShrineChargeService.chargeFxElement(item, AuraVessels.fromItem(item), score, false));
    }
  }

  @Test
  void shrineGainScalesWithConfiguredTickInterval() {
    try {
      artifact(0);
      Cache.tickIntervalTicks = 10;
      ShrineChargeService.tryStart(null, furniture, "main");
      ShrineChargeService.tick(MagicTickContext.of(1));
      assertEquals(1, fill());
      ShrineChargeService.stopAll(furniture.getEntityId());
      artifact(0);
      Cache.tickIntervalTicks = 40;
      ShrineChargeService.tryStart(null, furniture, "main");
      ShrineChargeService.tick(MagicTickContext.of(2));
      assertEquals(4, fill());
      ShrineChargeService.stopAll(furniture.getEntityId());
      var player = server.addPlayer();
      player.teleport(furniture.getLoc());
      artifact(0);
      Cache.tickIntervalTicks = 10;
      try (var targeting = mockStatic(SacrificeTargeting.class)) {
        targeting
            .when(() -> SacrificeTargeting.collectNearby(any(), anyInt(), anyInt()))
            .thenReturn(List.of(furniture));
        targeting
            .when(() -> SacrificeTargeting.originCenter(furniture))
            .thenAnswer(i -> furniture.getLoc());
        assertEquals(
            ShrineChargeService.AdminStart.STARTED,
            ShrineChargeService.startAdmin(player, "fire", 5));
        ShrineChargeService.tick(MagicTickContext.of(1));
        assertEquals(1, fill());
      }
    } finally {
      Cache.tickIntervalTicks = 20;
    }
  }

  static Object call(String name, Class<?>[] types, Object... args) throws Exception {
    var method = ShrineChargeService.class.getDeclaredMethod(name, types);
    method.setAccessible(true);
    return method.invoke(null, args);
  }

  /**
   * Deliberately malformed external adapter: AuraVessel declares no normalized-ID postcondition.
   */
  static final class ExternalVessel implements AuraVessel {
    final Map<String, Double> caps = new LinkedHashMap<>(), fills = new HashMap<>();
    String primary;
    boolean charge;

    public VesselKind kind() {
      return charge ? VesselKind.CHARGE : VesselKind.ARTIFACT;
    }

    public double getCap(String id) {
      return caps.getOrDefault(id, 0.);
    }

    public double getFill(String id) {
      return fills.getOrDefault(id, 0.);
    }

    public void setCap(String id, double v) {
      caps.put(id, v);
    }

    public void setFill(String id, double v) {
      fills.put(id, v);
    }

    public Set<String> getCappedElementIds() {
      return caps.keySet();
    }

    public boolean hasStoredAura() {
      return !fills.isEmpty();
    }

    public double totalFill() {
      return fills.values().stream().mapToDouble(x -> x).sum();
    }

    public String primaryElementId() {
      return primary;
    }

    public ItemStack write(ItemStack s) {
      return s;
    }

    public void persistPdc(ItemStack s) {}
  }

  @Test
  void malformedExternalVesselEntriesAreRejectedWithoutWritingUnsafeState() {
    var adapter = new ExternalVessel();
    adapter.caps.put(null, 10.);
    adapter.caps.put(" ", 10.);
    adapter.caps.put("zero", 0.);
    adapter.caps.put("fire", 10.);
    adapter.caps.put("other", 10.);
    adapter.charge = true;
    assertEquals(Set.of("fire", "other"), ShrineChargeService.allowedElements(null, adapter));
    adapter.charge = false;
    assertTrue(ShrineChargeService.allowedElements(null, adapter).isEmpty());
    adapter.primary = " ";
    assertTrue(ShrineChargeService.allowedElements(null, adapter).isEmpty());
    adapter.primary = "fire";
    assertEquals(Set.of("fire"), ShrineChargeService.allowedElements(null, adapter));
    assertTrue(adapter.fills.isEmpty());
  }

  @Test
  void sceneryRulesAndPrimaryTieBreaksSelectOnlyEligibleElements() throws Exception {
    var adapter = new ExternalVessel();
    adapter.primary = "fire";
    adapter.charge = true;
    adapter.caps.put("fire", 10.);
    adapter.caps.put("water", 10.);
    var equal =
        new ShrineScore(
            Map.of(
                "fire",
                new ShrineElementScore("fire", 10, 1, null, null),
                "water",
                new ShrineElementScore("water", 10, 1, null, null)));
    assertEquals("fire", ShrineChargeService.chargeFxElement(null, adapter, equal, false));
    adapter.primary = "water";
    assertEquals("water", ShrineChargeService.chargeFxElement(null, adapter, equal, false));
    adapter.primary = null;
    assertNotNull(ShrineChargeService.chargeFxElement(null, adapter, equal, false));
    adapter.caps.put("water", 20.);
    assertEquals("water", ShrineChargeService.chargeFxElement(null, adapter, equal, false));
    adapter.fills.put("water", 10.);
    assertEquals("fire", ShrineChargeService.chargeFxElement(null, adapter, equal, false));
    assertEquals("water", ShrineChargeService.chargeFxElement(null, adapter, equal, true));
    ShrineRegistry.register(new ShrineElementDef("fire", false, List.of()));
    ShrineRegistry.register(new ShrineElementDef("water", false, List.of()));
    assertTrue(ShrineChargeService.blocksVanillaCharge("fire"));
    assertFalse(ShrineChargeService.hasChargeable(adapter, null, equal));
    assertFalse(ShrineChargeService.shrineCanOffer(adapter, null, equal));
    assertNull(ShrineChargeService.chargeFxElement(null, adapter, equal, false));
    ShrineRegistry.register(new ShrineElementDef("fire", true, List.of()));
    assertFalse(ShrineChargeService.blocksVanillaCharge("fire"));
    SacrificeRegistry.setEnabled(true);
    SacrificeRegistry.setSceneryCharge(false);
    SacrificeRegistry.register(new SacrificeElementDef("fire", List.of(), "", "", "", 1));
    assertTrue(ShrineChargeService.blocksVanillaCharge("fire"));
  }

  @Test
  void privateSessionBoundariesHandleInvalidAdminTargetsAndChangedScores() throws Exception {
    artifact(0);
    var types = new Class[] {ShrineChargeSession.class, MagicTickContext.class};
    for (String id : Arrays.asList(null, " ", "water")) {
      var session = new ShrineChargeSession(furniture, "main", score, id, 10, true);
      assertEquals(false, call("tickOne", types, session, null));
    }
    var session = new ShrineChargeSession(furniture, "main", score, "fire", 0, true);
    assertEquals(false, call("tickOne", types, session, null));
    for (ShrineScore changed :
        List.of(
            new ShrineScore(Map.of()),
            new ShrineScore(Map.of("fire", new ShrineElementScore("fire", 0, 1, null, null))),
            new ShrineScore(Map.of("fire", new ShrineElementScore("fire", 10, 0, null, null)))))
      assertEquals(
          false, call("tickOne", types, new ShrineChargeSession(furniture, "main", changed), null));
    ShrineRegistry.register(new ShrineElementDef("fire", false, List.of()));
    assertEquals(
        false, call("tickOne", types, new ShrineChargeSession(furniture, "main", score), null));
    item = new ItemStack(Material.AIR);
    assertEquals(
        false, call("tickOne", types, new ShrineChargeSession(furniture, "main", score), null));
    call("persist", new Class[] {ShrineChargeSession.class, boolean.class}, null, false);
    call("persistFurniture", new Class[] {Furniture.class}, (Object) null);
    bridge
        .when(InteractibleFurniture::getInstance)
        .thenThrow(new IllegalStateException("disabled"));
    call("persistFurniture", new Class[] {Furniture.class}, furniture);
  }

  @Test
  void hintsDistinguishWeakReadyDisabledAndMissingScenery() throws Exception {
    var player = server.addPlayer();
    var types =
        new Class[] {
          org.bukkit.entity.Player.class, Furniture.class, String.class, ShrineScore.class
        };
    call("hintSacrificePlace", types, player, furniture, null, score);
    call("hintSacrificePlace", types, player, furniture, " ", score);
    SacrificeRegistry.setEnabled(false);
    call("hintSacrificePlace", types, player, furniture, "fire", score);
    SacrificeRegistry.setEnabled(true);
    call("hintSacrificePlace", types, player, furniture, "fire", score);
    SacrificeRegistry.register(new SacrificeElementDef("fire", false, List.of(), "", "", "", 50));
    call("hintSacrificePlace", types, player, furniture, "fire", score);
    SacrificeRegistry.register(new SacrificeElementDef("fire", true, List.of(), "", "", "", 50));
    for (ShrineScore next :
        Arrays.asList(
            null,
            new ShrineScore(Map.of()),
            score,
            new ShrineScore(Map.of("fire", new ShrineElementScore("fire", 30, 1, null, null))),
            new ShrineScore(Map.of("fire", new ShrineElementScore("fire", 50, 1, null, null)))))
      call("hintSacrificePlace", types, player, furniture, "fire", next);
    fx.verify(() -> ShrineChargeFx.sacrificeReady(furniture, "fire"));
  }

  @Test
  void slotRecoveryRejectsAirAndNonDisplayEntities() {
    item = new ItemStack(Material.AIR);
    when(slot.getDisplayStandId()).thenReturn(UUID.randomUUID());
    assertEquals(Material.AIR, ShrineChargeService.itemFromSlot(slot).getType());
    var player = server.addPlayer();
    when(slot.getDisplayStandId()).thenReturn(player.getUniqueId());
    assertEquals(Material.AIR, ShrineChargeService.itemFromSlot(slot).getType());
    var display = new org.mockbukkit.mockbukkit.entity.ItemDisplayMock(server, UUID.randomUUID());
    server.registerEntity(display);
    when(slot.getDisplayStandId()).thenReturn(display.getUniqueId());
    assertEquals(Material.AIR, ShrineChargeService.itemFromSlot(slot).getType());
  }

  @Test
  void nonNormalizedExternalCapsFailSafelyAfterElementNormalization() {
    var adapter = new ExternalVessel();
    adapter.charge = true;
    adapter.caps.put(" FIRE ", 10.);
    assertEquals(Set.of("fire"), ShrineChargeService.allowedElements(null, adapter));
    assertFalse(ShrineChargeService.hasChargeable(adapter, null, score));
    assertNull(ShrineChargeService.chargeFxElement(null, adapter, score, false));
  }

  @Test
  void companionsComeFromTheValidatedAffinityRegistry() {
    net.tfminecraft.magic.artifact.config.ArtifactAffinityRegistry.clear();
    net.tfminecraft.magic.artifact.config.ArtifactTypeRegistry.clear();
    try {
      var config = new org.bukkit.configuration.file.YamlConfiguration();
      config.set("enabled", true);
      config.set("model_scheme", "test");
      net.tfminecraft.magic.artifact.config.ArtifactTypeRegistry.register(
          new net.tfminecraft.magic.artifact.config.ArtifactTypeDef(
              "water", "test", "test", true, 1, Map.of(), Map.of()));
      net.tfminecraft.magic.artifact.config.ArtifactAffinityRegistry.registerGroup(
          "pair", List.of("fire", "water"));
      var adapter = new ExternalVessel();
      adapter.primary = "fire";
      adapter.caps.put("fire", 10.);
      adapter.caps.put("water", 10.);
      assertEquals(Set.of("fire", "water"), ShrineChargeService.allowedElements(null, adapter));
    } finally {
      net.tfminecraft.magic.artifact.config.ArtifactAffinityRegistry.clear();
      net.tfminecraft.magic.artifact.config.ArtifactTypeRegistry.clear();
    }
  }

  @Test
  void blankChargeImprintWorksWithoutAnInitiatingPlayer() {
    net.tfminecraft.magic.charge.ChargeRegistry.clear();
    net.tfminecraft.magic.charge.ChargeRegistry.register(
        new net.tfminecraft.magic.charge.ChargeDef(1, "v.STONE", 10));
    item = new ItemStack(Material.STONE);
    var meta = item.getItemMeta();
    meta.getPersistentDataContainer()
        .set(
            net.tfminecraft.magic.charge.ChargeKeys.chargeTier(),
            org.bukkit.persistence.PersistentDataType.INTEGER,
            1);
    item.setItemMeta(meta);
    var saved = score;
    score = new ShrineScore(Map.of());
    ShrineChargeService.tryStart(null, furniture, "main");
    score = saved;
    ShrineChargeService.tryStart(null, furniture, "main");
    ShrineChargeService.tryStart(null, furniture, "main");
    assertTrue(ShrineChargeService.hasSessions());
    score = new ShrineScore(Map.of());
    ShrineChargeService.tryStart(server.addPlayer(), furniture, "main");
  }

  @Test
  void adminSelectionHandlesAirNullRowsTiesAndSlotsRemovedDuringSelection() {
    var player = server.addPlayer();
    player.teleport(furniture.getLoc());
    try (var targeting = mockStatic(SacrificeTargeting.class)) {
      targeting
          .when(() -> SacrificeTargeting.collectNearby(any(), anyInt(), anyInt()))
          .thenReturn(Arrays.asList(null, furniture));
      targeting
          .when(() -> SacrificeTargeting.originCenter(furniture))
          .thenAnswer(a -> furniture.getLoc());
      item = new ItemStack(Material.AIR);
      assertEquals(
          ShrineChargeService.AdminStart.NO_ARTIFACT,
          ShrineChargeService.startAdmin(player, "fire", 5));
      artifact(0);
      var second = mock(PlacedSlot.class);
      when(second.getCurrentItem()).thenAnswer(a -> item);
      when(second.getId()).thenReturn("second");
      when(furniture.getActiveSlots())
          .thenReturn(new LinkedHashMap<>(Map.of("main", slot, "second", second)));
      ShrineChargeService.startAdmin(player, "fire", 5);
      ShrineChargeService.clearAll();
      when(furniture.getActiveSlots()).thenReturn(Map.of("main", slot));
      when(slot.getId()).thenReturn(null);
      assertEquals(
          ShrineChargeService.AdminStart.NO_ARTIFACT,
          ShrineChargeService.startAdmin(player, "fire", 5));
      var removed = new java.util.concurrent.atomic.AtomicBoolean();
      when(slot.getId())
          .thenAnswer(
              a -> {
                removed.set(true);
                return "main";
              });
      when(furniture.getActiveSlot("main"))
          .thenAnswer(a -> removed.get() ? Optional.empty() : Optional.of(slot));
      assertEquals(
          ShrineChargeService.AdminStart.NO_ARTIFACT,
          ShrineChargeService.startAdmin(player, "fire", 5));
    }
  }

  @Test
  void sessionSnapshotsTolerateReentrantRemovalDuringPersistence() {
    artifact(0);
    var second = mock(PlacedSlot.class);
    when(second.getId()).thenReturn("second");
    when(second.getCurrentItem()).thenAnswer(a -> item);
    when(furniture.getActiveSlot("second")).thenReturn(Optional.of(second));
    var once = new java.util.concurrent.atomic.AtomicBoolean();
    var furnitureManager = dependency.getFurnitureManager();
    doAnswer(
            a -> {
              if (once.compareAndSet(false, true)) ShrineChargeService.clearAll();
              return null;
            })
        .when(furnitureManager)
        .persistFurniture(furniture);
    ShrineChargeService.tryStart(null, furniture, "main");
    ShrineChargeService.tryStart(null, furniture, "second");
    ShrineChargeService.stopAll(furniture.getEntityId());
    assertFalse(ShrineChargeService.hasSessions());
    once.set(false);
    ShrineChargeService.tryStart(null, furniture, "main");
    ShrineChargeService.tryStart(null, furniture, "second");
    when(furniture.isCarried()).thenReturn(true);
    ShrineChargeService.tick(null);
    assertFalse(ShrineChargeService.hasSessions());
  }

  @Test
  void adminChargingExistingSchoolsAndHiddenSchoolsRespectImprintRules() {
    net.tfminecraft.magic.charge.ChargeRegistry.clear();
    net.tfminecraft.magic.charge.ChargeRegistry.register(
        new net.tfminecraft.magic.charge.ChargeDef(1, "v.STONE", 10));
    item = new ItemStack(Material.STONE);
    var meta = item.getItemMeta();
    meta.getPersistentDataContainer()
        .set(
            net.tfminecraft.magic.charge.ChargeKeys.chargeTier(),
            org.bukkit.persistence.PersistentDataType.INTEGER,
            1);
    item.setItemMeta(meta);
    var charge = net.tfminecraft.magic.charge.Charge.fromItem(item);
    charge.setCap("fire", 10);
    charge.persistPdc(item);
    var player = server.addPlayer();
    player.teleport(furniture.getLoc());
    var yaml = new org.bukkit.configuration.file.YamlConfiguration();
    yaml.set("permission", "magic.hidden");
    ShrineRegistry.register(new ShrineElementDef("hidden", false, List.of()));
    SacrificeRegistry.setEnabled(false);
    net.tfminecraft.magic.registry.ElementRegistry.register(
        new net.tfminecraft.magic.model.ElementDef("hidden", yaml));
    try (var targeting = mockStatic(SacrificeTargeting.class)) {
      targeting
          .when(() -> SacrificeTargeting.collectNearby(any(), anyInt(), anyInt()))
          .thenReturn(List.of(furniture));
      targeting
          .when(() -> SacrificeTargeting.originCenter(furniture))
          .thenAnswer(a -> furniture.getLoc());
      assertEquals(
          ShrineChargeService.AdminStart.STARTED,
          ShrineChargeService.startAdmin(player, "fire", 5));
      ShrineChargeService.clearAll();
      assertEquals(
          ShrineChargeService.AdminStart.NO_CAP,
          ShrineChargeService.startAdmin(player, "hidden", 5));
    } finally {
      net.tfminecraft.magic.registry.ElementRegistry.clear();
    }
  }

  @Test
  void lowerCapCandidateCannotDisplaceAStrongerElement() {
    var adapter = new ExternalVessel();
    adapter.charge = true;
    var scores =
        new ShrineScore(
            Map.of(
                "fire",
                new ShrineElementScore("fire", 20, 1, null, null),
                "water",
                new ShrineElementScore("water", 20, 1, null, null)));
    adapter.caps.put("fire", 20.);
    adapter.caps.put("water", 10.);
    assertEquals("fire", ShrineChargeService.chargeFxElement(null, adapter, scores, false));
    adapter.caps.put("water", 30.);
    assertEquals("water", ShrineChargeService.chargeFxElement(null, adapter, scores, false));
  }

  @Test
  void naturalProgressUsesCurrentElapsedTimeWhenNoContextIsProvided() {
    artifact(0);
    ShrineChargeService.tryStart(null, furniture, "main");
    ShrineChargeService.tick(null);
    assertEquals(2, fill());
    ShrineChargeService.clearAll();
    score = new ShrineScore(Map.of());
    ShrineChargeService.tryStart(null, furniture, "main");
    assertFalse(ShrineChargeService.hasSessions());
  }
}
