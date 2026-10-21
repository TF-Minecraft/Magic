package net.tfminecraft.magic.artifact.sealed;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.nio.file.*;
import java.util.*;
import java.util.logging.*;
import net.tfminecraft.magic.Cache;
import net.tfminecraft.magic.Magic;
import net.tfminecraft.magic.Messages;
import net.tfminecraft.magic.artifact.config.*;
import net.tfminecraft.magic.artifact.generate.ArtifactRoll;
import net.tfminecraft.magic.artifact.generate.ArtifactRoller;
import net.tfminecraft.magic.loader.ElementsLoader;
import net.tfminecraft.tlibs.TLibs;
import net.tfminecraft.tlibs.objects.api.ItemAPI;
import org.bukkit.Material;
import org.bukkit.entity.Item;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;

class SealedArtifactTest {
  @TempDir Path temp;
  ServerMock server;
  final List<String> warnings = new ArrayList<>();
  final List<String> infos = new ArrayList<>();

  @BeforeEach
  void setup() {
    server = MockBukkit.mock();
    Magic.plugin = mock(Magic.class);
    var logger = Logger.getAnonymousLogger();
    logger.setUseParentHandlers(false);
    logger.addHandler(
        new Handler() {
          @Override
          public void publish(LogRecord record) {
            if (record.getLevel().intValue() >= Level.WARNING.intValue()) {
              warnings.add(record.getMessage());
            } else {
              infos.add(record.getMessage());
            }
          }

          @Override
          public void flush() {}

          @Override
          public void close() {}
        });
    when(Magic.plugin.getLogger()).thenReturn(logger);
    when(Magic.plugin.namespace()).thenReturn("magic");
    clearRegistries();
  }

  @AfterEach
  void cleanup() {
    clearRegistries();
    Magic.plugin = null;
    MockBukkit.unmock();
  }

  static void clearRegistries() {
    SealedArtifactRegistry.clear();
    ArtifactRarityRegistry.clear();
    ArtifactTypeRegistry.clear();
  }

  static void rarities(String... ids) {
    for (String id : ids) {
      var rarity = mock(ArtifactRarityDef.class);
      when(rarity.getId()).thenReturn(id);
      ArtifactRarityRegistry.register(rarity);
    }
  }

  static void type(String id, boolean enabled, String... missingRarities) {
    var type = mock(ArtifactTypeDef.class);
    when(type.getElementId()).thenReturn(id);
    when(type.isEnabled()).thenReturn(enabled);
    when(type.hasRarity(anyString())).thenReturn(true);
    for (String rarity : missingRarities) {
      when(type.hasRarity(rarity)).thenReturn(false);
    }
    ArtifactTypeRegistry.register(type);
  }

  SealedArtifactDef def(String id) {
    return SealedArtifactRegistry.getAll().stream()
        .filter(d -> d.getId().equals(id))
        .findFirst()
        .orElse(null);
  }

  boolean load(String yaml) throws Exception {
    var file = temp.resolve("sealed.yml");
    Files.writeString(file, yaml);
    return SealedArtifactConfigLoader.load(file.toFile());
  }

  @Test
  void bundledSealedFileLoadsFiveSealsAgainstTheBundledGenerator() throws Exception {
    new ElementsLoader().loadFolder(Path.of("src/main/resources/elements").toFile());
    Cache.artifactAuraCap = 150;
    try (var files = Files.list(Path.of("src/main/resources/artifacts"))) {
      for (Path file : files.toList()) {
        Files.copy(file, temp.resolve(file.getFileName()));
      }
    }
    assertTrue(ArtifactConfigLoader.loadFolder(temp.toFile()));
    assertTrue(warnings.stream().noneMatch(w -> w.toLowerCase().contains("sealed")), warnings::toString);
    assertEquals(5, SealedArtifactRegistry.size());
    var weights = new LinkedHashMap<String, Double>();
    weights.put("common", 65.0);
    weights.put("uncommon", 25.0);
    weights.put("rare", 7.0);
    weights.put("legendary", 3.0);
    for (var seal : SealedArtifactRegistry.getAll()) {
      assertEquals(weights, seal.getRarities(), seal.getId());
    }
    assertNull(def("random").getElementId());
    assertEquals("m.loot.sealed_artifact", def("random").getItemPath());
    for (String school : List.of("oseni", "seithr", "cerrith", "mitlan")) {
      assertEquals(school, def(school).getElementId());
      assertEquals("m.loot.sealed_" + school + "_artifact", def(school).getItemPath());
    }
    assertEquals("entity.experience_orb.pickup", SealedArtifactRegistry.getSound());

    // A reload with no sealed.yml leaves the feature off without failing the load.
    Files.delete(temp.resolve("sealed.yml"));
    assertTrue(ArtifactConfigLoader.loadFolder(temp.toFile()));
    assertEquals(0, SealedArtifactRegistry.size());
  }

