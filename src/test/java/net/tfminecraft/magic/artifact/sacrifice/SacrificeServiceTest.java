package net.tfminecraft.magic.artifact.sacrifice;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.*;
import net.tfminecraft.interactiblefurniture.furniture.*;
import net.tfminecraft.magic.*;
import net.tfminecraft.magic.artifact.*;
import net.tfminecraft.magic.artifact.shrine.*;
import net.tfminecraft.magic.integration.RpCharactersBridge;
import net.tfminecraft.rpcharacters.objects.RPCharacter;
import org.bukkit.*;
import org.bukkit.event.entity.*;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.*;
import org.mockbukkit.mockbukkit.*;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import org.mockito.MockedStatic;

class SacrificeServiceTest {
  ServerMock server;
  PlayerMock caster, victim;
  Furniture furniture;
  PlacedSlot slot;
  ItemStack item;
  ShrineScore score;
  MockedStatic<SacrificeTargeting> targeting;
  MockedStatic<SacrificeRiteFx> fx;
  MockedStatic<SacrificeDaggerMatcher> dagger;
  MockedStatic<Messages> messages;

  @BeforeEach
  void setup() {
    server = MockBukkit.mock();
    var world = server.addSimpleWorld("rite");
    caster = server.addPlayer();
    victim = server.addPlayer();
    caster.teleport(new Location(world, 0, 64, 0));
    victim.teleport(caster.getLocation());
    Magic.plugin = mock(Magic.class);
    when(Magic.plugin.namespace()).thenReturn("magic");
    when(Magic.plugin.getServer()).thenReturn(server);
    when(Magic.plugin.isEnabled()).thenReturn(true);
    furniture = mock(Furniture.class);
    when(furniture.getEntityId()).thenReturn(UUID.randomUUID());
    when(furniture.getLoc()).thenReturn(caster.getLocation());
    slot = mock(PlacedSlot.class);
    when(furniture.getActiveSlot("main")).thenReturn(Optional.of(slot));
    item = new ItemStack(Material.STONE);
    var artifact = Artifact.create();
    artifact.setCap("fire", 10);
    artifact.persistPdc(item);
    when(slot.getCurrentItem()).thenAnswer(i -> item);
    score = new ShrineScore(Map.of());
    SacrificeRegistry.clear();
    SacrificeRegistry.setEnabled(true);
    SacrificeRegistry.register(
        new SacrificeElementDef("fire", true, List.of("word"), "pain", "fear", "soul", 0));
    targeting = mockStatic(SacrificeTargeting.class);
    targeting
        .when(() -> SacrificeTargeting.find(caster, "fire"))
        .thenReturn(SacrificeTargeting.Result.ok(furniture, "main", victim, score));
    targeting
        .when(() -> SacrificeTargeting.originCenter(furniture))
        .thenAnswer(i -> furniture.getLoc());
    fx = mockStatic(SacrificeRiteFx.class);
    dagger = mockStatic(SacrificeDaggerMatcher.class);
    dagger.when(() -> SacrificeDaggerMatcher.matches(any())).thenReturn(true);
    messages = mockStatic(Messages.class, i -> i.getArgument(0));
  }

  @AfterEach
  void cleanup() {
    SacrificeRiteService.clearAll();
    messages.close();
    dagger.close();
    fx.close();
    targeting.close();
    SacrificeRegistry.clear();
    Magic.plugin = null;
    MockBukkit.unmock();
  }

  void start() {
    SacrificeRiteService.tryStart(caster, "fire");
    assertTrue(SacrificeRiteService.hasCaster(caster.getUniqueId()));
    assertEquals("sacrifice.dread", victim.nextMessage());
  }

  void tick() {
    server.getScheduler().performTicks(2);
  }

  @Test
  void startValidatesInputsConfigurationDaggerAndTargetFailures() {
    SacrificeRiteService.tryStart(null, "fire");
    SacrificeRiteService.tryStart(caster, null);
    SacrificeRiteService.tryStart(caster, "");
    SacrificeRiteService.tryStart(caster, "missing");
    assertFalse(SacrificeRiteService.hasSessions());
    assertFalse(SacrificeRiteService.hasCaster(null));
    SacrificeRegistry.setEnabled(false);
    SacrificeRiteService.tryStart(caster, "fire");
    SacrificeRegistry.setEnabled(true);
    dagger.when(() -> SacrificeDaggerMatcher.matches(any())).thenReturn(false);
    SacrificeRiteService.tryStart(caster, "fire");
    assertEquals("sacrifice.fail.no_dagger", caster.nextMessage());
    dagger.when(() -> SacrificeDaggerMatcher.matches(any())).thenReturn(true);
    for (var fail :
        List.of(
            SacrificeTargeting.Fail.NO_TARGET,
            SacrificeTargeting.Fail.NO_SCORE,
            SacrificeTargeting.Fail.MIN_SCORE,
            SacrificeTargeting.Fail.FULL)) {
      targeting
          .when(() -> SacrificeTargeting.find(caster, "fire"))
          .thenReturn(SacrificeTargeting.Result.fail(fail));
      SacrificeRiteService.tryStart(caster, "fire");
      assertEquals("sacrifice.fail." + fail.name().toLowerCase(Locale.ROOT), caster.nextMessage());
    }
  }

