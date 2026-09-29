package net.tfminecraft.magic;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import io.lumine.mythic.lib.MythicLib;
import io.lumine.mythic.lib.api.item.NBTItem;
import java.util.*;
import java.util.logging.Logger;
import net.Indyuce.mmoitems.ItemStats;
import net.Indyuce.mmoitems.MMOItems;
import net.Indyuce.mmoitems.api.item.mmoitem.LiveMMOItem;
import net.Indyuce.mmoitems.stat.data.StringData;
import net.Indyuce.mmoitems.stat.type.NameData;
import net.Indyuce.mmoitems.stat.type.StatHistory;
import net.tfminecraft.magic.artifact.*;
import net.tfminecraft.magic.artifact.config.*;
import net.tfminecraft.magic.artifact.generate.*;
import net.tfminecraft.magic.charge.ChargeRegistry;
import net.tfminecraft.magic.registry.ElementRegistry;
import net.tfminecraft.tlibs.TLibs;
import net.tfminecraft.tlibs.objects.api.ItemAPI;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.junit.jupiter.api.*;
import org.mockbukkit.mockbukkit.*;
import org.mockito.*;

class ArtifactBuilderTest {
  ServerMock server;
  MMOItems previousMmo;
  MythicLib previousLib;

  @BeforeEach
  void setup() {
    server = MockBukkit.mock();
    Magic.plugin = mock(Magic.class);
    when(Magic.plugin.namespace()).thenReturn("magic");
    when(Magic.plugin.getLogger()).thenReturn(Logger.getAnonymousLogger());
    previousMmo = MMOItems.plugin;
    previousLib = MythicLib.plugin;
    MMOItems.plugin = mock(MMOItems.class, RETURNS_DEEP_STUBS);
    when(MMOItems.plugin.namespace()).thenReturn("mmoitems");
    MythicLib.plugin = mock(MythicLib.class, RETURNS_DEEP_STUBS);
    when(MythicLib.plugin.namespace()).thenReturn("mythiclib");
    ArtifactTypeRegistry.clear();
    ArtifactRarityRegistry.clear();
    ArtifactNamingSchemeRegistry.clear();
    ArtifactModelSchemeRegistry.clear();
    ArtifactAdjectiveRegistry.clear();
    ChargeRegistry.clear();
    ElementRegistry.clear();
    ArtifactTypeRegistry.register(ArtifactGenerationTest.type("fire", true, 1));
    ArtifactRarityRegistry.register(ArtifactGenerationTest.rarity("common", 1, 1, 1));
    ElementRegistry.register(DomainTest.element("fire"));
  }

  @AfterEach
  void cleanup() {
    Magic.plugin = null;
    MMOItems.plugin = previousMmo;
    MythicLib.plugin = previousLib;
    MockBukkit.unmock();
    ArtifactTypeRegistry.clear();
    ArtifactRarityRegistry.clear();
    ArtifactNamingSchemeRegistry.clear();
    ArtifactModelSchemeRegistry.clear();
    ArtifactAdjectiveRegistry.clear();
    ChargeRegistry.clear();
    ElementRegistry.clear();
  }

  ArtifactRoll roll() {
    return ArtifactRoll.ok("fire", "common", List.of(new ArtifactAuraSlot("fire", 10)));
  }

