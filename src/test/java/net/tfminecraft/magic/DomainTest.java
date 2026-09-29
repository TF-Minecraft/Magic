package net.tfminecraft.magic;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.*;
import net.tfminecraft.magic.meditation.*;
import net.tfminecraft.magic.model.*;
import net.tfminecraft.magic.modifier.*;
import net.tfminecraft.magic.profile.*;
import net.tfminecraft.magic.registry.*;
import net.tfminecraft.magic.service.*;
import net.tfminecraft.magic.session.*;
import net.tfminecraft.magic.util.*;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.*;

class DomainTest {
  @BeforeEach
  void reset() {
    ElementRegistry.clear();
    SkillElementRegistry.clear();
    Cache.defaultCastMode = "flow";
    Cache.defaultEquilibrium = 0;
    Cache.secondsPerHour = 3600;
    Cache.tickIntervalTicks = 20;
    Cache.defaultResonanceDecayPerHour = -.02;
    GuiCache.equilibriumMin = -100;
    GuiCache.equilibriumMax = 100;
  }

  @AfterEach
  void clear() {
    reset();
    Cache.setCastTriggers(null);
    GuiCache.resetColors(null);
  }

  static ElementDef element(String id) {
    return new ElementDef(id, new YamlConfiguration());
  }

  @Test
  void numbersRoundAndFormatWithoutLocaleOrNonfiniteLeakage() {
    assertEquals(1.24, MagicNumbers.round(1.235, 2));
    assertEquals(-1.24, MagicNumbers.round(-1.235, 2));
    for (double invalid :
        new double[] {Double.NaN, Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY})
      assertEquals(0, MagicNumbers.round(invalid, 2));
    assertThrows(IllegalArgumentException.class, () -> MagicNumbers.round(1, -1));
    assertEquals(3, MagicNumbers.clamp(10, 0, 3));
    assertEquals(0, MagicNumbers.clamp(-1, 0, 3));
    assertEquals("12.3", MagicNumbers.format(12.30));
    assertEquals("1.235", MagicNumbers.format(1.2345, 3));
    assertEquals("-0.02/h", MagicNumbers.formatRatePerHour(-.02));
    assertEquals("0", MagicNumbers.format(-.001));
  }

  @Test
  void meditationNeverPassesCircleOrElementCeilings() {
    assertEquals(0, MeditationCeiling.allowed(10, 5, 100, 3));
    assertEquals(2, MeditationCeiling.allowed(8, 10, 100, 5));
    assertEquals(1, MeditationCeiling.allowed(8, 100, 9, 5));
    assertEquals(3, MeditationCeiling.allowed(0, 100, 100, 3));
    assertEquals(0, MeditationCeiling.allowed(0, 100, 100, 0));
    assertEquals(0, MeditationCeiling.allowed(0, 0, 100, 1));
    assertEquals(0, MeditationCeiling.allowed(0, 100, -1, 1));
    assertEquals(0, MeditationCeiling.allowed(9.999, 10, 100, 1));
  }

  @Test
  void sitYieldCopiesInputsAndTracksIndividualArtifactExhaustion() {
    Map<String, Double> caps = new HashMap<>(Map.of("a", 2., "b", 0.));
    var yield = new MeditationSitYield(caps, Map.of("a", 0, "b", 3));
    caps.put("a", 20.);
    assertEquals(2, yield.sessionCap("a"));
    assertEquals(0, yield.sessionCap(null));
    assertEquals(0, yield.sessionCap("unknown"));
    assertEquals(1, yield.users(null));
    assertEquals(1, yield.users("a"));
    assertEquals(3, yield.users("b"));
    assertTrue(yield.hasAnyCap());
    assertFalse(yield.exhausted(null));
    assertFalse(yield.exhausted(Map.of("a", 1.)));
    assertTrue(yield.exhausted(Map.of("a", 1.999)));
    assertThrows(UnsupportedOperationException.class, () -> yield.sessionCaps().put("c", 1.));
    assertFalse(MeditationSitYield.empty().hasAnyCap());
    assertTrue(new MeditationSitYield(null, null).exhausted(null));
    assertFalse(new MeditationSitYield(Map.of("a", -.1), null).hasAnyCap());
  }

