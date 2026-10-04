package net.tfminecraft.magic;

import static org.junit.jupiter.api.Assertions.*;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

class BundledRuneConfigTest {
  private YamlConfiguration resource(String path) {
    return YamlConfiguration.loadConfiguration(new InputStreamReader(
        getClass().getResourceAsStream("/" + path), StandardCharsets.UTF_8));
  }

  @Test
  void mageSocketsUseTheSameNamesAsSchoolRunes() {
    var config = resource("config.yml");
    assertFalse(config.getBoolean("gear.socket_rarity_prefix"));
    assertEquals(List.of("CERRITH_RUNES", "OSENI_RUNES", "SEITHR_RUNES", "MITLAN_RUNES"),
        config.getStringList("runes.types"));
    var archetypes = resource("gear/archetypes.yml");
    var labels = resource("gear/socket-colours.yml");
    for (String type : List.of("staff", "wand", "sword")) {
      for (String tier : List.of("minor", "lesser", "greater", "ascendant")) {
        String id = tier + "_rune";
        String expected = Character.toUpperCase(tier.charAt(0)) + tier.substring(1) + " Rune";
        assertEquals(expected, archetypes.getString(type + ".slots." + id));
        assertEquals(expected, labels.getString("labels." + id));
      }
    }
    var parts = resource("gear/parts.yml");
    assertTrue(parts.getBoolean("petty_tom32.disabled"));
    assertEquals(parts.getConfigurationSection("petty_tome3.sockets").getValues(false),
        parts.getConfigurationSection("petty_tom32.sockets").getValues(false));
  }

  @Test
  void mitlanRunesHaveBindingsAtEveryTierAndClassSpellsStaySeparate() {
    var skills = resource("skills.yml");
    var names = List.of("Tide_Shard", "Bubble_Shard", "Scalding_Wave", "Tidal_Shield",
        "Mitlan_Glyph", "Crashing_Wave", "Maelstrom", "Drowning_Prison");
    for (int i = 0; i < names.size(); i++) {
      assertEquals("Mitlan", skills.getString(names.get(i) + ".element"));
      assertEquals(i / 2 + 1, skills.getInt(names.get(i) + ".tier"));
    }
    assertTrue(skills.getKeys(false).stream().noneMatch(k -> k.toUpperCase().startsWith("CLASS_")));
  }
}
