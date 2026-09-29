package net.tfminecraft.magic;

import static org.junit.jupiter.api.Assertions.*;

import java.util.*;
import net.tfminecraft.magic.gear.*;
import net.tfminecraft.magic.modifier.ModifierTriple;
import org.bukkit.Material;
import org.junit.jupiter.api.*;

class GearDefinitionTest {
  @BeforeEach
  @AfterEach
  void reset() {
    SocketLayout.clearLabels();
    SocketColourRegistry.clear();
    GearCache.socketRarityPrefix = true;
    GearCache.clearAlignment();
    GearCache.alignmentEnabled = false;
  }

  static PartDef part(String id, int tier, List<String> limit, Map<String, Integer> sockets) {
    return new PartDef(
        id,
        "Name",
        "CORE",
        tier,
        Set.of(GearType.STAFF),
        " v.STICK ",
        Map.of("v.STONE", 2),
        limit,
        Map.of("damage", 1.),
        sockets,
        List.of("lore"),
        " SCHEME ",
        2,
        false);
  }

  static ArchetypeDef archetype() {
    return new ArchetypeDef(
        GearType.STAFF,
        "Name",
        " template ",
        " icon ",
        false,
        List.of("core", "head", "grip"),
        new LinkedHashMap<>(Map.of("spell", "Spell", "support", "Support")));
  }

  @Test
  void partDefinitionsNormalizeInputsAndExposeStableGameplayContent() {
    var sockets = new HashMap<String, Integer>();
    sockets.put(null, 1);
    sockets.put("bad", null);
    sockets.put("zero", 0);
    sockets.put("SPELL", 2);
    var p = part(" Test ", 2, Arrays.asList(null, " ", " HEAD ", "head"), sockets);
    assertEquals("test", p.getId());
    assertEquals("Name", p.getName());
    assertEquals("core", p.getPartType());
    assertEquals(2, p.getTier());
    assertTrue(p.hasTier());
    assertEquals(Set.of(GearType.STAFF), p.getTypes());
    assertTrue(p.supports(GearType.STAFF));
    assertFalse(p.supports(GearType.WAND));
    assertFalse(p.supports(null));
    assertEquals("v.STICK", p.getItemPath());
    assertTrue(p.hasCost());
    assertEquals(Map.of("v.STONE", 2), p.getCost());
    assertEquals(List.of("head"), p.getPartLimit());
    assertTrue(p.hasPartLimit());
    assertTrue(p.allowsPart(" HEAD "));
    assertFalse(p.allowsPart(null));
    assertFalse(p.allowsPart(" "));
    assertFalse(p.allowsPart("grip"));
    assertEquals(Map.of("damage", 1.), p.getStats());
    assertEquals(Map.of("spell", 2), p.getSockets());
    assertEquals(0, p.socketCount(null));
    assertEquals(0, p.socketCount("unknown"));
    assertEquals(2, p.socketCount(" SPELL "));
    assertEquals(2, p.totalSocketCount());
    assertEquals(List.of("lore"), p.getLore());
    assertEquals("scheme", p.getSchemeId());
    assertEquals(2, p.getSchemeWeight());
    assertTrue(p.hasModelScheme());
    assertFalse(p.isDisabled());
    assertEquals(1, p.getRevision());
    p.setRevision(3);
    assertEquals(3, p.getRevision());
    assertTrue(p.buildRevisionContent().contains("sockets={spell=2}"));
    var empty =
        new PartDef(null, null, null, 0, null, null, null, null, null, null, null, null, 0, true);
    assertEquals("", empty.getId());
    assertEquals("", empty.getName());
    assertFalse(empty.hasTier());
    assertTrue(empty.getTypes().isEmpty());
    assertFalse(empty.hasCost());
    assertFalse(empty.hasPartLimit());
    assertTrue(empty.allowsPart(null));
    assertFalse(empty.hasModelScheme());
    assertEquals(1, empty.getSchemeWeight());
    assertTrue(empty.isDisabled());
    assertEquals(
        part("x", 1, List.of(), Map.of()).buildRevisionContent(),
        part("other", 1, List.of(), Map.of()).buildRevisionContent());
  }

