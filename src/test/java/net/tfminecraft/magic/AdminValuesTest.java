package net.tfminecraft.magic;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.List;
import net.tfminecraft.magic.artifact.*;
import net.tfminecraft.magic.gear.*;
import net.tfminecraft.magic.gear.orb.OrbCache;
import net.tfminecraft.magic.session.ResonanceSession;
import net.tfminecraft.rpcharacters.RPCharacters;
import net.tfminecraft.rpcharacters.focus.FocusService;
import org.bukkit.Material;
import org.bukkit.command.CommandSender;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.junit.jupiter.api.*;

class AdminValuesTest {
  CommandTest f = new CommandTest();
  ResonanceSession session;
  CharacterTestSupport identities;
  double defaultEq, wearMultiplier;
  String defaultMode;
  int riftCap;

  @BeforeEach void setup() {
    f.setup();
    defaultEq = Cache.defaultEquilibrium; defaultMode = Cache.defaultCastMode;
    wearMultiplier = Cache.artifactWearMultiplier; riftCap = OrbCache.riftCap;
    Cache.defaultEquilibrium = 0; Cache.defaultCastMode = "flow";
    GuiCache.equilibriumMin = -100; GuiCache.equilibriumMax = 100;
    Cache.artifactWearMultiplier = 1; OrbCache.riftCap = 100;
    identities = new CharacterTestSupport();
    var identity = identities.start(f.player, "admin-values");
    f.sessions.start(identities.plugin, null);
    session = new ResonanceSession(); f.sessions.put(identity, session);
  }

  @AfterEach void cleanup() {
    Cache.defaultEquilibrium = defaultEq; Cache.defaultCastMode = defaultMode;
    Cache.artifactWearMultiplier = wearMultiplier; OrbCache.riftCap = riftCap;
    f.cleanup();
  }

  void run(String... args) { f.run(f.player, args); while (f.player.nextMessage() != null) {} }
  ItemStack held() { return f.player.getInventory().getItemInMainHand(); }
  void hold(ItemStack item) { f.player.getInventory().setItemInMainHand(item); }
  ItemStack gear() {
    var item = new ItemStack(Material.DIAMOND_SWORD);
    GearProvenance.stamp(item, GearType.SWORD, List.of());
    return item;
  }

  @Test void equilibriumEditsClampRoundPersistAndRemovePendingDrift() {
    f.expect("values.player", "equilibrium", "get", "Mage");
    f.expect("values.player", "equilibrium", "set", "Mage", "-30");
    assertEquals(-30, session.getEquilibrium());
    session.driftEquilibrium(-0.009, -100);
    run("equilibrium", "add", "Mage", "10.125");
    assertEquals(-19.88, session.getEquilibrium());
    session.driftEquilibrium(-0.002, -100);
    assertEquals(-19.88, session.getEquilibrium());
    run("equilibrium", "remove", "Mage", "10"); assertEquals(-29.88, session.getEquilibrium());
    run("equilibrium", "set", "Mage", "1e300"); assertEquals(100, session.getEquilibrium());
    run("equilibrium", "add", "Mage", "-1e300"); assertEquals(-100, session.getEquilibrium());
    run("equilibrium", "reset", "Mage"); assertEquals(0, session.getEquilibrium());
    verify(Magic.plugin.getProfileService(), times(6)).savePlayerChecked(f.player);
    verify(Magic.plugin, times(6)).syncSpellModifiers(f.player, session);
    for (String bad : List.of("NaN", "Infinity", "oops"))
      f.expect("values.invalid_amount", "equilibrium", "set", "Mage", bad);
    f.expect("values.invalid_amount", "equilibrium", "remove", "Mage", "-1");
  }