  @Test
  void namesUseConfiguredFallbackKindsAndInsertAdjectivesAfterArticles() {
    var builder = new ArtifactItemBuilder(new Random(1));
    var type = ArtifactTypeRegistry.getById("fire");
    assertEquals("", builder.pickBaseName(null, "wand"));
    assertNull(builder.pickBaseName(ArtifactGenerationTest.type(null, true, 1), "wand", "common"));
    assertEquals("fire", builder.pickBaseName(type, "wand"));
    ArtifactNamingSchemeRegistry.register(new ArtifactNamingScheme("names", Map.of()));
    assertEquals("fire", builder.pickBaseName(type, "wand", "common"));
    ArtifactAdjectiveRegistry.setChance("common", 1);
    assertEquals("fire", builder.pickBaseName(type, "wand", "common"));
    ArtifactAdjectiveRegistry.setGlobal("common", List.of("Fiery"));
    for (String name : List.of("A Wand", "An Orb", "The Stone", "Crystal", "Two Words")) {
      ArtifactNamingSchemeRegistry.register(
          new ArtifactNamingScheme("names", Map.of("manuscript", List.of(name))));
      String expected =
          name.equals("Two Words")
              ? "Fiery Two Words"
              : name.equals("Crystal") ? "Fiery Crystal" : name.replace(" ", " Fiery ");
      assertEquals(expected, builder.pickBaseName(type, "unknown", "common"));
    }
    ArtifactNamingSchemeRegistry.register(
        new ArtifactNamingScheme("names", Map.of("orb", List.of("Orb"))));
    assertEquals("Fiery Orb", builder.pickBaseName(type, "missing", "common"));
    assertEquals("Orb", builder.pickBaseName(type, "orb", null));
    assertEquals("Orb", builder.pickBaseName(type, "orb", " "));
    assertNotNull(new ArtifactItemBuilder());
    assertNotNull(new ArtifactItemBuilder(null));
  }

  @Test
  void modelsFilterByRarityAndResolveTheirKinds() {
    var builder = new ArtifactItemBuilder(new Random(1));
    var type = ArtifactTypeRegistry.getById("fire");
    assertNull(builder.pickModel(null, "common"));
    assertNull(builder.pickModel(type, "common"));
    assertNull(builder.pickModelPath(type, "common"));
    assertNull(ArtifactItemBuilder.kindForPath(null, "v.STICK"));
    assertNull(ArtifactItemBuilder.kindForPath(type, null));
    assertNull(ArtifactItemBuilder.kindForPath(type, " "));
    assertNull(ArtifactItemBuilder.kindForPath(type, "v.STICK"));
    ArtifactModelSchemeRegistry.register(new ArtifactModelScheme("models", List.of()));
    assertNull(builder.pickModel(type, "common"));
    var entry = new ArtifactModelEntry("wand", "v.STICK", null, null);
    ArtifactModelSchemeRegistry.register(
        new ArtifactModelScheme(
            "models",
            Arrays.asList(null, new ArtifactModelEntry("orb", "v.STONE", "rare", "rare"), entry)));
    assertSame(entry, builder.pickModel(type, "common"));
    assertEquals("v.STICK", builder.pickModelPath(type, "common"));
    assertEquals("wand", ArtifactItemBuilder.kindForPath(type, "v.STICK"));
    assertNull(ArtifactItemBuilder.kindForPath(type, "missing"));
    assertNull(builder.pickModel(type, "unknown"));
  }

  @Test
  void adjectivePoolsNormalizeDeduplicateAndClampChance() {
    ArtifactAdjectiveRegistry.setChance(null, 1);
    ArtifactAdjectiveRegistry.setChance(" ", 1);
    assertEquals(0, ArtifactAdjectiveRegistry.chance(null));
    assertEquals(0, ArtifactAdjectiveRegistry.chance(" "));
    assertEquals(0, ArtifactAdjectiveRegistry.chance("unknown"));
    ArtifactAdjectiveRegistry.setChance(" COMMON ", 2);
    assertEquals(1, ArtifactAdjectiveRegistry.chance("common"));
    ArtifactAdjectiveRegistry.setChance("rare", -1);
    assertEquals(0, ArtifactAdjectiveRegistry.chance("rare"));
    ArtifactAdjectiveRegistry.setGlobal(null, List.of());
    ArtifactAdjectiveRegistry.setGlobal(" ", List.of());
    ArtifactAdjectiveRegistry.setGlobal("common", null);
    ArtifactAdjectiveRegistry.setGlobal("common", Arrays.asList(null, " ", " Hot "));
    ArtifactAdjectiveRegistry.setElement(null, "common", List.of());
    ArtifactAdjectiveRegistry.setElement(" ", "common", List.of());
    ArtifactAdjectiveRegistry.setElement(" FIRE ", "common", List.of("Hot", "Burning"));
    assertEquals(List.of("Hot", "Burning"), ArtifactAdjectiveRegistry.pool("fire", "common"));
    assertEquals(List.of("Hot"), ArtifactAdjectiveRegistry.pool(null, "common"));
    assertEquals(List.of(), ArtifactAdjectiveRegistry.pool("fire", null));
    assertEquals(List.of(), ArtifactAdjectiveRegistry.pool("fire", " "));
    assertEquals(List.of(), ArtifactAdjectiveRegistry.pool("unknown", "missing"));
  }

