package net.tfminecraft.magic;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import io.lumine.mythic.lib.api.event.skill.SkillCastEvent;
import io.lumine.mythic.lib.skill.Skill;
import io.lumine.mythic.lib.skill.SkillMetadata;
import io.lumine.mythic.lib.skill.handler.SkillHandler;
import io.lumine.mythic.lib.skill.trigger.TriggerType;
import java.util.*;
import net.tfminecraft.magic.artifact.config.ArtifactTypeRegistry;
import net.tfminecraft.magic.artifact.sacrifice.SacrificeRegistry;
import net.tfminecraft.magic.artifact.shrine.ShrineRegistry;
import net.tfminecraft.magic.charge.*;
import net.tfminecraft.magic.gear.*;
import net.tfminecraft.magic.integration.SkillIdResolver;
import net.tfminecraft.magic.listener.*;
import net.tfminecraft.magic.modifier.*;
import net.tfminecraft.magic.registry.ElementRegistry;
import net.tfminecraft.magic.session.*;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.junit.jupiter.api.*;
import org.mockbukkit.mockbukkit.*;

class SpellDomainTest {
  ServerMock server;

  @BeforeEach
  void setup() {
    server = MockBukkit.mock();
    Magic.plugin = mock(Magic.class, RETURNS_DEEP_STUBS);
    when(Magic.plugin.namespace()).thenReturn("magic");
    ElementRegistry.clear();
    ElementRegistry.register(DomainTest.element("fire"));
    ArtifactTypeRegistry.clear();
    ShrineRegistry.clear();
    SacrificeRegistry.clear();
    TierBands.clear();
    ChargeRegistry.clear();
    Cache.setCastTriggers(null);
    Cache.defaultEquilibrium = 0;
    Cache.defaultCastMode = "flow";
    Cache.castDriftManaDivisor = 1000;
    Cache.castDriftMin = .01;
    GuiCache.equilibriumMin = -100;
    GuiCache.equilibriumMax = 100;
    GearCache.alignmentEnabled = true;
    GearCache.clearAlignment();
  }

  @AfterEach
  void cleanup() {
    Magic.plugin = null;
    MockBukkit.unmock();
    ElementRegistry.clear();
    ArtifactTypeRegistry.clear();
    ShrineRegistry.clear();
    SacrificeRegistry.clear();
    TierBands.clear();
    ChargeRegistry.clear();
    Cache.setCastTriggers(null);
    GearCache.alignmentEnabled = false;
    GearCache.clearAlignment();
  }

  @Test
  void castTriggersAndHandlerIdsResolveWithoutAssumingMmoCoreCastType() {
    assertFalse(SkillIdResolver.isActiveCast(null));
    Skill skill = mock(Skill.class);
    assertFalse(SkillIdResolver.isActiveCast(skill));
    when(skill.getTrigger()).thenReturn(TriggerType.CAST);
    assertTrue(SkillIdResolver.isActiveCast(skill));
    when(skill.getTrigger()).thenReturn(TriggerType.API);
    assertTrue(SkillIdResolver.isActiveCast(skill));
    when(skill.getTrigger()).thenReturn(TriggerType.RIGHT_CLICK);
    assertFalse(SkillIdResolver.isActiveCast(skill));
    Cache.setCastTriggers(List.of("right_click"));
    assertTrue(SkillIdResolver.isActiveCast(skill));
    assertNull(SkillIdResolver.resolveSkillId(null));
    assertNull(SkillIdResolver.resolveSkillId(skill));
    var handler = mock(SkillHandler.class);
    when(skill.getHandler()).thenReturn(handler);
    assertNull(SkillIdResolver.resolveSkillId(skill));
    when(handler.getLowerCaseId()).thenReturn(" ");
    assertNull(SkillIdResolver.resolveSkillId(skill));
    when(handler.getLowerCaseId()).thenReturn("fireball");
    assertEquals("fireball", SkillIdResolver.resolveSkillId(skill));
    assertNull(SkillIdResolver.handlerForBinding(null));
    assertNull(SkillIdResolver.handlerForBinding(" "));
    assertNull(SkillIdResolver.handlerForBinding("fireball"));
  }

  @Test
  void spellGateRequiresBothWeaponAndCharacterTier() {
    assertEquals(SpellTierGate.Refuse.FOREIGN, SpellTierGate.refuse(false, 4, 4, 1));
    assertEquals(SpellTierGate.Refuse.WEAPON, SpellTierGate.refuse(true, 1, 4, 2));
    assertEquals(SpellTierGate.Refuse.SPELL, SpellTierGate.refuse(true, 4, 1, 2));
    assertEquals(SpellTierGate.Refuse.NONE, SpellTierGate.refuse(true, 4, 4, 2));
    assertEquals(SpellTierGate.Refuse.NONE, SpellTierGate.refuse(true, 1, 1, -1));
  }

