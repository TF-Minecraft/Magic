package net.tfminecraft.magic;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.*;
import net.tfminecraft.magic.gui.*;
import net.tfminecraft.magic.integration.*;
import net.tfminecraft.magic.manager.*;
import net.tfminecraft.magic.model.*;
import net.tfminecraft.magic.modifier.*;
import net.tfminecraft.magic.registry.*;
import net.tfminecraft.magic.session.*;
import net.tfminecraft.magic.util.GridLayout;
import net.tfminecraft.magic.util.GuiText;
import net.tfminecraft.rpcharacters.RPCharacters;
import net.tfminecraft.rpcharacters.api.CharacterSkull;
import net.tfminecraft.rpcharacters.focus.FocusService;
import net.tfminecraft.rpcharacters.objects.RPCharacter;
import org.bukkit.*;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.*;
import org.bukkit.event.inventory.*;
import org.bukkit.inventory.*;
import org.junit.jupiter.api.*;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockito.*;

class ResonanceGuiCoverageTest extends GearCoverageSupport {
  org.mockbukkit.mockbukkit.entity.PlayerMock p;
  MockedStatic<RpCharactersBridge> bridge;
  MockedStatic<CharacterSkull> skull;
  MockedStatic<RPCharacters> rp;

  @BeforeEach
  void guiSetup() {
    p = server.addPlayer();
    p.openInventory(Bukkit.createInventory(null, 9));
    ElementRegistry.clear();
    ArtifactGuiCoverageTest.element("fire", 21);
    ArtifactGuiCoverageTest.element("hidden", -1);
    var yaml = new YamlConfiguration();
    yaml.set("slot", 23);
    yaml.set("permission", "magic.secret");
    ElementRegistry.register(new ElementDef("locked", yaml));
    bridge = mockStatic(RpCharactersBridge.class);
    skull = mockStatic(CharacterSkull.class);
    skull
        .when(() -> CharacterSkull.ofOwner(p))
        .thenAnswer(a -> new ItemStack(Material.PLAYER_HEAD));
    rp = mockStatic(RPCharacters.class);
  }

  @AfterEach
  void guiCleanup() {
    rp.close();
    skull.close();
    bridge.close();
    ElementRegistry.clear();
  }

  InventoryClickEvent click(int slot) {
    var event = mock(InventoryClickEvent.class);
    when(event.getWhoClicked()).thenReturn(p);
    when(event.getView()).thenReturn(p.getOpenInventory());
    when(event.getRawSlot()).thenReturn(slot);
    return event;
  }

