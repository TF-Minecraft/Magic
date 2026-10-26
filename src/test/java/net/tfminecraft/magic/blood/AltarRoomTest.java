package net.tfminecraft.magic.blood;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.bukkit.Material;
import org.bukkit.block.Block;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

class AltarRoomTest {
  @TempDir Path temp;
  BloodFixture fx;

  @BeforeEach
  void setup() {
    fx = new BloodFixture(temp);
  }

  @AfterEach
  void cleanup() {
    fx.close();
  }

  void config(String yaml) throws Exception {
    Files.writeString(temp.resolve("r.yml"), "enabled: true\n" + yaml);
    assertTrue(BloodConfig.load(temp.resolve("r.yml").toFile(), fx.logger));
  }

  @Test
  void aNearlyOpenRoomIsCarvedAndItsFloorHolesFilled() throws Exception {
    config("placement:\n  min_cover: 1\n  cover_scan_height: 4\n");
    assertEquals(9, AltarRoom.size());
    fx.open(-4, 10, -4, 4, 11, 4);
    Block target = fx.pocket(0, 10, 0);
    Block hole = fx.world.getBlockAt(3, 9, 3);
    hole.setType(Material.AIR);
    Block standing = fx.world.getBlockAt(0, 30, 0);
    standing.setType(Material.DEEPSLATE);

    AltarRoom.Plan plan = AltarRoom.plan(target, standing);
    assertEquals(AltarRoom.Verdict.OK, plan.verdict());
    assertEquals(81, plan.carve().size());
    assertEquals(List.of(hole), plan.fill());
    assertEquals(Material.DEEPSLATE, plan.floor());
    plan.apply();
    assertEquals(Material.AIR, fx.world.getBlockAt(4, 12, -4).getType());
    assertEquals(Material.AIR, fx.world.getBlockAt(0, 12, 0).getType());
    assertEquals(Material.DEEPSLATE, hole.getType());
    assertEquals(Material.STONE, fx.world.getBlockAt(5, 11, 0).getType());
  }

  @Test
  void tooMuchRockSomethingThatWillNotMoveOrTheSkyRefuseIt() throws Exception {
    config("placement:\n  min_cover: 2\n  cover_scan_height: 4\n  room:\n    max_changes: 100\n");
    Block target = fx.pocket(0, 10, 0);
    assertEquals(AltarRoom.Verdict.TOO_MUCH, AltarRoom.plan(target, null).verdict());

    fx.open(-4, 10, -4, 4, 12, 4);
    assertEquals(AltarRoom.Verdict.OK, AltarRoom.plan(target, null).verdict());
    for (Material stubborn : List.of(Material.WATER, Material.CHEST, Material.BEDROCK)) {
      fx.world.getBlockAt(2, 11, 2).setType(stubborn);
      assertEquals(AltarRoom.Verdict.BLOCKED, AltarRoom.plan(target, null).verdict(), stubborn.name());
    }
    fx.world.getBlockAt(2, 11, 2).setType(Material.AIR);
    fx.world.getBlockAt(-3, 9, 1).setType(Material.LAVA);
    assertEquals(AltarRoom.Verdict.BLOCKED, AltarRoom.plan(target, null).verdict());
    fx.world.getBlockAt(-3, 9, 1).setType(Material.STONE);

    Block other = fx.world.getBlockAt(1, 10, -2);
    BloodAltar rival = BloodAltar.create("rival", "o", other, 0f, 1L);
    BloodAltars.add(rival);
    other.setType(Material.STONE);
    assertEquals(AltarRoom.Verdict.BLOCKED, AltarRoom.plan(target, null).verdict());
    BloodAltars.remove(rival);
    other.setType(Material.NETHERRACK);
    BloodAltar elsewhere = BloodAltar.create("far", "o", fx.pocket(40, 10, 40), 0f, 1L);
    elsewhere.getCorrupted().add(new BloodAltar.CorruptedBlock(1, 10, -2, "minecraft:stone", "NETHERRACK"));
    BloodAltars.add(elsewhere);
    assertEquals(AltarRoom.Verdict.BLOCKED, AltarRoom.plan(target, null).verdict());
    other.setType(Material.AIR);

    AltarRegions.setQuery(location -> location.getBlockX() == 4 ? List.of("claim") : List.of());
    assertEquals(AltarRoom.Verdict.BLOCKED, AltarRoom.plan(target, null).verdict());
    AltarRegions.setQuery(location -> List.of());

    for (int y = 13; y <= 16; y++) {
      fx.world.getBlockAt(-4, y, 3).setType(Material.AIR);
    }
    assertEquals(AltarRoom.Verdict.SHALLOW, AltarRoom.plan(target, null).verdict());
    config("placement:\n  require_underground: false\n");
    assertEquals(AltarRoom.Verdict.OK, AltarRoom.plan(target, null).verdict());
    config("placement:\n  room:\n    enabled: false\n");
    AltarRoom.Plan off = AltarRoom.plan(fx.pocket(-30, 10, -30), null);
    assertEquals(AltarRoom.Verdict.OK, off.verdict());
    assertTrue(off.carve().isEmpty());
  }

  @Test
  void theFloorIsThePlayersStoneElseTheAltarsElseStone() {
    Block glass = fx.world.getBlockAt(0, 40, 0);
    glass.setType(Material.GLASS);
    Block barrier = fx.world.getBlockAt(1, 40, 0);
    barrier.setType(Material.BARRIER);
    Block tuff = fx.world.getBlockAt(2, 40, 0);
    tuff.setType(Material.TUFF);
    assertEquals(Material.TUFF, AltarRoom.floorOf(null, glass, barrier, tuff));
    assertEquals(Material.STONE, AltarRoom.floorOf(glass, barrier));
    int top = fx.world.getMaxHeight() - 2;
    assertEquals(fx.world.getBlockAt(0, top + 1, 0).getType().isOccluding() ? 1 : 0,
        AltarRoom.cover(fx.world, 0, top, 0));
  }

  @Test
  void placingTheAltarShapesTheRoomOrSaysWhy() {
    PlayerMock player = fx.player("mage");
    fx.record("mage", BloodStage.ANIMAL);
    Block cramped = fx.pocket(0, 10, 0);
    assertEquals("blood.altar.place.room_too_much",
        AltarService.place(player, BloodItems.create(BloodItemKind.ALTAR, BloodFixture.id("mage")), cramped));
    assertNull(BloodAltars.at(cramped));
  }
}
