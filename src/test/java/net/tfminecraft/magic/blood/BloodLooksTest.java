package net.tfminecraft.magic.blood;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

import de.tr7zw.nbtapi.NBT;
import de.tr7zw.nbtapi.iface.ReadWriteItemNBT;
import de.tr7zw.nbtapi.iface.ReadWriteNBT;
import net.tfminecraft.tlibs.TLibs;
import net.tfminecraft.tlibs.character.CharacterInfo;
import net.tfminecraft.tlibs.character.CharacterRef;
import net.tfminecraft.tlibs.character.CharacterStatus;
import net.tfminecraft.tlibs.character.Characters;
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
  void trueArmourIsNamedAfterTheHuntVictimsFromTheMostImportantDown() throws Exception {
    Files.writeString(temp.resolve("n.yml"), String.join("\n",
        "enabled: true",
        "armour:",
        "  \"true\":",
        "    - { item: m.a.skull, name: '#5c0202{victim} Skull' }",
        "    - { item: m.a.ribs, name: '{victim} Ribcage' }",
        "    - { item: m.a.plain, name: ' ' }",
        "    - { item: m.a.hide, name: 3 }",
        "    - { item: m.a.bones, name: '{victim} Bones' }",
        ""));
    BloodConfig.load(temp.resolve("n.yml").toFile(), fx.logger);
    assertEquals(new ChoiceConfig.Piece("m.a.skull", null, "#5c0202{victim} Skull"), ChoiceConfig.armourFor(BloodPath.TRUE).get(0));
    assertNull(ChoiceConfig.armourFor(BloodPath.TRUE).get(2).name());
    assertNull(ChoiceConfig.armourFor(BloodPath.TRUE).get(3).name());

    String cara = BloodFixture.id("cara");
    String bram = BloodFixture.id("bram");
    String dell = BloodFixture.id("dell");
    String gone = BloodFixture.id("gone");
    BloodRecord record = BloodRecord.create(id, null);
    record.getSacrifices().add(new BloodRecord.Sacrifice("stranger", null, "HUNT", 1, 1));
    record.getSacrifices().add(new BloodRecord.Sacrifice(bram, null, "HUNT", 3, 2, 9));
    record.getSacrifices().add(new BloodRecord.Sacrifice(cara, null, "HUNT", 3, 3, 7));
    record.getSacrifices().add(new BloodRecord.Sacrifice(dell, null, "HUNT", 2, 0, 12));
    record.getSacrifices().add(new BloodRecord.Sacrifice("late", null, "HUNT", 2, 5, 12));
    record.getSacrifices().add(new BloodRecord.Sacrifice("named", null, "FINAL", 0, 6));
    record.getSacrifices().add(new BloodRecord.Sacrifice("skipped", null, "SKIPPED", 0, 7));
    record.getSacrifices().add(new BloodRecord.Sacrifice("far", null, "HUNT", 1, 0, 30));
    assertEquals(List.of(cara, bram, dell, "late", "far", "stranger"), BloodArmour.victims(record));

    try (var libs = mockStatic(TLibs.class); var chars = mockStatic(Characters.class)) {
      ItemAPI api = mock(ItemAPI.class, RETURNS_DEEP_STUBS);
      libs.when(TLibs::getItemAPI).thenReturn(api);
      when(api.getCreator().getItemFromPath(any())).thenAnswer(i -> new ItemStack(Material.LEATHER_CHESTPLATE));
      chars.when(() -> Characters.info(any())).thenReturn(Optional.empty());
      chars.when(() -> Characters.info(UUID.fromString(cara))).thenReturn(named(cara, "Cara"));
      chars.when(() -> Characters.info(UUID.fromString(bram))).thenReturn(named(bram, "Br§cam"));

      List<ItemStack> pieces = BloodArmour.create(BloodPath.TRUE, id, List.of(cara, bram));
      assertEquals(5, pieces.size());
      assertTrue(pieces.get(0).getItemMeta().getDisplayName().endsWith("Cara Skull"));
      assertEquals("Brcam Ribcage", pieces.get(1).getItemMeta().getDisplayName());
      assertFalse(pieces.get(2).getItemMeta().hasDisplayName());
      assertFalse(pieces.get(3).getItemMeta().hasDisplayName());
      assertTrue(pieces.get(4).getItemMeta().getDisplayName().endsWith("Cara Bones"));
      assertTrue(pieces.stream().allMatch(BloodArmour::is));

      List<ItemStack> unknown = BloodArmour.create(BloodPath.TRUE, id, List.of(gone));
      assertFalse(unknown.get(0).getItemMeta().hasDisplayName());
      assertFalse(BloodArmour.create(BloodPath.TRUE, id).get(0).getItemMeta().hasDisplayName());
    }
  }

  private static Optional<CharacterInfo> named(String id, String name) {
    return Optional.of(new CharacterInfo(new CharacterRef(UUID.randomUUID(), UUID.fromString(id)), name,
        CharacterStatus.DEAD, null, Set.of(), 0, Instant.EPOCH));
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
