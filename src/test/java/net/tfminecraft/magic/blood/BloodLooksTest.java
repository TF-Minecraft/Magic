package net.tfminecraft.magic.blood;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Consumer;

import de.tr7zw.nbtapi.NBT;
import de.tr7zw.nbtapi.iface.ReadWriteItemNBT;
import de.tr7zw.nbtapi.iface.ReadWriteNBT;
import net.tfminecraft.tlibs.TLibs;
import net.tfminecraft.tlibs.objects.api.ItemAPI;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockbukkit.mockbukkit.MockBukkit;

class BloodLooksTest {
  @TempDir Path temp;
  BloodFixture fx;
  final String id = BloodFixture.id("mage");

  @BeforeEach
  void setup() {
    fx = new BloodFixture(temp);
  }

  @AfterEach
  void cleanup() {
    fx.close();
  }

  @Test
  void armourPiecesTakeTheirLooks() throws Exception {
    Files.writeString(temp.resolve("a.yml"), String.join("\n",
        "enabled: true",
        "armour:",
        "  devotee:",
        "    - m.a.plain",
        "    - { item: m.a.robe, look: ia.blood:robe }",
        "    - { item: ' ', look: ia.x:y }",
        "    - { look: ia.x:y }",
        "    - { item: m.a.hood, look: ' ' }",
        "    - { item: m.a.boots, look: 3 }",
        "    - 7",
        "    - ' '",
        ""));
    BloodConfig.load(temp.resolve("a.yml").toFile(), fx.logger);
    assertEquals(List.of(new ChoiceConfig.Piece("m.a.plain", null), new ChoiceConfig.Piece("m.a.robe", "ia.blood:robe"),
        new ChoiceConfig.Piece("m.a.hood", null), new ChoiceConfig.Piece("m.a.boots", null)),
        ChoiceConfig.armourFor(BloodPath.DEVOTEE));

    try (var libs = mockStatic(TLibs.class)) {
      ItemAPI api = mock(ItemAPI.class, RETURNS_DEEP_STUBS);
      libs.when(TLibs::getItemAPI).thenReturn(api);
      when(api.getCreator().getItemFromPath(any())).thenAnswer(i -> new ItemStack(Material.LEATHER_CHESTPLATE));
      when(api.getArmorMerger().merge(any(), any(), eq("ia.blood:robe"))).thenReturn(new ItemStack(Material.LEATHER_HELMET));
      List<ItemStack> built = BloodArmour.create(BloodPath.DEVOTEE, id);
      assertEquals(4, built.size());
      assertEquals(Material.LEATHER_HELMET, built.get(1).getType());
      assertTrue(BloodArmour.is(built.get(1)));
      assertEquals(Material.LEATHER_CHESTPLATE, built.get(0).getType());

      when(api.getArmorMerger().merge(any(), any(), eq("ia.blood:robe"))).thenReturn(null);
      assertEquals(Material.LEATHER_CHESTPLATE, BloodArmour.create(BloodPath.DEVOTEE, id).get(1).getType());
      when(api.getArmorMerger().merge(any(), any(), eq("ia.blood:robe"))).thenReturn(new ItemStack(Material.AIR));
      assertEquals(Material.LEATHER_CHESTPLATE, BloodArmour.create(BloodPath.DEVOTEE, id).get(1).getType());
      when(api.getArmorMerger().merge(any(), any(), eq("ia.blood:robe"))).thenThrow(new IllegalStateException("no look"));
      assertEquals(Material.LEATHER_CHESTPLATE, BloodArmour.create(BloodPath.DEVOTEE, id).get(1).getType());
    }
  }

  @Test
  @SuppressWarnings("unchecked")
  void itemsAdderIsToldToLeaveBoundItemsAlone() {
    ItemStack item = new ItemStack(Material.STONE);
    BloodLooks.protect(item);
    MockBukkit.createMockPlugin("NBTAPI");
    try (var nbt = mockStatic(NBT.class)) {
      ReadWriteItemNBT root = mock(ReadWriteItemNBT.class);
      ReadWriteNBT compound = mock(ReadWriteNBT.class);
      when(root.getCompound("itemsadder")).thenReturn(compound);
      nbt.when(() -> NBT.modify(eq(item), any(Consumer.class))).thenAnswer(i -> {
        ((Consumer<ReadWriteItemNBT>) i.getArgument(1)).accept(root);
        return null;
      });
      BloodLooks.protect(item);
      verify(compound, never()).setBoolean(any(), any());
      when(root.hasTag("itemsadder")).thenReturn(true);
      BloodLooks.protect(item);
      verify(compound).setBoolean("override_auto_update", true);
      nbt.when(() -> NBT.modify(eq(item), any(Consumer.class))).thenThrow(new IllegalStateException("no nms"));
      BloodLooks.protect(item);
      nbt.when(() -> NBT.modify(eq(item), any(Consumer.class))).thenThrow(new NoClassDefFoundError("nbt"));
      BloodLooks.protect(item);
    }
  }
}
