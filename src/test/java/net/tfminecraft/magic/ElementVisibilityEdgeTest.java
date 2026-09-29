package net.tfminecraft.magic;

import static org.junit.jupiter.api.Assertions.*;

import java.util.*;
import net.tfminecraft.magic.artifact.config.*;
import net.tfminecraft.magic.artifact.sacrifice.*;
import net.tfminecraft.magic.artifact.shrine.*;
import net.tfminecraft.magic.model.*;
import org.junit.jupiter.api.*;

class ElementVisibilityEdgeTest {
  @BeforeEach
  @AfterEach
  void reset() {
    ArtifactTypeRegistry.clear();
    ShrineRegistry.clear();
    SacrificeRegistry.clear();
  }

  ArtifactTypeDef type(String id, boolean enabled) {
    return new ArtifactTypeDef(id, "name", "model", enabled, 1, Map.of(), Map.of());
  }

  @Test
  void enabledTypesResolveExactTrimmedAndCaseInsensitiveIds() {
    assertTrue(ElementVisibility.typeEnabled(null));
    assertTrue(ElementVisibility.typeEnabled(" "));
    assertTrue(ElementVisibility.typeEnabled("unknown"));
    ArtifactTypeRegistry.register(type("fire", false));
    assertFalse(ElementVisibility.typeEnabled("fire"));
    assertFalse(ElementVisibility.typeEnabled(" FIRE "));
    ArtifactTypeRegistry.register(type("Water", true));
    assertTrue(ElementVisibility.typeEnabled("water"));
    assertTrue(ElementVisibility.typeEnabled("earth"));
    assertFalse(ElementVisibility.shownOnCharge("fire"));
    assertFalse(ElementVisibility.shownOnArtifact("fire", "fire"));
  }

  @Test
  void shrineAndSacrificeAvailabilityDetermineVisibleElements() {
    assertFalse(ElementVisibility.playerCanCharge(null));
    assertFalse(ElementVisibility.playerCanCharge(" "));
    assertTrue(ElementVisibility.shownOnCharge("fire"));
    assertTrue(ElementVisibility.shownOnArtifact("fire", null));
    ShrineRegistry.register(new ShrineElementDef("fire", true, List.of()));
    assertTrue(ElementVisibility.playerCanCharge("fire"));
    ShrineRegistry.register(new ShrineElementDef("fire", false, List.of()));
    SacrificeRegistry.setEnabled(false);
    assertFalse(ElementVisibility.playerCanCharge("fire"));
    assertFalse(ElementVisibility.shownOnArtifact("fire", null));
    assertFalse(ElementVisibility.shownOnArtifact("fire", " "));
    assertFalse(ElementVisibility.shownOnArtifact("fire", "water"));
    assertTrue(ElementVisibility.shownOnArtifact("fire", "FIRE"));
    SacrificeRegistry.setEnabled(true);
    assertFalse(ElementVisibility.playerCanCharge("fire"));
    SacrificeRegistry.register(new SacrificeElementDef("fire", false, List.of(), "", "", "", 0));
    assertFalse(ElementVisibility.playerCanCharge("fire"));
    SacrificeRegistry.register(new SacrificeElementDef("fire", true, List.of(), "", "", "", 0));
    assertTrue(ElementVisibility.playerCanCharge("fire"));
    SacrificeRegistry.setSceneryCharge(false);
    ShrineRegistry.register(new ShrineElementDef("fire", true, List.of()));
    assertTrue(ElementVisibility.playerCanCharge("fire"));
  }
}
