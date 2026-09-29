package net.tfminecraft.magic;

import static org.junit.jupiter.api.Assertions.*;

import java.util.*;
import net.md_5.bungee.api.ChatColor;
import net.tfminecraft.magic.model.ElementDef;
import net.tfminecraft.magic.util.*;
import org.bukkit.Color;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.*;

class TextTest {
  @AfterEach
  void reset() {
    GuiCache.resetColors(null);
    GuiCache.equilibriumMax = 100;
    GuiCache.resonanceDriftLore = "{sign}{rate}/h";
  }

  String plain(String text) {
    return ChatColor.stripColor(text);
  }

  @Test
  void formattingSubstitutesKnownAndMissingColorTokens() {
    GuiCache.resetColors(Map.of("title", "#ff0000"));
    assertEquals("", MagicText.format(null));
    assertEquals("hello", plain(MagicText.format("{color:title}hello")));
    assertEquals("hello", plain(GuiText.format("{color:missing}hello")));
    assertEquals("#ff0000", GuiText.color("title"));
    assertEquals("#ffffff", MagicText.color("missing"));
    assertEquals("", plain(GuiText.text("title", null)));
    assertEquals("hi", plain(GuiText.text("title", "hi")));
    assertEquals(List.of(), GuiText.formatLoreLines(null));
    assertEquals(List.of(), GuiText.formatLoreLines(List.of()));
    assertEquals(List.of("hi", ""), GuiText.formatLoreLines(Arrays.asList("hi", null)));
  }

  @Test
  void elementNamesStripExistingColorsAndSupportGradients() {
    assertEquals("", MagicText.elementName(null));
    assertEquals("hello", plain(MagicText.elementText(null, "hello")));
    assertEquals("", plain(MagicText.elementText(null, null)));
    var cfg = new YamlConfiguration();
    cfg.set("name", "#ff0000Fire");
    cfg.set("color", "#abcdef");
    assertEquals("Fire", plain(MagicText.elementName(new ElementDef("fire", cfg))));
    cfg.set("name", "#ff0000");
    assertEquals("fire", plain(MagicText.elementName(new ElementDef("fire", cfg))));
    cfg.set("name", " ");
    assertEquals("fire", plain(MagicText.elementName(new ElementDef("fire", cfg))));
    cfg.set("name", "Fire");
    cfg.set("color", List.of("#ff0000", "#00ff00"));
    assertEquals("Fire", plain(MagicText.elementName(new ElementDef("fire", cfg))));
  }

  @Test
  void gradientsAndColorParsingHandleEndpointsAndInvalidInputs() {
    assertEquals("#7a1515", MagicText.gradientHex(-1));
    assertEquals("#e8c547", MagicText.gradientHex(.5));
    assertEquals("#8fd98a", MagicText.gradientHex(2));
    for (String bad : new String[] {null, " ", "#fff", "zzzzzz"})
      assertEquals(Color.BLUE, MagicText.bukkitColor(bad, Color.BLUE));
    assertEquals(Color.fromRGB(0xff0000), MagicText.bukkitColor("#ff0000", Color.BLUE));
    assertEquals(Color.fromRGB(0xabcdef), MagicText.bukkitColor("abcdef", Color.BLUE));
  }

  @Test
  void barsAlwaysContainConfiguredSegmentsAndDisplayClampedValues() {
    GuiCache.equilibriumMin = -100;
    GuiCache.equilibriumMax = 100;
    GuiCache.equilibriumBarSegments = 20;
    for (double value : new double[] {-1000, -50, 0, 50, 1000}) {
      assertEquals("[" + "|".repeat(20) + "]", plain(EquilibriumBar.build(value)));
      assertFalse(plain(EquilibriumBar.formatLore(value)).contains("{"));
      assertEquals(
          value == 0 ? "0" : String.valueOf((int) Math.min(100, Math.abs(value))),
          plain(EquilibriumBar.formatValue(value)));
    }
    assertNull(EquilibriumBar.formatDriftLore(0));
    assertTrue(plain(EquilibriumBar.formatDriftLore(-50)).contains("+"));
    assertTrue(plain(EquilibriumBar.formatDriftLore(50)).contains("-"));
    GuiCache.equilibriumMax = 0;
    assertEquals("[" + "|".repeat(20) + "]", plain(EquilibriumBar.build(0)));
    var element = DomainTest.element("fire");
    assertEquals("[||||]", plain(ResonanceBar.build(element, 500, 4)));
    assertTrue(plain(ResonanceBar.formatLore(element, 500)).contains("100/100"));
    assertNotNull(ResonanceBar.formatDriftLore(element));
    assertNull(ResonanceBar.formatDriftLore(null));
    var cfg = new YamlConfiguration();
    cfg.set("decay_per_hour", 0);
    assertNull(ResonanceBar.formatDriftLore(new ElementDef("fire", cfg)));
    cfg.set("decay_per_hour", 1);
    assertTrue(plain(ResonanceBar.formatDriftLore(new ElementDef("fire", cfg))).contains("+"));
    GuiCache.resonanceDriftLore = null;
    assertNull(ResonanceBar.formatDriftLore(element));
  }

  @Test
  void costsSkipMissingKeysAndQuantitiesAndRemainSorted() {
    var costs = new HashMap<String, Integer>();
    costs.put(null, 3);
    costs.put("v.STONE", 2);
    costs.put("v.AIR", null);
    costs.put("v.APPLE", 1);
    assertEquals(
        List.of("§7- §fv.APPLE §e1", "§7- §fv.STONE §e2"), CostFormatter.getCostsFormatted(costs));
    assertEquals(List.of(), CostFormatter.getCostsFormatted(null));
    assertEquals(List.of(), CostFormatter.getCostsFormatted(Map.of()));
    var lore = new ArrayList<String>();
    CostFormatter.appendInput(null, costs);
    CostFormatter.appendInput(lore, null);
    CostFormatter.appendInput(lore, Map.of());
    assertTrue(lore.isEmpty());
    CostFormatter.appendInput(lore, costs);
    assertEquals(4, lore.size());
    assertEquals("", lore.getFirst());
  }

  @Test
  void legacyModelDataUsesFirstFloatAndReplacesOtherComponents() {
    var meta = org.mockito.Mockito.mock(org.bukkit.inventory.meta.ItemMeta.class);
    var component =
        org.mockito.Mockito.mock(
            org.bukkit.inventory.meta.components.CustomModelDataComponent.class);
    org.mockito.Mockito.when(meta.getCustomModelDataComponent()).thenReturn(component);
    org.mockito.Mockito.when(component.getFloats()).thenReturn(List.of());
    assertFalse(LegacyModelData.has(meta));
    assertThrows(IllegalStateException.class, () -> LegacyModelData.get(meta));
    org.mockito.Mockito.when(component.getFloats()).thenReturn(List.of(42f));
    assertTrue(LegacyModelData.has(meta));
    assertEquals(42, LegacyModelData.get(meta));
    LegacyModelData.set(meta, 99);
    org.mockito.Mockito.verify(component).setFloats(List.of(99f));
    org.mockito.Mockito.verify(component).setFlags(List.of());
    org.mockito.Mockito.verify(component).setStrings(List.of());
    org.mockito.Mockito.verify(component).setColors(List.of());
    org.mockito.Mockito.verify(meta).setCustomModelDataComponent(component);
    LegacyModelData.set(meta, null);
    org.mockito.Mockito.verify(meta).setCustomModelDataComponent(null);
  }
}
