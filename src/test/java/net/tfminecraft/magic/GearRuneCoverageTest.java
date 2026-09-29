package net.tfminecraft.magic;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import io.lumine.mythic.lib.api.item.NBTItem;
import io.lumine.mythic.lib.skill.trigger.TriggerType;
import java.util.*;
import net.Indyuce.mmoitems.ItemStats;
import net.Indyuce.mmoitems.api.item.mmoitem.LiveMMOItem;
import net.Indyuce.mmoitems.stat.data.*;
import net.tfminecraft.magic.gear.*;
import org.bukkit.*;
import org.bukkit.inventory.*;
import org.junit.jupiter.api.*;
import org.mockbukkit.mockbukkit.MockBukkit;

class GearRuneCoverageTest extends GearMmoCoverageSupport {
  Set<String> oldRunes;

  @BeforeEach
  void configureRunes() {
    oldRunes = new HashSet<>(Cache.runeTypes);
    Cache.runeTypes.clear();
  }

  @AfterEach
  void cleanupRunes() {
    Cache.runeTypes.clear();
    Cache.runeTypes.addAll(oldRunes);
  }

  @Test
  void runeDetectionRequiresRegisteredTypeAndBothPlugins() {
    assertFalse(RuneKeybind.isRune(null));
    assertFalse(RuneKeybind.isRune(new ItemStack(Material.AIR)));
    var item = item();
    assertFalse(RuneKeybind.isRune(item));
    Cache.runeTypes.add("rune");
    assertFalse(RuneKeybind.isRune(item));
    MockBukkit.createMockPlugin("MMOItems");
    assertFalse(RuneKeybind.isRune(item));
    MockBukkit.createMockPlugin("MythicLib");
    try (var nbt = mockStatic(NBTItem.class)) {
      var data = mock(NBTItem.class);
      nbt.when(() -> NBTItem.get(item)).thenReturn(data);
      assertFalse(RuneKeybind.isRune(item));
      when(data.hasType()).thenReturn(true);
      when(data.getType()).thenReturn("OTHER");
      assertFalse(RuneKeybind.isRune(item));
      when(data.getType()).thenReturn("RUNE");
      assertTrue(RuneKeybind.isRune(item));
      assertEquals(RuneKeybind.Result.NOT_RUNE, RuneKeybind.apply(item, null).result());
    }
    assertEquals(
        RuneKeybind.Result.NOT_RUNE, RuneKeybind.apply(null, TriggerType.RIGHT_CLICK).result());
  }

  @Test
  void rebindingCopiesModifiersAndPreservesAmount() {
    Cache.runeTypes.add("rune");
    MockBukkit.createMockPlugin("MMOItems");
    MockBukkit.createMockPlugin("MythicLib");
    var held = item();
    held.setAmount(4);
    var rebuilt = item();
    var bad = mock(AbilityData.class);
    var good = mock(AbilityData.class);
    var ability = mock(net.Indyuce.mmoitems.skill.RegisteredSkill.class);
    when(good.getAbility()).thenReturn(ability);
    when(good.getModifiers()).thenReturn(Set.of("damage"));
    when(good.getParameter("damage")).thenReturn(12d);
    var list = new AbilityListData(Arrays.asList(null, bad, good));
    try (var nbt = mockStatic(NBTItem.class);
        var abilities = mockConstruction(AbilityData.class);
        var items =
            mockConstruction(
                LiveMMOItem.class,
                (m, c) -> {
                  when(m.hasData(ItemStats.ABILITIES)).thenReturn(true);
                  when(m.getData(ItemStats.ABILITIES)).thenReturn(list);
                  var builder = mock(net.Indyuce.mmoitems.api.item.build.ItemStackBuilder.class);
                  when(m.newBuilder()).thenReturn(builder);
                  when(builder.build()).thenReturn(rebuilt);
                })) {
      var data = mock(NBTItem.class);
      when(data.hasType()).thenReturn(true);
      when(data.getType()).thenReturn("RUNE");
      nbt.when(() -> NBTItem.get(held)).thenReturn(data);
      var result = RuneKeybind.apply(held, TriggerType.RIGHT_CLICK);
      assertEquals(RuneKeybind.Result.OK, result.result());
      assertSame(rebuilt, result.item());
      assertEquals(4, rebuilt.getAmount());
      verify(abilities.constructed().getFirst()).setModifier("damage", 12);
    }
  }

  @Test
  void invalidAbilityPayloadAndFailedBuildsReturnExplicitOutcomes() {
    Cache.runeTypes.add("rune");
    MockBukkit.createMockPlugin("MMOItems");
    MockBukkit.createMockPlugin("MythicLib");
    var held = item();
    try (var nbt = mockStatic(NBTItem.class)) {
      var data = mock(NBTItem.class);
      when(data.hasType()).thenReturn(true);
      when(data.getType()).thenReturn("RUNE");
      nbt.when(() -> NBTItem.get(held)).thenReturn(data);
      for (int scenario = 0; scenario < 7; scenario++) {
        int mode = scenario;
        try (var abilities = mockConstruction(AbilityData.class);
            var items =
                mockConstruction(
                    LiveMMOItem.class,
                    (m, c) -> {
                      when(m.hasData(ItemStats.ABILITIES)).thenReturn(mode != 0);
                      var bad = mock(AbilityData.class);
                      var good = mock(AbilityData.class);
                      when(good.getAbility())
                          .thenReturn(mock(net.Indyuce.mmoitems.skill.RegisteredSkill.class));
                      if (mode == 1) when(m.getData(ItemStats.ABILITIES)).thenReturn(null);
                      else if (mode == 2)
                        when(m.getData(ItemStats.ABILITIES)).thenReturn(new AbilityListData());
                      else if (mode == 3)
                        when(m.getData(ItemStats.ABILITIES)).thenReturn(new AbilityListData(bad));
                      else
                        when(m.getData(ItemStats.ABILITIES)).thenReturn(new AbilityListData(good));
                      var builder =
                          mock(net.Indyuce.mmoitems.api.item.build.ItemStackBuilder.class);
                      when(m.newBuilder()).thenReturn(builder);
                      if (mode == 5) when(builder.build()).thenReturn(new ItemStack(Material.AIR));
                      if (mode == 6)
                        when(builder.build()).thenThrow(new IllegalStateException("broken"));
                    })) {
          assertEquals(
              mode < 4 ? RuneKeybind.Result.NO_ABILITIES : RuneKeybind.Result.FAILED,
              RuneKeybind.apply(held, TriggerType.RIGHT_CLICK).result());
        }
      }
    }
  }
}
