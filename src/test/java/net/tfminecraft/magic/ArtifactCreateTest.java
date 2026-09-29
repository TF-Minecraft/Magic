package net.tfminecraft.magic;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.*;
import net.tfminecraft.magic.artifact.config.*;
import net.tfminecraft.magic.artifact.create.ArtifactCreateSession;
import net.tfminecraft.magic.artifact.create.ArtifactCreateSession.ElementRole;
import net.tfminecraft.magic.artifact.generate.ArtifactItemBuilder;
import net.tfminecraft.magic.artifact.sacrifice.SacrificeRegistry;
import net.tfminecraft.magic.artifact.shrine.ShrineRegistry;
import org.junit.jupiter.api.*;

class ArtifactCreateTest {
  @BeforeEach
  void setup() {
    ArtifactRarityRegistry.clear();
    ArtifactTypeRegistry.clear();
    ArtifactAffinityRegistry.clear();
    ShrineRegistry.clear();
    SacrificeRegistry.clear();
    ArtifactRarityRegistry.register(ArtifactGenerationTest.rarity("common", 1, 1, 3));
    ArtifactTypeRegistry.register(ArtifactGenerationTest.type("fire", true, 1));
    ArtifactTypeRegistry.register(ArtifactGenerationTest.type("water", true, 1));
    ArtifactTypeRegistry.register(ArtifactGenerationTest.type("disabled", false, 1));
  }

  @AfterEach
  void cleanup() {
    ArtifactRarityRegistry.clear();
    ArtifactTypeRegistry.clear();
    ArtifactAffinityRegistry.clear();
  }

  @Test
  void selectionCyclesOffSecondaryPrimaryAndBackOff() {
    var s = new ArtifactCreateSession();
    assertEquals("common", s.getRarityId());
    assertNull(s.toRoll());
    assertEquals(0, s.getCap(null));
    s.setCap(null, 2);
    s.setCap(" ", 2);
    assertEquals(Map.of(), s.getCaps());
    assertEquals(ElementRole.OFF, s.roleOf(null));
    assertEquals(ElementRole.OFF, s.roleOf(" "));
    assertFalse(s.cycleElement(null));
    assertFalse(s.cycleElement(" "));
    assertFalse(s.cycleElement("missing"));
    assertFalse(s.cycleElement("disabled"));
    assertTrue(s.cycleElement("fire"));
    assertEquals("fire", s.getSelectedElementId());
    assertEquals(ElementRole.SECONDARY, s.roleOf("fire"));
    assertEquals(20, s.getCap("fire"));
    assertTrue(s.cycleElement("fire"));
    assertEquals("fire", s.getPrimaryId());
    assertEquals(ElementRole.PRIMARY, s.roleOf("fire"));
    assertNotNull(s.toRoll());
    assertTrue(s.cycleElement("water"));
    assertEquals(5, s.getCap("water"));
    assertEquals(2, s.toRoll().getSlots().size());
    assertTrue(s.cycleElement("fire"));
    assertNull(s.getPrimaryId());
    assertEquals(0, s.getCap("fire"));
    assertNull(s.toRoll());
  }

  @Test
  void capAdjustmentsRespectPrimarySecondaryBoundsAndRarityChanges() {
    var s = new ArtifactCreateSession();
    assertFalse(s.adjustSelectedCap(1));
    s.setSelectedElementId(" ");
    assertFalse(s.adjustSelectedCap(1));
    s.setSelectedElementId("fire");
    assertFalse(s.adjustSelectedCap(1));
    s.setCap("fire", 20);
    s.setPrimaryId("fire");
    assertTrue(s.adjustSelectedCap(-1));
    assertEquals(19, s.getCap("fire"));
    assertTrue(s.adjustSelectedCap(-100));
    assertEquals(10, s.getCap("fire"));
    assertFalse(s.adjustSelectedCap(-1));
    s.setCap("water", 100);
    s.setRarityId("common");
    assertEquals(5, s.getCap("water"));
    assertEquals(1, s.capRange("water").getMin());
    assertEquals(20, s.capRange("fire").getMax());
    s.setRarityId("missing");
    assertNull(s.toRoll());
    s.setRarityId("common");
    s.setCap("fire", 0);
    assertNull(s.toRoll());
    s.setPrimaryId("missing");
    assertNull(s.toRoll());
    s.setCap("disabled", 10);
    s.setPrimaryId("disabled");
    assertNull(s.toRoll());
    assertEquals("disabled", s.getPrimaryId());
  }

  @Test
  void exclusionsPreventConflictingSelectionAndPreviewLocksUntilChanged() {
    var s = new ArtifactCreateSession();
    s.cycleElement("fire");
    s.cycleElement("fire");
    ArtifactAffinityRegistry.registerExclude(List.of("fire"), List.of("water"));
    assertFalse(s.cycleElement("water"));
    var builder = mock(ArtifactItemBuilder.class);
    var model = new ArtifactModelEntry("wand", "v.STICK", null, null);
    var type = ArtifactTypeRegistry.getById("fire");
    when(builder.pickModel(type, "common")).thenReturn(model);
    when(builder.pickBaseName(type, "wand", "common")).thenReturn("Spark");
    s.ensurePreviewLock(builder);
    assertEquals("Spark", s.getPreviewBaseName());
    assertEquals("v.STICK", s.getPreviewModelPath());
    s.ensurePreviewLock(builder);
    verify(builder, times(1)).pickModel(type, "common");
    s.setPrimaryId("fire");
    assertEquals("Spark", s.getPreviewBaseName());
    s.setRarityId("common");
    assertNull(s.getPreviewBaseName());
    s.ensurePreviewLock(null);
    assertNull(s.getPreviewModelPath());
  }

  @Test
  void missingDefaultsAndRemovedTypesDoNotProduceArtifacts() {
    ArtifactRarityRegistry.clear();
    assertEquals("", new ArtifactCreateSession().getRarityId());
    ArtifactRarityRegistry.register(ArtifactGenerationTest.rarity("rare", 1, 1, 1));
    assertEquals("rare", new ArtifactCreateSession().getRarityId());
    var s = new ArtifactCreateSession();
    s.ensurePreviewLock(mock(ArtifactItemBuilder.class));
    s.setCap("missing", 10);
    s.setPrimaryId("missing");
    assertEquals("missing", s.getPrimaryId());
    s.setCap("fire", 10);
    s.setPrimaryId("fire");
    ArtifactTypeRegistry.clear();
    s.ensurePreviewLock(mock(ArtifactItemBuilder.class));
    assertNull(s.getPreviewBaseName());
    assertNull(s.toRoll());
    assertTrue(ArtifactCreateCache.reservedSlots().contains(ArtifactCreateCache.confirmSlot));
    assertThrows(
        UnsupportedOperationException.class, () -> ArtifactCreateCache.reservedSlots().clear());
  }
}
