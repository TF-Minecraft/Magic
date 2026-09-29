package net.tfminecraft.magic;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import io.lumine.mythic.lib.skill.trigger.TriggerType;
import java.util.*;
import net.tfminecraft.magic.artifact.*;
import net.tfminecraft.magic.artifact.aura.*;
import net.tfminecraft.magic.artifact.config.*;
import net.tfminecraft.magic.artifact.generate.*;
import net.tfminecraft.magic.artifact.shrine.ShrineChargeService;
import net.tfminecraft.magic.command.MagicCommand;
import net.tfminecraft.magic.gear.*;
import net.tfminecraft.magic.registry.ElementRegistry;
import net.tfminecraft.magic.session.*;
import org.bukkit.*;
import org.bukkit.command.CommandSender;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.*;
import org.mockbukkit.mockbukkit.*;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import org.mockito.MockedStatic;

class CommandTest {
  ServerMock server;
  PlayerMock player;
  MagicCommand command = new MagicCommand();
  MockedStatic<Messages> messages;
  ResonanceSessionManager sessions;

  @BeforeEach
  void setup() {
    server = MockBukkit.mock();
    server.addSimpleWorld("world");
    player = server.addPlayer("Mage");
    player.setOp(true);
    Magic.plugin = mock(Magic.class, RETURNS_DEEP_STUBS);
    when(Magic.plugin.namespace()).thenReturn("magic");
    sessions = new ResonanceSessionManager();
    when(Magic.plugin.getResonanceGuiManager().getSessionManager()).thenReturn(sessions);
    messages = mockStatic(Messages.class, i -> i.getArgument(0));
    ElementRegistry.clear();
    ElementRegistry.register(DomainTest.element("fire"));
    ElementRegistry.register(DomainTest.element("water"));
    ArtifactTypeRegistry.clear();
    ArtifactTypeRegistry.register(ArtifactGenerationTest.type("fire", true, 1));
    ArtifactTypeRegistry.register(ArtifactGenerationTest.type("water", false, 1));
    ArtifactRarityRegistry.clear();
    Cache.runeKeybinds = new LinkedHashSet<>(List.of(TriggerType.RIGHT_CLICK));
    Cache.defaultResonance = 0;
  }

  @AfterEach
  void cleanup() {
    messages.close();
    Magic.plugin = null;
    MockBukkit.unmock();
    ElementRegistry.clear();
    ArtifactTypeRegistry.clear();
    ArtifactRarityRegistry.clear();
  }

  void run(CommandSender sender, String... args) {
    assertTrue(command.onCommand(sender, null, "magic", args));
  }

  void expect(String key, String... args) {
    run(player, args);
    assertEquals(key, player.nextMessage());
    assertNull(player.nextMessage());
  }

  List<String> tab(String... args) {
    return command.onTabComplete(player, null, "magic", args);
  }

  ItemStack artifact() {
    var item = new ItemStack(Material.STONE);
    var a = Artifact.create();
    a.setCap("fire", 10);
    a.setFill("fire", 4);
    a.persistPdc(item);
    ArtifactIds.writeNew(item);
    return item;
  }

  @Test
  void permissionsRoutesAndReloadReportActualResult() {
    player.setOp(false);
    expect("admin.no_permission");
    expect("admin.no_permission", "reload");
    expect("rune.no_permission", "rune");
    assertTrue(tab("r").isEmpty());
    player.addAttachment(MockBukkit.createMockPlugin(), "magic.rune.keybind", true);
    expect("rune.usage");
    assertEquals(List.of("rune"), tab(""));
    assertTrue(tab("artifact", "").isEmpty());
    player.setOp(true);
    expect("admin.usage");
    expect("admin.usage", "unknown");
    expect("reload.failed", "reload");
    when(Magic.plugin.reloadAll()).thenReturn(true);
    expect("reload.success", "reload");
    run(player, "open");
    verify(Magic.plugin.getResonanceGuiManager()).tryOpen(player, true);
    var console = mock(CommandSender.class);
    when(console.hasPermission(anyString())).thenReturn(true);
    for (String route : List.of("open", "refresh", "rune", "fillchest")) run(console, route);
    verify(console).sendMessage("open.players_only");
    verify(console).sendMessage("gear.refresh.players_only");
    verify(console).sendMessage("rune.players_only");
    verify(console).sendMessage("fillchest.players_only");
  }