  @Test
  void modifiersInterpolateMultiplyAndClamp() {
    var a = new ModifierTriple(.2, -.2, .3);
    var b = new ModifierTriple(.4, .2, -.1);
    assertSame(a, a.lerp(null, .5));
    assertSame(a, a.lerp(b, 0));
    assertSame(b, a.lerp(b, 1));
    assertEquals(.3, a.lerp(b, .5).mana(), 1e-10);
    assertEquals(0, a.lerp(b, .5).damage(), 1e-10);
    assertEquals(.1, a.lerp(b, .5).cooldown(), 1e-10);
    assertEquals(.68, a.combine(b).mana(), 1e-10);
    assertEquals(-.04, a.combine(b).damage(), 1e-10);
    assertEquals(.17, a.combine(b).cooldown(), 1e-10);
    var clamped = new ModifierTriple(4, -4, -4).clamp();
    assertEquals(.9, clamped.mana());
    assertEquals(-.9, clamped.damage());
    assertEquals(-.5, clamped.cooldown());
    assertEquals(.2, a.combine(null).mana());
    assertTrue(ModifierTriple.ZERO.isZero());
    assertFalse(a.isZero());
    assertFalse(new ModifierTriple(0, 1, 0).isZero());
    assertFalse(new ModifierTriple(0, 0, 1).isZero());
    assertSame(ModifierTriple.ZERO, ModifierTriple.from(null));
    var config = new YamlConfiguration();
    config.set("mana", .5);
    assertEquals(.5, ModifierTriple.from(config).mana());
  }

  @Test
  void curvesUseEndpointsExactKeysAndInterpolation() {
    assertTrue(new KeyframeCurve(null).sample(100).isZero());
    assertEquals(1, new KeyframeCurve(new TreeMap<>()).size());
    var curve = KeyframeCurve.defaultSurge();
    assertEquals(4, curve.size());
    assertTrue(curve.sample(-1).isZero());
    assertEquals(.1, curve.sample(15).damage());
    assertEquals(.125, curve.sample(37.5).damage(), 1e-10);
    assertEquals(.1, curve.sample(1000).damage());
    assertEquals(.25, KeyframeCurve.defaultTranquility().sample(100).damage());
    assertEquals(0, KeyframeCurve.defaultResonance().sample(50).damage(), 1e-10);
    assertTrue(KeyframeCurve.fromSection(null).sample(1).isZero());
    var config = new YamlConfiguration();
    config.set("not-number.mana", 1);
    config.set("30", "not-section");
    assertEquals(1, KeyframeCurve.fromSection(config).size());
    config.set("0.mana", .2);
    config.set("100.mana", -.2);
    assertEquals(0, KeyframeCurve.fromSection(config).sample(50).mana(), 1e-10);
  }

  @Test
  void elementConfigurationSupportsColorsPermissionsAndCurves() {
    var defaults = element("fire");
    assertEquals("fire", defaults.getName());
    assertEquals("v.BARRIER", defaults.getIcon());
    assertEquals("#ffffff", defaults.getColor());
    assertNull(defaults.getPermission());
    assertTrue(defaults.isUnlocked(null));
    assertEquals(100, defaults.getMaxResonance());
    assertEquals(-.02, defaults.getDecayPerHour());
    assertEquals(0, defaults.getAuraDecayPerHour());
    assertEquals(-1, defaults.getSlot());
    assertEquals(2, defaults.getResonanceCurve().size());
    var cfg = new YamlConfiguration();
    cfg.set("name", "Flame");
    cfg.set("icon", "v.BLAZE_ROD");
    cfg.set("color", List.of("FF0000", "#00FF00", "bad"));
    cfg.set("permission", " magic.fire ");
    cfg.set("max_resonance", 0);
    cfg.set("decay_per_hour", 2);
    cfg.set("aura_decay_per_hour", 3);
    cfg.set("slot", 10);
    cfg.set("resonance.0.damage", 0);
    cfg.set("resonance.100.damage", 1);
    var fire = new ElementDef("fire", cfg);
    assertEquals(List.of("#ff0000", "#00ff00"), fire.getColors());
    assertEquals("Flame", fire.getName());
    assertEquals("v.BLAZE_ROD", fire.getIcon());
    assertEquals(1, fire.getMaxResonance());
    assertEquals(2, fire.getDecayPerHour());
    assertEquals(3, fire.getAuraDecayPerHour());
    assertEquals(10, fire.getSlot());
    assertFalse(fire.isUnlocked(null));
    Player p = mock(Player.class);
    assertFalse(fire.isUnlocked(p));
    when(p.hasPermission("magic.fire")).thenReturn(true);
    assertTrue(fire.isUnlocked(p));
    assertEquals(.5, fire.getResonanceCurve().sample(50).damage());
    cfg.set("color", "broken");
    cfg.set("resonance", null);
    cfg.set("resonance.10.damage", 1);
    assertEquals("#ffffff", new ElementDef("x", cfg).getColor());
    assertEquals(2, new ElementDef("x", cfg).getResonanceCurve().size());
  }