  @Test
  void activeRiteRejectsDuplicatesTicksAndCancellationNotifiesVictim() {
    start();
    SacrificeRiteService.tryStart(caster, "fire");
    assertEquals("sacrifice.fail.active", caster.nextMessage());
    tick();
    assertTrue(SacrificeRiteService.hasSessions());
    SacrificeRiteService.stop(null, null);
    SacrificeRiteService.stop(UUID.randomUUID(), null);
    SacrificeRiteService.stop(furniture.getEntityId(), "wrong");
    SacrificeRiteService.cancelForPlayer(null);
    SacrificeRiteService.cancelForPlayer(UUID.randomUUID());
    assertTrue(SacrificeRiteService.hasSessions());
    SacrificeRiteService.stop(furniture.getEntityId(), "main");
    assertEquals("sacrifice.subside", victim.nextMessage());
    assertFalse(SacrificeRiteService.hasSessions());
    start();
    SacrificeRiteService.cancelForPlayer(caster.getUniqueId());
    assertEquals("sacrifice.subside", victim.nextMessage());
    start();
    SacrificeRiteService.stopAll(furniture.getEntityId());
    assertFalse(SacrificeRiteService.hasSessions());
  }

  @Test
  void missingArtifactCarriedFurnitureRangeAndDisconnectCancel() {
    start();
    when(furniture.isCarried()).thenReturn(true);
    tick();
    assertFalse(SacrificeRiteService.hasSessions());
    victim.nextMessage();
    when(furniture.isCarried()).thenReturn(false);
    start();
    item = null;
    tick();
    assertFalse(SacrificeRiteService.hasSessions());
    victim.nextMessage();
    var artifact = Artifact.create();
    artifact.setCap("fire", 10);
    item = new ItemStack(Material.STONE);
    artifact.persistPdc(item);
    start();
    victim.teleport(caster.getLocation().add(20, 0, 0));
    tick();
    assertFalse(SacrificeRiteService.hasSessions());
    victim.nextMessage();
    victim.teleport(caster.getLocation());
    start();
    victim.disconnect();
    tick();
    assertFalse(SacrificeRiteService.hasSessions());
  }

  @Test
  void offlineCasterAndChangedWorldCancel() {
    start();
    caster.disconnect();
    tick();
    assertFalse(SacrificeRiteService.hasSessions());
    assertEquals("sacrifice.subside", victim.nextMessage());
  }

  @Test
  void fullChargeSendsOneNearNoticeAndSessionStoresResolution() throws Exception {
    var session =
        new SacrificeRiteSession(
            caster.getUniqueId(),
            victim.getUniqueId(),
            furniture,
            "main",
            "fire",
            score,
            System.currentTimeMillis() - 20_000);
    assertEquals(1, session.charge());
    assertEquals(caster.getUniqueId(), session.getCasterId());
    assertEquals(victim.getUniqueId(), session.getVictimId());
    assertEquals(furniture.getEntityId(), session.getFurnitureId());
    assertEquals("main", session.getSlotId());
    assertEquals("fire", session.getElementId());
    assertSame(score, session.getScore());
    assertSame(furniture, session.getFurniture());
    assertTrue(session.getStartMillis() > 0);
    assertFalse(session.isResolved());
    session.markResolved(true, "soul", .8);
    assertTrue(session.isResolved());
    assertEquals("soul", session.getResolvedTierId());
    assertEquals(.8, session.getResolvedCharge());
    session.setNearSent(true);
    assertTrue(session.isNearSent());
    var future =
        new SacrificeRiteSession(
            null, null, null, null, null, null, System.currentTimeMillis() + 10_000);
    assertEquals(0, future.charge());
    assertNull(future.getFurnitureId());
    SacrificeRegistry.setGlobals(
        .000001, 4, null, true, true, 0, 0, true, true, true, null, null, null);
    start();
    // MockBukkit ticks do not advance the wall clock used by rite charging.
    var active = SacrificeRiteService.sessions().iterator().next();
    var startMillis = SacrificeRiteSession.class.getDeclaredField("startMillis");
    startMillis.setAccessible(true);
    startMillis.setLong(active, System.currentTimeMillis() - 1_000);
    assertEquals(1, active.charge());
    tick();
    tick();
    assertEquals("sacrifice.near", victim.nextMessage());
    assertNull(victim.nextMessage());
  }

