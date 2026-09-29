package net.tfminecraft.magic;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.*;
import net.tfminecraft.magic.artifact.aura.*;
import net.tfminecraft.magic.charge.*;
import net.tfminecraft.magic.registry.ElementRegistry;
import org.bukkit.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.junit.jupiter.api.*;
import org.mockbukkit.mockbukkit.*;

class ChargeLoreTest {
  ServerMock server;

  @BeforeEach
  void setup() {
    server = MockBukkit.mock();
    Magic.plugin = mock(Magic.class);
    when(Magic.plugin.namespace()).thenReturn("magic");
    ChargeRegistry.clear();
    ChargeRegistry.register(new ChargeDef(1, "v.STONE", 20));
    TierBands.clear();
    TierBands.register("default", 1, 5);
    ElementRegistry.clear();
    for (String id : List.of("fire", "water", "earth", "air"))
      ElementRegistry.register(DomainTest.element(id));
    net.tfminecraft.magic.artifact.config.ArtifactTypeRegistry.clear();
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

  ItemStack blank() {
    var item = new ItemStack(Material.STONE);
    var meta = item.getItemMeta();
    meta.getPersistentDataContainer().set(ChargeKeys.chargeTier(), PersistentDataType.INTEGER, 1);
    item.setItemMeta(meta);
    return item;
  }

  List<String> plain(ItemStack item) {
    return item.getItemMeta().getLore().stream().map(ChatColor::stripColor).toList();
  }

  @Test
  void blankChargesDescribePlacementAndRefreshingDoesNotDuplicateLore() {
    assertNull(ChargeLore.updateItem(null));
    var ordinary = new ItemStack(Material.APPLE);
    ChargeLore.apply(ordinary);
    assertFalse(ordinary.getItemMeta().hasLore());
    var item = blank();
    var meta = item.getItemMeta();
    meta.setLore(List.of("Flavor", ""));
    item.setItemMeta(meta);
    ChargeLore.updateItem(item);
    assertEquals(List.of("Flavor", "", "Uncharged", "Place on a shrine pedestal"), plain(item));
    ChargeLore.apply(item);
    assertEquals(4, plain(item).size());
    assertFalse(item.getItemMeta().getEnchantmentGlintOverride());
    for (String old : List.of("Charge", "Uncharged", "§0§1§3Uncharged")) {
      meta = item.getItemMeta();
      meta.setLore(List.of("Flavor", old, "outdated"));
      item.setItemMeta(meta);
      ChargeLore.apply(item);
      assertEquals(List.of("Flavor", "Uncharged", "Place on a shrine pedestal"), plain(item));
    }
  }

  @Test
  void imprintedChargesSortPrimaryThenFillAndNameShowBandsAndGlint() {
    var item = blank();
    var charge = Charge.fromItem(item);
    for (String id : List.of("fire", "water", "earth", "air"))
      assertTrue(charge.imprintElement(item, id));
    charge.setFill("fire", 0);
    charge.setFill("water", 6);
    charge.setFill("air", 6);
    charge.setFill("earth", 4);
    charge.persistPdc(item);
    ChargeLore.apply(item);
    var lines = plain(item);
    assertEquals("Charge", lines.get(0));
    assertTrue(lines.get(1).toLowerCase(Locale.ROOT).contains("fire"));
    assertTrue(lines.get(1).endsWith("-"));
    assertTrue(lines.get(2).toLowerCase(Locale.ROOT).contains("air"));
    assertTrue(lines.get(2).endsWith("I"));
    assertTrue(lines.get(3).toLowerCase(Locale.ROOT).contains("water"));
    assertTrue(lines.get(4).toLowerCase(Locale.ROOT).contains("earth"));
    assertTrue(item.getItemMeta().getEnchantmentGlintOverride());
    Cache.artifactGlint = false;
    ChargeLore.apply(item);
    assertFalse(item.getItemMeta().getEnchantmentGlintOverride());
  }

  @Test
  void vesselLoreRefreshesAllInventorySectionsAndHandlesEmptyInputs() {
    VesselLore.updateInventory(null);
    assertNull(VesselLore.updateItem(null));
    var air = new ItemStack(Material.AIR);
    assertSame(air, VesselLore.updateItem(air));
    var player = server.addPlayer();
    var item = blank();
    player.getInventory().setItem(0, item);
    player.getInventory().setHelmet(item);
    player.getInventory().setItemInOffHand(item);
    VesselLore.updateInventory(player);
    assertTrue(player.getInventory().getItem(0).getItemMeta().hasLore());
    assertTrue(player.getInventory().getHelmet().getItemMeta().hasLore());
    assertTrue(player.getInventory().getItemInOffHand().getItemMeta().hasLore());
  }
}
