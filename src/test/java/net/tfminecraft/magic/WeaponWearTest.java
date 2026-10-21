package net.tfminecraft.magic;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import net.Indyuce.mmoitems.api.event.item.UntargetedWeaponUseEvent;
import net.Indyuce.mmoitems.api.interaction.weapon.Weapon;
import net.tfminecraft.magic.gear.GearKeys;
import net.tfminecraft.magic.gear.WeaponWear;
import net.tfminecraft.magic.listener.WeaponWearListener;
import org.bukkit.Material;
import org.bukkit.event.player.PlayerItemDamageEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.persistence.PersistentDataType;
import org.junit.jupiter.api.*;
import org.mockbukkit.mockbukkit.*;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

class WeaponWearTest {
  ServerMock server;
  Magic plugin;

  @BeforeEach
  void setup() {
    server = MockBukkit.mock();
    plugin = mock(Magic.class, RETURNS_DEEP_STUBS);
    Magic.plugin = plugin;
    when(plugin.namespace()).thenReturn("magic");
    when(plugin.getName()).thenReturn("Magic");
  }

  @AfterEach
  void cleanup() {
    Magic.plugin = null;
    MockBukkit.unmock();
  }

  static ItemStack damaged(Material material, int damage) {
    var stack = new ItemStack(material);
    var meta = (Damageable) stack.getItemMeta();
    meta.setDamage(damage);
    stack.setItemMeta(meta);
    return stack;
  }

  static ItemStack gear(ItemStack stack) {
    var meta = stack.getItemMeta();
    meta.getPersistentDataContainer().set(GearKeys.archetype(), PersistentDataType.STRING, "staff");
    stack.setItemMeta(meta);
    return stack;
  }

  static int damage(ItemStack stack) {
    return ((Damageable) stack.getItemMeta()).getDamage();
  }

  @Test
  void remainingCountsVanillaOrComponentDurabilityAndSkipsWhatCannotWear() {
    assertEquals(-1, WeaponWear.remaining(null));
    assertEquals(-1, WeaponWear.remaining(new ItemStack(Material.AIR)));
    assertEquals(-1, WeaponWear.remaining(new ItemStack(Material.STICK)));
    var unbreakable = new ItemStack(Material.IRON_SWORD);
    var meta = unbreakable.getItemMeta();
    meta.setUnbreakable(true);
    unbreakable.setItemMeta(meta);
    assertEquals(-1, WeaponWear.remaining(unbreakable));
    assertEquals(250, WeaponWear.remaining(new ItemStack(Material.IRON_SWORD)));
    assertEquals(0, WeaponWear.remaining(damaged(Material.IRON_SWORD, 300)));
    var stick = new ItemStack(Material.STICK);
    var stickMeta = (Damageable) stick.getItemMeta();
    stickMeta.setMaxDamage(500);
    stick.setItemMeta(stickMeta);
    assertEquals(500, WeaponWear.remaining(stick));
  }

  @Test
  void wornMeansTheLastPointOrNoneLeft() {
    assertFalse(WeaponWear.isWorn(new ItemStack(Material.STICK)));
    assertFalse(WeaponWear.isWorn(damaged(Material.IRON_SWORD, 248)));
    assertTrue(WeaponWear.isWorn(damaged(Material.IRON_SWORD, 249)));
    assertTrue(WeaponWear.isWorn(damaged(Material.IRON_SWORD, 250)));
  }

  @Test
  void wearSpendsPointsButNeverTheLastOne() {
    var sword = new ItemStack(Material.IRON_SWORD);
    WeaponWear.wear(sword, 0);
    assertEquals(0, damage(sword));
    WeaponWear.wear(sword, 2);
    assertEquals(2, damage(sword));
    WeaponWear.wear(sword, 1000);
    assertEquals(249, damage(sword));
    WeaponWear.wear(sword, 1);
    assertEquals(249, damage(sword));
    var stick = new ItemStack(Material.STICK);
    WeaponWear.wear(stick, 1);
    assertFalse(stick.getItemMeta() instanceof Damageable d && d.hasDamage());
  }

  @Test
  void itemDamageIsCancelledOnStickAndPaperGearAndCappedBeforeTheLastPointOnSwords() {
    var listener = new WeaponWearListener();
    PlayerMock player = server.addPlayer();
    var plain = new ItemStack(Material.IRON_SWORD);
    var notGear = new PlayerItemDamageEvent(player, plain, 1);
    listener.onItemDamage(notGear);
    assertFalse(notGear.isCancelled());
    var paperStick = new ItemStack(Material.PAPER);
    var paperMeta = (Damageable) paperStick.getItemMeta();
    paperMeta.setMaxDamage(2000);
    paperMeta.setDamage(10);
    paperStick.setItemMeta(paperMeta);
    var skinned = new PlayerItemDamageEvent(player, gear(paperStick), 1);
    listener.onItemDamage(skinned);
    assertTrue(skinned.isCancelled());
    var worn = new PlayerItemDamageEvent(player, gear(damaged(Material.IRON_SWORD, 249)), 1);
    listener.onItemDamage(worn);
    assertTrue(worn.isCancelled());
    var nearlyWorn = new PlayerItemDamageEvent(player, gear(damaged(Material.IRON_SWORD, 240)), 20);
    listener.onItemDamage(nearlyWorn);
    assertFalse(nearlyWorn.isCancelled());
    assertEquals(9, nearlyWorn.getDamage());
    var fresh = new PlayerItemDamageEvent(player, gear(new ItemStack(Material.IRON_SWORD)), 1);
    listener.onItemDamage(fresh);
    assertFalse(fresh.isCancelled());
    assertEquals(1, fresh.getDamage());
  }

  @Test
  void wornWandShotIsRefusedWithATitleAndEverythingElsePasses() {
    var listener = new WeaponWearListener();
    PlayerMock player = server.addPlayer();
    var weapon = mock(Weapon.class);
    var event = mock(UntargetedWeaponUseEvent.class);
    when(event.getWeapon()).thenReturn(weapon);
    when(event.getPlayer()).thenReturn(player);
    var plainWorn = damaged(Material.IRON_SWORD, 249);
    var freshGear = gear(new ItemStack(Material.IRON_SWORD));
    var wornGear = gear(damaged(Material.IRON_SWORD, 249));
    when(weapon.getItem()).thenReturn(plainWorn);
    listener.onWeaponShot(event);
    when(weapon.getItem()).thenReturn(freshGear);
    listener.onWeaponShot(event);
    verify(event, never()).setCancelled(true);
    when(weapon.getItem()).thenReturn(wornGear);
    listener.onWeaponShot(event);
    verify(event).setCancelled(true);
    assertNotNull(player.nextTitle());
  }
}