  @Test
  void builderRendersCharacterFocusElementsAndBothModes() throws Exception {
    var session = new ResonanceSession();
    var holder = new ResonanceGuiHolder(p.getUniqueId(), null, new ResonanceSession());
    assertEquals(p.getUniqueId(), holder.getPlayerUuid());
    assertNull(holder.getSession());
    var inventory = ResonanceGuiBuilder.build(p, holder, session);
    holder.setInventory(inventory);
    assertSame(inventory, holder.getInventory());
    assertEquals(Material.PLAYER_HEAD, inventory.getItem(GridLayout.characterHeadSlot()).getType());
    assertNotNull(inventory.getItem(21));
    var character = mock(RPCharacter.class);
    when(character.getName()).thenReturn("Mage");
    when(character.getSkull()).thenAnswer(a -> new ItemStack(Material.PLAYER_HEAD));
    bridge.when(() -> RpCharactersBridge.getActiveCharacter(p)).thenReturn(character);
    for (String tab : Arrays.asList(null, " ", "Display Name")) {
      bridge.when(() -> RpCharactersBridge.resolveRealName(character)).thenReturn(tab);
      ResonanceGuiBuilder.populate(inventory, p, session);
      assertTrue(
          inventory
              .getItem(GridLayout.characterHeadSlot())
              .getItemMeta()
              .getDisplayName()
              .contains(tab == null || tab.isBlank() ? "Mage" : "Display Name"));
    }
    var focus = mock(FocusService.class);
    when(focus.getPoints(p)).thenReturn(7);
    when(focus.getMax()).thenReturn(20);
    rp.when(RPCharacters::getFocusService).thenReturn(focus);
    session.setEquilibrium(-20);
    session.setResonance("fire", 10);
    ResonanceGuiBuilder.populate(inventory, p, session);
    session.setEquilibrium(30);
    session.setCastModeId(GuiCache.castModeRight.getId());
    ResonanceGuiBuilder.populate(inventory, p, session);
    verify(focus, times(2)).getPoints(p);
    assertEquals(
        "Surge",
        invoke(
            ResonanceGuiBuilder.class,
            "formatCastModeLabel",
            new Class[] {String.class},
            (Object) null));
    assertEquals(
        "Surge",
        invoke(ResonanceGuiBuilder.class, "formatCastModeLabel", new Class[] {String.class}, " "));
    assertEquals(
        "X",
        invoke(ResonanceGuiBuilder.class, "formatCastModeLabel", new Class[] {String.class}, "x"));
    for (String id :
        Arrays.asList(
            null, " ", "unknown", GuiCache.castModeLeft.getId(), GuiCache.castModeRight.getId()))
      assertNotNull(
          invoke(ResonanceGuiBuilder.class, "normalizeCastModeId", new Class[] {String.class}, id));
    invoke(
        ResonanceGuiBuilder.class,
        "buildElementItem",
        new Class[] {ElementDef.class, ResonanceSession.class, ItemStack.class},
        ElementRegistry.getById("fire"),
        null,
        null);
    invoke(
        ResonanceGuiBuilder.class,
        "buildCastModeItem",
        new Class[] {CastModeDef.class, boolean.class, ResonanceSession.class, List.class, ItemStack.class},
        new CastModeDef("other", "v.PAPER", "Other", List.of()),
        false,
        session,
        List.of(),
        null);
  }

  @Test
  void lockedElementSlotsShowFillerUntilUnlocked() {
    var yaml = new YamlConfiguration();
    yaml.set("slot", 9);
    yaml.set("permission", "magic.secret");
    ElementRegistry.register(new ElementDef("lockedEdge", yaml));
    var onControl = new YamlConfiguration();
    onControl.set("slot", GridLayout.castModeLeftSlot());
    onControl.set("permission", "magic.secret");
    ElementRegistry.register(new ElementDef("onControl", onControl));
    var session = new ResonanceSession();
    var inventory = ResonanceGuiBuilder.build(p, new ResonanceGuiHolder(p.getUniqueId(), null, new ResonanceSession()), session);
    var inner = inventory.getItem(GridLayout.innerFillerSlots().get(0));
    var border = inventory.getItem(GridLayout.borderFillerSlots().get(0));
    assertEquals(inner, inventory.getItem(23));
    assertEquals(border, inventory.getItem(9));
    var castMode = inventory.getItem(GridLayout.castModeLeftSlot());
    assertEquals(
        ChatColor.stripColor(GuiText.format(GuiCache.castModeLeft.getName())),
        ChatColor.stripColor(castMode.getItemMeta().getDisplayName()));

    p.addAttachment(MockBukkit.createMockPlugin(), "magic.secret", true);
    ResonanceGuiBuilder.populate(inventory, p, session);
    for (var entry : Map.of(23, "locked", 9, "lockedEdge").entrySet()) {
      var item = inventory.getItem(entry.getKey());
      assertEquals(Material.BARRIER, item.getType());
      assertEquals(entry.getValue(), ChatColor.stripColor(item.getItemMeta().getDisplayName()));
    }
    assertEquals(castMode, inventory.getItem(GridLayout.castModeLeftSlot()));
  }