  @Test
  void archetypeCopiesConfigurationAndHashesGameplayFieldsOnly() {
    var slots = new HashMap<String, String>();
    slots.put(null, "bad");
    slots.put("missing", null);
    slots.put("empty", " ");
    slots.put(" SPELL ", " Spell ");
    var a = new ArchetypeDef(GearType.STAFF, null, null, null, true, null, slots);
    assertEquals(GearType.STAFF, a.getType());
    assertEquals("Staff", a.getName());
    assertEquals("", a.getTemplate());
    assertEquals("", a.getIcon());
    assertTrue(a.isMelee());
    assertEquals(List.of(), a.getRequired());
    assertEquals(Map.of("spell", "Spell"), a.getSlots());
    assertEquals(List.of("spell"), a.slotIds());
    assertEquals("", a.slotSuffix(null));
    assertEquals("", a.slotSuffix("unknown"));
    assertEquals("Spell", a.slotSuffix(" SPELL "));
    assertEquals(1, a.getRevision());
    a.setRevision(2);
    assertEquals(2, a.getRevision());
    assertTrue(a.buildRevisionContent().contains("melee=true"));
    assertEquals(
        Map.of(), new ArchetypeDef(GearType.WAND, " ", null, null, false, null, null).getSlots());
    var full = archetype();
    assertEquals("Name", full.getName());
    assertEquals("template", full.getTemplate());
    assertEquals("icon", full.getIcon());
    assertFalse(full.isMelee());
    assertEquals(List.of("core", "head", "grip"), full.getRequired());
  }

  @Test
  void partSlotsIntersectCoreRestrictionsAndRequiredCategories() {
    assertEquals(List.of(), PartSlots.open(null, null));
    assertEquals(List.of("core", "head", "grip"), PartSlots.open(archetype(), null));
    assertEquals(
        List.of("core", "head", "grip"),
        PartSlots.open(archetype(), part("a", 1, List.of(), Map.of())));
    assertEquals(
        List.of("core", "head"),
        PartSlots.open(archetype(), part("a", 1, List.of("core", "head", "unknown"), Map.of())));
    assertFalse(PartSlots.contains(null, "a"));
    assertFalse(PartSlots.contains(List.of("a"), null));
    assertFalse(PartSlots.contains(List.of("a"), " "));
    assertTrue(PartSlots.contains(List.of("head"), " HEAD "));
  }

  @Test
  void majorityTierUsesCountsAndBreaksTiesTowardHigherTier() {
    assertEquals(0, MajorityTierResolver.resolve(null));
    assertEquals(0, MajorityTierResolver.resolve(List.of()));
    var one = part("one", 1, List.of(), Map.of());
    var two = part("two", 2, List.of(), Map.of());
    var three = part("three", 3, List.of(), Map.of());
    assertEquals(
        1,
        MajorityTierResolver.resolve(
            Arrays.asList(null, part("zero", 0, List.of(), Map.of()), one, one, two)));
    assertEquals(3, MajorityTierResolver.resolve(List.of(one, two, three)));
    assertEquals(
        List.of("0", "I", "II", "III", "IV", "5"),
        java.util.stream.IntStream.rangeClosed(0, 5)
            .mapToObj(MajorityTierResolver::toRoman)
            .toList());
  }

