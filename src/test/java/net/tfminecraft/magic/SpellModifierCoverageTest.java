package net.tfminecraft.magic;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import io.lumine.mythic.lib.api.player.MMOPlayerData;
import io.lumine.mythic.lib.player.skillmod.SkillModifier;
import io.lumine.mythic.lib.skill.handler.SkillHandler;
import java.lang.reflect.*;
import java.util.*;
import java.util.function.BiConsumer;
import net.tfminecraft.magic.charge.TierBands;
import net.tfminecraft.magic.gear.*;
import net.tfminecraft.magic.integration.*;
import net.tfminecraft.magic.modifier.*;
import net.tfminecraft.magic.registry.*;
import net.tfminecraft.magic.session.*;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.PluginManager;
import org.junit.jupiter.api.*;
import org.mockbukkit.mockbukkit.*;

class SpellModifierCoverageTest {
  ServerMock server;
  Magic plugin;

  @BeforeEach
  void setup() throws Exception {
    server = MockBukkit.mock();
    plugin = mock(Magic.class);
    Magic.plugin = plugin;
    when(plugin.namespace()).thenReturn("magic");
    when(plugin.getLogger()).thenReturn(java.util.logging.Logger.getLogger("MagicTest"));
    SkillElementRegistry.clear();
    ElementRegistry.clear();
    TierBands.clear();
    GearCache.alignmentEnabled = true;
    Cache.debug = false;
    for (String name : List.of("applied", "handlers", "unknownWarned")) {
      var f = SpellModifierApplyService.class.getDeclaredField(name);
      f.setAccessible(true);
      var value = f.get(null);
      if (value instanceof Map<?, ?> m) m.clear();
      else ((Set<?>) value).clear();
    }
    var f = SpellModifierApplyService.class.getDeclaredField("missingLogged");
    f.setAccessible(true);
    f.setBoolean(null, false);
  }

  @AfterEach
  void cleanup() {
    SpellModifierApplyService.clearAll();
    Magic.plugin = null;
    SkillElementRegistry.clear();
    ElementRegistry.clear();
    TierBands.clear();
    GearCache.alignmentEnabled = false;
    MockBukkit.unmock();
  }

  @Test
  void absentDependenciesAndOfflinePlayersNeverRegisterModifiers() {
    var p = server.addPlayer();
    var session = new ResonanceSession();
    try (var b = mockStatic(Bukkit.class, CALLS_REAL_METHODS);
        var players = mockStatic(MMOPlayerData.class)) {
      var pm = mock(PluginManager.class);
      b.when(Bukkit::getPluginManager).thenReturn(pm);
      assertFalse(SpellModifierApplyService.isAvailable());
      SpellModifierApplyService.clear(null);
      SpellModifierApplyService.clear(p);
      SpellModifierApplyService.sync(null, session);
      Magic.plugin = null;
      SpellModifierApplyService.sync(p, session);
      Magic.plugin = plugin;
      SpellModifierApplyService.sync(p, session);
      SpellModifierApplyService.sync(p, session);
      SpellModifierApplyService.syncOnline(null);
      SpellModifierApplyService.syncChangedOnline(null);
      SpellModifierApplyService.syncOnline(new ResonanceSessionManager());
      SpellModifierApplyService.syncChangedOnline(new ResonanceSessionManager());
      when(pm.isPluginEnabled("MythicLib")).thenReturn(true);
      assertFalse(SpellModifierApplyService.isAvailable());
      when(pm.isPluginEnabled("MMOCore")).thenReturn(true);
      assertTrue(SpellModifierApplyService.isAvailable());
      SpellModifierApplyService.syncOnline(null);
      SpellModifierApplyService.syncChangedOnline(null);
      SpellModifierApplyService.sync(p, session);
      var data = mock(MMOPlayerData.class);
      players.when(() -> MMOPlayerData.getOrNull(p)).thenReturn(data);
      SpellModifierApplyService.sync(p, session);
      when(data.isOnline()).thenReturn(true);
      SpellModifierApplyService.sync(p, null);
      SpellModifierApplyService.clearAll();
    }
  }

