package net.tfminecraft.magic;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import io.lumine.mythic.lib.api.event.skill.PlayerCastSkillEvent;
import io.lumine.mythic.lib.api.event.skill.SkillCastEvent;
import io.lumine.mythic.lib.skill.Skill;
import io.lumine.mythic.lib.skill.trigger.TriggerType;
import net.Indyuce.mmocore.skill.CastableSkill;
import net.tfminecraft.magic.gear.GearHand;
import net.tfminecraft.magic.integration.SkillIdResolver;
import net.tfminecraft.magic.listener.CastDriftListener;
import net.tfminecraft.magic.listener.ResonanceCastListener;
import net.tfminecraft.magic.registry.SkillElementRegistry;
import net.tfminecraft.magic.session.ResonanceSessionManager;
import org.junit.jupiter.api.*;
import org.mockbukkit.mockbukkit.*;

/** MMOCore class skills are cast from the skill bar, not from runes: Magic must not touch them. */
class ClassCastSkipTest {
  ServerMock server;

  @BeforeEach
  void setup() {
    server = MockBukkit.mock();
    Magic.plugin = mock(Magic.class, RETURNS_DEEP_STUBS);
    SkillElementRegistry.clear();
  }

  @AfterEach
  void cleanup() {
    Magic.plugin = null;
    SkillElementRegistry.clear();
    MockBukkit.unmock();
  }

  @Test
  void onlyMmocoreCastableSkillsCountAsClassCasts() {
    assertTrue(SkillIdResolver.isClassCast(mock(CastableSkill.class)));
    assertFalse(SkillIdResolver.isClassCast(mock(Skill.class)));
    assertFalse(SkillIdResolver.isClassCast(null));
  }

  @Test
  void resonanceGateIgnoresClassCastsEvenForBoundSpells() {
    var listener = new ResonanceCastListener();
    var event = mock(PlayerCastSkillEvent.class);
    var cast = mock(CastableSkill.class);
    when(event.getCast()).thenReturn(cast);
    when(event.getPlayer()).thenReturn(server.addPlayer());
    SkillElementRegistry.register("fire_shard", "oseni", 1);
    try (var ids = mockStatic(SkillIdResolver.class);
        var hand = mockStatic(GearHand.class)) {
      ids.when(() -> SkillIdResolver.isActiveCast(cast)).thenReturn(true);
      ids.when(() -> SkillIdResolver.isClassCast(cast)).thenReturn(true);
      ids.when(() -> SkillIdResolver.resolveSkillId(cast)).thenReturn("fire_shard");
      listener.onPlayerCastSkill(event);
      ids.verify(() -> SkillIdResolver.resolveSkillId(cast), never());
      hand.verifyNoInteractions();
    }
    verify(event, never()).setCancelled(anyBoolean());
    verify(cast, never()).whenCast(any());
  }

  @Test
  void castDriftIgnoresClassCasts() {
    var listener = new CastDriftListener();
    var event = mock(SkillCastEvent.class);
    var cast = mock(CastableSkill.class);
    when(cast.getTrigger()).thenReturn(TriggerType.CAST);
    when(cast.getParameter("mana")).thenReturn(40.);
    when(event.getCast()).thenReturn(cast);
    var player = server.addPlayer();
    when(event.getPlayer()).thenReturn(player);
    var sessions = new ResonanceSessionManager();
    when(Magic.plugin.getResonanceGuiManager().getSessionManager()).thenReturn(sessions);
    var session = sessions.getOrCreate(player);
    listener.onSkillCast(event);
    assertEquals(0., session.getEquilibrium());
    verify(Magic.plugin, never()).syncSpellModifiers(player, session);
  }
}