  @Test
  void runeCommandsValidateAndReplaceOnlySuccessfulResults() {
    expect("rune.usage", "rune");
    expect("rune.usage", "rune", "bad", "x");
    expect("rune.unknown_trigger", "rune", "keybind", "");
    expect("rune.unknown_trigger", "rune", "keybind", "invalid");
    try (var rune = mockStatic(RuneKeybind.class)) {
      var replacement = new ItemStack(Material.BLAZE_ROD);
      for (var result : RuneKeybind.Result.values()) {
        rune.when(() -> RuneKeybind.apply(any(), eq(TriggerType.RIGHT_CLICK)))
            .thenReturn(new RuneKeybind.Outcome(result, replacement));
        String key =
            switch (result) {
              case NOT_RUNE -> "not_a_rune";
              case NO_ABILITIES -> "no_abilities";
              case FAILED -> "failed";
              case OK -> "success";
            };
        expect("rune." + key, "rune", "keybind", "right_click");
      }
      assertEquals(replacement, player.getInventory().getItemInMainHand());
    }
  }

  @Test
  void refreshReportsFailureOrReplacesAndResyncs() {
    try (var refresh = mockStatic(GearRefresher.class)) {
      expect("gear.refresh.not_gear", "refresh");
      refresh.when(() -> GearRefresher.isManaged(any())).thenReturn(true);
      expect("gear.refresh.failed", "refresh");
      var item = new ItemStack(Material.DIAMOND_SWORD);
      refresh.when(() -> GearRefresher.refresh(any(), eq(player), eq(true))).thenReturn(item);
      expect("gear.refresh.ok", "refresh");
      assertEquals(item, player.getInventory().getItemInMainHand());
      verify(Magic.plugin).syncSpellModifiers(player, null);
    }
  }

  @Test
  void resonanceCommandsValidateTargetsMutateAndPersist() {
    expect("resonance.admin.usage", "resonance");
    expect("artifact.give.unknown_player", "resonance", "get", "missing");
    expect("resonance.admin.no_character", "resonance", "get", "Mage");
    sessions.setLoadedCharacterId(player, "character");
    expect("resonance.admin.no_character", "resonance", "get", "Mage");
    var s = sessions.getOrCreate(player);
    run(player, "resonance", "get", "Mage");
    assertEquals("resonance.admin.get_header", player.nextMessage());
    assertEquals("resonance.admin.get_line", player.nextMessage());
    assertEquals("resonance.admin.get_line", player.nextMessage());
    expect("resonance.admin.usage", "resonance", "set", "Mage");
    expect("resonance.admin.invalid_amount", "resonance", "set", "Mage", "fire", "oops");
    for (String action : List.of("set", "add", "reset"))
      expect("resonance.admin.unknown_element", "resonance", action, "Mage", "missing", "2");
    expect("resonance.admin.set_ok", "resonance", "set", "Mage", "fire", "12");
    assertEquals(12, s.getResonance("fire"));
    expect("resonance.admin.add_ok", "resonance", "add", "Mage", "fire", "3");
    assertEquals(15, s.getResonance("fire"));
    expect("resonance.admin.set_ok", "resonance", "set", "Mage", "all", "5");
    expect("resonance.admin.add_ok", "resonance", "add", "Mage", "all", "2");
    assertEquals(7, s.getResonance("water"));
    expect("resonance.admin.reset_ok", "resonance", "reset", "Mage");
    assertEquals(0, s.getResonance("fire"));
    when(Magic.plugin.getProfileService()).thenReturn(null);
    expect("resonance.admin.reset_ok", "resonance", "reset", "Mage", "fire");
    expect("resonance.admin.usage", "resonance", "bogus", "Mage", "fire", "2");
  }