  @Test
  void managerHonorsReadinessAndRejectsOldGenerationClicksAndRefresh() throws Exception {
    var manager = new ResonanceGuiManager();
    var provider = new CharacterTestSupport();
    manager.getSessionManager().start(provider.plugin, null);
    assertFalse(manager.tryOpen(p, false));
    assertTrue(manager.tryOpen(p, true));
    manager.onInventoryClick(click(GridLayout.castModeLeftSlot()));
    p.openInventory(Bukkit.createInventory(null, 9));
    assertTrue(manager.tryOpen(p, true));
    manager.refreshOpenInventories();
    p.openInventory(Bukkit.createInventory(null, 9));
    manager.close(p);
    manager.refreshOpenInventories();
    var nonplayer = click(0);
    when(nonplayer.getWhoClicked()).thenReturn(mock(HumanEntity.class));
    manager.onInventoryClick(nonplayer);
    manager.onInventoryClick(click(0));

    var token = provider.start(p, "Alice");
    var state = new ResonanceSession();
    manager.getSessionManager().put(token, state);
    assertTrue(manager.tryOpen(p, false));
    var same = click(GridLayout.castModeLeftSlot());
    manager.onInventoryClick(same);
    manager.onInventoryClick(same);
    manager.onInventoryClick(click(GridLayout.castModeRightSlot()));
    assertEquals(GuiCache.castModeRight.getId(), state.getCastModeId());
    manager.onInventoryClick(click(GridLayout.castModeLeftSlot()));
    manager.onInventoryClick(click(1));
    manager.onInventoryClick(click(60));
    manager.refreshOpenInventories();
    var holder = (ResonanceGuiHolder) p.getOpenInventory().getTopInventory().getHolder();
    assertEquals(token, holder.getSession());
    var drag = mock(InventoryDragEvent.class);
    when(drag.getView()).thenReturn(p.getOpenInventory());
    manager.onInventoryDrag(drag);
    verify(drag).setCancelled(true);
    var lateClick = click(GridLayout.castModeRightSlot());
    provider.start(p, "Bob");
    var returned = provider.start(p, "Alice");
    var replacement = new ResonanceSession();
    manager.getSessionManager().put(returned, replacement);
    String originalMode = replacement.getCastModeId();
    manager.onInventoryClick(lateClick);
    assertEquals(originalMode, replacement.getCastModeId());
    assertTrue(manager.tryOpen(p, false));
    provider.start(p, "Bob");
    manager.refreshOpenInventories();
    p.openInventory(Bukkit.createInventory(null, 9));
    when(drag.getView()).thenReturn(p.getOpenInventory());
    manager.onInventoryDrag(drag);
    provider.unregister();
    Bukkit.getServicesManager().unregister(net.tfminecraft.tlibs.character.CharacterIdentity.class, characters);
    assertTrue(manager.tryOpen(p, false));
    manager.refreshOpenInventories();
    manager.close(p);
    p.openInventory(Bukkit.createInventory(null, 9));
    for (String mode : Arrays.asList(null, " ", "surge", "flow", "OTHER"))
      assertNotNull(invoke(ResonanceGuiManager.class, "castModeChatLabel", new Class[] {String.class}, mode));
  }

  static ElementDef seithr() {
    var yaml = new YamlConfiguration();
    yaml.set("name", "Seithr");
    yaml.set("slot", 30);
    yaml.set("resonance.0.mana", 0.20);
    yaml.set("resonance.0.damage", -0.20);
    yaml.set("resonance.0.cooldown", 0.20);
    yaml.set("resonance.100.mana", -0.20);
    yaml.set("resonance.100.damage", 0.20);
    yaml.set("resonance.100.cooldown", -0.15);
    var def = new ElementDef("seithr", yaml);
    ElementRegistry.register(def);
    return def;
  }

  static List<String> plain(List<String> lines) {
    return lines.stream().map(ChatColor::stripColor).toList();
  }

