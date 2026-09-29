package net.tfminecraft.magic;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.*;
import net.tfminecraft.magic.artifact.config.*;
import net.tfminecraft.magic.artifact.create.*;
import net.tfminecraft.magic.artifact.generate.*;
import net.tfminecraft.magic.gui.*;
import net.tfminecraft.magic.manager.*;
import net.tfminecraft.magic.model.*;
import net.tfminecraft.magic.registry.*;
import org.bukkit.*;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.*;
import org.bukkit.event.inventory.*;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.*;
import org.junit.jupiter.api.*;
import org.mockito.*;

class ArtifactGuiCoverageTest extends GearCoverageSupport {
  org.mockbukkit.mockbukkit.entity.PlayerMock p;
  MockedConstruction<ArtifactItemBuilder> builders;
  ItemStack result;

  @BeforeEach
  void guiSetup() {
    p = server.addPlayer();
    p.openInventory(Bukkit.createInventory(null, 9));
    ArtifactRarityRegistry.clear();
    ArtifactTypeRegistry.clear();
    ArtifactAffinityRegistry.clear();
    ElementRegistry.clear();
    ArtifactRarityRegistry.register(ArtifactGenerationTest.rarity("common", 1, 1, 3));
    ArtifactRarityRegistry.register(
        new ArtifactRarityDef("rare", "Rare", "", 1, 1, 3, new CapRange(1, 20)));
    ArtifactRarityRegistry.register(
        new ArtifactRarityDef("epic", "Epic", null, 1, 1, 3, new CapRange(1, 20)));
    for (String id : List.of("fire", "water", "disabled"))
      ArtifactTypeRegistry.register(ArtifactGenerationTest.type(id, !id.equals("disabled"), 1));
    element("fire", 21);
    element("water", 23);
    element("disabled", 24);
    element("missing", 25);
    element("hidden", -1);
    result = item();
    builders =
        mockConstruction(
            ArtifactItemBuilder.class,
            (mock, context) -> {
              when(mock.pickModel(any(), any()))
                  .thenReturn(new ArtifactModelEntry("wand", "v.STICK", null, null));
              when(mock.pickBaseName(any(), any(), any())).thenReturn("Spark");
              when(mock.build(any(), any(), any())).thenAnswer(a -> result);
              when(mock.build(any(), any(), any(), anyBoolean())).thenAnswer(a -> result);
            });
  }

  @AfterEach
  void guiCleanup() {
    builders.close();
    ArtifactRarityRegistry.clear();
    ArtifactTypeRegistry.clear();
    ArtifactAffinityRegistry.clear();
    ElementRegistry.clear();
  }

  static ElementDef element(String id, int slot) {
    var yaml = new YamlConfiguration();
    yaml.set("slot", slot);
    var def = new ElementDef(id, yaml);
    ElementRegistry.register(def);
    return def;
  }

  InventoryClickEvent click(int slot) {
    var event = mock(InventoryClickEvent.class);
    when(event.getWhoClicked()).thenReturn(p);
    when(event.getView()).thenReturn(p.getOpenInventory());
    when(event.getRawSlot()).thenReturn(slot);
    return event;
  }

  @Test
  void builderRendersSelectionsCapsRaritiesAndLockedPreview() {
    var holder = new ArtifactCreateGuiHolder(p.getUniqueId());
    assertEquals(p.getUniqueId(), holder.getPlayerUuid());
    var inventory = ArtifactCreateGuiBuilder.build(holder, null);
    holder.setInventory(inventory);
    assertSame(inventory, holder.getInventory());
    assertEquals(Material.BARRIER, inventory.getItem(ArtifactCreateCache.previewSlot).getType());
    var session = new ArtifactCreateSession();
    session.cycleElement("water");
    session.cycleElement("fire");
    session.cycleElement("fire");
    ArtifactCreateGuiBuilder.populate(inventory, session);
    assertEquals(Material.STICK, inventory.getItem(ArtifactCreateCache.previewSlot).getType());
    assertEquals(1, inventory.getItem(ArtifactCreateCache.previewSlot).getAmount());
    assertTrue(
        inventory.getItem(21).getItemMeta().getLore().stream()
            .anyMatch(x -> x.contains("Primary")));
    assertTrue(
        inventory.getItem(23).getItemMeta().getLore().stream()
            .anyMatch(x -> x.contains("Secondary")));
    result = null;
    ArtifactCreateGuiBuilder.populate(inventory, session);
    assertEquals(Material.BARRIER, inventory.getItem(ArtifactCreateCache.previewSlot).getType());
    result = new ItemStack(Material.AIR);
    ArtifactCreateGuiBuilder.populate(inventory, session);
    assertEquals(Material.BARRIER, inventory.getItem(ArtifactCreateCache.previewSlot).getType());
  }

