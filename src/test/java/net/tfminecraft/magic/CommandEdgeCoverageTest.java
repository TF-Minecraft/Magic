package net.tfminecraft.magic;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import io.lumine.mythic.lib.skill.trigger.TriggerType;
import java.util.*;
import net.tfminecraft.magic.artifact.*;
import net.tfminecraft.magic.artifact.config.*;
import net.tfminecraft.magic.command.MagicCommand;
import net.tfminecraft.magic.session.*;
import org.bukkit.Material;
import org.bukkit.command.CommandSender;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.*;

class CommandEdgeCoverageTest {
  CommandTest f = new CommandTest();

  @BeforeEach
  void setup() {
    f.setup();
  }

  @AfterEach
  void cleanup() {
    f.cleanup();
  }

  Object call(String name, Class<?>[] types, Object... args) throws Exception {
    var m = MagicCommand.class.getDeclaredMethod(name, types);
    m.setAccessible(true);
    return m.invoke(null, args);
  }

  @Test
  void nullableParserHelpersKeepSafeFallbacks() throws Exception {
    assertEquals("-", call("dash", new Class[] {String.class}, " "));
    assertEquals("-", call("formatExtras", new Class[] {Map.class}, (Object) null));
    assertNull(call("resolveRuneKeybind", new Class[] {String.class}, (Object) null));
    assertEquals(
        false,
        call(
            "applyResonance",
            new Class[] {
              org.bukkit.entity.Player.class,
              ResonanceSession.class,
              String.class,
              double.class,
              boolean.class,
              CommandSender.class
            },
            f.player,
            new ResonanceSession(),
            null,
            2d,
            false,
            f.player));
    assertEquals("resonance.admin.unknown_element", f.player.nextMessage());
  }

  @Test
  void reloadPermissionOffersAdminTabsWithoutRuneAccess() {
    var sender = mock(CommandSender.class);
    when(sender.hasPermission("magic.admin.reload")).thenReturn(true);
    assertTrue(
        f.command.onTabComplete(sender, null, "magic", new String[] {""}).contains("reload"));
    assertFalse(f.command.onTabComplete(sender, null, "magic", new String[] {""}).contains("rune"));
    assertTrue(f.command.onTabComplete(sender, null, "magic", new String[] {"rune", ""}).isEmpty());
    assertTrue(f.tab("resonance", "set", "Mage", "fire", "").isEmpty());
    assertTrue(f.tab("shrine", "fill", "fire", "").isEmpty());
    ArtifactRarityRegistry.register(ArtifactGenerationTest.rarity("rare", 1, 1, 1));
    assertTrue(f.tab("artifact", "roll", "fire", "ra").contains("rare"));
  }

  @Test
  void malformedExternalTriggerEntriesAreSkipped() throws Exception {
    var unnamed = mock(TriggerType.class);
    when(unnamed.name()).thenReturn(null);
    Cache.runeKeybinds = new LinkedHashSet<>(Arrays.asList(null, unnamed, TriggerType.RIGHT_CLICK));
    assertSame(
        TriggerType.RIGHT_CLICK,
        call("resolveRuneKeybind", new Class[] {String.class}, "right_click"));
    assertEquals(List.of("RIGHT_CLICK"), f.tab("rune", "keybind", ""));
  }

  @Test
  void identifiedItemWithoutAuraCannotBeFilled() {
    var item = new ItemStack(Material.STONE);
    ArtifactIds.writeNew(item);
    f.player.getInventory().setItemInMainHand(item);
    f.expect("artifact.setfill.no_cap", "artifact", "setfill", "fire", "2");
    assertEquals(item, f.player.getInventory().getItemInMainHand());
  }
}
