package net.tfminecraft.magic.blood;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import net.tfminecraft.tlibs.character.CharacterInfo;
import net.tfminecraft.tlibs.character.CharacterRef;
import net.tfminecraft.tlibs.character.CharacterStatus;
import net.tfminecraft.tlibs.character.Characters;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

class AltarServiceTest {
  @TempDir Path temp;

  ItemStack altarItem(BloodFixture fx, String label) {
    return BloodItems.create(BloodItemKind.ALTAR, BloodFixture.id(label));
  }

  @Test
  void itemsCarryKindAndOwner() {
    try (BloodFixture fx = new BloodFixture(temp)) {
      ItemStack knife = BloodItems.create(BloodItemKind.RITUAL_KNIFE, "c1");
      assertEquals(BloodItemKind.RITUAL_KNIFE, BloodItems.kind(knife));
      assertEquals("c1", BloodItems.owner(knife));
      assertTrue(knife.getItemMeta().isUnbreakable());
      assertTrue(BloodItems.is(knife, BloodItemKind.RITUAL_KNIFE));
      assertFalse(BloodItems.is(knife, null));
      assertFalse(BloodItems.is(knife, BloodItemKind.ALTAR));
      ItemStack altar = altarItem(fx, "a");
      assertFalse(altar.getItemMeta().isUnbreakable());
      assertNull(BloodItems.kind(null));
      assertNull(BloodItems.kind(new ItemStack(Material.AIR)));
      assertNull(BloodItems.kind(new ItemStack(Material.STONE)));
      ItemStack bare = mock(ItemStack.class);
      when(bare.getType()).thenReturn(Material.STONE);
      assertNull(BloodItems.owner(bare));
      ItemStack plain = new ItemStack(Material.STONE);
      var meta = plain.getItemMeta();
      meta.setDisplayName("x");
      plain.setItemMeta(meta);
      assertNull(BloodItems.owner(plain));
      ItemStack unknown = new ItemStack(Material.STONE);
      meta = unknown.getItemMeta();
      meta.getPersistentDataContainer().set(BloodItems.kindKey(), PersistentDataType.STRING, "other");
      unknown.setItemMeta(meta);
      assertNull(BloodItems.kind(unknown));

      BloodConfig.load(new java.io.File("src/main/resources/wildmagic/bloodmagic"), fx.logger);
      ItemStack named = BloodItems.create(BloodItemKind.ALTAR, "c1");
      assertTrue(named.getItemMeta().hasDisplayName());
      assertEquals(3, named.getItemMeta().getLore().size());
    }
  }

  @Test
  void placingNeedsTheOwnerOnTheRightStageWithNoOtherAltar() {
    try (BloodFixture fx = new BloodFixture(temp)) {
      PlayerMock player = fx.player("mage");
      Block target = fx.room(0, 10, 0);
      ItemStack item = altarItem(fx, "mage");
      BloodConfig.setEnabled(false);
      assertEquals("blood.altar.inert", AltarService.place(player, item, target));
      BloodConfig.setEnabled(true);
      assertEquals("blood.altar.not_yours", AltarService.place(player, altarItem(fx, "other"), target));
      fx.characters.end(player, net.tfminecraft.tlibs.character.CharacterSessionEndEvent.Reason.QUIT);
      assertNull(AltarService.currentCharacter(player));
      assertEquals("blood.altar.not_yours", AltarService.place(player, item, target));
      fx.characters.start(player, "mage");
      assertEquals("blood.altar.inert", AltarService.place(player, item, target));
      fx.record("mage", BloodStage.WHISPERED);
      assertEquals("blood.altar.inert", AltarService.place(player, item, target));
      fx.record("mage", BloodStage.ANIMAL);
      target.getRelative(0, -1, 0).setType(Material.AIR);
      assertEquals("blood.altar.place.no_floor", AltarService.place(player, item, target));
      target.getRelative(0, -1, 0).setType(Material.STONE);

      item.setAmount(2);
      assertEquals("blood.altar.placed", AltarService.place(player, item, target));
      assertEquals(1, item.getAmount());
      assertEquals(Material.BARRIER, target.getType());
      BloodAltar altar = BloodAltars.at(target);
      assertNotNull(altar);
      assertEquals(altar.getId(), BloodRecords.get(BloodFixture.id("mage")).getAltarId());
      assertInstanceOf(ItemDisplay.class, Bukkit.getEntity(altar.getDisplayId()));
      assertEquals("blood.altar.already", AltarService.place(player, item, fx.pocket(3, 10, 3)));

      assertEquals("blood.altar.owner_touch", AltarService.touch(player, altar));
      assertEquals("blood.altar.stranger_touch", AltarService.touch(fx.player("other"), altar));
      PlayerMock none = fx.server.addPlayer();
      assertEquals("blood.altar.stranger_touch", AltarService.touch(none, altar));
    }
  }