  @Test
  void daggerDeathAppliesResolvedTierAndFillOnlyWhenRoleplayAccepts() {
    try (var bridge = mockStatic(RpCharactersBridge.class);
        var fill = mockStatic(SacrificeFillService.class)) {
      var cause = mock(EntityDamageByEntityEvent.class);
      when(cause.getDamager()).thenReturn(caster);
      victim.setLastDamageCause(cause);
      start();
      SacrificeRiteService.handleDeath(victim);
      assertFalse(SacrificeRiteService.hasSessions());
      fill.verifyNoInteractions();
      var character = mock(RPCharacter.class);
      when(character.getId()).thenReturn("victim-character");
      when(character.getName()).thenReturn("Victim");
      bridge.when(() -> RpCharactersBridge.getActiveCharacter(victim)).thenReturn(character);
      bridge
          .when(() -> RpCharactersBridge.applySacrificeTier(eq(victim), eq(caster), any()))
          .thenReturn(true);
      start();
      SacrificeRiteService.handleDeath(victim);
      fill.verify(
          () -> SacrificeFillService.apply(any(), any(), eq("victim-character"), eq("Victim")));
      assertFalse(SacrificeRiteService.hasSessions());
    }
  }

  @Test
  void otherDeathsOnlyCancelWithoutResolving() {
    SacrificeRiteService.handleDeath(null);
    start();
    SacrificeRiteService.handleDeath(victim);
    assertFalse(SacrificeRiteService.hasSessions());
    victim.nextMessage();
    var damage = mock(EntityDamageByEntityEvent.class);
    victim.setLastDamageCause(damage);
    for (var entity :
        List.of(
            mock(org.bukkit.entity.Projectile.class),
            mock(org.bukkit.entity.Zombie.class),
            caster)) {
      when(damage.getDamager()).thenReturn(entity);
      dagger.when(() -> SacrificeDaggerMatcher.matches(any())).thenReturn(true);
      start();
      dagger.when(() -> SacrificeDaggerMatcher.matches(any())).thenReturn(false);
      SacrificeRiteService.handleDeath(victim);
      assertFalse(SacrificeRiteService.hasSessions());
      victim.nextMessage();
    }
  }

  @Test
  void resolvedSacrificeAddsScaledAuraAndStoresCharacterHistory() {
    Cache.artifactAuraCap = 100;
    var shrine = new ShrineScore(Map.of("fire", new ShrineElementScore("fire", 50, 1, null, null)));
    var session =
        new SacrificeRiteSession(
            caster.getUniqueId(), victim.getUniqueId(), furniture, "main", "fire", shrine, 0);
    var tier = new SacrificeTierDef("soul", 0, 1, .1, "soul", true);
    SacrificeFillService.apply(null, tier, "character", "Name");
    SacrificeFillService.apply(session, tier, "character", "Name");
    assertEquals(0, Artifact.fromItem(item).getFill("fire"));
    session.markResolved(true, "soul", 1);
    SacrificeFillService.apply(session, null, "character", "Name");
    SacrificeFillService.apply(session, tier, "character", "Name");
    assertEquals(5, Artifact.fromItem(item).getFill("fire"));
    assertEquals(1, SacrificeImprintStore.read(item).size());
    SacrificeFillService.apply(session, tier, "character", "Name");
    assertEquals(10, Artifact.fromItem(item).getFill("fire"));
    assertEquals(1, SacrificeImprintStore.read(item).size());
    verify(slot, times(2)).setCurrentItem(item);
  }