  @Test
  void managerRoutesSelectionCapsAndLifecycleWithoutBottomInventoryActions() {
    var manager = new ArtifactCreateGuiManager();
    assertNull(manager.getSession((Player) null));
    assertNull(manager.getSession((UUID) null));
    var nonplayer = click(0);
    when(nonplayer.getWhoClicked()).thenReturn(mock(HumanEntity.class));
    manager.onInventoryClick(nonplayer);
    manager.onInventoryClick(click(0));
    manager.open(p);
    var session = manager.getSession(p);
    assertSame(session, manager.getSession(p.getUniqueId()));
    manager.onInventoryClick(click(60));
    manager.onInventoryClick(click(-999));
    manager.onInventoryClick(click(ArtifactCreateCache.raritySlots.get(0)));
    manager.onInventoryClick(click(ArtifactCreateCache.raritySlots.get(1)));
    assertEquals("rare", session.getRarityId());
    manager.onInventoryClick(click(ArtifactCreateCache.capPlusSlot));
    manager.onInventoryClick(click(21));
    manager.onInventoryClick(click(21));
    assertEquals("fire", session.getPrimaryId());
    session.setCap("fire", 10);
    var plus = click(ArtifactCreateCache.capPlusSlot);
    when(plus.isShiftClick()).thenReturn(true);
    manager.onInventoryClick(plus);
    assertEquals(15, session.getCap("fire"));
    manager.onInventoryClick(click(ArtifactCreateCache.capMinusSlot));
    assertEquals(14, session.getCap("fire"));
    session.setCap("fire", 20);
    manager.onInventoryClick(click(ArtifactCreateCache.capPlusSlot));
    var drag = mock(InventoryDragEvent.class);
    when(drag.getView()).thenReturn(p.getOpenInventory());
    manager.onInventoryDrag(drag);
    verify(drag).setCancelled(true);
    var close = mock(InventoryCloseEvent.class);
    when(close.getPlayer()).thenReturn(p);
    when(close.getView()).thenReturn(p.getOpenInventory());
    manager.onInventoryClose(close);
    assertNull(manager.getSession(p));
    manager.onInventoryClick(click(21));
    when(close.getPlayer()).thenReturn(mock(HumanEntity.class));
    manager.onInventoryClose(close);
    manager.open(p);
    manager.onPlayerQuit(new PlayerQuitEvent(p, "quit"));
    assertNull(manager.getSession(p));
    manager.open(p);
    manager.onInventoryClick(click(ArtifactCreateCache.cancelSlot));
    assertNull(p.getOpenInventory().getTopInventory());
    p.openInventory(Bukkit.createInventory(null, 9));
    when(drag.getView()).thenReturn(p.getOpenInventory());
    manager.onInventoryDrag(drag);
    when(close.getView()).thenReturn(p.getOpenInventory());
    when(close.getPlayer()).thenReturn(p);
    manager.onInventoryClose(close);
  }

  @Test
  void confirmRejectsInvalidPreviewsAndGivesOrDropsOneArtifact() {
    var manager = new ArtifactCreateGuiManager();
    manager.open(p);
    manager.onInventoryClick(click(ArtifactCreateCache.confirmSlot));
    assertTrue(
        p.getOpenInventory().getTopInventory().getHolder() instanceof ArtifactCreateGuiHolder);
    var session = manager.getSession(p);
    session.cycleElement("fire");
    session.cycleElement("fire");
    for (ItemStack invalid : Arrays.asList(null, new ItemStack(Material.AIR))) {
      result = invalid;
      manager.onInventoryClick(click(ArtifactCreateCache.confirmSlot));
      assertTrue(
          p.getOpenInventory().getTopInventory().getHolder() instanceof ArtifactCreateGuiHolder);
    }
    result = new ItemStack(Material.DIAMOND, 32);
    manager.onInventoryClick(click(ArtifactCreateCache.confirmSlot));
    assertEquals(
        1,
        Arrays.stream(p.getInventory().getContents())
            .filter(Objects::nonNull)
            .filter(i -> i.getType() == Material.DIAMOND)
            .mapToInt(ItemStack::getAmount)
            .sum());
    manager.open(p);
    session = manager.getSession(p);
    session.cycleElement("fire");
    session.cycleElement("fire");
    for (int i = 0; i < p.getInventory().getSize(); i++)
      p.getInventory().setItem(i, new ItemStack(Material.STONE, 64));
    manager.onInventoryClick(click(ArtifactCreateCache.confirmSlot));
    assertTrue(
        p.getWorld().getEntitiesByClass(Item.class).stream()
            .anyMatch(
                e ->
                    e.getItemStack().getType() == Material.DIAMOND
                        && e.getItemStack().getAmount() == 1));
  }

  @Test
  void clickOutsideRaritySlotsAndDisabledElementsKeepsSelection() {
    var old = ArtifactCreateCache.raritySlots;
    try {
      ArtifactCreateCache.raritySlots = List.of(10);
      var manager = new ArtifactCreateGuiManager();
      manager.open(p);
      manager.getSession(p).setRarityId("common");
      manager.onInventoryClick(click(10));
      manager.onInventoryClick(click(1));
      manager.onInventoryClick(click(24));
      assertNull(manager.getSession(p).getPrimaryId());
      ArtifactCreateCache.raritySlots = List.of();
      manager.onInventoryClick(click(1));
    } finally {
      ArtifactCreateCache.raritySlots = old;
    }
  }
}
