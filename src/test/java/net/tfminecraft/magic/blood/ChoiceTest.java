package net.tfminecraft.magic.blood;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import net.tfminecraft.magic.integration.RpCharactersBridge;
import net.tfminecraft.tlibs.TLibs;
import net.tfminecraft.tlibs.character.CharacterInfo;
import net.tfminecraft.tlibs.character.CharacterRef;
import net.tfminecraft.tlibs.character.CharacterStatus;
import net.tfminecraft.tlibs.character.Characters;
import net.tfminecraft.tlibs.objects.api.ItemAPI;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import org.mockito.MockedStatic;

class ChoiceTest {
  @TempDir Path temp;
  BloodFixture fx;
  PlayerMock mage;
  String id;
  BloodRecord record;
  MockedStatic<RpCharactersBridge> bridge;

  /** Stands in for RPCharacters' ClassUnlocks. */
  public static final class FakeUnlocks {
    static boolean answer = true;
    static String last;

    private FakeUnlocks() {}

    public static boolean unlock(Player player, String classId) {
      last = classId;
      return answer;
    }
  }

  @BeforeEach
  void setup() {
    fx = new BloodFixture(temp);
    BloodConfig.load(new File("src/main/resources/bloodmagic.yml"), fx.logger);
    mage = fx.player("mage");
    id = BloodFixture.id("mage");
    record = fx.record("mage", BloodStage.CHOICE);
    record.addSpecies("cow");
    BloodAltars.add(BloodAltar.create(id, "o", fx.pocket(0, 10, 0), 0f, 1L));
    ClassUnlockBridge.unlocksClass = FakeUnlocks.class.getName();
    FakeUnlocks.answer = true;
    FakeUnlocks.last = null;
    bridge = mockStatic(RpCharactersBridge.class);
    bridge.when(() -> RpCharactersBridge.applyPermanentInjury(any())).thenReturn(true);
  }

  @AfterEach
  void cleanup() {
    bridge.close();
    ClassUnlockBridge.unlocksClass = "net.tfminecraft.rpcharacters.classpick.ClassUnlocks";
    WhisperService.clear();
    fx.close();
  }

  InventoryClickEvent click(int slot) {
    InventoryView view = mage.getOpenInventory();
    return new InventoryClickEvent(view, InventoryType.SlotType.CONTAINER, slot, ClickType.LEFT, InventoryAction.PICKUP_ALL);
  }

  @Test
  void bundledQuestionClassesAndArmourLoad() {
    assertEquals("#5c0202The Blood Father asks", ChoiceConfig.title());
    assertEquals(3, ChoiceConfig.button(ChoiceConfig.Slot.QUESTION).lore().size());
    assertEquals("v.iron_sword", ChoiceConfig.button(ChoiceConfig.Slot.TRUE).item());
    assertEquals(2, ChoiceConfig.devoteeInjuries());
    assertEquals("bloodsworn", ChoiceConfig.classFor(BloodPath.DEVOTEE));
    assertEquals("sanguinar", ChoiceConfig.classFor(BloodPath.TRUE));
    assertNull(ChoiceConfig.classFor(BloodPath.NONE));
    assertEquals(4, ChoiceConfig.armourFor(BloodPath.TRUE).size());
    assertTrue(ChoiceConfig.armourFor(BloodPath.NONE).isEmpty());
  }

  @Test
  void missingSectionsFallBack() throws Exception {
    Files.writeString(temp.resolve("e.yml"), "enabled: true\nchoice:\n  devotee_injuries: -3\nclasses:\n  devotee: ' '\n  'true': [x]\n");
    BloodConfig.load(temp.resolve("e.yml").toFile(), fx.logger);
    assertEquals("...", ChoiceConfig.title());
    assertEquals(0, ChoiceConfig.devoteeInjuries());
    assertEquals("v.red_dye", ChoiceConfig.button(ChoiceConfig.Slot.DEVOTEE).item());
    assertNull(ChoiceConfig.classFor(BloodPath.DEVOTEE));
    assertNull(ChoiceConfig.classFor(BloodPath.TRUE));
    assertTrue(ChoiceConfig.armourFor(BloodPath.DEVOTEE).isEmpty());
    BloodConfig.reset();
    assertEquals("...", ChoiceConfig.title());
  }

