package net.tfminecraft.magic;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.*;
import net.tfminecraft.magic.charge.*;
import net.tfminecraft.magic.gear.*;
import net.tfminecraft.magic.gear.orb.GearOrbService;
import net.tfminecraft.magic.registry.ElementRegistry;
import net.tfminecraft.magic.session.ResonanceSession;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.inventory.*;
import org.junit.jupiter.api.*;

class GearChargeCoverageTest extends GearCoverageSupport {
  long oldConfirm;

  @BeforeEach
  void config() throws Exception {
    oldConfirm = GearCache.confirmMillis;
    GearCache.confirmMillis = 100000;
    ElementRegistry.clear();
    TierBands.clear();
    TierBands.register("default", 1, 5);
    var field = GearChargeService.class.getDeclaredField("CONFIRMS");
    field.setAccessible(true);
    ((Map<?, ?>) field.get(null)).clear();
  }

  @AfterEach
  void reset() {
    GearCache.confirmMillis = oldConfirm;
    ElementRegistry.clear();
    TierBands.clear();
  }

  Charge charge(int tier) {
    var stack = item();
    tag(stack, ChargeKeys.chargeTier(), org.bukkit.persistence.PersistentDataType.INTEGER, tier);
    return Charge.fromItem(stack);
  }

  @Test void confirmationsBindFullSessionGenerationAndItemAndOutgoingCleanupPreservesIncoming()throws Exception{
    var p=server.addPlayer();var q=server.addPlayer();var token=GearStationStore.property().identity().current(p).orElseThrow();var other=GearStationStore.property().identity().current(q).orElseThrow();var occupancy=mock(GearStationStore.Occupancy.class);var generation=UUID.randomUUID();var weapon=item();when(occupancy.generation()).thenReturn(generation);when(occupancy.getItem()).thenReturn(weapon);
    var type=Class.forName("net.tfminecraft.magic.gear.GearChargeService$Confirm");var ctor=type.getDeclaredConstructor(String.class,long.class);ctor.setAccessible(true);Object confirm=ctor.newInstance("station",System.currentTimeMillis()+100000);
    for(var entry:Map.of("token",token,"generation",generation,"itemHash",net.tfminecraft.magic.gear.ownership.GearPropertyStore.hash(net.tfminecraft.magic.gear.ownership.GearItems.bytes(weapon))).entrySet()){var f=type.getDeclaredField(entry.getKey());f.setAccessible(true);f.set(confirm,entry.getValue());}
    var f=GearChargeService.class.getDeclaredField("CONFIRMS");f.setAccessible(true);@SuppressWarnings("unchecked")var confirms=(Map<UUID,Object>)f.get(null);confirms.put(p.getUniqueId(),confirm);
    Class<?>[] args={Player.class,String.class,GearStationStore.Occupancy.class,net.tfminecraft.magic.gear.ownership.GearIdentity.Token.class};
    assertEquals(true,invoke(GearChargeService.class,"confirmed",args,p,"station",occupancy,token));assertEquals(false,invoke(GearChargeService.class,"confirmed",args,p,"station",occupancy,other));
    when(occupancy.generation()).thenReturn(UUID.randomUUID());assertEquals(false,invoke(GearChargeService.class,"confirmed",args,p,"station",occupancy,token));when(occupancy.generation()).thenReturn(generation);when(occupancy.getItem()).thenReturn(new ItemStack(Material.DIAMOND));assertEquals(false,invoke(GearChargeService.class,"confirmed",args,p,"station",occupancy,token));
    GearChargeService.clear(q,other);GearChargeService.clearStale(q);GearChargeService.clear(p,other);GearChargeService.clearStale(p);assertTrue(confirms.containsKey(p.getUniqueId()));GearChargeService.clear(p,token);assertFalse(confirms.containsKey(p.getUniqueId()));
    confirms.put(p.getUniqueId(),confirm);var current=characters.currentSession(p).orElseThrow();characters.sessions.put(p.getUniqueId(),new net.tfminecraft.tlibs.character.CharacterSession(current.ref(),current.sessionId()+1));GearChargeService.clearStale(p);assertFalse(confirms.containsKey(p.getUniqueId()));
  }