  @Test
  void shrineCommandsValidateAndReportEveryServiceOutcome() {
    expect("shrine.fill.usage", "shrine");
    expect("shrine.fill.usage", "shrine", "other");
    expect("shrine.fill.usage", "shrine", "fill");
    expect("shrine.fill.unknown_element", "shrine", "fill", "bad", "1");
    for (String amount : List.of("bad", "0", "-1"))
      expect("shrine.fill.invalid_amount", "shrine", "fill", "fire", amount);
    var console = mock(CommandSender.class);
    when(console.hasPermission(anyString())).thenReturn(true);
    run(console, "shrine", "fill");
    verify(console).sendMessage("shrine.fill.players_only");
    try (var service = mockStatic(ShrineChargeService.class)) {
      for (var result : ShrineChargeService.AdminStart.values()) {
        service.when(() -> ShrineChargeService.startAdmin(player, "fire", 5)).thenReturn(result);
        expect(
            "shrine.fill." + result.name().toLowerCase(Locale.ROOT), "shrine", "fill", "fire", "5");
      }
    }
  }

  @Test
  void artifactCreationAndPathsValidateBeforeOpening() {
    expect("artifact.usage", "artifact");
    expect("artifact.usage", "artifact", "bad");
    expect("artifact.create.usage", "artifact", "create", "extra");
    run(player, "artifact", "create");
    verify(Magic.plugin.getArtifactCreateGuiManager()).open(player);
    var console = mock(CommandSender.class);
    when(console.hasPermission(anyString())).thenReturn(true);
    run(console, "artifact", "create");
    verify(console).sendMessage("artifact.create.players_only");
    expect("artifact.path.usage", "artifact", "path");
    expect("artifact.path.invalid", "artifact", "path", "");
    expect("artifact.path.spec", "artifact", "path", "magic.artifact");
    expect(
        "artifact.path.spec", "artifact", "path", "magic.(primary=fire;rarity=rare;water=2;fire)");
  }

  @Test
  void artifactFillValidatesIdentityCapAndAmount() {
    expect("artifact.setfill.usage", "artifact", "setfill");
    var console = mock(CommandSender.class);
    when(console.hasPermission(anyString())).thenReturn(true);
    run(console, "artifact", "setfill", "fire", "1");
    verify(console).sendMessage("artifact.setfill.players_only");
    expect("artifact.setfill.no_item", "artifact", "setfill", "fire", "1");
    player.getInventory().setItemInMainHand(new ItemStack(Material.STONE));
    expect("artifact.setfill.no_id", "artifact", "setfill", "fire", "1");
    var item = artifact();
    player.getInventory().setItemInMainHand(item);
    expect("artifact.setfill.unknown_element", "artifact", "setfill", "unknown", "1");
    expect("artifact.setfill.no_cap", "artifact", "setfill", "water", "1");
    expect("artifact.setfill.invalid_amount", "artifact", "setfill", "fire", "bad");
    expect("artifact.setfill.ok", "artifact", "setfill", "fire", "9");
    assertEquals(
        9, AuraVessels.fromItem(player.getInventory().getItemInMainHand()).getFill("fire"));
  }

  @Test
  void nonFiniteAmountsAreRejectedWithoutChangingArtifactOrResonance() {
    player.getInventory().setItemInMainHand(artifact());
    sessions.setLoadedCharacterId(player, "character");
    var session = sessions.getOrCreate(player);
    session.setResonance("fire", 7);
    for (String amount : List.of("NaN", "Infinity", "-Infinity")) {
      expect("artifact.setfill.invalid_amount", "artifact", "setfill", "fire", amount);
      assertEquals(
          4, AuraVessels.fromItem(player.getInventory().getItemInMainHand()).getFill("fire"));
      expect("resonance.admin.invalid_amount", "resonance", "set", "Mage", "fire", amount);
      assertEquals(7, session.getResonance("fire"));
      expect("shrine.fill.invalid_amount", "shrine", "fill", "fire", amount);
    }
  }

