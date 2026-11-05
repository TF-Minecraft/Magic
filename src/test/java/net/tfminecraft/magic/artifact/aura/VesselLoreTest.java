package net.tfminecraft.magic.artifact.aura;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.List;
import net.tfminecraft.magic.Cache;
import net.tfminecraft.magic.Magic;
import net.tfminecraft.magic.artifact.ArtifactKeys;
import net.tfminecraft.magic.charge.ChargeDef;
import net.tfminecraft.magic.charge.ChargeKeys;
import net.tfminecraft.magic.charge.ChargeLore;
import net.tfminecraft.magic.charge.ChargeRegistry;
import net.tfminecraft.magic.charge.TierBands;
import net.tfminecraft.magic.registry.ElementRegistry;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.persistence.PersistentDataType;
import org.junit.jupiter.api.*;
import org.mockbukkit.mockbukkit.MockBukkit;

class VesselLoreTest {

  @BeforeEach
  void setup() {
    MockBukkit.mock();
    Magic.plugin = mock(Magic.class);
    when(Magic.plugin.namespace()).thenReturn("magic");
    ChargeRegistry.clear();
    TierBands.clear();
    TierBands.register("default", 1, 5);
    ElementRegistry.clear();
    Cache.artifactGlint = true;
  }

  @AfterEach
  void cleanup() {
    Magic.plugin = null;
    MockBukkit.unmock();
    ChargeRegistry.clear();
    ElementRegistry.clear();
    TierBands.clear();
  }

  static ItemStack withLore(String... lines) {
    var item = new ItemStack(Material.STONE);
    var meta = item.getItemMeta();
    meta.setLore(List.of(lines));
    item.setItemMeta(meta);
    return item;
  }

  static ItemStack withKey(NamespacedKey key) {
    var item = new ItemStack(Material.STONE);
    var meta = item.getItemMeta();
    meta.getPersistentDataContainer().set(key, PersistentDataType.INTEGER, 1);
    item.setItemMeta(meta);
    return item;
  }

  @Test
  void onlyItemsWithVesselKeysAChargeMatchOrSlashedLoreAreLookedAt() {
    assertFalse(VesselLore.mayHaveVesselLore(new ItemStack(Material.STONE)));
    assertFalse(VesselLore.mayHaveVesselLore(withLore("Only lore", "A sword, well kept")));
    assertTrue(VesselLore.mayHaveVesselLore(withLore("Only lore", "Fire 3 / 10")));
    assertTrue(VesselLore.mayHaveVesselLore(withKey(ArtifactKeys.auraCap())));
    assertTrue(VesselLore.mayHaveVesselLore(withKey(ArtifactKeys.auraFill())));
    assertTrue(VesselLore.mayHaveVesselLore(withKey(ArtifactKeys.auraData())));
    assertTrue(VesselLore.mayHaveVesselLore(withKey(ChargeKeys.chargeTier())));
    var shard = new ItemStack(Material.AMETHYST_SHARD);
    try (var registry = mockStatic(ChargeRegistry.class)) {
      registry.when(() -> ChargeRegistry.match(shard)).thenReturn(new ChargeDef(1, "v.AMETHYST_SHARD", 20));
      assertTrue(VesselLore.mayHaveVesselLore(shard));
    }
  }

  @Test
  void itemsNoWriterTouchesComeBackUnreadAndUnchanged() {
    var plain = withLore("Only lore");
    var before = plain.clone();
    try (var charge = mockStatic(ChargeLore.class)) {
      assertSame(plain, VesselLore.updateItem(plain));
      charge.verifyNoInteractions();
    }
    assertEquals(before, plain);
    // Lore an artifact could be read back from still goes to the artifact writer, which leaves this one alone.
    var slashed = withLore("Ten / twenty");
    assertSame(slashed, VesselLore.updateItem(slashed));
    assertEquals(withLore("Ten / twenty"), slashed);
  }

  @Test
  void inventoryRefreshSetsOnlyTheSlotsWhoseItemChanged() {
    var blank = withKey(ChargeKeys.chargeTier());
    var current = withKey(ChargeKeys.chargeTier());
    ChargeLore.apply(current);
    var player = mock(Player.class);
    var inventory = mock(PlayerInventory.class);
    when(player.getInventory()).thenReturn(inventory);
    when(inventory.getStorageContents())
        .thenReturn(new ItemStack[] {withLore("Only lore"), null, new ItemStack(Material.AIR), blank});
    when(inventory.getArmorContents()).thenReturn(new ItemStack[] {null, current.clone()});
    var offHand = withKey(ChargeKeys.chargeTier());
    when(inventory.getExtraContents()).thenReturn(new ItemStack[] {offHand});
    VesselLore.updateInventory(player);
    verify(inventory).setItem(3, blank);
    verify(inventory).setItem(6, offHand);
    verify(inventory, times(2)).setItem(anyInt(), any());
    assertEquals(current, blank, "the blank charge got its lore");
  }
}
