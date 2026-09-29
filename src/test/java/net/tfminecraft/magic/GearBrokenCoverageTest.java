package net.tfminecraft.magic;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.*;
import net.Indyuce.mmoitems.MMOItems;
import net.tfminecraft.magic.gear.*;
import org.bukkit.*;
import org.bukkit.inventory.*;
import org.bukkit.persistence.PersistentDataType;
import org.junit.jupiter.api.*;
import org.mockbukkit.mockbukkit.MockBukkit;

class GearBrokenCoverageTest extends GearMmoCoverageSupport {
  @Test
  void marksAndClearsFlagsWithoutLosingOrphans() {
    GearBrokenMarker.clearWarningSession();
    for (ItemStack s : Arrays.asList(null, new ItemStack(Material.AIR))) {
      assertFalse(GearBrokenMarker.isBroken(s));
      GearBrokenMarker.mark(s);
      GearBrokenMarker.clear(s);
      assertTrue(GearBrokenMarker.orphans(s).isEmpty());
      GearBrokenMarker.addOrphans(s, List.of(new GearBrokenMarker.Orphan("RUNE", "A")));
    }
    var s = item();
    assertFalse(GearBrokenMarker.isBroken(s));
    tag(s, GearKeys.broken(), PersistentDataType.BYTE, (byte) 0);
    assertFalse(GearBrokenMarker.isBroken(s));
    GearBrokenMarker.addOrphans(s, null);
    GearBrokenMarker.addOrphans(s, List.of());
    assertTrue(GearBrokenMarker.orphans(s).isEmpty());
    tag(s, GearKeys.orphans(), PersistentDataType.STRING, " ");
    assertTrue(GearBrokenMarker.orphans(s).isEmpty());
    tag(s, GearKeys.orphans(), PersistentDataType.STRING, "bad,:id,type:, RUNE : A ");
    assertEquals(List.of(new GearBrokenMarker.Orphan("RUNE", "A")), GearBrokenMarker.orphans(s));
    GearBrokenMarker.addOrphans(s, List.of(new GearBrokenMarker.Orphan("RUNE", "B")));
    try (var lore = mockStatic(WeaponLore.class)) {
      GearBrokenMarker.mark(s);
      lore.verify(() -> WeaponLore.apply(s));
    }
    assertTrue(GearBrokenMarker.isBroken(s));
    assertEquals(2, GearBrokenMarker.orphans(s).size());
    GearBrokenMarker.clear(s);
    assertFalse(GearBrokenMarker.isBroken(s));
    assertTrue(GearBrokenMarker.orphans(s).isEmpty());
  }

  @Test
  void rebuildsAvailableRunesAndWarnsForUnavailableOnes() {
    var s = item();
    GearBrokenMarker.addOrphans(
        s,
        List.of(
            new GearBrokenMarker.Orphan("RUNE", "good"),
            new GearBrokenMarker.Orphan("RUNE", "missing"),
            new GearBrokenMarker.Orphan("RUNE", "air"),
            new GearBrokenMarker.Orphan("RUNE", "error")));
    assertTrue(GearBrokenMarker.buildOrphanItems(s).isEmpty());
    MockBukkit.createMockPlugin("MMOItems");
    var rune = item();
    when(MMOItems.plugin.getItem("RUNE", "missing")).thenReturn(null);
    when(MMOItems.plugin.getItem("RUNE", "good")).thenReturn(rune);
    when(MMOItems.plugin.getItem("RUNE", "air")).thenReturn(new ItemStack(Material.AIR));
    when(MMOItems.plugin.getItem("RUNE", "error")).thenThrow(new IllegalArgumentException());
    assertEquals(List.of(rune), GearBrokenMarker.buildOrphanItems(s));
    GearBrokenMarker.clearWarningSession();
    GearBrokenMarker.notifyOnce(null, s, null);
    GearBrokenMarker.notifyOnce(null, s, List.of());
    GearBrokenMarker.clearWarningSession();
    GearBrokenMarker.notifyOnce(server.addPlayer(), s, List.of("missing"));
    GearBrokenMarker.clearWarningSession();
    GearBrokenMarker.notifyOnce(null, s, List.of());
  }
}