  @Test void modesAndPlayerResetRespectConfiguredDefaultsAndShowFocus() {
    run("castmode", "set", "Mage", "SURGE"); assertEquals("surge", session.getCastModeId());
    f.expect("values.invalid_mode", "castmode", "set", "Mage", "bogus");
    run("castmode", "get", "Mage");
    run("castmode", "reset", "Mage"); assertEquals("flow", session.getCastModeId());
    Cache.defaultResonance = 2; Cache.defaultEquilibrium = 3;
    session.setResonance("fire", 20); session.setResonance("water", 30);
    session.driftResonance("fire", -0.009); session.driftEquilibrium(-0.009, -100);
    try (var rpc = mockStatic(RPCharacters.class)) {
      run("player", "get", "Mage");
      assertEquals(20, session.getResonance("fire"));
      var focus = mock(FocusService.class);
      rpc.when(RPCharacters::getFocusService).thenReturn(focus);
      when(focus.getPoints(f.player)).thenReturn(22); when(focus.getMax()).thenReturn(150);
      run("player", "reset", "Mage");
      verifyNoInteractions(Magic.plugin.getArtifactCreateGuiManager());
      verify(focus, never()).restore(any());
    }
    assertEquals(2, session.getResonance("fire")); assertEquals(2, session.getResonance("water"));
    assertEquals(3, session.getEquilibrium()); assertEquals("flow", session.getCastModeId());
    session.driftResonance("fire", -0.002); session.driftEquilibrium(-0.002, -100);
    assertEquals(2, session.getResonance("fire")); assertEquals(3, session.getEquilibrium());
  }

  @Test void playerValidationRejectsBadSyntaxUnavailableTargetsAndStaleCharacters() {
    for (String[] args : List.of(new String[]{"player"}, new String[]{"player", "oops", "Mage"},
        new String[]{"player", "set", "Mage", "5"}, new String[]{"castmode", "add", "Mage", "1"},
        new String[]{"equilibrium", "get", "Mage", "extra"}, new String[]{"equilibrium", "set", "Mage"}))
      f.expect("values.player_usage", args);
    f.expect("artifact.give.unknown_player", "player", "get", "Missing");
    var unbound = f.server.addPlayer("Unbound");
    f.expect("resonance.admin.no_character", "equilibrium", "set", "Unbound", "1");
    f.sessions.getOrCreate(unbound);
    f.expect("resonance.admin.no_character", "equilibrium", "set", "Unbound", "1");
    identities.start(f.player, "other-character");
    f.expect("resonance.admin.no_character", "equilibrium", "set", "Mage", "1");
    assertEquals(0, session.getEquilibrium());
    f.player.setOp(false);
    for (String root : List.of("player", "equilibrium", "castmode", "gear")) {
      f.expect("admin.no_permission", root, "reset", "Mage");
      assertTrue(f.tab(root, "").isEmpty());
    }
  }

  @Test void failedSavesRollBackEveryCharacterValueAndDoNotResync() {
    session.setEquilibrium(12); session.setResonance("fire", 25); session.setCastModeId("surge");
    when(Magic.plugin.getProfileService().savePlayerChecked(f.player)).thenReturn(false);
    for (String[] args : List.of(new String[]{"player", "reset", "Mage"},
        new String[]{"equilibrium", "set", "Mage", "99"}, new String[]{"castmode", "set", "Mage", "flow"},
        new String[]{"resonance", "set", "Mage", "all", "0"},
        new String[]{"resonance", "add", "Mage", "fire", "3"},
        new String[]{"resonance", "reset", "Mage"})) {
      f.expect("values.save_failed", args);
      assertEquals(12, session.getEquilibrium()); assertEquals(25, session.getResonance("fire"));
      assertEquals("surge", session.getCastModeId());
    }
    verify(Magic.plugin, never()).syncSpellModifiers(any(), any());
  }

  @Test void resonanceArityIsValidatedBeforeAnyMutation() {
    for (String[] args : List.of(new String[]{"resonance", "get", "Mage", "extra"},
        new String[]{"resonance", "reset", "Mage", "all", "extra"},
        new String[]{"resonance", "set", "Mage", "fire", "1", "extra"}))
      f.expect("resonance.admin.usage", args);
    identities.unregister();
    f.sessions.getOrCreate(f.player);
    f.expect("resonance.admin.no_character", "equilibrium", "get", "Mage");
    assertTrue(f.tab("equilibrium", "set", "Mage", "1", "").isEmpty());
  }

