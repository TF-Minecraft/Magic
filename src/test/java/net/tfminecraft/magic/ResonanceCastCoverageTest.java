package net.tfminecraft.magic;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import io.lumine.mythic.lib.api.event.skill.PlayerCastSkillEvent;
import io.lumine.mythic.lib.api.item.NBTItem;
import io.lumine.mythic.lib.skill.*;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import net.Indyuce.mmoitems.api.interaction.util.DurabilityItem;
import net.tfminecraft.magic.charge.TierBands;
import net.tfminecraft.magic.gear.*;
import net.tfminecraft.magic.integration.SkillIdResolver;
import net.tfminecraft.magic.listener.ResonanceCastListener;
import net.tfminecraft.magic.registry.*;
import net.tfminecraft.magic.session.*;
import org.bukkit.*;
import org.bukkit.inventory.*;
import org.junit.jupiter.api.*;
import org.mockbukkit.mockbukkit.*;

class ResonanceCastCoverageTest {
  ServerMock server;
  Magic plugin;
  ResonanceCastListener listener;

  @BeforeEach
  void setup() {
    server = MockBukkit.mock();
    plugin = mock(Magic.class, RETURNS_DEEP_STUBS);
    Magic.plugin = plugin;
    when(plugin.namespace()).thenReturn("magic");
    when(plugin.getName()).thenReturn("Magic");
    ElementRegistry.clear();
    SkillElementRegistry.clear();
    TierBands.clear();
    ResonanceCastListener.clearAll();
    Cache.refuseChatMillis = 1000;
    listener = new ResonanceCastListener();
  }

  @AfterEach
  void cleanup() {
    Magic.plugin = null;
    ElementRegistry.clear();
    SkillElementRegistry.clear();
    TierBands.clear();
    ResonanceCastListener.clearAll();
    MockBukkit.unmock();
  }

  @Test
  void irrelevantCastsMissingWeaponsAndBrokenWeaponsDoNotSpendAnything() {
    var event = mock(PlayerCastSkillEvent.class);
    Magic.plugin = null;
    listener.onPlayerCastSkill(event);
    Magic.plugin = plugin;
    var skill = mock(Skill.class);
    when(event.getCast()).thenReturn(skill);
    var p = server.addPlayer();
    try (var ids = mockStatic(SkillIdResolver.class);
        var hand = mockStatic(GearHand.class);
        var broken = mockStatic(GearBrokenMarker.class)) {
      listener.onPlayerCastSkill(event);
      ids.when(() -> SkillIdResolver.isActiveCast(skill)).thenReturn(true);
      ids.when(() -> SkillIdResolver.resolveSkillId(skill)).thenReturn("fireball");
      listener.onPlayerCastSkill(event);
      SkillElementRegistry.register("fireball", "fire", 1);
      listener.onPlayerCastSkill(event);
      when(event.getPlayer()).thenReturn(p);
      listener.onPlayerCastSkill(event);
      hand.when(() -> GearHand.heldSlot(p)).thenReturn(GearHand.HeldSlot.MAIN_HAND);
      listener.onPlayerCastSkill(event);
      var weapon = new ItemStack(Material.STICK);
      hand.when(() -> GearHand.held(p)).thenReturn(weapon);
      broken.when(() -> GearBrokenMarker.isBroken(weapon)).thenReturn(true);
      listener.onPlayerCastSkill(event);
      listener.onPlayerCastSkill(event);
      verify(event, times(2)).setCancelled(true);
      verify(skill, never()).whenCast(any());
    }
  }

  @Test
  void refusalChecksForeignElementWeaponBandAndCasterBand() {
    var event = mock(PlayerCastSkillEvent.class);
    var skill = mock(Skill.class);
    var p = server.addPlayer();
    var weapon = new ItemStack(Material.STICK);
    when(event.getCast()).thenReturn(skill);
    when(event.getPlayer()).thenReturn(p);
    var session = new ResonanceSession();
    when(plugin.getResonanceGuiManager().getSessionManager().get(p)).thenReturn(session);
    SkillElementRegistry.register("fireball", "fire", 2);
    TierBands.register(null, 1, 1);
    TierBands.register(null, 2, 10);
    try (var ids = mockStatic(SkillIdResolver.class);
        var hand = mockStatic(GearHand.class)) {
      ids.when(() -> SkillIdResolver.isActiveCast(skill)).thenReturn(true);
      ids.when(() -> SkillIdResolver.resolveSkillId(skill)).thenReturn("fireball");
      hand.when(() -> GearHand.heldSlot(p)).thenReturn(GearHand.HeldSlot.MAIN_HAND);
      hand.when(() -> GearHand.held(p)).thenReturn(weapon);
      listener.onPlayerCastSkill(event);
      verify(event).setCancelled(true);
      ElementRegistry.register(DomainTest.element("fire"));
      var requirement = WeaponRequirement.fromItem(weapon);
      requirement.mergeAmounts(Map.of("fire", 5.));
      requirement.persist(weapon);
      listener.onPlayerCastSkill(event);
      requirement.mergeAmounts(Map.of("fire", 20.));
      requirement.persist(weapon);
      listener.onPlayerCastSkill(event);
      verify(event, times(3)).setCancelled(true);
      when(plugin.getResonanceGuiManager().getSessionManager().get(p)).thenReturn(null);
      listener.onPlayerCastSkill(event);
      Cache.refuseChatMillis = 0;
      listener.onPlayerCastSkill(event);
    }
  }

