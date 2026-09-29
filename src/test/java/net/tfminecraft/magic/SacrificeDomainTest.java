package net.tfminecraft.magic;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.*;
import net.tfminecraft.magic.artifact.ArtifactKeys;
import net.tfminecraft.magic.artifact.sacrifice.*;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.junit.jupiter.api.*;
import org.mockbukkit.mockbukkit.MockBukkit;

class SacrificeDomainTest {
  @BeforeEach
  void setup() {
    MockBukkit.mock();
    Magic.plugin = mock(Magic.class);
    when(Magic.plugin.namespace()).thenReturn("magic");
    SacrificeRegistry.clear();
  }

  @AfterEach
  void cleanup() {
    SacrificeRegistry.clear();
    Magic.plugin = null;
    MockBukkit.unmock();
  }

  SacrificeElementDef element(String id, boolean enabled, List<String> words) {
    return new SacrificeElementDef(
        id,
        enabled,
        words,
        "Pain of {character}",
        "Screams of {character}",
        "Soul of {character}",
        20);
  }

  void globals(SacrificeWordMatch mode, boolean ignoreCase) {
    SacrificeRegistry.setGlobals(
        5,
        3,
        mode,
        ignoreCase,
        false,
        5,
        2,
        false,
        false,
        false,
        " altar ",
        " slot ",
        new SacrificeDaggerDef(" v.STONE "));
  }

  @Test
  void wordModesStripFormattingAndRespectCaseAndEnabledSchools() {
    assertNull(SacrificeWordMatcher.matchElementId("spell"));
    SacrificeRegistry.setEnabled(true);
    assertNull(SacrificeWordMatcher.matchElementId("spell"));
    SacrificeRegistry.register(element("disabled", false, List.of("spell")));
    SacrificeRegistry.register(element("fire", true, Arrays.asList(null, " ", "Spell")));
    assertNull(SacrificeWordMatcher.matchElementId(null));
    assertNull(SacrificeWordMatcher.matchElementId(" "));
    assertEquals("fire", SacrificeWordMatcher.matchElementId(" §cSPELL now "));
    assertNull(SacrificeWordMatcher.matchElementId("say Spell"));
    globals(SacrificeWordMatch.EXACT, false);
    assertEquals("fire", SacrificeWordMatcher.matchElementId("Spell"));
    assertNull(SacrificeWordMatcher.matchElementId("spell"));
    assertNull(SacrificeWordMatcher.matchElementId("Spell now"));
    globals(SacrificeWordMatch.CONTAINS, true);
    assertEquals("fire", SacrificeWordMatcher.matchElementId("say SPELL now"));
    assertEquals(SacrificeWordMatch.STARTS_WITH, SacrificeWordMatch.fromConfig(null));
    assertEquals(SacrificeWordMatch.STARTS_WITH, SacrificeWordMatch.fromConfig(" "));
    assertEquals(SacrificeWordMatch.STARTS_WITH, SacrificeWordMatch.fromConfig("starts-with"));
    assertEquals(SacrificeWordMatch.EXACT, SacrificeWordMatch.fromConfig(" EXACT "));
    assertEquals(SacrificeWordMatch.CONTAINS, SacrificeWordMatch.fromConfig("contains"));
  }

  @Test
  void tiersUseInclusiveLowerExclusiveUpperBoundAndNormalizeConsequences() {
    var tier = new SacrificeTierDef(" WOUND ", .2, .5, .3, " HEALING ", false);
    assertEquals("wound", tier.getId());
    assertEquals(.2, tier.getMin());
    assertEquals(.5, tier.getBelow());
    assertEquals(.3, tier.getAuraFraction());
    assertEquals("healing", tier.getInjury());
    assertFalse(tier.isPermadeath());
    assertFalse(tier.matches(.1));
    assertTrue(tier.matches(.2));
    assertTrue(tier.matches(.49));
    assertFalse(tier.matches(.5));
    assertTrue(new SacrificeTierDef(null, 0, 0, 0, null, false).matches(10));
    assertEquals("none", new SacrificeTierDef("x", 0, 0, 0, " ", false).getInjury());
    assertNull(SacrificeImprint.stageForTier(null));
    assertEquals("pain", SacrificeImprint.stageForTier(tier));
    assertEquals(
        "pain",
        SacrificeImprint.stageForTier(new SacrificeTierDef("other", 0, 0, 0, "healing", false)));
    assertEquals(
        "screams",
        SacrificeImprint.stageForTier(new SacrificeTierDef("maim", 0, 0, 0, "none", false)));
    assertEquals(
        "screams",
        SacrificeImprint.stageForTier(new SacrificeTierDef("other", 0, 0, 0, "permanent", false)));
    assertEquals(
        "soul", SacrificeImprint.stageForTier(new SacrificeTierDef("death", 0, 0, 0, null, false)));
    assertEquals(
        "soul", SacrificeImprint.stageForTier(new SacrificeTierDef("other", 0, 0, 0, null, true)));
    assertNull(SacrificeImprint.stageForTier(new SacrificeTierDef("none", 0, 0, 0, null, false)));
    SacrificeRegistry.registerTier(null);
    SacrificeRegistry.registerTier(new SacrificeTierDef(null, 0, 0, 0, null, false));
    assertEquals(List.of(), SacrificeRegistry.getTiers());
    assertEquals("none", SacrificeRegistry.tierForCharge(1).getId());
    SacrificeRegistry.registerTier(tier);
    assertSame(tier, SacrificeRegistry.getTier(" WOUND "));
    assertNull(SacrificeRegistry.getTier(null));
    assertSame(tier, SacrificeRegistry.tierForCharge(.3));
    var none = new SacrificeTierDef("none", 9, 10, 0, null, false);
    SacrificeRegistry.registerTier(none);
    assertSame(none, SacrificeRegistry.tierForCharge(1));
  }