  @Test
  void theMenuAsksThenConfirmsAndGoesBack() {
    ChoiceMenu.open(mage);
    ChoiceMenu menu = (ChoiceMenu) mage.getOpenInventory().getTopInventory().getHolder();
    assertEquals(BloodPath.NONE, menu.pending());
    assertSame(mage.getOpenInventory().getTopInventory(), menu.getInventory());
    ChoiceMenu.onClick(click(0));
    ChoiceMenu.onClick(click(ChoiceMenu.RIGHT_SLOT));
    fx.server.getScheduler().performTicks(1);
    menu = (ChoiceMenu) mage.getOpenInventory().getTopInventory().getHolder();
    assertEquals(BloodPath.TRUE, menu.pending());
    ChoiceMenu.onClick(click(0));
    ChoiceMenu.onClick(click(ChoiceMenu.RIGHT_SLOT));
    fx.server.getScheduler().performTicks(1);
    menu = (ChoiceMenu) mage.getOpenInventory().getTopInventory().getHolder();
    assertEquals(BloodPath.NONE, menu.pending());
    ChoiceMenu.onClick(click(ChoiceMenu.LEFT_SLOT));
    fx.server.getScheduler().performTicks(1);
    menu = (ChoiceMenu) mage.getOpenInventory().getTopInventory().getHolder();
    assertEquals(BloodPath.DEVOTEE, menu.pending());
    InventoryClickEvent below = click(40);
    ChoiceMenu.onClick(below);
    assertTrue(below.isCancelled());
    ChoiceMenu.onClick(click(ChoiceMenu.LEFT_SLOT));
    fx.server.getScheduler().performTicks(1);
    assertEquals(BloodPath.DEVOTEE, record.getPath());

    InventoryDragEvent drag = mock(InventoryDragEvent.class);
    when(drag.getInventory()).thenReturn(fx.server.createInventory(null, 9));
    ChoiceMenu.onDrag(drag);
    verify(drag, never()).setCancelled(true);
    mage.openInventory(fx.server.createInventory(null, 9));
    InventoryClickEvent elsewhere = click(0);
    ChoiceMenu.onClick(elsewhere);
    assertFalse(elsewhere.isCancelled());
    ChoiceMenu.open(mage);
    InventoryDragEvent menuDrag = mock(InventoryDragEvent.class);
    when(menuDrag.getInventory()).thenReturn(mage.getOpenInventory().getTopInventory());
    ChoiceMenu.onDrag(menuDrag);
    verify(menuDrag).setCancelled(true);
  }

  @Test
  void buttonsWithoutNamesStayBlank() throws Exception {
    Files.writeString(temp.resolve("b.yml"), "enabled: true\nchoice:\n  back:\n    item: v.stone\n");
    BloodConfig.load(temp.resolve("b.yml").toFile(), fx.logger);
    assertEquals(" ", ChoiceMenu.button(ChoiceConfig.Slot.BACK).getItemMeta().getDisplayName());
  }

  @Test
  void devoteesGiveThemselvesAndGetClassAndArmour() {
    try (MockedStatic<TLibs> libs = mockStatic(TLibs.class)) {
      ItemAPI api = mock(ItemAPI.class, RETURNS_DEEP_STUBS);
      libs.when(TLibs::getItemAPI).thenReturn(api);
      when(api.getCreator().getItemFromPath(anyString())).thenAnswer(i -> new ItemStack(Material.LEATHER_HELMET));
      when(api.getArmorMerger().merge(any(), any(), anyString())).thenAnswer(i -> i.getArgument(0));
      assertTrue(BloodChoice.answer(mage, BloodPath.DEVOTEE));
    }
    bridge.verify(() -> RpCharactersBridge.applyPermanentInjury(mage), times(2));
    assertEquals(BloodPath.DEVOTEE, record.getPath());
    assertEquals(BloodStage.FINAL, record.getStage());
    assertEquals("bloodsworn", FakeUnlocks.last);
    assertEquals(4, java.util.Arrays.stream(mage.getInventory().getContents()).filter(BloodArmour::is).count());
    assertEquals("blood.choice.devoted", mage.nextMessage());
    assertNotNull(WhisperService.nextVoice(mage.getUniqueId()));
    assertFalse(BloodChoice.answer(mage, BloodPath.TRUE));
    assertEquals(BloodConfig.level(BloodProgress.of(record)), BloodAltars.ofCharacter(id).getLevel());
  }

  @Test
  void refusingKeepsTheClassAndStartsTheHunt() {
    bridge.when(() -> RpCharactersBridge.applyPermanentInjury(any())).thenReturn(false);
    assertFalse(BloodChoice.answer(mage, BloodPath.NONE));
    assertTrue(BloodChoice.answer(mage, BloodPath.TRUE));
    assertEquals(BloodStage.HUNT, record.getStage());
    assertNull(FakeUnlocks.last);
    assertEquals("blood.choice.refused", mage.nextMessage());
    bridge.verify(() -> RpCharactersBridge.applyPermanentInjury(any()), never());
  }