  @Test
  void buildsRequireEnabledDependenciesAndValidTemplates() {
    var builder = new ArtifactItemBuilder();
    assertNull(builder.build(null));
    assertNull(builder.build(ArtifactRoll.error("bad")));
    assertNull(builder.build(roll()));
    var mmo = MockBukkit.createMockPlugin("MMOItems");
    assertNull(builder.build(roll(), null, null));
    var mythic = MockBukkit.createMockPlugin("MythicLib");
    try (var libs = mockStatic(TLibs.class)) {
      var api = mock(ItemAPI.class, RETURNS_DEEP_STUBS);
      libs.when(TLibs::getItemAPI).thenReturn(api);
      assertNull(builder.build(ArtifactRoll.ok("missing", "common", List.of())));
      assertNull(builder.build(ArtifactRoll.ok("fire", "missing", List.of())));
      when(api.getCreator().getItemFromPath(anyString())).thenReturn(null);
      assertNull(builder.build(roll()));
      when(api.getCreator().getItemFromPath(anyString())).thenReturn(new ItemStack(Material.AIR));
      assertNull(builder.build(roll()));
    }
    server.getPluginManager().disablePlugin(mythic);
    assertNull(builder.build(roll()));
    server.getPluginManager().disablePlugin(mmo);
    assertNull(builder.build(roll()));
  }

