package net.tfminecraft.magic.blood;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.nio.file.Path;
import java.util.List;

import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.protection.ApplicableRegionSet;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.block.BlockMock;

class AltarPlacementTest {
  @TempDir Path temp;

  @Test
  void deepDarkPocketOnSolidFloorPasses() {
    try (BloodFixture fx = new BloodFixture(temp)) {
      Block block = fx.pocket(0, 10, 0);
      assertEquals(AltarPlacement.Result.OK, AltarPlacement.check(block));
      assertEquals(48, AltarPlacement.cover(block));
    }
  }

  @Test
  void eachRuleRefusesInOrder() throws Exception {
    try (BloodFixture fx = new BloodFixture(temp)) {
      Block block = fx.pocket(0, 10, 0);
      java.nio.file.Files.writeString(temp.resolve("w.yml"), "enabled: true\nplacement:\n  worlds: [elsewhere]\n");
      BloodConfig.load(temp.resolve("w.yml").toFile(), fx.logger);
      assertEquals(AltarPlacement.Result.WORLD, AltarPlacement.check(block));
      BloodConfig.reset();

      block.setType(Material.STONE);
      assertEquals(AltarPlacement.Result.OCCUPIED, AltarPlacement.check(block));
      block.setType(Material.SHORT_GRASS);
      assertEquals(AltarPlacement.Result.OK, AltarPlacement.check(block));
      block.setType(Material.AIR);

      BloodAltars.add(BloodAltar.create("c", "o", block, 0f, 1L));
      assertEquals(AltarPlacement.Result.OCCUPIED, AltarPlacement.check(block));
      BloodAltars.remove(BloodAltars.at(block));

      block.getRelative(0, -1, 0).setType(Material.AIR);
      assertEquals(AltarPlacement.Result.NO_FLOOR, AltarPlacement.check(block));
      block.getRelative(0, -1, 0).setType(Material.STONE);

      ((BlockMock) block).setLightFromSky((byte) 4);
      assertEquals(AltarPlacement.Result.SKY, AltarPlacement.check(block));
      ((BlockMock) block).setLightFromSky((byte) 0);

      Block high = fx.pocket(5, 300, 5);
      assertEquals(AltarPlacement.Result.SHALLOW, AltarPlacement.check(high));
      java.nio.file.Files.writeString(temp.resolve("u.yml"), "enabled: true\nplacement:\n  require_underground: false\n");
      BloodConfig.load(temp.resolve("u.yml").toFile(), fx.logger);
      assertEquals(AltarPlacement.Result.OK, AltarPlacement.check(high));
      BloodConfig.reset();

      AltarRegions.setQuery(location -> List.of("town"));
      assertEquals(AltarPlacement.Result.REGION, AltarPlacement.check(block));
    }
  }

  @Test
  void regionsHonourAllowListAndDenySwitch() throws Exception {
    try (BloodFixture fx = new BloodFixture(temp)) {
      Block block = fx.pocket(0, 10, 0);
      AltarRegions.setQuery(location -> List.of("wilds", "town"));
      assertTrue(AltarRegions.blocked(block.getLocation()));
      java.nio.file.Files.writeString(temp.resolve("r.yml"),
          "enabled: true\nplacement:\n  allowed_regions: [wilds, town]\n");
      BloodConfig.load(temp.resolve("r.yml").toFile(), fx.logger);
      assertFalse(AltarRegions.blocked(block.getLocation()));
      java.nio.file.Files.writeString(temp.resolve("d.yml"), "enabled: true\nplacement:\n  deny_regions: false\n");
      BloodConfig.load(temp.resolve("d.yml").toFile(), fx.logger);
      assertFalse(AltarRegions.blocked(block.getLocation()));
    }
  }

  @Test
  void withoutWorldGuardNothingIsARegionAndWithItRegionsAreAsked() {
    try (BloodFixture fx = new BloodFixture(temp)) {
      Block block = fx.pocket(0, 10, 0);
      AltarRegions.setQuery(null);
      assertEquals(List.of(), AltarRegions.at(block.getLocation()));
      MockBukkit.createMockPlugin("WorldGuard");
      var region = mock(ProtectedRegion.class);
      when(region.getId()).thenReturn("spawn");
      var set = mock(ApplicableRegionSet.class);
      when(set.iterator()).thenReturn(List.of(region).iterator());
      var worldGuard = mock(WorldGuard.class, RETURNS_DEEP_STUBS);
      when(worldGuard.getPlatform().getRegionContainer().createQuery().getApplicableRegions(any()))
          .thenReturn(set);
      var original = WorldGuardAltarRegions.adapter;
      WorldGuardAltarRegions.adapter = location -> null;
      try (var wg = mockStatic(WorldGuard.class)) {
        wg.when(WorldGuard::getInstance).thenReturn(worldGuard);
        assertEquals(List.of("spawn"), AltarRegions.at(block.getLocation()));
      } finally {
        WorldGuardAltarRegions.adapter = original;
      }
    }
  }
}