  @Test
  void guardsRejectInvalidEmptyBusyAndBrokenStations() {
    var player = server.addPlayer();
    var loc = player.getLocation();
    var hand = item();
    var charge = charge(1);
    var occupancy = mock(GearStationStore.Occupancy.class);
    try (var charges = mockStatic(Charge.class);
        var stations = mockStatic(GearStationStore.class, CALLS_REAL_METHODS);
        var effects = mockStatic(net.tfminecraft.magic.gear.ownership.GearEffects.class);
        var orbs = mockStatic(GearOrbService.class);
        var broken = mockStatic(GearBrokenMarker.class)) {
      GearChargeService.tryApply(player, loc, hand);
      charges.when(() -> Charge.fromItem(hand)).thenReturn(charge);
      GearChargeService.tryApply(player, loc, hand);
      charge.setCap("fire", 5);
      GearChargeService.tryApply(player, loc, hand);
      charge.setFill("fire", 5);
      GearChargeService.tryApply(player, loc, hand);
      stations.when(() -> GearStationStore.get(loc)).thenReturn(occupancy);
      when(occupancy.isOrbSessionActive()).thenReturn(true);
      GearChargeService.tryApply(player, loc, hand);
      when(occupancy.isOrbSessionActive()).thenReturn(false);
      orbs.when(() -> GearOrbService.isActive(loc)).thenReturn(true);
      GearChargeService.tryApply(player, loc, hand);
      orbs.when(() -> GearOrbService.isActive(loc)).thenReturn(false);
      var weapon = item();
      when(occupancy.getItem()).thenReturn(weapon);
      broken.when(() -> GearBrokenMarker.isBroken(weapon)).thenReturn(true);
      GearChargeService.tryApply(player, loc, hand);
      orbs.verify(() -> GearOrbService.begin(any(), any(), any(), any(), anyInt()), never());
    }
  }

  @Test
  void playerWithoutACharacterIsToldSoAndTheStationIsLeftAlone() {
    var characters = new CharacterTestSupport();
    var player = server.addPlayer();
    var loc = player.getLocation();
    var hand = item();
    var charge = charge(1);
    charge.setCap("fire", 5);
    charge.setFill("fire", 5);
    try (var charges = mockStatic(Charge.class);
        var stations = mockStatic(GearStationStore.class, CALLS_REAL_METHODS);
        var effects = mockStatic(net.tfminecraft.magic.gear.ownership.GearEffects.class);
        var orbs = mockStatic(GearOrbService.class)) {
      charges.when(() -> Charge.fromItem(hand)).thenReturn(charge);
      GearChargeService.tryApply(player, loc, hand);
      // Whether messages.yml is loaded depends on test order, so compare against the live lookup.
      assertEquals(org.bukkit.ChatColor.stripColor(Messages.get("gear.charge.no_character")),
          org.bukkit.ChatColor.stripColor(player.nextMessage()));
      stations.verify(() -> GearStationStore.get(any()), never());
      orbs.verifyNoInteractions();
    } finally {
      characters.unregister();
    }
  }

  @Test
  void successfulRunsConsumeOnlyAfterBeginAndSnapshotPositiveFill() {
    var p = server.addPlayer();
    var loc = p.getLocation();
    var hand = item();
    hand.setAmount(2);
    p.getInventory().setItemInMainHand(hand);
    var charge = charge(2);
    charge.setCap("fire", 5);
    charge.setCap("water", 5);
    charge.setFill("fire", 5);
    var occupancy = mock(GearStationStore.Occupancy.class);
    try (var charges = mockStatic(Charge.class);
        var stations = mockStatic(GearStationStore.class, CALLS_REAL_METHODS);
        var effects = mockStatic(net.tfminecraft.magic.gear.ownership.GearEffects.class);
        var orbs = mockStatic(GearOrbService.class)) {
      charges.when(() -> Charge.fromItem(hand)).thenReturn(charge);
      stations.when(() -> GearStationStore.get(loc)).thenReturn(occupancy);
      GearChargeService.tryApply(p, loc, hand);
      assertEquals(2, hand.getAmount());
      assertEquals(2, p.getInventory().getItemInMainHand().getAmount());
      effects.when(() -> net.tfminecraft.magic.gear.ownership.GearEffects.charge(eq(p), any(), eq(loc), eq(hand), eq(Map.of("fire",5d)), eq("fire"), eq(2))).thenAnswer(call -> {
        var current = p.getInventory().getItemInMainHand(); if(current.getAmount()<=1)p.getInventory().setItemInMainHand(null); else current.setAmount(current.getAmount()-1); return true;
      });
      GearChargeService.tryApply(p, loc, hand);
      assertEquals(1, p.getInventory().getItemInMainHand().getAmount());
      GearChargeService.tryApply(p, loc, hand);
      assertTrue(p.getInventory().getItemInMainHand().getType().isAir());
      effects.verify(() -> net.tfminecraft.magic.gear.ownership.GearEffects.charge(eq(p), any(), eq(loc), eq(hand), eq(Map.of("fire",5d)), eq("fire"), eq(2)), times(3));
    }
  }