  @Test
  void registryRejectsMissingIdsAndReturnsImmutableSnapshots() {
    ElementRegistry.register(null);
    ElementRegistry.register(element(null));
    ElementRegistry.register(element(" "));
    assertEquals(0, ElementRegistry.size());
    ElementRegistry.register(element("fire"));
    var old = ElementRegistry.getAll();
    var cfg = new YamlConfiguration();
    cfg.set("slot", 12);
    ElementRegistry.register(new ElementDef("water", cfg));
    assertEquals(1, old.size());
    assertEquals(2, ElementRegistry.size());
    assertTrue(ElementRegistry.contains("fire"));
    assertEquals(List.of("fire", "water"), ElementRegistry.getAllIds());
    assertEquals(List.of(12), ElementRegistry.getOccupiedSlots());
    assertThrows(UnsupportedOperationException.class, () -> ElementRegistry.getAll().clear());
  }

  @Test
  void skillRegistryNormalizesAndBoundsTiers() {
    SkillElementRegistry.register(null, "a");
    SkillElementRegistry.register(" ", "a");
    SkillElementRegistry.register("a", null);
    SkillElementRegistry.register("a", " ");
    assertEquals(0, SkillElementRegistry.size());
    SkillElementRegistry.register(" FIRE_BALL ", " FIRE ", 9);
    SkillElementRegistry.register("wave", "water");
    assertEquals("fire", SkillElementRegistry.elementOf(" Fire_Ball "));
    assertEquals(4, SkillElementRegistry.tierOf("fire_ball"));
    assertEquals(1, SkillElementRegistry.tierOf("wave"));
    assertNull(SkillElementRegistry.elementOf(null));
    assertNull(SkillElementRegistry.elementOf(" "));
    assertNull(SkillElementRegistry.elementOf("unknown"));
    assertEquals(1, SkillElementRegistry.tierOf("unknown"));
    assertEquals(1, SkillElementRegistry.clampTier(-1));
    assertEquals(2, SkillElementRegistry.clampTier(2));
    assertEquals(Map.of("fire_ball", "fire", "wave", "water"), SkillElementRegistry.bindings());
    assertThrows(
        UnsupportedOperationException.class, () -> SkillElementRegistry.bindings().clear());
  }

  @Test
  void sessionClampsRoundsAndInvalidatesOnlyChangedValues() {
    ElementRegistry.register(element("fire"));
    var s = new ResonanceSession();
    assertEquals("flow", s.getCastModeId());
    assertEquals(0, s.modifierRevision());
    s.setEquilibrium(0);
    assertEquals(0, s.modifierRevision());
    s.setEquilibrium(1000);
    assertEquals(100, s.getEquilibrium());
    s.setEquilibrium(-1000);
    assertEquals(-100, s.getEquilibrium());
    s.setResonance("unknown", 1);
    assertEquals(0, s.getResonance("unknown"));
    s.setResonance("fire", 12.345);
    assertEquals(12.35, s.getResonance("fire"));
    long revision = s.modifierRevision();
    s.setResonance("fire", 12.35);
    s.addResonance("fire", 0);
    assertEquals(revision, s.modifierRevision());
    s.addResonance("fire", 1000);
    assertEquals(100, s.getResonance("fire"));
    assertEquals(Map.of("fire", 100.), s.copyResonance());
    s.setResonance("fire", -1);
    assertEquals(0, s.getResonance("fire"));
    revision = s.modifierRevision();
    s.setResonance("fire", 0);
    assertEquals(revision, s.modifierRevision());
    for (String mode : new String[] {null, " ", "unknown", "SURGE"}) {
      s.setCastModeId(mode);
      assertEquals("surge", s.getCastModeId());
    }
    s.setCastModeId("FLOW");
    assertEquals("flow", s.getCastModeId());
    Map<String, Double> next = new HashMap<>();
    next.put(null, 1.);
    next.put("water", null);
    next.put("fire", 5.);
    s.setResonanceMap(next);
    assertEquals(Map.of("fire", 5.), s.copyResonance());
    s.setResonanceMap(null);
    assertTrue(s.copyResonance().isEmpty());
    s.setResonanceMap(Map.of());
  }