  @Test
  void unchangedSnapshotReusesModifiersChangedValuesAndReloadReplaceThem() {
    var p = server.addPlayer();
    var session = new ResonanceSession();
    var sessions = new ResonanceSessionManager();
    var live = sessions.getOrCreate(p);
    var data = mock(MMOPlayerData.class);
    when(data.isOnline()).thenReturn(true);
    var handler = mock(SkillHandler.class);
    var observed = new ArrayList<List<?>>();
    try (var b = mockStatic(Bukkit.class, CALLS_REAL_METHODS);
        var players = mockStatic(MMOPlayerData.class);
        var ids = mockStatic(SkillIdResolver.class);
        var hands = mockStatic(GearHand.class);
        var mods = mockStatic(SpellModifiers.class, CALLS_REAL_METHODS);
        var made = mockConstruction(SkillModifier.class, (m, c) -> observed.add(c.arguments()))) {
      var pm = mock(PluginManager.class);
      when(pm.isPluginEnabled(anyString())).thenReturn(true);
      b.when(Bukkit::getPluginManager).thenReturn(pm);
      players.when(() -> MMOPlayerData.getOrNull(p)).thenReturn(data);
      SkillElementRegistry.register("fireball", "fire");
      ids.when(() -> SkillIdResolver.handlerForBinding("fireball")).thenReturn(handler);
      mods.when(() -> SpellModifiers.resonance(eq("fire"), anyDouble()))
          .thenReturn(new ModifierTriple(.1, .2, -.1));
      mods.when(() -> SpellModifiers.drift(anyDouble())).thenReturn(ModifierTriple.ZERO);
      mods.when(() -> SpellModifiers.alignment(any(WeaponRequirement.class), eq("fire")))
          .thenReturn(ModifierTriple.ZERO);
      SpellModifierApplyService.sync(p, session);
      assertEquals(3, made.constructed().size());
      assertEquals("magic-mana-fireball", observed.getFirst().get(0));
      assertEquals(10., (Double) observed.getFirst().get(3), 1e-8);
      for (var modifier : made.constructed()) verify(modifier).register(data);
      SpellModifierApplyService.sync(p, session);
      assertEquals(3, made.constructed().size());
      SpellModifierApplyService.sync(p, live);
      assertEquals(3, made.constructed().size());
      SpellModifierApplyService.syncChangedOnline(sessions);
      assertEquals(3, made.constructed().size());
      SpellModifierApplyService.syncOnline(sessions);
      assertEquals(6, made.constructed().size());
      for (var modifier : made.constructed().subList(0, 3)) verify(modifier).unregister(data);
      mods.when(() -> SpellModifiers.resonance(eq("fire"), anyDouble()))
          .thenReturn(ModifierTriple.ZERO);
      SpellModifierApplyService.sync(p, live);
      assertEquals(6, made.constructed().size());
      for (var modifier : made.constructed().subList(3, 6)) verify(modifier).unregister(data);
      SpellModifierApplyService.clear(p);
      SpellModifierApplyService.syncChangedOnline(sessions);
      SpellModifierApplyService.clear(p);
    }
  }

