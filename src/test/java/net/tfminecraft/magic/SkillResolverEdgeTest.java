package net.tfminecraft.magic;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import io.lumine.mythic.lib.skill.*;
import io.lumine.mythic.lib.skill.handler.SkillHandler;
import io.lumine.mythic.lib.skill.trigger.TriggerType;
import java.util.*;
import net.Indyuce.mmocore.MMOCore;
import net.Indyuce.mmocore.manager.SkillManager;
import net.Indyuce.mmocore.skill.*;
import net.tfminecraft.magic.integration.SkillIdResolver;
import org.junit.jupiter.api.*;

class SkillResolverEdgeTest {
  MMOCore previous;

  @BeforeEach
  void setup() {
    previous = MMOCore.plugin;
  }

  @AfterEach
  void cleanup() {
    MMOCore.plugin = previous;
    Cache.setCastTriggers(null);
  }

  @Test
  void customTriggerCanUseLowercaseIdentifierWithoutEnumName() {
    var skill = mock(Skill.class);
    var trigger = mock(TriggerType.class);
    when(skill.getTrigger()).thenReturn(trigger);
    Cache.setCastTriggers(List.of("custom"));
    assertFalse(SkillIdResolver.isActiveCast(skill));
    when(trigger.getLowerCaseId()).thenReturn("custom");
    assertTrue(SkillIdResolver.isActiveCast(skill));
    when(trigger.name()).thenReturn("different");
    assertTrue(SkillIdResolver.isActiveCast(skill));
    when(trigger.getLowerCaseId()).thenReturn("different");
    assertFalse(SkillIdResolver.isActiveCast(skill));
  }

  @Test
  void coreCastFallsBackToRegisteredNameWhenHandlerIdMissing() {
    var cast = mock(CastableSkill.class, RETURNS_DEEP_STUBS);
    when(cast.getHandler()).thenReturn(null);
    when(cast.getSkill().getSkill()).thenReturn(null);
    assertNull(SkillIdResolver.resolveSkillId(cast));
    var registered = mock(SkillHandler.class);
    when(cast.getSkill().getSkill()).thenReturn(registered);
    assertNull(SkillIdResolver.resolveSkillId(cast));
    when(registered.getName()).thenReturn(" ");
    assertNull(SkillIdResolver.resolveSkillId(cast));
    when(registered.getName()).thenReturn("Fireball");
    assertEquals("Fireball", SkillIdResolver.resolveSkillId(cast));
  }

  @Test
  void bindingLookupSupportsNamesAndHandlersCaseInsensitively() throws Exception {
    MMOCore.plugin = mock(MMOCore.class);
    var manager = mock(SkillManager.class);
    var field = MMOCore.class.getDeclaredField("skillManager");
    field.setAccessible(true);
    field.set(MMOCore.plugin, manager);
    var exact = mock(RegisteredSkill.class);
    var handler = mock(SkillHandler.class);
    when(manager.getSkill("exact")).thenReturn(exact);
    when(exact.getHandler()).thenReturn(handler);
    assertSame(handler, SkillIdResolver.handlerForBinding("exact"));
    when(exact.getHandler()).thenReturn(null);
    assertNull(SkillIdResolver.handlerForBinding("exact"));
    var named = mock(RegisteredSkill.class);
    when(named.getName()).thenReturn("Named");
    when(named.getHandler()).thenReturn(handler);
    var handlerMatch = mock(RegisteredSkill.class);
    when(handlerMatch.getName()).thenReturn("different");
    var second = mock(SkillHandler.class);
    when(handlerMatch.getHandler()).thenReturn(second);
    when(second.getLowerCaseId()).thenReturn("ability");
    when(manager.getAll()).thenReturn(Arrays.asList(null, exact, named, handlerMatch));
    assertSame(handler, SkillIdResolver.handlerForBinding("NAMED"));
    assertSame(second, SkillIdResolver.handlerForBinding("ABILITY"));
    assertNull(SkillIdResolver.handlerForBinding("unknown"));
  }
}
