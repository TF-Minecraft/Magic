package net.tfminecraft.magic.artifact;

import static org.junit.jupiter.api.Assertions.*;

import java.util.*;
import net.tfminecraft.magic.artifact.config.*;
import net.tfminecraft.magic.artifact.sacrifice.*;
import net.tfminecraft.magic.artifact.shrine.*;
import org.bukkit.persistence.PersistentDataType;
import org.junit.jupiter.api.*;

class ArtifactLoreEdgeTest {
  ArtifactLoreTest f = new ArtifactLoreTest();

  @BeforeEach
  void setup() {
    f.setup();
  }

  @AfterEach
  void cleanup() {
    f.cleanup();
    ArtifactTypeRegistry.clear();
    SacrificeRegistry.clear();
    ShrineRegistry.clear();
  }

  @Test
  void overflowingAttunementCountIsClampedToAvailableLore() {
    var item = f.artifact();
    ArtifactIds.writeNew(item);
    ArtifactLore.apply(item);
    var meta = item.getItemMeta();
    meta.getPersistentDataContainer()
        .set(ArtifactKeys.attuneCount(), PersistentDataType.INTEGER, Integer.MAX_VALUE);
    item.setItemMeta(meta);
    assertDoesNotThrow(() -> ArtifactLore.refreshAttune(item));
    assertEquals(
        0,
        item.getItemMeta()
            .getPersistentDataContainer()
            .get(ArtifactKeys.attuneCount(), PersistentDataType.INTEGER));
  }

  @Test
  void nonFiniteLoreNumbersDoNotCreateInvalidAura() {
    for (String number : List.of("NaN", "Infinity", "-Infinity"))
      assertNull(ArtifactLore.readAura(f.item(List.of("Fire 1 / " + number))));
    assertEquals(
        1, ArtifactLore.readAura(f.item(List.of("Muffled: NaN%", "Fire 1 / 10"))).getFill("fire"));
  }

  @Test
  void missingAuraMissingIndexesAndOversizedIndexesRebuildSafely() {
    var item = f.item(List.of("Original"));
    ArtifactIds.writeNew(item);
    ArtifactLore.refreshAttune(item);
    assertEquals(List.of("Original"), f.plain(item));
    item = f.artifact();
    ArtifactIds.writeNew(item);
    ArtifactLore.apply(item);
    var meta = item.getItemMeta();
    meta.getPersistentDataContainer().remove(ArtifactKeys.attuneCount());
    item.setItemMeta(meta);
    ArtifactLore.refreshAttune(item);
    assertEquals(4, f.plain(item).size());
    meta = item.getItemMeta();
    meta.getPersistentDataContainer()
        .set(ArtifactKeys.attuneCount(), PersistentDataType.INTEGER, 1);
    item.setItemMeta(meta);
    ArtifactLore.refreshAttune(item);
    assertEquals(4, f.plain(item).size());
  }

  @Test
  void imprintLoreUsesKnownFallbackAndEmptyTemplates() {
    var item = f.artifact();
    SacrificeImprintStore.write(item, List.of(new SacrificeImprint("id", "Name", "pain", "fire")));
    ArtifactLore.apply(item);
    assertEquals(4, f.plain(item).size());
    SacrificeRegistry.register(
        new SacrificeElementDef("water", List.of(), "Pain of {character}", "Screams", "Soul", 0));
    ArtifactLore.apply(item);
    assertEquals("Pain of Name", f.plain(item).getLast());
    SacrificeRegistry.register(
        new SacrificeElementDef("fire", List.of(), " ", "Screams", "Soul", 0));
    ArtifactLore.apply(item);
    assertEquals(4, f.plain(item).size());
    SacrificeRegistry.register(
        new SacrificeElementDef("fire", List.of(), "Fire of {character}", "Screams", "Soul", 0));
    ArtifactLore.apply(item);
    assertEquals("Fire of Name", f.plain(item).getLast());
  }

  @Test
  void raritiesUseFallbackColorsAndBlankRarityIsHidden() {
    var item = f.artifact();
    for (String color : Arrays.asList(null, " ", "#ff0000")) {
      ArtifactRarityRegistry.register(new ArtifactRarityDef("rare", "Rare", color, 1, 1, 1, null));
      var meta = item.getItemMeta();
      meta.getPersistentDataContainer()
          .set(ArtifactKeys.artifactRarity(), PersistentDataType.STRING, "rare");
      item.setItemMeta(meta);
      ArtifactLore.apply(item);
      assertEquals("Rare", f.plain(item).get(1));
    }
    var meta = item.getItemMeta();
    meta.getPersistentDataContainer()
        .set(ArtifactKeys.artifactRarity(), PersistentDataType.STRING, " ");
    item.setItemMeta(meta);
    ArtifactLore.apply(item);
    assertEquals("Aura", f.plain(item).get(1));
  }