  @Test
  void builtItemsCarryUniqueIdentityAuraModelAndUpdatedNameHistory() {
    MockBukkit.createMockPlugin("MMOItems");
    MockBukkit.createMockPlugin("MythicLib");
    var builder = new ArtifactItemBuilder(new Random(1));
    try (var libs = mockStatic(TLibs.class);
        var nbt = mockStatic(NBTItem.class);
        var models = mockStatic(net.tfminecraft.magic.util.LegacyModelData.class)) {
      var api = mock(ItemAPI.class, RETURNS_DEEP_STUBS);
      libs.when(TLibs::getItemAPI).thenReturn(api);
      when(api.getCreator().getItemFromPath(anyString())).thenReturn(new ItemStack(Material.STONE));
      nbt.when(() -> NBTItem.get(any(ItemStack.class))).thenReturn(mock(NBTItem.class));
      StringData oldName = new StringData("Old");
      NameData original = new NameData("Old");
      var history = mock(StatHistory.class);
      when(history.getOriginalData()).thenReturn(original);
      try (var live =
          mockConstruction(
              LiveMMOItem.class,
              withSettings().defaultAnswer(RETURNS_DEEP_STUBS),
              (m, c) -> {
                when(m.getData(ItemStats.NAME)).thenReturn(oldName);
                when(m.computeStatHistory(ItemStats.NAME)).thenReturn(history);
                when(m.newBuilder().build()).thenAnswer(i -> new ItemStack(Material.STONE));
              })) {
        var result = builder.build(roll(), "Spark", "v.STICK.42");
        assertNotNull(result);
        assertEquals(Material.STICK, result.getType());
        models.verify(() -> net.tfminecraft.magic.util.LegacyModelData.set(any(), eq(42)));
        assertNotNull(ArtifactIds.read(result));
        assertEquals(
            "common",
            result
                .getItemMeta()
                .getPersistentDataContainer()
                .get(ArtifactKeys.artifactRarity(), PersistentDataType.STRING));
        assertEquals(10, Artifact.fromItem(result).getCap("fire"));
        assertEquals(0, Artifact.fromItem(result).getFill("fire"));
        assertTrue(original.getString().contains("Spark"));
        assertEquals(original.getString(), oldName.getString());
        assertNotNull(builder.build(roll(), null, null, true));
        ArtifactModelSchemeRegistry.register(
            new ArtifactModelScheme(
                "models", List.of(new ArtifactModelEntry("wand", " ", null, null))));
        assertNotNull(builder.build(roll(), null, null, true));
        ArtifactModelSchemeRegistry.register(
            new ArtifactModelScheme(
                "models", List.of(new ArtifactModelEntry("wand", "v.STICK", null, null))));
        ArtifactNamingSchemeRegistry.register(
            new ArtifactNamingScheme("names", Map.of("wand", List.of(" "))));
        assertNotNull(builder.build(roll(), " ", " ", true));
        ArtifactNamingSchemeRegistry.register(
            new ArtifactNamingScheme("names", Map.of("wand", List.of("Wand"))));
        assertNotNull(builder.build(roll(), null, null, true));
        assertNotNull(
            builder.build(
                ArtifactRoll.ok(
                    "fire",
                    "common",
                    Arrays.asList(
                        null,
                        new ArtifactAuraSlot(null, 1),
                        new ArtifactAuraSlot(" ", 1),
                        new ArtifactAuraSlot("fire", 10))),
                "Spark",
                "v.STICK"));
        for (String path : List.of("unknown.model", "v", "v.BAD_MATERIAL", "v.STICK.bad")) {
          assertNotNull(builder.build(roll(), "Spark", path, true));
        }
        assertNotNull(builder.build(roll(), "Spark", null, true));
        when(api.getArmorMerger().merge(any(), any(), eq("ia.model")))
            .thenAnswer(i -> i.getArgument(0));
        assertNotNull(builder.build(roll(), "Spark", "ia.model", true));
      }
      var failedBuilds = new java.util.concurrent.atomic.AtomicInteger();
      try (var live =
          mockConstruction(
              LiveMMOItem.class,
              withSettings().defaultAnswer(RETURNS_DEEP_STUBS),
              (m, c) -> {
                when(m.getData(ItemStats.NAME)).thenReturn(null);
                when(m.computeStatHistory(ItemStats.NAME)).thenReturn(null);
                when(m.newBuilder().build())
                    .thenAnswer(
                        i ->
                            failedBuilds.getAndIncrement() == 0
                                ? null
                                : new ItemStack(Material.AIR));
              })) {
        assertNull(builder.build(roll(), null, null, true));
        assertNull(builder.build(roll(), null, null, true));
      }
    }
  }

  @Test
  void invalidModelsPreserveMaterialAndAllExistingMetadata() throws Exception {
    var item = new ItemStack(Material.STONE);
    var meta = item.getItemMeta();
    meta.setDisplayName("Keep me");
    meta.getPersistentDataContainer()
        .set(ArtifactKeys.artifactPrimary(), PersistentDataType.STRING, "fire");
    item.setItemMeta(meta);
    var baseline = item.clone();
    var builder = new ArtifactItemBuilder();
    var apply =
        ArtifactItemBuilder.class.getDeclaredMethod(
            "applyModel", ItemStack.class, ArtifactTypeDef.class, String.class, boolean.class);
    apply.setAccessible(true);
    for (String path : List.of("v.AIR", "v.WATER", "v.DIAMOND.bad-number", ".")) {
      assertSame(
          item, apply.invoke(builder, item, ArtifactTypeRegistry.getById("fire"), path, false));
      assertEquals(baseline, item, path);
    }
    try (var libs = mockStatic(TLibs.class)) {
      var api = mock(ItemAPI.class, RETURNS_DEEP_STUBS);
      libs.when(TLibs::getItemAPI).thenReturn(api);
      when(api.getArmorMerger().merge(any(), any(), eq("ia.invalid")))
          .thenReturn(null, new ItemStack(Material.AIR));
      for (int i = 0; i < 2; i++) {
        assertSame(
            item,
            apply.invoke(builder, item, ArtifactTypeRegistry.getById("fire"), "ia.invalid", false));
        assertEquals(baseline, item);
      }
    }
  }
}