  @Test
  void displaysFollowLevelRestoreBlocksAndDropStrays() throws Exception {
    try (BloodFixture fx = new BloodFixture(temp)) {
      java.nio.file.Files.writeString(temp.resolve("l.yml"),
          "enabled: true\naltar:\n  models:\n    0: v.stone\n    1: v.dirt\n  levels:\n    1: 1\n");
      BloodConfig.load(temp.resolve("l.yml").toFile(), fx.logger);
      Block target = fx.pocket(0, 10, 0);
      BloodRecord record = fx.record("mage", BloodStage.ANIMAL);
      BloodAltar altar = BloodAltar.create(BloodFixture.id("mage"), "o", target, 0f, 1L);
      BloodAltars.add(altar);
      assertTrue(AltarDisplays.ensure(altar));
      ItemDisplay display = (ItemDisplay) Bukkit.getEntity(altar.getDisplayId());
      assertEquals(altar.getId(), display.getPersistentDataContainer().get(AltarDisplays.key(), PersistentDataType.STRING));

      record.addSpecies("cow");
      AltarService.refresh(altar);
      assertEquals(1, altar.getLevel());
      AltarService.refresh(altar);
      AltarService.refreshFor(BloodFixture.id("mage"));
      AltarService.refreshFor("nobody");

      target.setType(Material.AIR);
      assertFalse(AltarDisplays.ensure(altar));
      assertEquals(Material.BARRIER, target.getType());

      display.remove();
      assertTrue(AltarDisplays.ensure(altar));
      UUID second = altar.getDisplayId();
      assertNotEquals(display.getUniqueId(), second);
      PlayerMock squatter = fx.server.addPlayer();
      altar.setDisplayId(squatter.getUniqueId());
      assertTrue(AltarDisplays.ensure(altar));
      Bukkit.getEntity(altar.getDisplayId()).remove();
      altar.setDisplayId(second);

      ItemDisplay stray = fx.world.spawn(target.getLocation(), ItemDisplay.class);
      stray.getPersistentDataContainer().set(AltarDisplays.key(), PersistentDataType.STRING, altar.getId());
      ItemDisplay orphan = fx.world.spawn(target.getLocation(), ItemDisplay.class);
      orphan.getPersistentDataContainer().set(AltarDisplays.key(), PersistentDataType.STRING, "gone");
      ItemDisplay untagged = fx.world.spawn(target.getLocation(), ItemDisplay.class);
      AltarDisplays.removeStrays(List.of(stray, orphan, untagged, Bukkit.getEntity(second)));
      assertFalse(stray.isValid());
      assertFalse(orphan.isValid());
      assertTrue(untagged.isValid());
      assertTrue(Bukkit.getEntity(second).isValid());

      BloodAltar elsewhere = BloodAltar.create("x", "o", target, 0f, 1L);
      var world = BloodAltar.class.getDeclaredField("world");
      world.setAccessible(true);
      world.set(elsewhere, "gone");
      assertFalse(AltarDisplays.ensure(elsewhere));
      AltarDisplays.remove(elsewhere);
      BloodAltar far = BloodAltar.create("x", "o", fx.world.getBlockAt(100000, 10, 100000), 0f, 1L);
      fx.world.unloadChunk(100000 >> 4, 100000 >> 4);
      assertFalse(AltarDisplays.ensure(far));

      AltarDisplays.remove(altar);
      assertEquals(Material.AIR, target.getType());
      AltarDisplays.remove(altar);
      altar.setDisplayId(null);
      target.setType(Material.STONE);
      AltarDisplays.remove(altar);
      assertEquals(Material.STONE, target.getType());
    }
  }