  @Test
  void registryDefaultsOverridesAndChargeBlockingAreConsistent() {
    assertFalse(SacrificeRegistry.isEnabled());
    assertTrue(SacrificeRegistry.isSceneryCharge());
    assertFalse(SacrificeRegistry.blocksSceneryCharge("fire"));
    globals(null, false);
    assertEquals(5, SacrificeRegistry.getChargeSeconds());
    assertEquals(3, SacrificeRegistry.getVictimRange());
    assertEquals(5, SacrificeRegistry.getMinSceneryAura());
    assertEquals(2, SacrificeRegistry.getHintMinAura());
    assertFalse(SacrificeRegistry.isRequireMinScore());
    assertFalse(SacrificeRegistry.isRequireDaggerInHand());
    assertFalse(SacrificeRegistry.isOneRitePerCaster());
    assertFalse(SacrificeRegistry.isRefuseNewWordsWhileActive());
    assertEquals("altar", SacrificeRegistry.getPedestalId());
    assertEquals("slot", SacrificeRegistry.getPedestalSlot());
    assertEquals("v.STONE", SacrificeRegistry.getDagger().getPath());
    SacrificeRegistry.setGlobals(0, 0, null, true, true, -1, -1, true, true, true, null, " ", null);
    assertEquals(10, SacrificeRegistry.getChargeSeconds());
    assertEquals(4, SacrificeRegistry.getVictimRange());
    assertEquals(0, SacrificeRegistry.getMinSceneryAura());
    assertEquals("pedestal", SacrificeRegistry.getPedestalId());
    assertEquals("*", SacrificeRegistry.getPedestalSlot());
    SacrificeRegistry.setRpChannel(null);
    assertEquals("rp", SacrificeRegistry.getRpChannel());
    SacrificeRegistry.setRpChannel(" ");
    assertEquals("rp", SacrificeRegistry.getRpChannel());
    SacrificeRegistry.setRpChannel(" CHAT ");
    assertEquals("chat", SacrificeRegistry.getRpChannel());
    SacrificeRegistry.setHideIncantation(true);
    assertTrue(SacrificeRegistry.isHideIncantation());
    SacrificeRegistry.setRequireRpCharacters(false);
    assertFalse(SacrificeRegistry.isRequireRpCharacters());
    SacrificeRegistry.setFx(null);
    assertNotNull(SacrificeRegistry.getFx());
    var fx = SacrificeFxDef.empty();
    SacrificeRegistry.setFx(fx);
    assertSame(fx, SacrificeRegistry.getFx());
    SacrificeRegistry.register(null);
    SacrificeRegistry.register(element(null, true, null));
    assertEquals(0, SacrificeRegistry.size());
    var def = element(" FIRE ", true, List.of("word"));
    SacrificeRegistry.register(def);
    assertEquals(List.of(def), SacrificeRegistry.getAll());
    assertEquals(20, SacrificeRegistry.minSceneryAura("fire"));
    assertEquals(0, SacrificeRegistry.minSceneryAura("missing"));
    assertNull(SacrificeRegistry.getById(null));
    SacrificeRegistry.setEnabled(true);
    assertFalse(SacrificeRegistry.blocksSceneryCharge("fire"));
    SacrificeRegistry.setSceneryCharge(false);
    assertFalse(SacrificeRegistry.blocksSceneryCharge(null));
    assertFalse(SacrificeRegistry.blocksSceneryCharge(" "));
    assertFalse(SacrificeRegistry.blocksSceneryCharge("missing"));
    assertTrue(SacrificeRegistry.blocksSceneryCharge(" FIRE "));
    assertEquals("v.golden_sword", new SacrificeDaggerDef(" ").getPath());
  }