  @Test
  void missingOrBrokenFilesTurnSealsOffWithoutFailingTheLoad() throws Exception {
    SealedArtifactRegistry.register(new SealedArtifactDef("old", "v.STONE", null, Map.of("x", 1.0)));
    assertTrue(SealedArtifactConfigLoader.load(null));
    assertEquals(0, SealedArtifactRegistry.size());
    assertTrue(SealedArtifactConfigLoader.load(temp.resolve("absent.yml").toFile()));
    assertEquals(List.of(), warnings);
    assertTrue(load("broken: [unterminated"));
    assertEquals(1, warnings.size());
    assertTrue(warnings.get(0).startsWith("[Magic] Failed to load sealed.yml, sealed artifacts disabled"));
    assertEquals(0, SealedArtifactRegistry.size());
    assertTrue(load("sound: note\n"));
    assertEquals(0, SealedArtifactRegistry.size());
    assertEquals("note", SealedArtifactRegistry.getSound());
  }

  @Test
  void invalidEntriesAndWeightsAreSkippedWithAWarning() throws Exception {
    rarities("common", "uncommon", "rare", "legendary");
    type("cerrith", true);
    type("seithr", true, "legendary");
    type("mitlan", false);
    String yaml =
        """
        rarities:
          Common: 5
          mythic: 3
          rare: -1
          legendary: .inf
          uncommon: 0
        sealed:
          scalar: bad
          noitem: {element: cerrith}
          unknown: {item: v.STONE, element: nope}
          disabled: {item: v.STONE, element: mitlan}
          own: {item: ' v.STONE ', element: CERRITH, rarities: {rare: 2}}
          nothing: {item: v.STONE, rarities: {common: 0}}
          random: {item: v.STONE}
          partial: {item: v.STONE, element: seithr, rarities: {common: 1, legendary: 4}}
          bracketless: {item: v.STONE, element: seithr, rarities: {legendary: 4}}
        """;
    assertTrue(load(yaml));
    assertEquals(
        List.of(
            "[Magic] Unknown rarity 'mythic' in sealed.yml rarities",
            "[Magic] Rarity 'rare' in sealed.yml rarities needs a weight of 0 or more",
            "[Magic] Rarity 'legendary' in sealed.yml rarities needs a weight of 0 or more",
            "[Magic] Sealed artifact 'scalar' has no data",
            "[Magic] Sealed artifact 'noitem' has no item",
            "[Magic] Sealed artifact 'unknown' has unknown or disabled element 'nope'",
            "[Magic] Sealed artifact 'disabled' has unknown or disabled element 'mitlan'",
            "[Magic] Sealed artifact 'nothing' has no rarity with a weight above 0",
            "[Magic] Sealed artifact 'partial': seithr has no legendary bracket",
            "[Magic] Sealed artifact 'bracketless': seithr has no legendary bracket",
            "[Magic] Sealed artifact 'bracketless' has no rarity with a weight above 0"),
        warnings);
    assertEquals(
        List.of("own", "random", "partial"),
        SealedArtifactRegistry.getAll().stream().map(SealedArtifactDef::getId).toList());
    assertEquals("v.STONE", def("own").getItemPath());
    assertEquals("cerrith", def("own").getElementId());
    assertEquals(Map.of("rare", 2.0), def("own").getRarities());
    assertNull(def("random").getElementId());
    assertEquals(Map.of("common", 5.0), def("random").getRarities());
    assertEquals(Map.of("common", 1.0), def("partial").getRarities());
    assertEquals("", SealedArtifactRegistry.getSound());

    assertTrue(load("rarities: {common: 1}\nsealed:\n  random: {item: v.STONE}\n"));
    assertEquals(1, SealedArtifactRegistry.size());
  }