  @Test
  void sortingUsesPrimaryThenFillThenCapThenNameAndHidesDisabledSecondaries() {
    f.element("air", "Air", 0);
    f.element("earth", "Earth", 0);
    f.element("zero", "Zero", 0);
    var item = f.artifact();
    var a = Artifact.fromItem(item);
    for (String id : List.of("fire", "water", "air", "earth")) {
      a.setCap(id, 10);
      a.setFill(id, 1);
    }
    a.setCap("earth", 20);
    a.persistPdc(item);
    var meta = item.getItemMeta();
    meta.getPersistentDataContainer()
        .set(ArtifactKeys.artifactPrimary(), PersistentDataType.STRING, "fire");
    item.setItemMeta(meta);
    ArtifactLore.apply(item);
    assertEquals(
        List.of(
            "Original lore", "Aura", "Fire 1 / 10", "Earth 1 / 20", "Air 1 / 10", "Water 1 / 10"),
        f.plain(item));
    ShrineRegistry.register(new ShrineElementDef("water", false, List.of()));
    ArtifactLore.apply(item);
    assertFalse(f.plain(item).stream().anyMatch(x -> x.startsWith("Water ")));
    meta = item.getItemMeta();
    meta.getPersistentDataContainer()
        .set(ArtifactKeys.artifactPrimary(), PersistentDataType.STRING, " ");
    item.setItemMeta(meta);
    ArtifactLore.apply(item);
    assertEquals("Earth 1 / 20", f.plain(item).get(2));
  }

  @Test
  void auraRecoverySkipsMalformedLabelsButAcceptsLongestMatchingName() {
    f.element("other", "Fire", 0);
    f.element("large", "Fire Storm", 0);
    var aura =
        ArtifactLore.readAura(
            f.item(List.of("Muffled: %", "Fire 1..2 / 10", "Fire 2 / ", "Fire Storm 3 / 10")));
    assertNotNull(aura);
    assertEquals(0, aura.getFill("fire"));
    assertEquals(3, aura.getFill("large"));
    var item = f.artifact();
    for (List<String> old :
        List.of(
            List.of("Aura", "Fire 1 / 10"),
            List.of("Fire 1 / 10"),
            List.of("", "Aura", "Fire 1 / 10"),
            List.of("§0§1§2old"))) {
      var meta = item.getItemMeta();
      meta.setLore(old);
      item.setItemMeta(meta);
      ArtifactLore.apply(item);
      assertEquals(1, f.plain(item).stream().filter(x -> x.equals("Aura")).count());
    }
    f.element("growing", "Growing", 1);
    var a = Artifact.fromItem(item);
    a.setCap("growing", 100);
    a.persistPdc(item);
    assertTrue(ArtifactLore.careVisibleWouldChange(item, 0, 1));
  }

  @Test
  void rawLegacyParserHandlesUnnormalisedMarkersAndEmptyInput() throws Exception {
    var hidden = ArtifactLore.class.getDeclaredMethod("hasHiddenMarker", String.class);
    hidden.setAccessible(true);
    assertEquals(false, hidden.invoke(null, (String) null));
    assertEquals(true, hidden.invoke(null, "prefix§0§1§2§rAura"));
    assertEquals(true, hidden.invoke(null, "prefix§0§1§2Aura"));
    var plain = ArtifactLore.class.getDeclaredMethod("plain", String.class);
    plain.setAccessible(true);
    assertEquals("", plain.invoke(null, (String) null));
    var parse = ArtifactLore.class.getDeclaredMethod("parseAmount", String.class);
    parse.setAccessible(true);
    assertNull(parse.invoke(null, (String) null));
    var label = ArtifactLore.class.getDeclaredMethod("auraLabel", String.class, String.class);
    label.setAccessible(true);
    assertEquals(false, label.invoke(null, null, "fire"));
    assertEquals(false, label.invoke(null, "fire 1", null));
    assertEquals(false, label.invoke(null, "fire 1", ""));
    var number = ArtifactLore.class.getDeclaredMethod("leadingNumber", String.class);
    number.setAccessible(true);
    assertEquals("123", number.invoke(null, "123"));
    var item = f.artifact();
    var meta = item.getItemMeta();
    meta.setLore(List.of("Armour 2 / 10", "Water 3 / 20"));
    item.setItemMeta(meta);
    ArtifactLore.apply(item);
    assertEquals("Armour 2 / 10", f.plain(item).getFirst());
  }

  @Test
  void legacyAuraLoreDoesNotTurnAChargeIntoAnArtifact() {
    var item = f.item(List.of("Fire 1 / 10"));
    var meta = item.getItemMeta();
    meta.getPersistentDataContainer()
        .set(net.tfminecraft.magic.charge.ChargeKeys.chargeTier(), PersistentDataType.INTEGER, 1);
    item.setItemMeta(meta);
    assertNotNull(ArtifactLore.readAura(item));
    assertNull(Artifact.fromItem(item));
    assertFalse(item.getItemMeta().getPersistentDataContainer().has(ArtifactKeys.auraData()));
  }
}