  @Test
  void imprintsUpgradeSeverityAndPreserveKnownNameAndElement() {
    var defaults = new SacrificeImprint(null, null, null, null);
    assertEquals("", defaults.getCharacterId());
    assertEquals("", defaults.getCharacterName());
    assertEquals("", defaults.getElementId());
    assertEquals("pain", defaults.getStage());
    assertEquals(0, defaults.rank());
    assertEquals("pain", SacrificeImprint.normalizeStage("unknown"));
    assertEquals("soul", SacrificeImprint.normalizeStage("death"));
    assertEquals(1, SacrificeImprint.rankOf("screams"));
    assertEquals(2, SacrificeImprint.rankOf("soul"));
    var first = new SacrificeImprint("a", "Alice", "pain", " FIRE ");
    var second = new SacrificeImprint("b", "Bob", "screams");
    var existing = List.of(first, second);
    assertEquals(existing, SacrificeImprint.upsert(existing, null));
    assertEquals(existing, SacrificeImprint.upsert(existing, defaults));
    assertEquals(1, SacrificeImprint.upsert(null, first).size());
    var updated = SacrificeImprint.upsert(existing, new SacrificeImprint("a", "", "soul", ""));
    assertEquals(2, updated.size());
    assertEquals("Alice", updated.getFirst().getCharacterName());
    assertEquals("fire", updated.getFirst().getElementId());
    assertEquals("soul", updated.getFirst().getStage());
    updated =
        SacrificeImprint.upsert(updated, new SacrificeImprint("a", "New Name", "pain", "water"));
    assertEquals("New Name", updated.getFirst().getCharacterName());
    assertEquals("soul", updated.getFirst().getStage());
    assertEquals("water", updated.getFirst().getElementId());
    assertEquals(
        3, SacrificeImprint.upsert(existing, new SacrificeImprint("c", "C", "pain")).size());
  }

  @Test
  void imprintStorageRoundTripsSanitizedNamesAndSkipsMalformedEntries() {
    var item = new ItemStack(Material.STONE);
    assertEquals(List.of(), SacrificeImprintStore.read(null));
    assertEquals(List.of(), SacrificeImprintStore.read(item));
    SacrificeImprintStore.write(null, List.of());
    SacrificeImprintStore.write(new ItemStack(Material.AIR), List.of());
    var entries =
        Arrays.asList(
            null,
            new SacrificeImprint(null, null, null),
            new SacrificeImprint("id", "Alice|Bob;C", "soul", "fire"),
            new SacrificeImprint("two", "Two", "pain"));
    SacrificeImprintStore.write(item, entries);
    var read = SacrificeImprintStore.read(item);
    assertEquals(2, read.size());
    assertEquals("Alice Bob C", read.getFirst().getCharacterName());
    assertEquals("fire", read.getFirst().getElementId());
    var def = element("fire", true, List.of());
    assertEquals("Soul of Alice Bob C", SacrificeImprintStore.formatLine(read.getFirst(), def));
    assertEquals("Pain of Two", SacrificeImprintStore.formatLine(read.get(1), def));
    assertEquals(
        "Screams of X",
        SacrificeImprintStore.formatLine(new SacrificeImprint("x", "X", "screams"), def));
    assertEquals("", SacrificeImprintStore.formatLine(null, def));
    assertEquals("", SacrificeImprintStore.formatLine(read.getFirst(), null));
    assertEquals(
        "",
        SacrificeImprintStore.formatLine(
            read.getFirst(), new SacrificeElementDef("x", null, null, null, null, 0)));
    var meta = item.getItemMeta();
    meta.getPersistentDataContainer()
        .set(
            ArtifactKeys.sacrificeImprints(),
            PersistentDataType.STRING,
            "bad; ;|name|pain;old|Old|pain;new|New|soul|fire");
    item.setItemMeta(meta);
    assertEquals(2, SacrificeImprintStore.read(item).size());
    SacrificeImprintStore.write(item, Arrays.asList(null, new SacrificeImprint(null, null, null)));
    assertTrue(SacrificeImprintStore.read(item).isEmpty());
    SacrificeImprintStore.write(item, null);
    SacrificeImprintStore.write(item, List.of());
  }

  @Test
  void absentAndBlankStoredImprintsReadAsEmpty() {
    assertTrue(SacrificeImprintStore.read(null).isEmpty());
    assertTrue(SacrificeImprintStore.read(new ItemStack(Material.AIR)).isEmpty());
    var item = new ItemStack(Material.STONE);
    assertTrue(SacrificeImprintStore.read(item).isEmpty());
    var meta = item.getItemMeta();
    meta.getPersistentDataContainer()
        .set(ArtifactKeys.sacrificeImprints(), PersistentDataType.STRING, " ");
    item.setItemMeta(meta);
    assertTrue(SacrificeImprintStore.read(item).isEmpty());
  }
}