  @Test
  void registryMatchesOnlyConfiguredSealedItems() {
    SealedArtifactRegistry.setSound(null);
    assertEquals("", SealedArtifactRegistry.getSound());
    assertNull(SealedArtifactRegistry.match(null));
    assertNull(SealedArtifactRegistry.match(new ItemStack(Material.AIR)));
    assertNull(SealedArtifactRegistry.match(new ItemStack(Material.STONE)));
    var seal = new SealedArtifactDef("random", "v.STONE", null, Map.of("common", 1.0));
    var broken = new SealedArtifactDef("broken", "v.BAD", null, Map.of("common", 1.0));
    SealedArtifactRegistry.register(seal);
    try (var libs = mockStatic(TLibs.class)) {
      ItemAPI api = mock(ItemAPI.class, RETURNS_DEEP_STUBS);
      libs.when(TLibs::getItemAPI).thenReturn(api);
      var creator = api.getCreator();
      var stone = new ItemStack(Material.STONE);
      when(api.getCreator().getItemFromPath("v.STONE")).thenReturn(new ItemStack(Material.STONE));
      when(api.getChecker().checkItemWithPath(stone, "v.STONE")).thenReturn(true);
      assertNull(SealedArtifactRegistry.match(new ItemStack(Material.APPLE)));
      assertSame(seal, SealedArtifactRegistry.match(stone));
      verify(api.getCreator(), times(1)).getItemFromPath("v.STONE");
      when(api.getChecker().checkItemWithPath(stone, "v.STONE")).thenReturn(false);
      assertNull(SealedArtifactRegistry.match(stone));

      // A path that does not resolve turns the material fast path off.
      SealedArtifactRegistry.register(broken);
      when(api.getCreator().getItemFromPath("v.BAD")).thenThrow(new IllegalStateException("bad"));
      when(api.getChecker().checkItemWithPath(any(), eq("v.BAD"))).thenThrow(new IllegalStateException());
      var apple = new ItemStack(Material.APPLE);
      when(api.getChecker().checkItemWithPath(apple, "v.STONE")).thenReturn(true);
      assertSame(seal, SealedArtifactRegistry.match(apple));
      assertNull(SealedArtifactRegistry.match(stone));
      doReturn(null).when(creator).getItemFromPath("v.BAD");
      SealedArtifactRegistry.register(broken);
      assertSame(seal, SealedArtifactRegistry.match(apple));
      doReturn(new ItemStack(Material.AIR)).when(creator).getItemFromPath("v.BAD");
      SealedArtifactRegistry.register(broken);
      assertSame(seal, SealedArtifactRegistry.match(apple));
    }
  }

  static Random fixed(double... values) {
    return new Random() {
      int next;

      @Override
      public double nextDouble() {
        return values[Math.min(next++, values.length - 1)];
      }
    };
  }

  static Map<String, Double> weights() {
    var weights = new LinkedHashMap<String, Double>();
    weights.put("common", 65.0);
    weights.put("uncommon", 25.0);
    weights.put("rare", 7.0);
    weights.put("legendary", 3.0);
    return weights;
  }

  @Test
  void rarityPickFollowsTheWeights() {
    var service = new SealedArtifactService(fixed(0.0, 0.6499, 0.6501, 0.8999, 0.9001, 0.9699, 0.9701, 0.999, 1.0), null, null);
    var picks = new ArrayList<String>();
    for (int i = 0; i < 9; i++) {
      picks.add(service.pickRarity(weights()));
    }
    assertEquals(
        List.of("common", "common", "uncommon", "uncommon", "rare", "rare", "legendary", "legendary", "legendary"),
        picks);
    assertNull(service.pickRarity(Map.of()));

    // Weights too large to add up still split evenly.
    var huge = new LinkedHashMap<String, Double>();
    huge.put("common", 1e308);
    huge.put("legendary", 1e308);
    var halves = new SealedArtifactService(fixed(0.25, 0.75), null, null);
    assertEquals("common", halves.pickRarity(huge));
    assertEquals("legendary", halves.pickRarity(huge));

    var seeded = new SealedArtifactService(new Random(7), null, null);
    var counts = new HashMap<String, Integer>();
    for (int i = 0; i < 100_000; i++) {
      counts.merge(seeded.pickRarity(weights()), 1, Integer::sum);
    }
    assertEquals(65_000, counts.get("common"), 1_000);
    assertEquals(25_000, counts.get("uncommon"), 1_000);
    assertEquals(7_000, counts.get("rare"), 500);
    assertEquals(3_000, counts.get("legendary"), 300);
  }

  ItemStack sealed(int amount) {
    return new ItemStack(Material.ECHO_SHARD, amount);
  }

  ItemStack artifactItem() {
    return new ItemStack(Material.BLAZE_ROD);
  }