  @Test void resonanceRemovalClampsAndClearsFractionalDrift() {
    session.setResonance("fire", 30); session.driftResonance("fire", -0.009);
    f.expect("resonance.admin.add_ok", "resonance", "remove", "Mage", "fire", "10");
    assertEquals(20, session.getResonance("fire"));
    session.driftResonance("fire", -0.002); assertEquals(20, session.getResonance("fire"));
    f.expect("resonance.admin.invalid_amount", "resonance", "remove", "Mage", "fire", "-10");
    run("resonance", "remove", "Mage", "all", "100"); assertEquals(0, session.getResonance("fire"));
    assertTrue(f.tab("resonance", "").contains("remove"));
  }

  @Test void artifactFillClampsUpdatesLoreAndPreservesIdentity() {
    var item = f.artifact(); var id = ArtifactIds.read(item); hold(item);
    run("artifact", "fill", "get", "fire"); assertEquals(4, Artifact.fromItem(held()).getFill("fire"));
    run("artifact", "fill", "set", "fire", "8"); assertEquals(8, Artifact.fromItem(held()).getFill("fire"));
    run("artifact", "fill", "add", "all", "20"); assertEquals(10, Artifact.fromItem(held()).getFill("fire"));
    run("artifact", "fill", "remove", "fire", "3"); assertEquals(7, Artifact.fromItem(held()).getFill("fire"));
    run("artifact", "fill", "reset", "all"); assertEquals(0, Artifact.fromItem(held()).getFill("fire"));
    assertEquals(id, ArtifactIds.read(held()));
    f.expect("artifact.setfill.no_cap", "artifact", "fill", "set", "water", "2");
    f.expect("artifact.setfill.no_cap", "artifact", "fill", "get", "bogus");
    run("artifact", "inspect");
    assertNotNull(held().getItemMeta().getLore());
  }

  @Test void artifactMufflingAndWearCanBeRepairedOrSpent() {
    hold(f.artifact());
    run("artifact", "muffle", "set", "25"); assertEquals(.25, ArtifactCareStore.readMuffle(held()));
    run("artifact", "muffle", "add", "100"); assertEquals(1, ArtifactCareStore.readMuffle(held()));
    run("artifact", "muffle", "remove", "25"); assertEquals(.75, ArtifactCareStore.readMuffle(held()));
    run("artifact", "muffle", "get");
    run("artifact", "muffle", "reset"); assertEquals(0, ArtifactCareStore.readMuffle(held()));
    run("artifact", "wear", "set", "3"); assertEquals(3, ArtifactWear.readWear(held()));
    run("artifact", "wear", "get");
    run("artifact", "wear", "remove", "1"); assertEquals(2, ArtifactWear.readWear(held()));
    run("artifact", "wear", "reset"); assertEquals(0, ArtifactWear.readWear(held()));
    Cache.artifactWearMultiplier = 0;
    f.expect("values.wear_disabled", "artifact", "wear", "set", "1");
    Cache.artifactWearMultiplier = 1;
    run("artifact", "wear", "add", "10"); assertTrue(ArtifactWear.isHusk(held()));
    f.expect("values.wrong_item", "artifact", "wear", "reset");
  }

  @Test void gearRiftAndDurabilityEditsPreserveLastPointAndCustomMaximum() {
    var item = gear(); var req = WeaponRequirement.empty();
    req.aura().setCap("fire", 30); req.aura().setFill("fire", 20); req.persist(item); hold(item);
    run("gear", "rift", "set", "25"); assertEquals(25, WeaponRift.get(held()));
    run("gear", "rift", "get"); run("gear", "inspect");
    run("gear", "rift", "add", "2147483647"); assertEquals(100, WeaponRift.get(held()));
    run("gear", "rift", "remove", "2147483647"); assertEquals(0, WeaponRift.get(held()));
    run("gear", "rift", "reset");
    run("gear", "durability", "set", "20"); assertEquals(20, WeaponWear.remaining(held()));
    run("gear", "durability", "remove", "100"); assertEquals(1, WeaponWear.remaining(held()));
    run("gear", "durability", "get"); run("gear", "durability", "reset");
    assertEquals(Material.DIAMOND_SWORD.getMaxDurability(), WeaponWear.remaining(held()));
    item = held(); var meta = (Damageable)item.getItemMeta(); meta.setMaxDamage(200); item.setItemMeta(meta); hold(item);
    run("gear", "durability", "reset"); assertEquals(200, WeaponWear.remaining(held()));
    item = held(); meta = (Damageable)item.getItemMeta(); meta.setUnbreakable(true); item.setItemMeta(meta); hold(item);
    f.expect("values.no_durability", "gear", "durability", "set", "2");
    for (String bad : List.of("1.5", "2147483648", "NaN", "bad"))
      f.expect("values.invalid_amount", "gear", "rift", "set", bad);
    f.expect("values.invalid_amount", "gear", "rift", "remove", "-1");
  }