  @Test
  void castManaDriftUsesActualSpentManaAndSavesOnlyLoadedSessions() {
    var listener = new CastDriftListener();
    var event = mock(SkillCastEvent.class);
    listener.onSkillCast(event);
    var skill = mock(Skill.class);
    when(skill.getTrigger()).thenReturn(TriggerType.CAST);
    when(event.getCast()).thenReturn(skill);
    listener.onSkillCast(event);
    var player = server.addPlayer();
    when(event.getPlayer()).thenReturn(player);
    listener.onSkillCast(event);
    when(skill.getParameter("mana")).thenReturn(40.);
    var sessions = new ResonanceSessionManager();
    when(Magic.plugin.getResonanceGuiManager().getSessionManager()).thenReturn(sessions);
    listener.onSkillCast(event);
    var session = sessions.getOrCreate(player);
    listener.onSkillCast(event);
    assertEquals(.04, session.getEquilibrium());
    verify(Magic.plugin).syncSpellModifiers(player, session);
    var metadata = mock(SkillMetadata.class);
    when(event.getMetadata()).thenReturn(metadata);
    when(metadata.getParameter("mana")).thenReturn(100.);
    session.setCastModeId("surge");
    listener.onSkillCast(event);
    assertEquals(-.06, session.getEquilibrium());
    when(metadata.getParameter("mana")).thenReturn(0.);
    when(skill.getParameter("mana")).thenReturn(.1);
    when(Magic.plugin.getProfileService()).thenReturn(null);
    listener.onSkillCast(event);
    assertEquals(-.07, session.getEquilibrium());
    var oldLeft = GuiCache.castModeLeft;
    var oldRight = GuiCache.castModeRight;
    try {
      GuiCache.castModeLeft = new net.tfminecraft.magic.model.CastModeDef("", null, null, null);
      session.setCastModeId(null);
      listener.onSkillCast(event);
      assertEquals(-.06, session.getEquilibrium(), 1e-9);
      GuiCache.castModeLeft =
          new net.tfminecraft.magic.model.CastModeDef("custom", null, null, null);
      GuiCache.castModeRight =
          new net.tfminecraft.magic.model.CastModeDef("surge", null, null, null);
      session.setCastModeId("surge");
      listener.onSkillCast(event);
      assertEquals(-.07, session.getEquilibrium(), 1e-9);
    } finally {
      GuiCache.castModeLeft = oldLeft;
      GuiCache.castModeRight = oldRight;
    }
    Magic.plugin = null;
    assertDoesNotThrow(() -> listener.onSkillCast(event));
  }

  @Test
  void weaponRequirementsMergeOnlyHigherPlayableAuraAndPersistSeparately() {
    var item = new ItemStack(Material.STICK);
    var req = WeaponRequirement.fromItem(item);
    assertFalse(req.hasStored());
    assertEquals(0, req.highestBand());
    req.mergeAmounts(null);
    req.mergeFrom(null);
    var amounts = new HashMap<String, Double>();
    amounts.put(null, 10.);
    amounts.put("missing", null);
    amounts.put("zero", 0.);
    amounts.put("fire", 10.);
    req.mergeAmounts(amounts);
    assertEquals(10, req.aura().getFill("fire"));
    req.mergeAmounts(Map.of("fire", 5.));
    assertEquals(10, req.aura().getFill("fire"));
    req.mergeAmounts(Map.of("fire", 20.));
    assertEquals(20, req.aura().getCap("fire"));
    assertTrue(req.hasStored());
    req.persist(item);
    assertEquals(20, WeaponRequirement.fromItem(item).aura().getFill("fire"));
    TierBands.register(null, 1, 5);
    TierBands.register(null, 2, 15);
    assertEquals(2, req.highestBand());
    assertEquals(0, WeaponRequirement.highestBand((Map<String, Double>) null));
    assertEquals(1, WeaponRequirement.highestBand(amounts));
    assertEquals(0, WeaponRequirement.highestBand((Charge) null));
    ChargeRegistry.register(new ChargeDef(1, "v.STONE", 30));
    var chargeItem = new ItemStack(Material.STONE);
    var meta = chargeItem.getItemMeta();
    meta.getPersistentDataContainer().set(ChargeKeys.chargeTier(), PersistentDataType.INTEGER, 1);
    chargeItem.setItemMeta(meta);
    var charge = Charge.fromItem(chargeItem);
    charge.setCap("fire", 30);
    charge.setFill("fire", 25);
    req.mergeFrom(charge);
    assertEquals(25, req.aura().getFill("fire"));
    assertEquals(2, WeaponRequirement.highestBand(charge));
  }

  @Test
  void spellModifiersCombineElementCurvesDriftAndAlignment() {
    assertTrue(SpellModifiers.resonance(null, 20).isZero());
    assertTrue(SpellModifiers.resonance(" ", 20).isZero());
    assertTrue(SpellModifiers.resonance("missing", 20).isZero());
    assertEquals(.2, SpellModifiers.resonanceAt100(" FIRE ").damage());
    assertEquals(.15, SpellModifiers.surgeAt60().damage());
    assertEquals(.25, SpellModifiers.tranquilityAt100().damage());
    assertTrue(SpellModifiers.drift(0).isZero());
    assertEquals(.15, SpellModifiers.drift(-60).damage());
    assertEquals(.25, SpellModifiers.drift(100).damage());
    assertTrue(SpellModifiers.combine(null, null).isZero());
    assertEquals(.1, SpellModifiers.combine(new ModifierTriple(0, .1, 0), null).damage(), 1e-10);
    assertTrue(SpellModifiers.alignment((ItemStack) null, "fire").isZero());
    assertTrue(SpellModifiers.alignment((WeaponRequirement) null, "fire").isZero());
    var req = WeaponRequirement.fromItem(new ItemStack(Material.STICK));
    assertTrue(SpellModifiers.alignment(req, "missing").isZero());
    assertTrue(SpellModifiers.alignment(req, "fire").isZero());
    req.mergeAmounts(Map.of("fire", 20.));
    TierBands.register(null, 1, 1);
    var triple = new ModifierTriple(0, .2, 0);
    GearCache.putAlignment(1, triple);
    assertSame(triple, SpellModifiers.alignment(req, "fire"));
    GearCache.alignmentEnabled = false;
    assertTrue(SpellModifiers.alignment(req, "fire").isZero());
  }
}