  @Test
  void purifyAndDeathClearAltarAndPath() {
    try (BloodFixture fx = new BloodFixture(temp)) {
      PlayerMock player = fx.player("mage");
      fx.record("mage", BloodStage.ANIMAL);
      Block target = fx.room(0, 10, 0);
      AltarService.place(player, altarItem(fx, "mage"), target);
      BloodAltar altar = BloodAltars.at(target);
      AltarService.purify(altar, "test");
      assertNull(BloodAltars.at(target));
      assertNull(BloodRecords.get(BloodFixture.id("mage")).getAltarId());
      assertEquals(Material.AIR, target.getType());

      BloodAltar loose = BloodAltar.create("nobody", "o", fx.pocket(4, 10, 4), 0f, 1L);
      BloodAltars.add(loose);
      AltarService.purify(loose, "no record");
      BloodRecord other = fx.record("other", BloodStage.ANIMAL);
      other.setAltarId("different");
      BloodAltar mismatched = BloodAltar.create(BloodFixture.id("other"), "o", fx.pocket(6, 10, 6), 0f, 1L);
      BloodAltars.add(mismatched);
      AltarService.purify(mismatched, "mismatch");
      assertEquals("different", other.getAltarId());

      AltarService.place(player, altarItem(fx, "mage"), target);
      AltarService.onCharacterDeath(BloodFixture.id("mage"));
      assertNull(BloodAltars.ofCharacter(BloodFixture.id("mage")));
      assertNull(BloodRecords.get(BloodFixture.id("mage")));
      AltarService.onCharacterDeath(BloodFixture.id("mage"));
    }
  }

  @Test
  void sweepFindsDeadOwnersOnly() {
    try (BloodFixture fx = new BloodFixture(temp)) {
      String dead = UUID.randomUUID().toString();
      String alive = UUID.randomUUID().toString();
      String recordOnly = UUID.randomUUID().toString();
      BloodAltars.add(BloodAltar.create(dead, "o", fx.pocket(0, 10, 0), 0f, 1L));
      BloodAltars.add(BloodAltar.create(alive, "o", fx.pocket(2, 10, 2), 0f, 1L));
      BloodAltars.add(BloodAltar.create("not-a-uuid", "o", fx.pocket(4, 10, 4), 0f, 1L));
      BloodRecords.getOrCreate(recordOnly, "o").setStage(BloodStage.WHISPERED);
      BloodRecords.getOrCreate(alive, "o").setStage(BloodStage.ANIMAL);
      try (var chars = mockStatic(Characters.class)) {
        chars.when(Characters::available).thenReturn(false);
        AltarService.sweepDead();
        assertEquals(3, BloodAltars.all().size());
        chars.when(Characters::available).thenReturn(true);
        chars.when(() -> Characters.info(any())).thenReturn(Optional.empty());
        chars.when(() -> Characters.info(UUID.fromString(dead))).thenReturn(info(dead, CharacterStatus.DEAD));
        chars.when(() -> Characters.info(UUID.fromString(recordOnly))).thenReturn(info(recordOnly, CharacterStatus.DEAD));
        chars.when(() -> Characters.info(UUID.fromString(alive))).thenReturn(info(alive, CharacterStatus.ALIVE));
        AltarService.sweepDead();
      }
      assertNull(BloodAltars.ofCharacter(dead));
      assertNotNull(BloodAltars.ofCharacter(alive));
      assertNotNull(BloodAltars.ofCharacter("not-a-uuid"));
      assertNull(BloodRecords.get(recordOnly));
      assertNotNull(BloodRecords.get(alive));
    }
  }

  static Optional<CharacterInfo> info(String id, CharacterStatus status) {
    return Optional.of(new CharacterInfo(new CharacterRef(UUID.randomUUID(), UUID.fromString(id)), "N", status,
        null, Set.of(), 0, Instant.EPOCH));
  }
}