  @Test
  void sacrificeFillValidatesTargetAndHandlesMissingOrZeroScenery() {
    Cache.artifactAuraCap = 100;
    var tier = new SacrificeTierDef("pain", 0, 1, .1, "pain", false);
    for (String id : Arrays.asList(null, "", "water", "fire")) {
      var session = new SacrificeRiteSession(null, null, furniture, "main", id, null, 0);
      session.markResolved(true, "pain", 1);
      SacrificeFillService.apply(session, tier, null, null);
      assertEquals(0, Artifact.fromItem(item).getFill("fire"));
    }
    var session =
        new SacrificeRiteSession(null, null, furniture, "main", "fire", new ShrineScore(null), 0);
    session.markResolved(true, "pain", 1);
    SacrificeFillService.apply(session, tier, "", "");
    assertEquals(0, Artifact.fromItem(item).getFill("fire"));
    Cache.artifactAuraCap = 0;
    SacrificeFillService.apply(session, tier, null, null);
    SacrificeFillService.apply(
        session, new SacrificeTierDef("none", 0, 1, 0, "none", false), null, null);
    item = null;
    SacrificeFillService.apply(session, tier, null, null);
    var absent = new SacrificeRiteSession(null, null, null, "main", "fire", score, 0);
    absent.markResolved(true, "pain", 1);
    SacrificeFillService.apply(absent, tier, null, null);
  }

  @Test
  void optionalDaggerAndConcurrencySettingsAllowSeveralCasters() {
    SacrificeRegistry.setGlobals(
        10, 4, null, true, true, 0, 0, false, false, false, null, null, null);
    dagger.when(() -> SacrificeDaggerMatcher.matches(any())).thenReturn(false);
    start();
    var other = server.addPlayer();
    other.teleport(caster.getLocation());
    targeting
        .when(() -> SacrificeTargeting.find(other, "fire"))
        .thenReturn(SacrificeTargeting.Result.ok(furniture, "main", victim, score));
    SacrificeRiteService.tryStart(other, "fire");
    assertTrue(SacrificeRiteService.hasCaster(other.getUniqueId()));
    SacrificeRiteService.cancelForPlayer(caster.getUniqueId());
    assertTrue(SacrificeRiteService.hasSessions());
    SacrificeRiteService.cancelForPlayer(victim.getUniqueId());
    assertFalse(SacrificeRiteService.hasSessions());
    SacrificeRegistry.clear();
    SacrificeRegistry.setEnabled(true);
    SacrificeRiteService.tryStart(caster, "fire");
    SacrificeRegistry.register(
        new SacrificeElementDef("fire", false, List.of("word"), "pain", "fear", "soul", 0));
    SacrificeRiteService.tryStart(caster, "fire");
    assertFalse(SacrificeRiteService.hasSessions());
  }

  @Test
  void changedWorldAndMissingOriginCancelButDeadVictimWaitsForDeathHandler() {
    start();
    victim.teleport(new Location(server.addSimpleWorld("elsewhere"), 0, 64, 0));
    tick();
    assertFalse(SacrificeRiteService.hasSessions());
    victim.nextMessage();
    victim.teleport(caster.getLocation());
    start();
    when(furniture.getLoc()).thenReturn(null);
    tick();
    assertFalse(SacrificeRiteService.hasSessions());
    victim.nextMessage();
    var place = caster.getLocation();
    when(furniture.getLoc()).thenReturn(place);
    start();
    victim.setHealth(0);
    tick();
    assertTrue(SacrificeRiteService.hasSessions());
  }

  @Test
  void daggerKillWithoutMatchingCasterOrVictimNeverResolves() {
    var damage = mock(EntityDamageByEntityEvent.class);
    var outsider = server.addPlayer();
    when(damage.getDamager()).thenReturn(outsider);
    victim.setLastDamageCause(damage);
    start();
    SacrificeRiteService.handleDeath(victim);
    assertFalse(SacrificeRiteService.hasSessions());
    victim.nextMessage();
    start();
    outsider.setLastDamageCause(damage);
    SacrificeRiteService.handleDeath(outsider);
    assertTrue(SacrificeRiteService.hasSessions());
  }

  @Test
  void reentrantVictimNotificationsCanRemoveOtherSessionsDuringSnapshotTraversal() {
    var other = server.addPlayer();
    other.teleport(caster.getLocation());
    targeting
        .when(() -> SacrificeTargeting.find(other, "fire"))
        .thenReturn(SacrificeTargeting.Result.ok(furniture, "main", victim, score));
    var observed = spy(victim);
    var victimId = victim.getUniqueId();
    var entered = new java.util.concurrent.atomic.AtomicBoolean();
    doAnswer(
            call -> {
              if (entered.compareAndSet(false, true)) SacrificeRiteService.clearAll();
              return null;
            })
        .when(observed)
        .sendMessage("sacrifice.subside");
    try (var bukkit = mockStatic(org.bukkit.Bukkit.class, CALLS_REAL_METHODS)) {
      bukkit.when(() -> org.bukkit.Bukkit.getPlayer(victimId)).thenReturn(observed);
      for (int operation = 0; operation < 3; operation++) {
        when(furniture.isCarried()).thenReturn(false);
        entered.set(false);
        SacrificeRiteService.tryStart(caster, "fire");
        SacrificeRiteService.tryStart(other, "fire");
        assertEquals(
            2,
            java.util.stream.StreamSupport.stream(
                    SacrificeRiteService.sessions().spliterator(), false)
                .count());
        if (operation == 0) SacrificeRiteService.stopAll(furniture.getEntityId());
        else if (operation == 1) SacrificeRiteService.cancelForPlayer(victimId);
        else {
          when(furniture.isCarried()).thenReturn(true);
          tick();
        }
        assertFalse(SacrificeRiteService.hasSessions());
      }
    }
  }