  @Test
  void injuriesThatFailAreCounted() {
    bridge.when(() -> RpCharactersBridge.applyPermanentInjury(any())).thenReturn(false);
    assertTrue(BloodChoice.answer(mage, BloodPath.DEVOTEE));
    assertEquals(BloodStage.FINAL, record.getStage());
  }

  @Test
  void onlyACharacterAtTheQuestionWithAnAltarMayAnswer() {
    BloodAltars.remove(BloodAltars.ofCharacter(id));
    assertFalse(BloodChoice.canAnswer(mage));
    record.setStage(BloodStage.ANIMAL);
    assertFalse(BloodChoice.canAnswer(mage));
    PlayerMock nobody = fx.server.addPlayer();
    assertFalse(BloodChoice.canAnswer(nobody));
  }

  @Test
  void classUnlockBridgeReportsFailures() {
    assertFalse(ClassUnlockBridge.unlock(mage, null));
    FakeUnlocks.answer = false;
    assertFalse(ClassUnlockBridge.unlock(mage, "x"));
    FakeUnlocks.answer = true;
    assertTrue(ClassUnlockBridge.unlock(mage, "x"));
    ClassUnlockBridge.unlocksClass = "no.such.Unlocks";
    assertFalse(ClassUnlockBridge.unlock(mage, "x"));
  }

  @Test
  void armourIsBoundUnbreakableAndOnlyItsOwnerWearsIt() {
    ItemStack helmet = BloodArmour.bind(new ItemStack(Material.LEATHER_HELMET), id);
    assertTrue(BloodArmour.is(helmet));
    assertTrue(helmet.getItemMeta().isUnbreakable());
    assertFalse(BloodArmour.is(null));
    assertFalse(BloodArmour.is(new ItemStack(Material.AIR)));
    assertFalse(BloodArmour.is(new ItemStack(Material.STONE)));
    assertFalse(BloodArmour.is(BloodItems.create(BloodItemKind.ALTAR, id)));
    assertTrue(BloodArmour.create(BloodPath.DEVOTEE, id).isEmpty());
    ItemStack bareItem = mock(ItemStack.class);
    when(bareItem.getType()).thenReturn(Material.LEATHER_HELMET);
    assertFalse(BloodArmour.is(bareItem));
    ItemStack ownerless = new ItemStack(Material.LEATHER_HELMET);
    var meta = ownerless.getItemMeta();
    meta.getPersistentDataContainer().set(BloodItems.kindKey(), org.bukkit.persistence.PersistentDataType.STRING, "armour");
    ownerless.setItemMeta(meta);
    PlayerMock wearer = fx.player("wearer");
    wearer.getInventory().setHelmet(ownerless);
    assertEquals(1, BloodArmour.enforce(wearer));

    mage.getInventory().setHelmet(helmet);
    mage.getInventory().setChestplate(new ItemStack(Material.LEATHER_CHESTPLATE));
    assertEquals(0, BloodArmour.enforce(mage));
    PlayerMock thief = fx.player("thief");
    thief.getInventory().setHelmet(helmet.clone());
    assertEquals(1, BloodArmour.enforce(thief));
    assertNull(thief.getInventory().getHelmet());
    assertEquals("blood.armour.refuses", thief.nextMessage());
    assertTrue(thief.getInventory().containsAtLeast(helmet, 1));
    PlayerMock bare = fx.server.addPlayer();
    bare.getInventory().setBoots(BloodArmour.bind(new ItemStack(Material.LEATHER_BOOTS), id));
    assertEquals(1, BloodArmour.enforce(bare));

    String dead = UUID.randomUUID().toString();
    ItemStack relic = BloodArmour.bind(new ItemStack(Material.LEATHER_LEGGINGS), dead);
    try (MockedStatic<Characters> chars = mockStatic(Characters.class, CALLS_REAL_METHODS)) {
      chars.when(() -> Characters.info(UUID.fromString(dead))).thenReturn(Optional.of(new CharacterInfo(
          new CharacterRef(UUID.randomUUID(), UUID.fromString(dead)), "N", CharacterStatus.DEAD, null, Set.of(), 0,
          Instant.EPOCH)));
      assertTrue(BloodArmour.crumbles(relic));
      assertFalse(BloodArmour.crumbles(helmet));
      assertFalse(BloodArmour.crumbles(new ItemStack(Material.STONE)));
      thief.getInventory().setLeggings(relic);
      assertEquals(1, BloodArmour.enforce(thief));
      assertNull(thief.getInventory().getLeggings());
      assertEquals("blood.armour.crumbles", thief.nextMessage());
    }
  }
}
