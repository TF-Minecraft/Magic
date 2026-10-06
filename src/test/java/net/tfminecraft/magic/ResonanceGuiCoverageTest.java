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
    var holder = new ResonanceGuiHolder(p.getUniqueId(), "character");
    assertEquals(p.getUniqueId(), holder.getPlayerUuid());
    assertEquals("character", holder.getCharacterId());
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
      bridge.when(() -> RpCharactersBridge.resolveDisplayTab(p)).thenReturn(tab);
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
        new Class[] {ElementDef.class, ResonanceSession.class},
        ElementRegistry.getById("fire"),
        null);
    invoke(
        ResonanceGuiBuilder.class,
        "buildCastModeItem",
        new Class[] {CastModeDef.class, boolean.class, ResonanceSession.class},
        new CastModeDef("other", "v.PAPER", "Other", List.of()),
        false,
        session);
  }

  @Test
  void lockedElementSlotsShowFillerUntilUnlocked() {
    var yaml = new YamlConfiguration();
    yaml.set("slot", 9);
    yaml.set("permission", "magic.secret");
    ElementRegistry.register(new ElementDef("lockedEdge", yaml));
    var session = new ResonanceSession();
    var inventory = ResonanceGuiBuilder.build(p, new ResonanceGuiHolder(p.getUniqueId(), "c"), session);
    var inner = inventory.getItem(GridLayout.innerFillerSlots().get(0));
    var border = inventory.getItem(GridLayout.borderFillerSlots().get(0));
    assertEquals(inner, inventory.getItem(23));
    assertEquals(border, inventory.getItem(9));

    p.addAttachment(MockBukkit.createMockPlugin(), "magic.secret", true);
    ResonanceGuiBuilder.populate(inventory, p, session);
    assertNotEquals(inner, inventory.getItem(23));
    assertNotEquals(border, inventory.getItem(9));
  }

  @Test
  void managerHonorsCharacterGateChangesModeAndClosesStaleCharacters() throws Exception {
    var manager = new ResonanceGuiManager();
    assertNotNull(manager.getSessionManager());
    bridge.when(RpCharactersBridge::isAvailable).thenReturn(true);
    assertFalse(manager.tryOpen(p, false));
    assertTrue(manager.tryOpen(p, true));
    manager.refreshOpenInventories();
    var same = click(GridLayout.castModeLeftSlot());
    manager.onInventoryClick(same);
    manager.onInventoryClick(same);
    var event = click(GridLayout.castModeRightSlot());
    manager.onInventoryClick(event);
    assertEquals(
        GuiCache.castModeRight.getId(), manager.getSessionManager().get(p).getCastModeId());
    manager.onInventoryClick(click(GridLayout.castModeLeftSlot()));
    manager.onInventoryClick(click(1));
    manager.onInventoryClick(click(60));
    manager.closeIfCharacterChanged(p, "new");
    assertNull(p.getOpenInventory().getTopInventory());
    p.openInventory(Bukkit.createInventory(null, 9));
    manager.closeIfCharacterChanged(p, "new");
    manager.refreshOpenInventories();
    var nonplayer = click(0);
    when(nonplayer.getWhoClicked()).thenReturn(mock(HumanEntity.class));
    manager.onInventoryClick(nonplayer);
    manager.onInventoryClick(click(0));
    var character = mock(RPCharacter.class);
    when(character.getId()).thenReturn("c1");
    when(character.getName()).thenReturn("Mage");
    when(character.getSkull()).thenAnswer(a -> new ItemStack(Material.PLAYER_HEAD));
    bridge.when(() -> RpCharactersBridge.getActiveCharacter(p)).thenReturn(character);
    assertTrue(manager.tryOpen(p, false));
    manager.closeIfCharacterChanged(p, "c1");
    assertTrue(p.getOpenInventory().getTopInventory().getHolder() instanceof ResonanceGuiHolder);
    var drag = mock(InventoryDragEvent.class);
    when(drag.getView()).thenReturn(p.getOpenInventory());
    manager.onInventoryDrag(drag);
    verify(drag).setCancelled(true);
    manager.closeIfCharacterChanged(p, "c2");
    p.openInventory(Bukkit.createInventory(null, 9));
    when(drag.getView()).thenReturn(p.getOpenInventory());
    manager.onInventoryDrag(drag);
    bridge.when(RpCharactersBridge::isAvailable).thenReturn(false);
    assertTrue(manager.tryOpen(p, false));
    for (String mode : Arrays.asList(null, " ", "surge", "flow", "OTHER"))
      assertNotNull(
          invoke(ResonanceGuiManager.class, "castModeChatLabel", new Class[] {String.class}, mode));
  }

  @Test
  void modifierLoreRendersSignedPercentagesAndCurrentAxisOnlyWhenActive() throws Exception {
    assertTrue(ModifierLore.linesForElement(null, null).isEmpty());
    var fire = ElementRegistry.getById("fire");
    assertEquals(8, ModifierLore.linesForElement(fire, null).size());
    var session = new ResonanceSession();
    session.setEquilibrium(0);
    assertEquals(4, ModifierLore.linesForSurge(null).size());
    assertEquals(4, ModifierLore.linesForFlow(null).size());
    assertEquals(4, ModifierLore.linesForSurge(session).size());
    assertEquals(4, ModifierLore.linesForFlow(session).size());
    session.setEquilibrium(-20);
    assertEquals(8, ModifierLore.linesForSurge(session).size());
    session.setEquilibrium(30);
    assertEquals(8, ModifierLore.linesForFlow(session).size());
    assertEquals(8, ModifierLore.linesForElement(fire, session).size());
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
    assertTrue(
        ((String)
                invoke(
                    ModifierLore.class,
                    "axisHeader",
                    new Class[] {String.class, double.class},
                    null,
                    12))
            .contains("12"));
    var lines = new ArrayList<String>();
    invoke(
        ModifierLore.class,
        "addBlock",
        new Class[] {List.class, String.class, ModifierTriple.class},
        lines,
        "heading",
        null);
    assertTrue(lines.isEmpty());
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
                new Class[] {ElementDef.class, ResonanceSession.class},
                element,
                null);
    assertNotNull(item.getItemMeta().getLore());
  }
}