  @Test
  void schoolLoreNamesItsPartsAndKeepsThePeakResonanceOnly() {
    assertTrue(ModifierLore.linesForElement(null, null, null).isEmpty());
    var seithr = seithr();
    var session = new ResonanceSession();
    session.setResonance("seithr", 98.61);
    session.setEquilibrium(63.35);
    assertEquals(
        List.of(
            "Your Seithr spells now:",
            " Mana -29.65%  Cooldown -17.22%  Damage +38.36%",
            "Made of:",
            " Resonance 98.61: Mana -19.44%  Cooldown -14.51%  Damage +19.44%",
            " Tranquility 63.35: Mana -12.67%  Cooldown -3.17%  Damage +15.84%",
            "At 100 Resonance:",
            " Mana -20%  Cooldown -15%  Damage +20%"),
        plain(ModifierLore.linesForElement(seithr, session, null)));
    session.setResonance("seithr", 3.2);
    session.setEquilibrium(-27.8);
    var corrupted = plain(ModifierLore.linesForElement(seithr, session, null));
    assertEquals(" Mana +11.1%  Cooldown +5.3%  Damage -9.44%", corrupted.get(1));
    assertEquals(" Corruption 27.8: Mana -6.42%  Cooldown -11.42%  Damage +11.42%", corrupted.get(4));
    session.setResonance("seithr", 50);
    session.setEquilibrium(0);
    assertEquals(
        List.of(
            "Your Seithr spells now:",
            " Mana 0%  Cooldown +2.5%  Damage 0%",
            "Made of:",
            " Resonance 50: Mana 0%  Cooldown +2.5%  Damage 0%",
            "At 100 Resonance:",
            " Mana -20%  Cooldown -15%  Damage +20%"),
        plain(ModifierLore.linesForElement(seithr, session, null)));
    assertEquals(
        " Mana +20%  Cooldown +20%  Damage -20%",
        plain(ModifierLore.linesForElement(seithr, null, null)).get(1));
  }

  @Test
  void schoolNowLineIsWhatACastGets() throws Exception {
    var seithr = seithr();
    var session = new ResonanceSession();
    session.setResonance("seithr", 98.61);
    session.setEquilibrium(63.35);
    var cast =
        SpellModifiers.combine(
            SpellModifiers.combine(
                SpellModifiers.resonance("seithr", 98.61), SpellModifiers.drift(63.35)),
            SpellModifiers.alignment((net.tfminecraft.magic.gear.WeaponRequirement) null, "seithr"));
    var shown = plain(ModifierLore.linesForElement(seithr, session, null)).get(1);
    assertEquals(
        " Mana "
            + invoke(ModifierLore.class, "formatPercent", new Class[] {double.class}, cast.mana())
            + "  Cooldown "
            + invoke(ModifierLore.class, "formatPercent", new Class[] {double.class}, cast.cooldown())
            + "  Damage "
            + invoke(ModifierLore.class, "formatPercent", new Class[] {double.class}, cast.damage()),
        shown);
  }

  @Test
  void anAlignedWeaponJoinsTheNowRowsAndGetsItsOwnSource() throws Exception {
    var seithr = seithr();
    var weapon = new ItemStack(Material.STICK);
    var session = new ResonanceSession();
    session.setResonance("seithr", 98.61);
    session.setEquilibrium(63.35);
    try (var modifiers = mockStatic(SpellModifiers.class, CALLS_REAL_METHODS)) {
      modifiers
          .when(() -> SpellModifiers.alignment(weapon, "seithr"))
          .thenReturn(new ModifierTriple(0.0, 0.05, -0.04));
      var cast =
          SpellModifiers.combine(
              SpellModifiers.combine(
                  SpellModifiers.resonance("seithr", 98.61), SpellModifiers.drift(63.35)),
              SpellModifiers.alignment(weapon, "seithr"));
      var expected =
          " Mana "
              + invoke(ModifierLore.class, "formatPercent", new Class[] {double.class}, cast.mana())
              + "  Cooldown "
              + invoke(ModifierLore.class, "formatPercent", new Class[] {double.class}, cast.cooldown())
              + "  Damage "
              + invoke(ModifierLore.class, "formatPercent", new Class[] {double.class}, cast.damage());
      var school = plain(ModifierLore.linesForElement(seithr, session, weapon));
      assertEquals(expected, school.get(1));
      assertEquals(" Weapon: Mana 0%  Cooldown -4%  Damage +5%", school.get(5));
      assertEquals("At 100 Resonance:", school.get(6));
      var flow = plain(ModifierLore.linesForFlow(session, List.of(seithr), weapon));
      assertEquals(" Seithr:" + expected, flow.get(5));
    }
  }