  @Test
  void successfulAndFumbledCastsWearTheHeldSlotExactlyOnce() {
    var event = mock(PlayerCastSkillEvent.class);
    var skill = mock(Skill.class);
    var metadata = mock(SkillMetadata.class);
    var p = server.addPlayer();
    var weapon = new ItemStack(Material.STICK);
    when(event.getCast()).thenReturn(skill);
    when(event.getPlayer()).thenReturn(p);
    when(event.getMetadata()).thenReturn(metadata);
    SkillElementRegistry.register("fireball", "fire", 1);
    ElementRegistry.register(DomainTest.element("fire"));
    TierBands.register(null, 1, 1);
    var req = WeaponRequirement.fromItem(weapon);
    req.mergeAmounts(Map.of("fire", 20.));
    req.persist(weapon);
    var session = mock(ResonanceSession.class);
    when(session.getResonance("fire")).thenReturn(20.);
    when(plugin.getResonanceGuiManager().getSessionManager().get(p)).thenReturn(session);
    var nbt = mock(NBTItem.class);
    var durability = mock(DurabilityItem.class);
    when(durability.decreaseDurability(1)).thenReturn(durability);
    when(durability.toItem()).thenReturn(weapon);
    try (var ids = mockStatic(SkillIdResolver.class);
        var hand = mockStatic(GearHand.class);
        var nbtApi = mockStatic(NBTItem.class);
        var durabilityApi = mockStatic(DurabilityItem.class);
        var rifts = mockStatic(WeaponRift.class);
        var random = mockStatic(ThreadLocalRandom.class)) {
      ids.when(() -> SkillIdResolver.isActiveCast(skill)).thenReturn(true);
      ids.when(() -> SkillIdResolver.resolveSkillId(skill)).thenReturn("fireball");
      hand.when(() -> GearHand.heldSlot(p)).thenReturn(GearHand.HeldSlot.MAIN_HAND);
      hand.when(() -> GearHand.held(p)).thenReturn(weapon);
      nbtApi.when(() -> NBTItem.get(weapon)).thenReturn(nbt);
      listener.onPlayerCastSkill(event);
      verify(event, never()).setCancelled(true);
      when(nbt.getInteger("MMOITEMS_MAX_DURABILITY")).thenReturn(10);
      listener.onPlayerCastSkill(event);
      durabilityApi
          .when(() -> DurabilityItem.from(p, nbt, EquipmentSlot.HAND))
          .thenReturn(durability);
      listener.onPlayerCastSkill(event);
      hand.verify(() -> GearHand.setHeld(p, GearHand.HeldSlot.MAIN_HAND, weapon));
      hand.when(() -> GearHand.heldSlot(p)).thenReturn(GearHand.HeldSlot.OFF_HAND);
      durabilityApi
          .when(() -> DurabilityItem.from(p, nbt, EquipmentSlot.OFF_HAND))
          .thenReturn(durability);
      hand.when(() -> GearHand.staffCount(p)).thenReturn(2);
      listener.onPlayerCastSkill(event);
      verify(skill).whenCast(metadata);
      hand.verify(() -> GearHand.setHeld(p, GearHand.HeldSlot.OFF_HAND, weapon));
      hand.when(() -> GearHand.staffCount(p)).thenReturn(1);
      var rng = mock(ThreadLocalRandom.class);
      random.when(ThreadLocalRandom::current).thenReturn(rng);
      rifts.when(() -> WeaponRift.get(weapon)).thenReturn(50);
      when(rng.nextDouble()).thenReturn(.25, .75);
      listener.onPlayerCastSkill(event);
      listener.onPlayerCastSkill(event);
      verify(skill, times(2)).whenCast(metadata);
      verify(event, times(2)).setCancelled(true);
      verify(durability, times(4)).decreaseDurability(1);
    }
  }

  @Test
  void refusalRateLimiterExpiresOldEntriesButKeepsRecentOnes() throws Exception {
    var field = ResonanceCastListener.class.getDeclaredField("lastRefuseChat");
    field.setAccessible(true);
    var map = (Map<String, Long>) field.get(null);
    for (int i = 0; i < 513; i++) map.put("old" + i, 0L);
    map.put("recent", System.currentTimeMillis());
    var claim =
        ResonanceCastListener.class.getDeclaredMethod(
            "claimChat", org.bukkit.entity.Player.class, ItemStack.class, String.class);
    claim.setAccessible(true);
    var p = server.addPlayer();
    var weapon = new ItemStack(Material.STICK);
    assertEquals(true, claim.invoke(null, p, weapon, "fire"));
    assertEquals(2, map.size());
    assertEquals(false, claim.invoke(null, p, weapon, "fire"));
    map.replaceAll((key, time) -> 0L);
    assertEquals(true, claim.invoke(null, p, weapon, "fire"));
    Cache.refuseChatMillis = 0;
    assertEquals(true, claim.invoke(null, p, weapon, "fire"));
  }
}