  @Test
  void socketLabelsColorsAndCapacityAreStable() {
    SocketLayout.putLabel(null, "x");
    SocketLayout.putLabel(" ", "x");
    SocketLayout.putLabel("x", null);
    SocketLayout.putLabel("x", " ");
    SocketLayout.putLabel(" CAST ", " Custom ");
    assertEquals("Custom", SocketLayout.label("cast"));
    assertEquals("Minor Spell", SocketLayout.label("minor_spell"));
    assertEquals("New Kind", SocketLayout.label("new_kind"));
    assertEquals("", SocketLayout.label(null));
    assertEquals("", SocketLayout.label(" "));
    assertEquals("", SocketLayout.prettyId(null));
    assertEquals("", SocketLayout.prettyId(" "));
    assertEquals("A Long Name", SocketLayout.prettyId("a__long-name"));
    assertEquals("Custom", SocketLayout.labels().get("cast"));
    SocketColourRegistry.setDefaultPrefix(null);
    SocketColourRegistry.setDefaultPrefix(" ");
    SocketColourRegistry.setDefaultPrefix(" Rare ");
    SocketColourRegistry.put(0, "x");
    SocketColourRegistry.put(1, null);
    SocketColourRegistry.put(1, " ");
    SocketColourRegistry.put(2, " Epic ");
    assertEquals("Rare", SocketColourRegistry.defaultPrefix());
    assertEquals("Rare", SocketColourRegistry.prefix(0));
    assertEquals("Epic", SocketColourRegistry.prefix(2));
    assertEquals("Rare", SocketColourRegistry.prefix(3));
    assertEquals("", SocketColourRegistry.colour(1, null));
    assertEquals("", SocketColourRegistry.colour(1, " "));
    assertEquals("Epic Spell", SocketColourRegistry.colour(2, "Spell"));
    assertEquals(Map.of(0, "Rare", 2, "Epic"), SocketColourRegistry.prefixes());
    GearCache.socketRarityPrefix = false;
    assertEquals("Spell", SocketColourRegistry.colour(2, " Spell "));
    assertEquals(List.of(), SocketLayout.colours(null, null, 1));
    assertEquals(0, SocketLayout.rawTotal(null, null));
    assertEquals(0, SocketLayout.rawTotal(archetype(), null));
    assertEquals(List.of("§8No rune sockets"), SocketLayout.previewLines(null, null));
    assertEquals(List.of("§8No rune sockets"), SocketLayout.previewLines(archetype(), null));
    var parts = Arrays.asList(null, part("a", 1, List.of(), Map.of("spell", 6)));
    assertEquals(6, SocketLayout.rawTotal(archetype(), parts));
    assertEquals(4, SocketLayout.colours(archetype(), parts, 1).size());
    var preview = SocketLayout.previewLines(archetype(), parts);
    assertEquals(5, preview.size());
    assertEquals("§8Clamped to 4 (was 6)", preview.getLast());
    assertEquals(
        1,
        SocketLayout.previewLines(archetype(), List.of(part("a", 1, List.of(), Map.of("spell", 1))))
            .size());
  }

  @Test
  void gearTypesAndAlignmentAreExplicitlyBounded() {
    assertNull(GearType.fromId(null));
    assertNull(GearType.fromId(" "));
    assertNull(GearType.fromId("bad"));
    assertEquals(GearType.STAFF, GearType.fromId(" staff "));
    assertEquals(Material.STICK, GearType.STAFF.getIcon());
    assertEquals("Staff", GearType.STAFF.getDisplayName());
    GearCache.clearAlignment();
    GearCache.putAlignment(0, ModifierTriple.ZERO);
    GearCache.putAlignment(1, null);
    assertEquals(0, GearCache.alignmentBands());
    var triple = new ModifierTriple(.1, .2, .3);
    GearCache.putAlignment(2, triple);
    assertEquals(1, GearCache.alignmentBands());
    GearCache.alignmentEnabled = false;
    assertSame(ModifierTriple.ZERO, GearCache.alignment(2));
    GearCache.alignmentEnabled = true;
    assertSame(ModifierTriple.ZERO, GearCache.alignment(0));
    assertSame(ModifierTriple.ZERO, GearCache.alignment(1));
    assertSame(triple, GearCache.alignment(2));
    var type = new PartTypeDef(" head ", 4);
    assertEquals("head", type.getId());
    assertEquals("Head", type.getName());
    assertEquals(4, type.getSlot());
    assertEquals("Fancy", new PartTypeDef(null, 0, " Fancy ").getName());
  }
}