  @Test
  void breakingASealUsesOneItemAndGivesTheRolledArtifact() {
    var player = server.addPlayer();
    var roller = mock(ArtifactRoller.class);
    var rolled = ArtifactRoll.ok("cerrith", "rare", List.of());
    when(roller.roll(anyString(), anyString())).thenReturn(rolled);
    var built = new ArrayList<ArtifactRoll>();
    var service =
        new SealedArtifactService(
            fixed(0.95),
            roller,
            roll -> {
              built.add(roll);
              return artifactItem();
            });
    var random = new SealedArtifactDef("random", "v.ECHO_SHARD", null, weights());
    var cerrith = new SealedArtifactDef("cerrith", "v.ECHO_SHARD", "cerrith", weights());
    SealedArtifactRegistry.setSound("entity.experience_orb.pickup");
    try (var messages = mockStatic(Messages.class, i -> i.getArgument(0))) {
      player.getInventory().setItemInMainHand(sealed(2));
      assertTrue(service.open(player, EquipmentSlot.HAND, random));
      verify(roller).roll("random", "rare");
      assertEquals(List.of(rolled), built);
      assertEquals(1, player.getInventory().getItemInMainHand().getAmount());
      assertTrue(player.getInventory().contains(Material.BLAZE_ROD, 1));
      assertEquals("artifact.sealed.opened", player.nextMessage());
      player.assertSoundHeard("entity.experience_orb.pickup");
      assertEquals(
          List.of(
              "[Magic] Sealed artifact opened: player="
                  + player.getName()
                  + " seal=random element=cerrith rarity=rare artifact=null"),
          infos);

      SealedArtifactRegistry.setSound("");
      player.getInventory().setItemInOffHand(sealed(1));
      assertTrue(service.open(player, EquipmentSlot.OFF_HAND, cerrith));
      verify(roller).roll("cerrith", "rare");
      assertTrue(player.getInventory().getItemInOffHand().getType().isAir());
      assertTrue(player.getInventory().contains(Material.BLAZE_ROD, 2));
    }
  }

  @Test
  void aFullInventoryDropsTheArtifactAtThePlayersFeet() {
    var player = server.addPlayer();
    var roller = mock(ArtifactRoller.class);
    when(roller.roll(anyString(), anyString())).thenReturn(ArtifactRoll.ok("seithr", "common", List.of()));
    var service = new SealedArtifactService(fixed(0.0), roller, roll -> artifactItem());
    var seal = new SealedArtifactDef("seithr", "v.ECHO_SHARD", "seithr", weights());
    // MockBukkit's addItem also fills armour and off-hand slots, so fill those too.
    for (int slot = 0; slot < player.getInventory().getSize(); slot++) {
      player.getInventory().setItem(slot, new ItemStack(Material.DIRT, 64));
    }
    player.getInventory().setItemInMainHand(sealed(2));
    try (var messages = mockStatic(Messages.class, i -> i.getArgument(0))) {
      assertTrue(service.open(player, EquipmentSlot.HAND, seal));
    }
    assertEquals(1, player.getInventory().getItemInMainHand().getAmount());
    assertFalse(player.getInventory().contains(Material.BLAZE_ROD));
    var dropped = player.getWorld().getEntitiesByClass(Item.class);
    assertEquals(1, dropped.size());
    assertEquals(Material.BLAZE_ROD, dropped.iterator().next().getItemStack().getType());
  }

  @Test
  void aSealThatCannotBeMadeIsKept() {
    var player = server.addPlayer();
    var roller = mock(ArtifactRoller.class);
    var seal = new SealedArtifactDef("cerrith", "v.ECHO_SHARD", "cerrith", weights());
    player.getInventory().setItemInMainHand(sealed(1));
    try (var messages = mockStatic(Messages.class, i -> i.getArgument(0))) {
      when(roller.roll(anyString(), anyString())).thenReturn(ArtifactRoll.error("artifact.roll.no_bracket"));
      var builder = new ArrayList<ArtifactRoll>();
      var service =
          new SealedArtifactService(
              fixed(0.0),
              roller,
              roll -> {
                builder.add(roll);
                return artifactItem();
              });
      assertFalse(service.open(player, EquipmentSlot.HAND, seal));
      assertTrue(builder.isEmpty());
      assertEquals("artifact.sealed.failed", player.nextMessage());

      when(roller.roll(anyString(), anyString())).thenReturn(ArtifactRoll.ok("cerrith", "common", List.of()));
      assertFalse(new SealedArtifactService(fixed(0.0), roller, roll -> null).open(player, EquipmentSlot.HAND, seal));
      assertEquals("artifact.sealed.failed", player.nextMessage());

      // The real builder needs MMOItems, which tests do not load, so it fails safely too.
      new ElementsLoader().loadFolder(Path.of("src/main/resources/elements").toFile());
      Cache.artifactAuraCap = 150;
      assertTrue(ArtifactConfigLoader.loadFolder(Path.of("src/main/resources/artifacts").toFile()));
      assertFalse(new SealedArtifactService().open(player, EquipmentSlot.HAND, seal));
      assertEquals("artifact.sealed.failed", player.nextMessage());
    }
    assertEquals(1, player.getInventory().getItemInMainHand().getAmount());
    assertEquals(Material.ECHO_SHARD, player.getInventory().getItemInMainHand().getType());
  }
}