  @Test
  void profileRoundTripsSessionAndPrunesInvalidAmounts() {
    ElementRegistry.register(element("fire"));
    UUID owner = UUID.randomUUID();
    var profile = MagicProfile.fromDefaults("character", owner);
    assertEquals("character", profile.getCharacterId());
    assertEquals(owner.toString(), profile.getOwnerUuid());
    assertEquals("flow", profile.getCastMode());
    assertEquals(0, profile.getEquilibrium());
    profile.setCharacterId("second");
    profile.setOwnerUuid("owner");
    profile.setCastMode("surge");
    profile.setEquilibrium(-3.5);
    assertEquals("second", profile.getCharacterId());
    assertEquals("owner", profile.getOwnerUuid());
    Map<String, Double> amounts = new HashMap<>();
    amounts.put(null, 4.);
    amounts.put("empty", null);
    amounts.put("low", 0.);
    amounts.put("tiny", .0002);
    amounts.put("fire", 1000.);
    profile.setResonance(amounts);
    assertEquals(2, profile.copyResonance().size());
    profile.pruneEmptyResonance();
    assertEquals(100, profile.getResonance().get("fire"));
    var session = new ResonanceSession();
    profile.applyTo(session);
    assertEquals("surge", session.getCastModeId());
    assertEquals(-3.5, session.getEquilibrium());
    assertEquals(100, session.getResonance("fire"));
    var saved = MagicProfile.fromSession("saved", null, session);
    assertNull(saved.getOwnerUuid());
    assertEquals(Map.of("fire", 100.), saved.copyResonance());
    profile.setResonance(null);
    assertTrue(profile.getResonance().isEmpty());
    assertNull(MagicProfile.fromDefaults("x", null).getOwnerUuid());
  }

  @Test
  void runtimeTickUnitsAndTriggerNamesAreStable() {
    assertEquals(1, Cache.tickIntervalSeconds());
    assertEquals(1. / 3600, Cache.tickHours());
    Cache.tickIntervalTicks = 0;
    assertEquals(.05, Cache.tickIntervalSeconds());
    Cache.secondsPerHour = 0;
    assertEquals(.05 / 3600, Cache.tickHours());
    Cache.secondsPerHour = 1;
    assertEquals(.05, Cache.tickHours());
    Cache.setCastTriggers(Arrays.asList(null, " ", " api ", "API", "cast"));
    assertEquals(Set.of("API", "CAST"), Cache.castTriggers);
    assertThrows(UnsupportedOperationException.class, () -> Cache.castTriggers.clear());
    Cache.setCastTriggers(null);
    assertTrue(Cache.castTriggers.isEmpty());
  }

  @Test
  void guiColorsAndModesKeepImmutableCopies() {
    GuiCache.resetColors(Map.of());
    assertEquals("fallback", GuiCache.color("x", "fallback"));
    var input = new HashMap<>(Map.of("x", "#fff", "empty", " "));
    GuiCache.resetColors(input);
    input.clear();
    assertEquals("#fff", GuiCache.color("x", ""));
    assertEquals("fallback", GuiCache.color("empty", "fallback"));
    var mode = new CastModeDef(null, null, null, null);
    assertEquals("", mode.getId());
    assertEquals("v.BARRIER", mode.getIcon());
    assertNull(mode.getName());
    assertEquals(List.of(), mode.getLore());
    mode = new CastModeDef("flow", "icon", "name", List.of("lore"));
    assertEquals("flow", mode.getId());
    assertEquals("icon", mode.getIcon());
    assertEquals("name", mode.getName());
    assertEquals(List.of("lore"), mode.getLore());
  }

  @Test
  void equilibriumRatesRespectSignAndDepth() {
    assertEquals(0, EquilibriumRates.corruptionDriftPerHour(0));
    assertEquals(0, EquilibriumRates.tranquilityDecayPerHour(-1));
    assertEquals(.05 * 1.625, EquilibriumRates.corruptionDriftPerHour(-50));
    assertEquals(.03, EquilibriumRates.tranquilityDecayPerHour(50));
    EquilibriumService.tickOnlineSessions(null);
    ResonanceService.tickOnlineSessions(null);
  }
}
