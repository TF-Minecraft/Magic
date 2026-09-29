package net.tfminecraft.magic;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.*;
import net.tfminecraft.magic.artifact.config.*;
import net.tfminecraft.magic.artifact.create.*;
import net.tfminecraft.magic.artifact.generate.*;
import net.tfminecraft.magic.artifact.sacrifice.*;
import net.tfminecraft.magic.artifact.shrine.*;
import org.junit.jupiter.api.*;

class ArtifactCreateEdgeTest {
  ArtifactCreateTest f = new ArtifactCreateTest();

  @BeforeEach
  void setup() {
    f.setup();
    Cache.artifactAuraCap = 100;
  }

  @AfterEach
  void cleanup() {
    f.cleanup();
    ShrineRegistry.clear();
    SacrificeRegistry.clear();
  }

  @Test
  void invisibleSecondariesAreRefusedAndHiddenExistingCapsAreOmitted() {
    var s = new ArtifactCreateSession();
    s.setCap("fire", 10);
    s.setPrimaryId("fire");
    ShrineRegistry.register(new ShrineElementDef("water", false, List.of()));
    SacrificeRegistry.setEnabled(false);
    assertFalse(s.cycleElement("water"));
    s.setCap("water", 5);
    assertEquals(1, s.toRoll().getSlots().size());
    s.setPrimaryId("water");
    s.setPrimaryId("fire");
    assertEquals(0, s.getCap("water"));
  }

  @Test
  void publicSelectionChangesHandleBlankMissingAndRemovedPrimaries() {
    var s = new ArtifactCreateSession();
    s.setRarityId("common");
    s.setCap("fire", 20);
    s.setPrimaryId(" ");
    assertEquals(20, s.getCap("fire"));
    assertNull(s.getPrimaryId());
    s.setCap("fire", 10);
    s.setPrimaryId("fire");
    s.setCap("fire", 0);
    s.ensurePreviewLock(new ArtifactItemBuilder());
    assertNull(s.getPreviewBaseName());
    s.setRarityId("common");
    assertNull(s.getPrimaryId());
    s.setCap("unknown", 1);
    s.setPrimaryId("unknown");
    assertNull(s.toRoll());
    assertEquals(.01, s.capRange(null).getMin());
  }

  @Test
  void zeroConfigCapsStillGivePositiveEditorBounds() {
    ArtifactTypeRegistry.register(
        new ArtifactTypeDef(
            "fire", "name", "model", true, 1, Map.of("common", new CapRange(0, 0)), Map.of()));
    var s = new ArtifactCreateSession();
    assertEquals(.01, s.capRange("fire").getMin());
    assertEquals(.01, s.capRange("fire").getMax());
  }

  @Test
  void previewWithoutModelsAndEmptyNamesIsRetriedAndRarityChangesInvalidateIt() {
    var s = new ArtifactCreateSession();
    s.setCap("fire", 10);
    s.setPrimaryId("fire");
    var b = mock(ArtifactItemBuilder.class);
    var t = ArtifactTypeRegistry.getById("fire");
    when(b.pickBaseName(eq(t), nullable(String.class), eq("common"))).thenReturn("", "Spark");
    s.ensurePreviewLock(b);
    assertEquals("", s.getPreviewModelPath());
    s.ensurePreviewLock(b);
    assertEquals("Spark", s.getPreviewBaseName());
    s.ensurePreviewLock(b);
    verify(b, times(2)).pickModel(t, "common");
    s.setCap("water", 1);
    s.setPrimaryId("water");
    s.ensurePreviewLock(b);
    assertNull(s.getPreviewBaseName());
    s.ensurePreviewLock(b);
    s.setRarityId(null);
    s.ensurePreviewLock(b);
    s.ensurePreviewLock(b);
    assertNull(s.getPreviewBaseName());
  }

  @Test
  void nonFiniteCapsAndDeltasCannotPoisonPreviewAmounts() {
    var s = new ArtifactCreateSession();
    s.setCap("fire", 10);
    s.setPrimaryId("fire");
    s.setSelectedElementId("fire");
    for (double bad :
        new double[] {Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY}) {
      s.setCap("fire", bad);
      assertEquals(10, s.getCap("fire"));
      assertFalse(s.adjustSelectedCap(bad));
      assertEquals(10, s.getCap("fire"));
    }
  }
}