  @Test void heldItemValidationDoesNotModifyWrongItemsStacksOrMalformedRequests() {
    var console = mock(CommandSender.class); when(console.hasPermission(anyString())).thenReturn(true);
    f.run(console, "gear", "inspect"); verify(console).sendMessage("values.players_only");
    for (String[] args : List.of(new String[]{"gear"}, new String[]{"gear", "bogus"},
        new String[]{"gear", "inspect", "extra"}, new String[]{"gear", "rift"},
        new String[]{"artifact", "fill", "bogus", "fire"}, new String[]{"artifact", "muffle", "set"}))
      f.expect("values.item_usage", args);
    f.expect("values.wrong_item", "gear", "inspect");
    f.expect("values.wrong_item", "artifact", "inspect");
    hold(new ItemStack(Material.STONE)); f.expect("values.wrong_item", "artifact", "inspect");
    var item = f.artifact(); var meta = item.getItemMeta(); meta.getPersistentDataContainer().remove(ArtifactKeys.artifactId());
    item.setItemMeta(meta); hold(item); f.expect("values.wrong_item", "artifact", "inspect");
    item = f.artifact(); item.setAmount(2); hold(item);
    f.expect("values.single_item", "artifact", "fill", "set", "fire", "1");
    run("artifact", "fill", "get", "all"); run("artifact", "inspect");
    hold(f.artifact()); f.expect("values.invalid_amount", "artifact", "muffle", "set", "NaN");
    f.expect("values.invalid_amount", "artifact", "fill", "remove", "fire", "-1");
  }

  @Test void completionCoversNewRoutesAndRespectsConsoleAndPermissionBoundaries() {
    assertTrue(f.tab("").containsAll(List.of("player", "equilibrium", "castmode", "gear")));
    assertEquals(List.of("get", "reset"), f.tab("player", ""));
    assertEquals(List.of("get", "set", "reset"), f.tab("castmode", ""));
    assertTrue(f.tab("equilibrium", "").containsAll(List.of("get", "set", "add", "remove", "reset")));
    assertEquals(List.of("Mage"), f.tab("equilibrium", "set", "M"));
    assertEquals(List.of("flow"), f.tab("castmode", "set", "Mage", "f"));
    assertTrue(f.tab("castmode", "reset", "Mage", "").isEmpty());
    assertTrue(f.tab("equilibrium", "set", "Mage", "").isEmpty());
    assertTrue(f.tab("gear", "").contains("rift")); assertTrue(f.tab("gear", "rift", "").contains("remove"));
    assertTrue(f.tab("artifact", "").contains("inspect"));
    assertTrue(f.tab("artifact", "fill", "").contains("get"));
    assertTrue(f.tab("artifact", "fill", "get", "").containsAll(List.of("fire", "all")));
    assertTrue(f.tab("artifact", "inspect", "").isEmpty());
    assertTrue(f.tab("gear", "rift", "set", "").isEmpty());
    var console = mock(CommandSender.class); when(console.hasPermission(anyString())).thenReturn(true);
    assertTrue(f.command.onTabComplete(console, null, "magic", new String[]{""}).contains("equilibrium"));
    assertTrue(f.command.onTabComplete(console, null, "magic", new String[]{"gear", ""}).isEmpty());
  }
}