  @Test
  void balanceLoreShowsYourAmountThePeakAndEachSchoolOnlyOnYourSide() {
    var seithr = seithr();
    var fire = ElementRegistry.getById("fire");
    var session = new ResonanceSession();
    session.setResonance("seithr", 98.61);
    session.setEquilibrium(63.35);
    var flow = plain(ModifierLore.linesForFlow(session, List.of(seithr, fire), null));
    assertEquals(
        List.of(
            "Your Tranquility 63.35:",
            " Mana -12.67%  Cooldown -3.17%  Damage +15.84%",
            "At 100 Tranquility:",
            " Mana -20%  Cooldown -5%  Damage +25%",
            "Your spells now, with each school's Resonance:",
            " Seithr: Mana -29.65%  Cooldown -17.22%  Damage +38.36%"),
        flow.subList(0, 6));
    assertEquals(7, flow.size());
    assertTrue(flow.get(6).startsWith(" fire: "));
    assertEquals(
        List.of(
            "You have no Corruption.",
            "At 60 Corruption:",
            " Mana -10%  Cooldown -15%  Damage +15%"),
        plain(ModifierLore.linesForSurge(session, List.of(seithr), null)));
    session.setResonance("seithr", 3.2);
    session.setEquilibrium(-27.8);
    var surge = plain(ModifierLore.linesForSurge(session, List.of(seithr), null));
    assertEquals("Your Corruption 27.8:", surge.get(0));
    assertEquals(" Mana -6.42%  Cooldown -11.42%  Damage +11.42%", surge.get(1));
    assertEquals(" Seithr: Mana +11.1%  Cooldown +5.3%  Damage -9.44%", surge.get(5));
    assertEquals("You have no Tranquility.", plain(ModifierLore.linesForFlow(session, List.of(seithr), null)).get(0));
    assertEquals(4, ModifierLore.linesForSurge(session, List.of(), null).size());
    assertEquals(4, ModifierLore.linesForSurge(session, null, null).size());
    assertEquals(3, ModifierLore.linesForSurge(null, null, null).size());
    assertEquals(3, ModifierLore.linesForFlow(null, List.of(seithr), null).size());
  }

  @Test
  void modifierLoreRendersSignedPercentagesAndColours() throws Exception {
    for (double value : List.of(-.2, 0., .2)) {
      assertEquals(
          value < 0 ? "-20%" : value > 0 ? "+20%" : "0%",
          invoke(ModifierLore.class, "formatPercent", new Class[] {double.class}, value));
      for (boolean higher : List.of(false, true))
        assertEquals(
            value == 0 ? "§7" : (higher == (value > 0)) ? "§a" : "§c",
            invoke(
                ModifierLore.class,
                "percentColor",
                new Class[] {double.class, boolean.class},
                value,
                higher));
    }
  }

  @Test
  void zeroDecayElementOmitsDriftLine() throws Exception {
    var config = new YamlConfiguration();
    config.set("decay_per_hour", 0);
    var element = new ElementDef("steady", config);
    var item =
        (ItemStack)
            invoke(
                ResonanceGuiBuilder.class,
                "buildElementItem",
                new Class[] {ElementDef.class, ResonanceSession.class, ItemStack.class},
                element,
                null,
                null);
    assertNotNull(item.getItemMeta().getLore());
  }
  @Test
  void aGuiOwnedByAnotherAccountCannotChangeItsBoundResonance() {
    var manager = new ResonanceGuiManager();
    var state = new ResonanceSession();
    String original = state.getCastModeId();
    var holder = new ResonanceGuiHolder(UUID.randomUUID(), null, state);
    var inventory = Bukkit.createInventory(holder, 54);
    holder.setInventory(inventory);
    p.openInventory(inventory);
    manager.onInventoryClick(click(GridLayout.castModeRightSlot()));
    assertEquals(original, state.getCastModeId());
  }

}
