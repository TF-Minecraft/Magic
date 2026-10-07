package net.tfminecraft.magic;

import static org.junit.jupiter.api.Assertions.*;

import java.util.*;
import net.tfminecraft.magic.gear.*;
import org.bukkit.*;
import org.bukkit.inventory.*;
import org.bukkit.persistence.*;
import org.junit.jupiter.api.*;

class GearProvenanceCoverageTest extends GearCoverageSupport {
  @Test
  void unstampedItemsAndMissingMetadataAreSafe() {
    for (ItemStack item : Arrays.asList(null, new ItemStack(Material.AIR))) {
      GearProvenance.stamp(item, GearType.STAFF, null);
      GearProvenance.applyMajority(item, null);
      GearProvenance.syncRevisions(item);
      GearProvenance.lockSockets(item);
      assertEquals(0, GearProvenance.majorityOf(item));
      assertEquals("", GearProvenance.partsRaw(item));
      assertTrue(GearProvenance.resolveParts(item).isEmpty());
      assertFalse(GearProvenance.isGear(item));
      assertFalse(GearProvenance.stationCanCreate(item));
      assertFalse(GearProvenance.isOutdated(item));
      assertFalse(GearProvenance.socketsLocked(item));
    }
    var item = item();
    GearProvenance.stamp(item, null, null);
    assertEquals(0, GearProvenance.majorityOf(item));
    assertFalse(GearProvenance.isOutdated(item));
    assertTrue(GearProvenance.missingPartIds(item).isEmpty());
    assertTrue(GearProvenance.resolveParts(item).isEmpty());
    assertFalse(GearProvenance.socketsLocked(item));
    tag(item, GearKeys.socketsLocked(), PersistentDataType.BYTE, (byte) 0);
    assertFalse(GearProvenance.socketsLocked(item));
    GearProvenance.lockSockets(item);
    assertTrue(GearProvenance.socketsLocked(item));
  }

  @Test
  void craftInputsRecordWhatWasChargedAndReadBack() {
    for (ItemStack item : Arrays.asList(null, new ItemStack(Material.AIR))) {
      GearProvenance.stampInputs(item, Map.of("v.gold", 1));
      assertNull(GearProvenance.readInputs(item));
    }
    var item = item();
    assertNull(GearProvenance.readInputs(item), "Weapons crafted before the record have none");
    GearProvenance.stampInputs(item, null);
    assertEquals(Map.of(), GearProvenance.readInputs(item));
    GearProvenance.stampInputs(item, Map.of("m.materials.steel_ingot", 4, "v.gold", 2));
    assertEquals(Map.of("m.materials.steel_ingot", 4, "v.gold", 2), GearProvenance.readInputs(item));
    tag(item, GearKeys.craftInputs(), PersistentDataType.STRING, "{not json");
    assertNull(GearProvenance.readInputs(item));
  }

  @Test
  void stampingTracksPartAndArchetypeRevisions() {
    var item = item();
    GearProvenance.stamp(item, GearType.STAFF, null);
    assertEquals(GearType.STAFF, GearProvenance.archetypeOf(item));
    assertEquals("", GearProvenance.partsRaw(item));
    assertEquals(
        1,
        item.getItemMeta()
            .getPersistentDataContainer()
            .get(GearKeys.archetypeRevision(), PersistentDataType.INTEGER));
    assertFalse(GearProvenance.isOutdated(item));
    var a = GearDefinitionTest.archetype();
    ArchetypeRegistry.register(a);
    var p = part("core", 2);
    PartRegistry.register(p);
    GearProvenance.stamp(item, GearType.STAFF, Arrays.asList(null, p));
    assertEquals("core:1", GearProvenance.partsRaw(item));
    assertEquals(List.of(p), GearProvenance.resolveParts(item));
    assertEquals(2, GearProvenance.majorityOf(item));
    assertFalse(GearProvenance.isOutdated(item));
    p.setRevision(2);
    assertTrue(GearProvenance.isOutdated(item));
    GearProvenance.syncRevisions(item);
    assertFalse(GearProvenance.isOutdated(item));
    a.setRevision(3);
    assertTrue(GearProvenance.isOutdated(item));
    GearProvenance.syncRevisions(item);
    assertFalse(GearProvenance.isOutdated(item));
    GearProvenance.applyMajority(item, null);
    assertEquals(0, GearProvenance.majorityOf(item));
    GearProvenance.applyMajority(item, List.of(p));
    assertEquals(2, GearProvenance.majorityOf(item));
    tag(item, GearKeys.archetypeRevision(), PersistentDataType.INTEGER, null);
    assertTrue(GearProvenance.isOutdated(item));
  }

  @Test
  void legacyMalformedAndMissingPartTokensStayRecoverable() {
    var item = item();
    GearProvenance.stamp(item, GearType.STAFF, null);
    tag(item, GearKeys.parts(), PersistentDataType.STRING, "old,missing:4, core:bad, ,:1");
    var p = part("core", 1);
    PartRegistry.register(p);
    assertEquals(List.of("old", "missing", ":1"), GearProvenance.missingPartIds(item));
    assertFalse(GearProvenance.isOutdated(item));
    GearProvenance.syncRevisions(item);
    assertEquals("old:1,missing:4,core:1,:1:1", GearProvenance.partsRaw(item));
    assertEquals(List.of(p), GearProvenance.resolveParts(item));
    tag(item, GearKeys.parts(), PersistentDataType.STRING, "  ");
    assertTrue(GearProvenance.resolveParts(item).isEmpty());
    GearProvenance.syncRevisions(item);
    assertEquals("", GearProvenance.partsRaw(item));
  }
}