  @Test
  void confirmationIsPlayerStationAndExpirySpecific() {
    ElementRegistry.register(DomainTest.element("fire"));
    TierBands.register("default", 1, 5);
    TierBands.register("default", 2, 10);
    var p = server.addPlayer();
    var loc = p.getLocation();
    var hand = item();
    var charge = charge(1);
    charge.setCap("fire", 10);
    charge.setFill("fire", 10);
    var occupancy = mock(GearStationStore.Occupancy.class);
    when(occupancy.getItem()).thenReturn(item());
    when(occupancy.generation()).thenReturn(UUID.randomUUID());
    var sessions = Magic.plugin.getResonanceGuiManager().getSessionManager();
    when(sessions.get(p)).thenReturn(null);
    try (var charges = mockStatic(Charge.class);
        var stations = mockStatic(GearStationStore.class, CALLS_REAL_METHODS);
        var effects = mockStatic(net.tfminecraft.magic.gear.ownership.GearEffects.class);
        var orbs = mockStatic(GearOrbService.class)) {
      charges.when(() -> Charge.fromItem(hand)).thenReturn(charge);
      stations.when(() -> GearStationStore.get(loc)).thenReturn(occupancy);
      stations.when(() -> GearStationStore.key(loc)).thenReturn("first");
      GearCache.confirmMillis = -1;
      GearChargeService.tryApply(p, loc, hand);
      GearCache.confirmMillis = 100000;
      GearChargeService.tryApply(p, loc, hand);
      stations.when(() -> GearStationStore.key(loc)).thenReturn("second");
      GearChargeService.tryApply(p, loc, hand);
      orbs.verify(() -> GearOrbService.begin(any(), any(), any(), any(), anyInt()), never());
      GearChargeService.tryApply(p, loc, hand);
      effects.verify(() -> net.tfminecraft.magic.gear.ownership.GearEffects.charge(eq(p), any(), eq(loc), eq(hand), eq(Map.of("fire",10d)), eq("fire"), eq(1)));
    }
  }

  @Test
  void resonanceWarningsSkipUnchargedAndAdequatelyMatchedElements() throws Exception {
    var p = server.addPlayer();
    var legacy = item();
    tag(legacy, ChargeKeys.chargeTier(), org.bukkit.persistence.PersistentDataType.INTEGER, 1);
    tag(
        legacy,
        net.tfminecraft.magic.artifact.ArtifactKeys.auraData(),
        org.bukkit.persistence.PersistentDataType.STRING,
        "tiny:1:0,high:0:10,low:10:10,matched:10:10");
    var charge = Charge.fromItem(legacy);
    for (String id : List.of("none", "tiny", "high", "low", "matched"))
      ElementRegistry.register(DomainTest.element(id));
    TierBands.register("default", 1, 5);
    TierBands.register("default", 2, 10);
    var session = mock(ResonanceSession.class);
    when(session.getResonance("low")).thenReturn(5d);
    when(session.getResonance("matched")).thenReturn(10d);
    when(Magic.plugin.getResonanceGuiManager().getSessionManager().get(p)).thenReturn(session);
    var lines =
        (List<?>)
            invoke(
                GearChargeService.class,
                "overResonance",
                new Class[] {Player.class, Charge.class},
                p,
                charge);
    assertEquals(2, lines.size());
  }
}