  @Test
  void fillsPersistHistoryOnlyForNamedArtifactCharacters() {
    Cache.artifactAuraCap = 100;
    ArtifactIds.writeNew(item);
    var shrine = new ShrineScore(Map.of("fire", new ShrineElementScore("fire", 50, 1, null, null)));
    var session =
        new SacrificeRiteSession(
            caster.getUniqueId(), victim.getUniqueId(), furniture, "main", "fire", shrine, 0);
    session.markResolved(true, "soul", 1);
    var tier = new SacrificeTierDef("soul", 0, 1, .1, "soul", true);
    try (var external =
        mockStatic(net.tfminecraft.interactiblefurniture.InteractibleFurniture.class)) {
      var plugin =
          mock(
              net.tfminecraft.interactiblefurniture.InteractibleFurniture.class,
              RETURNS_DEEP_STUBS);
      external
          .when(net.tfminecraft.interactiblefurniture.InteractibleFurniture::getInstance)
          .thenReturn(plugin);
      SacrificeFillService.apply(session, tier, null, null);
      SacrificeFillService.apply(session, tier, " ", "Name");
      assertTrue(SacrificeImprintStore.read(item).isEmpty());
      verify(plugin.getFurnitureManager(), times(2)).persistFurniture(furniture);
      var meta = item.getItemMeta();
      meta.getPersistentDataContainer()
          .set(
              net.tfminecraft.magic.charge.ChargeKeys.chargeTier(),
              org.bukkit.persistence.PersistentDataType.INTEGER,
              1);
      item.setItemMeta(meta);
      SacrificeFillService.apply(session, tier, "id", "Name");
      assertTrue(SacrificeImprintStore.read(item).isEmpty());
    }
  }

  @Test
  void refusesActiveWordsIndependentlyAndRecreatesCancelledTicker() throws Exception {
    SacrificeRegistry.setGlobals(
        10, 4, null, true, true, 0, 0, true, false, true, null, null, null);
    start();
    SacrificeRiteService.tryStart(caster, "fire");
    assertEquals("sacrifice.fail.active", caster.nextMessage());
    server.getScheduler().cancelTasks(Magic.plugin);
    var other = server.addPlayer();
    targeting
        .when(() -> SacrificeTargeting.find(other, "fire"))
        .thenReturn(SacrificeTargeting.Result.ok(furniture, "main", victim, score));
    SacrificeRiteService.tryStart(other, "fire");
    assertTrue(SacrificeRiteService.hasCaster(other.getUniqueId()));
    SacrificeRiteService.clearAll();
    var saved = Magic.plugin;
    Magic.plugin = null;
    try {
      var ensure = SacrificeRiteService.class.getDeclaredMethod("ensureTicker");
      ensure.setAccessible(true);
      ensure.invoke(null);
    } finally {
      Magic.plugin = saved;
    }
  }

  @Test
  void disconnectBetweenPlayerLookupAndUseStopsRitesWithoutNotification() {
    for (int scenario = 0; scenario < 3; scenario++) {
      var c = server.addPlayer();
      var v = server.addPlayer();
      c.teleport(caster.getLocation());
      v.teleport(caster.getLocation());
      targeting
          .when(() -> SacrificeTargeting.find(c, "fire"))
          .thenReturn(SacrificeTargeting.Result.ok(furniture, "main", v, score));
      SacrificeRiteService.tryStart(c, "fire");
      var subject = scenario == 2 ? c : v;
      var id = subject.getUniqueId();
      try (var bukkit = mockStatic(org.bukkit.Bukkit.class, CALLS_REAL_METHODS)) {
        bukkit
            .when(() -> org.bukkit.Bukkit.getPlayer(id))
            .thenAnswer(
                call -> {
                  var found = server.getPlayer(id);
                  subject.disconnect();
                  return found;
                });
        if (scenario == 0) SacrificeRiteService.cancelForPlayer(c.getUniqueId());
        else tick();
        assertFalse(SacrificeRiteService.hasCaster(c.getUniqueId()));
      }
    }
  }
}