  @Test
  void chestModesAndTabSuggestionsRespectPermissionsEnabledTypesAndPositions() {
    expect("fillchest.usage", "fillchest");
    expect("fillchest.unknown", "fillchest", "bad");
    for (String mode : List.of("random", "all", "fire"))
      expect("fillchest.armed", "fillchest", mode);
    assertTrue(tab().isEmpty());
    assertTrue(tab("").containsAll(List.of("rune", "artifact", "shrine")));
    assertEquals(List.of("reload", "resonance", "refresh"), tab("re"));
    assertEquals(List.of("keybind"), tab("rune", ""));
    assertEquals(List.of("RIGHT_CLICK"), tab("rune", "keybind", ""));
    assertTrue(tab("rune", "bad", "").isEmpty());
    assertTrue(tab("rune", "keybind", "", "extra").isEmpty());
    assertEquals(List.of("random", "all", "fire"), tab("fillchest", ""));
    assertTrue(tab("fillchest", "all", "").isEmpty());
    assertEquals(List.of("get", "set", "add", "reset"), tab("resonance", ""));
    assertEquals(List.of("Mage"), tab("resonance", "get", ""));
    assertTrue(tab("resonance", "set", "Mage", "").containsAll(List.of("all", "fire", "water")));
    assertTrue(tab("resonance", "get", "Mage", "").isEmpty());
    assertEquals(List.of("fill"), tab("shrine", ""));
    assertEquals(2, tab("shrine", "fill", "").size());
    assertTrue(tab("shrine", "other", "").isEmpty());
    assertEquals(5, tab("artifact", "").size());
    assertEquals(2, tab("artifact", "setfill", "").size());
    assertTrue(tab("artifact", "setfill", "fire", "").isEmpty());
    assertEquals(List.of("random", "fire"), tab("artifact", "roll", ""));
    assertEquals(List.of("roll"), tab("artifact", "roll", "fire", ""));
    assertTrue(tab("artifact", "roll", "fire", "roll", "").isEmpty());
    assertEquals(List.of("Mage"), tab("artifact", "give", ""));
    assertEquals(List.of("random", "fire"), tab("artifact", "give", "Mage", ""));
    assertEquals(List.of("roll"), tab("artifact", "give", "Mage", "fire", ""));
    assertTrue(tab("artifact", "path", "").isEmpty());
    assertTrue(tab("unknown", "").isEmpty());
  }

  @Test
  void artifactRollAndGiveReportErrorsBuildItemsAndDropOverflow() {
    expect("artifact.give.usage", "artifact", "give");
    expect("artifact.give.unknown_player", "artifact", "give", "missing");
    ArtifactRoll[] roll = {ArtifactRoll.error("test.error", "fire", "rare")};
    ItemStack[] output = {null};
    try (var rolls =
            mockConstruction(
                ArtifactRoller.class,
                (m, c) -> when(m.roll(anyString(), anyString())).thenAnswer(i -> roll[0]));
        var builders =
            mockConstruction(
                ArtifactItemBuilder.class,
                (m, c) -> when(m.build(any())).thenAnswer(i -> output[0]))) {
      expect("test.error", "artifact", "roll", "fire", "rare");
      expect("test.error", "artifact", "give", "Mage", "fire", "rare");
      roll[0] = ArtifactRoll.error(null);
      expect("artifact.roll.usage", "artifact", "roll");
      expect("artifact.roll.usage", "artifact", "give", "Mage");
      roll[0] = ArtifactRoll.ok("fire", "rare", List.of());
      expect("artifact.roll.usage", "artifact", "roll");
      roll[0] =
          ArtifactRoll.ok(
              "fire",
              "rare",
              List.of(new ArtifactAuraSlot("fire", 10), new ArtifactAuraSlot("water", 2)));
      expect("artifact.give.failed", "artifact", "give", "Mage");
      output[0] = new ItemStack(Material.DIAMOND);
      run(player, "artifact", "give", "Mage");
      assertEquals("artifact.roll.header", player.nextMessage());
      assertEquals("artifact.roll.secondary", player.nextMessage());
      assertEquals("artifact.give.ok", player.nextMessage());
      assertTrue(player.getInventory().contains(Material.DIAMOND));
      ArtifactRarityRegistry.register(ArtifactGenerationTest.rarity("rare", 1, 0, 1));
      run(player, "artifact", "roll");
      assertEquals("artifact.roll.header", player.nextMessage());
      assertEquals("artifact.roll.secondary", player.nextMessage());
      for (int slot = 0; slot < player.getInventory().getSize(); slot++)
        player.getInventory().setItem(slot, new ItemStack(Material.STONE, 64));
      long before = player.getWorld().getEntitiesByClass(org.bukkit.entity.Item.class).size();
      run(player, "artifact", "give", "Mage", "fire", "rare");
      assertEquals(
          before + 1, player.getWorld().getEntitiesByClass(org.bukkit.entity.Item.class).size());
    }
  }
}
