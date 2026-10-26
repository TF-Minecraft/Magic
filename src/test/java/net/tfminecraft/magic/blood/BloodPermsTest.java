package net.tfminecraft.magic.blood;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Consumer;

import de.tr7zw.nbtapi.NBT;
import de.tr7zw.nbtapi.iface.ReadWriteItemNBT;
import org.bukkit.Material;
import org.bukkit.entity.HumanEntity;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

class BloodPermsTest {
  @TempDir Path temp;
  BloodFixture fx;

  @BeforeEach
  void setup() {
    fx = new BloodFixture(temp);
  }

  @AfterEach
  void cleanup() {
    BloodPerms.clearAll();
    fx.close();
  }

  @Test
  void aPathsCharacterHoldsItsPermissionOnlyWhilePlayed() throws Exception {
    Files.writeString(temp.resolve("p.yml"), String.join("\n",
        "enabled: true",
        "permissions:",
        "  devotee: magic.blood.bloodsworn",
        "  \"true\": ' '",
        "  none: magic.blood.none",
        ""));
    BloodConfig.load(temp.resolve("p.yml").toFile(), fx.logger);
    assertEquals("magic.blood.bloodsworn", ChoiceConfig.permissionFor(BloodPath.DEVOTEE));
    assertNull(ChoiceConfig.permissionFor(BloodPath.TRUE));
    assertNull(ChoiceConfig.permissionFor(BloodPath.NONE));
    Files.writeString(temp.resolve("q.yml"), "enabled: true\npermissions:\n  devotee: 3\n");
    BloodConfig.load(temp.resolve("q.yml").toFile(), fx.logger);
    assertNull(ChoiceConfig.permissionFor(BloodPath.DEVOTEE));
    BloodConfig.load(temp.resolve("p.yml").toFile(), fx.logger);

    PlayerMock mage = fx.player("mage");
    BloodPerms.refresh(mage);
    assertFalse(mage.hasPermission("magic.blood.bloodsworn"));
    BloodRecord record = fx.record("mage", BloodStage.FINAL);
    BloodPerms.refresh(mage);
    assertFalse(BloodPerms.holds(mage.getUniqueId()));

    record.setPath(BloodPath.DEVOTEE);
    BloodPerms.refresh(mage);
    assertTrue(mage.hasPermission("magic.blood.bloodsworn"));
    BloodPerms.refresh(mage);
    assertTrue(mage.hasPermission("magic.blood.bloodsworn"));

    BloodPerms.clear(mage);
    assertFalse(mage.hasPermission("magic.blood.bloodsworn"));
    BloodPerms.clear(mage);

    BloodPerms.refreshAll();
    assertTrue(mage.hasPermission("magic.blood.bloodsworn"));
    mage.getEffectivePermissions().stream().filter(p -> p.getAttachment() != null)
        .forEach(p -> mage.removeAttachment(p.getAttachment()));
    BloodPerms.clear(mage);
    assertFalse(BloodPerms.holds(mage.getUniqueId()));

    record.setPath(BloodPath.TRUE);
    BloodPerms.refresh(mage);
    assertFalse(BloodPerms.holds(mage.getUniqueId()));
    record.setPath(BloodPath.DEVOTEE);
    BloodPerms.refresh(mage);
    BloodPerms.clearAll();
    assertFalse(mage.hasPermission("magic.blood.bloodsworn"));
  }

  @Test
  @SuppressWarnings("unchecked")
  void closingAnInventoryMarksSkinnedBloodPiecesAgain() {
    PlayerMock mage = fx.player("mage");
    ItemStack piece = BloodArmour.bind(new ItemStack(Material.LEATHER_HELMET), BloodFixture.id("mage"));
    mage.getInventory().setItem(3, piece);
    mage.getInventory().setItem(4, new ItemStack(Material.STONE));
    BloodListener listener = new BloodListener();
    InventoryView view = mock(InventoryView.class);
    when(view.getPlayer()).thenReturn(mage);
    listener.onInventoryClose(new InventoryCloseEvent(view));
    assertEquals(piece, mage.getInventory().getItem(3));

    MockBukkit.createMockPlugin("NBTAPI");
    try (var nbt = mockStatic(NBT.class)) {
      nbt.when(() -> NBT.modify(any(ItemStack.class), any(Consumer.class))).thenAnswer(i -> {
        ItemStack item = i.getArgument(0);
        ItemMeta meta = item.getItemMeta();
        meta.setLore(List.of("marked"));
        item.setItemMeta(meta);
        return null;
      });
      listener.onInventoryClose(new InventoryCloseEvent(view));
      assertEquals(List.of("marked"), mage.getInventory().getItem(3).getItemMeta().getLore());
      assertTrue(BloodArmour.is(mage.getInventory().getItem(3)));
      assertEquals(Material.STONE, mage.getInventory().getItem(4).getType());

      InventoryView other = mock(InventoryView.class);
      when(other.getPlayer()).thenReturn(mock(HumanEntity.class));
      listener.onInventoryClose(new InventoryCloseEvent(other));
      nbt.verify(() -> NBT.modify(any(ItemStack.class), any(Consumer.class)), times(1));
    }
  }
}