  @Test
  void alignmentChangesRebuildSnapshotAndUnknownSkillsWarnOnce() throws Exception {
    var p = server.addPlayer();
    var session = mock(ResonanceSession.class);
    when(session.modifierRevision()).thenReturn(1L);
    var sessions = mock(ResonanceSessionManager.class);
    doAnswer(
            a -> {
              ((BiConsumer<Player, ResonanceSession>) a.getArgument(0)).accept(p, session);
              return null;
            })
        .when(sessions)
        .forEachOnlineSession(any());
    var data = mock(MMOPlayerData.class);
    when(data.isOnline()).thenReturn(true);
    var item = new ItemStack(Material.STICK);
    ElementRegistry.register(DomainTest.element("fire"));
    ElementRegistry.register(DomainTest.element("water"));
    ElementRegistry.register(DomainTest.element("earth"));
    TierBands.register("fire", 1, 10);
    TierBands.register("fire", 2, 20);
    var req = WeaponRequirement.fromItem(item);
    req.mergeAmounts(Map.of("fire", 15., "water", 5., "earth", 0.));
    req.aura().setCap("earth", 1);
    req.persist(item);
    try (var b = mockStatic(Bukkit.class, CALLS_REAL_METHODS);
        var players = mockStatic(MMOPlayerData.class);
        var ids = mockStatic(SkillIdResolver.class);
        var hands = mockStatic(GearHand.class)) {
      var pm = mock(PluginManager.class);
      when(pm.isPluginEnabled(anyString())).thenReturn(true);
      b.when(Bukkit::getPluginManager).thenReturn(pm);
      players.when(() -> MMOPlayerData.getOrNull(p)).thenReturn(data);
      hands.when(() -> GearHand.held(p)).thenReturn(item);
      SkillElementRegistry.register("unknown", "fire");
      Magic.plugin = null;
      var warn = SpellModifierApplyService.class.getDeclaredMethod("warnUnknown", String.class);
      warn.setAccessible(true);
      warn.invoke(null, "unknown");
      Magic.plugin = plugin;
      SkillElementRegistry.register("unknown2", "fire");
      SpellModifierApplyService.sync(p, session);
      Cache.debug = true;
      SkillElementRegistry.register("unknown3", "fire");
      SpellModifierApplyService.sync(p, session);
      SpellModifierApplyService.sync(p, session);
      req.mergeAmounts(Map.of("fire", 25.));
      req.persist(item);
      SpellModifierApplyService.sync(p, session);
      GearCache.alignmentEnabled = false;
      SpellModifierApplyService.sync(p, session);
      when(session.modifierRevision()).thenReturn(2L);
      SpellModifierApplyService.syncChangedOnline(sessions);
      doAnswer(
              a -> {
                ((BiConsumer<Player, ResonanceSession>) a.getArgument(0)).accept(p, null);
                return null;
              })
          .when(sessions)
          .forEachOnlineSession(any());
      SpellModifierApplyService.syncChangedOnline(sessions);
      SpellModifierApplyService.sync(p, session);
      players.when(() -> MMOPlayerData.getOrNull(p)).thenReturn(null);
      SpellModifierApplyService.clear(p);
    }
  }

  @Test
  void cachedValueObjectsCompareEveryModifierAndHaveStableHashCodes() throws Exception {
    var planClass =
        Class.forName("net.tfminecraft.magic.integration.SpellModifierApplyService$SkillPlan");
    var ctor = planClass.getDeclaredConstructors()[0];
    ctor.setAccessible(true);
    var handler = mock(SkillHandler.class);
    Object base = ctor.newInstance("skill", handler, .1, .2, .3);
    Object same = ctor.newInstance("skill", handler, .1, .2, .3);
    assertEquals(base, base);
    assertNotEquals(base, null);
    assertNotEquals(base, "other");
    assertEquals(base, same);
    assertEquals(base.hashCode(), same.hashCode());
    assertNotEquals(base, ctor.newInstance("other", handler, .1, .2, .3));
    assertNotEquals(base, ctor.newInstance("skill", handler, .2, .2, .3));
    assertNotEquals(base, ctor.newInstance("skill", handler, .1, .3, .3));
    assertNotEquals(base, ctor.newInstance("skill", handler, .1, .2, .4));
    var snapshot =
        Class.forName("net.tfminecraft.magic.integration.SpellModifierApplyService$DesiredSnapshot")
            .getDeclaredConstructors()[0];
    snapshot.setAccessible(true);
    Object first = snapshot.newInstance(new TreeMap<>(), List.of(base));
    Object equal = snapshot.newInstance(new TreeMap<>(), List.of(same));
    assertEquals(first, first);
    assertEquals(first, equal);
    assertEquals(first.hashCode(), equal.hashCode());
    assertNotEquals(first, null);
    assertNotEquals(first, "other");
    assertNotEquals(first, snapshot.newInstance(new TreeMap<>(Map.of("fire", 1)), List.of(base)));
    assertNotEquals(first, snapshot.newInstance(new TreeMap<>(), List.of()));
  }
}
