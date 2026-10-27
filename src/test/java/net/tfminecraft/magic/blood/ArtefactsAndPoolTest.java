package net.tfminecraft.magic.blood;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.command.ConsoleCommandSenderMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import org.mockito.MockedStatic;

import io.lumine.mythic.lib.api.event.skill.PlayerCastSkillEvent;
import io.lumine.mythic.lib.skill.Skill;
import io.lumine.mythic.lib.skill.handler.SkillHandler;
import net.tfminecraft.interactiblefurniture.events.FurnitureSlotItemTakeEvent;
import net.tfminecraft.interactiblefurniture.furniture.Furniture;
import net.tfminecraft.magic.manager.MagicTickService;
import net.tfminecraft.tlibs.character.CharacterStatus;
import net.tfminecraft.tlibs.character.Characters;

class ArtefactsAndPoolTest {
  @TempDir Path temp;
  BloodFixture fx;
  PlayerMock mage;
  String mageId;
  BloodRecord record;
  Block target;
  final List<String> logs = new ArrayList<>();

  @BeforeEach
  void setup() {
    fx = new BloodFixture(temp);
    fx.logger.addHandler(new java.util.logging.Handler() {
      @Override public void publish(java.util.logging.LogRecord line) { logs.add(line.getMessage()); }
      @Override public void flush() {}
      @Override public void close() {}
    });
    BloodConfig.load(new File("src/main/resources/wildmagic/bloodmagic"), fx.logger);
    mage = fx.player("mage");
    mageId = BloodFixture.id("mage");
    record = fx.record("mage", BloodStage.FINAL);
    record.setPath(BloodPath.DEVOTEE);
    target = fx.pocket(0, 10, 0);
  }

  @AfterEach
  void cleanup() {
    BloodSpellGate.clock = System::currentTimeMillis;
    fx.close();
  }

  String messages(PlayerMock player) {
    StringBuilder out = new StringBuilder();
    String message;
    while ((message = player.nextMessage()) != null) {
      out.append(message).append('\n');
    }
    return out.toString();
  }

  BloodAltar altar() {
    BloodAltar altar = BloodAltar.create(mageId, mage.getUniqueId().toString(), target, 0f, 1L);
    BloodAltars.add(altar);
    return altar;
  }

  void sinkAll() {
    record.getSunk().clear();
    record.getSunk().addAll(BloodArtefacts.ids(record));
  }

  PlayerInteractEvent click(PlayerMock player, ItemStack held) {
    player.getInventory().setItemInMainHand(held);
    return new PlayerInteractEvent(player, Action.RIGHT_CLICK_BLOCK, held, target, BlockFace.UP, EquipmentSlot.HAND);
  }

  @Test
  void theDefaultsSetOutThePoolTiersAndArtefacts() {
    assertEquals(100, PoolConfig.capacity());
    assertEquals(0.5, PoolConfig.drainPerHour());
    assertEquals(5, PoolConfig.floor());
    assertEquals(40, PoolConfig.refill(0));
    assertEquals(100, PoolConfig.refill(3));
    assertEquals(60, PoolConfig.refill(20));
    assertEquals(40, PoolConfig.refill(40));
    assertEquals(20, PoolConfig.worth(BloodPath.DEVOTEE, 4));
    assertEquals(20, PoolConfig.worth(BloodPath.TRUE, -1));
    assertEquals(55, PoolConfig.worth(BloodPath.TRUE, 2));
    assertEquals(95, PoolConfig.worth(BloodPath.TRUE, Integer.MAX_VALUE));
    assertEquals(0, PoolConfig.worth(BloodPath.NONE, 1));
    assertEquals(30, PoolConfig.need(3));
    assertEquals(0, PoolConfig.need(9));
    assertEquals(0, PoolConfig.band(4));
    assertEquals(2, PoolConfig.band(15));
    assertEquals(6, PoolConfig.band(95));
    assertEquals(1, PoolConfig.tierOf(" bloodsworn_blood_bolt "));
    assertEquals(6, PoolConfig.tierOf("SANGUINAR_CRIMSON_TIDE"));
    assertEquals(0, PoolConfig.tierOf("FIREBALL"));
    assertEquals(0, PoolConfig.tierOf(null));
    assertEquals(8, ArtefactConfig.devotee().size());
    assertEquals(8, ArtefactConfig.hunt().size());
    assertEquals(5, ArtefactConfig.finals().size());
    assertEquals(List.of("§7§oA small gift from the Blood Father.", "§7§oIt only wakes in the pool of blood."),
        ArtefactConfig.devotee().get(0).lore());
    assertEquals("§7§oAll that is left of them.", ArtefactConfig.finals().get(0).lore().get(0));
  }

  @Test
  void badEntriesAreSkippedAndMissingSectionsLeaveTheDefaults() throws Exception {
    Files.writeString(temp.resolve("p.yml"), String.join("\n",
        "enabled: true",
        "pool:",
        "  capacity: 50",
        "  refill:",
        "    people:",
        "      ranks:",
        "        - { up_to: 5 }",
        "        - { amount: 3 }",
        "        - { up_to: 9, amount: 30 }",
        "  worth:",
        "    devotee: { x: 4, 1: lots, 2: 8, 3: 8.5 }",
        "  spells:",
        "    x: [A]",
        "    3: [' ', ' b ']",
        "artefacts:",
        "  devotee:",
        "    - { name: nothing }",
        "    - { item: ' ' }",
        "    - { item: m.a.b, lore: [1, two] }",
        ""));
    BloodConfig.load(temp.resolve("p.yml").toFile(), fx.logger);
    assertEquals(50, PoolConfig.capacity());
    assertEquals(30, PoolConfig.refill(6));
    assertEquals(40, PoolConfig.refill(10));
    assertEquals(0, PoolConfig.worth(BloodPath.DEVOTEE, 1));
    assertEquals(8, PoolConfig.worth(BloodPath.DEVOTEE, 2));
    assertEquals(8.5, PoolConfig.worth(BloodPath.DEVOTEE, 3));
    assertEquals(0, PoolConfig.worth(BloodPath.TRUE, 2));
    assertEquals(0, PoolConfig.need(1));
    assertEquals(3, PoolConfig.tierOf("B"));
    assertEquals(0, PoolConfig.tierOf("A"));
    assertEquals(List.of(new ArtefactConfig.Piece("m.a.b", "", List.of("1", "two"))), ArtefactConfig.devotee());
    assertTrue(ArtefactConfig.hunt().isEmpty());
    assertTrue(logs.stream().anyMatch(line -> line.contains("pool.refill.people.ranks entry needs up_to and amount")));
    assertTrue(logs.stream().anyMatch(line -> line.contains("pool.worth.devotee entry 'x' skipped")));
    assertTrue(logs.stream().anyMatch(line -> line.contains("pool.worth.devotee entry '1' skipped")));
    assertTrue(logs.stream().anyMatch(line -> line.contains("pool.spells entry 'x' skipped")));
    assertTrue(logs.stream().anyMatch(line -> line.contains("artefacts.devotee entry needs an item")));

    Files.writeString(temp.resolve("e.yml"), "enabled: true\n");
    BloodConfig.load(temp.resolve("e.yml").toFile(), fx.logger);
    assertEquals(100, PoolConfig.capacity());
    assertTrue(ArtefactConfig.devotee().isEmpty());
  }

  @Test
  void idsFollowThePathAndItsFinals() {
    assertTrue(BloodArtefacts.ids(null).isEmpty());
    assertEquals(List.of("devotee:0", "devotee:1"), BloodArtefacts.ids(record).subList(0, 2));
    assertEquals(8, BloodArtefacts.ids(record).size());
    record.setStage(BloodStage.HUNT);
    assertTrue(BloodArtefacts.ids(record).isEmpty());
    record.setStage(BloodStage.FINAL);
    record.setPath(BloodPath.NONE);
    assertTrue(BloodArtefacts.ids(record).isEmpty());
    record.setPath(BloodPath.TRUE);
    assertEquals("true:hunt:0", BloodArtefacts.ids(record).get(0));
    record.setFinalsDone(2);
    assertEquals(List.of("true:final:0", "true:final:1", "true:hunt:2"), BloodArtefacts.ids(record).subList(0, 3));
    record.setFinalsDone(9);
    assertEquals(List.of("true:final:4", "true:hunt:5"), BloodArtefacts.ids(record).subList(4, 6));

    assertNull(BloodArtefacts.piece(null));
    assertNull(BloodArtefacts.piece("x"));
    assertNull(BloodArtefacts.piece("devotee:9"));
    assertNull(BloodArtefacts.piece("devotee:-1"));
    assertNull(BloodArtefacts.piece("devotee:x"));
    assertNull(BloodArtefacts.piece("true:hunt"));
    assertNull(BloodArtefacts.piece("true:other:1"));
    assertNull(BloodArtefacts.piece("devotee:1:2"));
    assertNull(BloodArtefacts.piece("false:hunt:1"));
    assertEquals("#5c0202Hunter's Orb", BloodArtefacts.piece("true:hunt:0").name());
    assertEquals("#5c0202Eye of {victim}", BloodArtefacts.piece("true:final:4").name());
  }

  @Test
  void artefactsAreBoundAndNamedAfterTheirFinalsVictims() throws Exception {
    ItemStack ring = BloodArtefacts.create(record, "devotee:0");
    assertTrue(BloodArtefacts.is(ring));
    assertEquals("devotee:0", BloodArtefacts.id(ring));
    assertEquals(mageId, BloodItems.owner(ring));
    assertNull(BloodArtefacts.create(record, "nope"));
    assertFalse(BloodArtefacts.is(null));
    assertFalse(BloodArtefacts.is(new ItemStack(Material.AIR)));
    assertFalse(BloodArtefacts.is(new ItemStack(Material.STONE)));
    assertFalse(BloodArtefacts.is(BloodItems.create(BloodItemKind.BLOOD_KNIFE, mageId)));
    assertNull(BloodArtefacts.id(new ItemStack(Material.STONE)));

    record.setPath(BloodPath.TRUE);
    String victim = UUID.randomUUID().toString();
    record.getSacrifices().add(new BloodRecord.Sacrifice("not-a-uuid", "a", "HUNT", 1, 1));
    try (MockedStatic<Characters> chars = mockStatic(Characters.class, CALLS_REAL_METHODS)) {
      chars.when(() -> Characters.info(UUID.fromString(victim)))
          .thenReturn(AltarServiceTest.info(victim, CharacterStatus.DEAD));
      assertEquals("someone", BloodArtefacts.victim(record, 0));
      record.getSacrifices().add(new BloodRecord.Sacrifice("not-a-uuid", "a", "FINAL", 0, 2));
      record.getSacrifices().add(new BloodRecord.Sacrifice(victim, "b", "FINAL", 0, 3));
      assertEquals("someone", BloodArtefacts.victim(record, 0));
      assertEquals("N", BloodArtefacts.victim(record, 1));
      assertEquals("someone", BloodArtefacts.victim(record, 2));
      assertTrue(BloodArtefacts.create(record, "true:final:1").getItemMeta().getDisplayName().contains("Heart of N"));
    }

    Files.writeString(temp.resolve("a.yml"), "enabled: true\nartefacts:\n  devotee:\n    - { item: m.a.b }\n");
    BloodConfig.load(temp.resolve("a.yml").toFile(), fx.logger);
    record.setPath(BloodPath.DEVOTEE);
    ItemStack plain = BloodArtefacts.create(record, "devotee:0");
    assertFalse(plain.getItemMeta().hasDisplayName());
    assertFalse(plain.getItemMeta().hasLore());
  }

  @Test
  void syncHandsOutWhatIsMissingAndFinalsReplaceTheirPlace() {
    PlayerMock bare = fx.server.addPlayer();
    assertEquals(0, BloodArtefacts.sync(bare));
    assertEquals(8, BloodArtefacts.sync(mage));
    assertTrue(messages(mage).contains("blood.artefact.given"));
    assertEquals(8, BloodArtefacts.ids(record).stream().filter(id -> mage.getInventory().containsAtLeast(
        BloodArtefacts.create(record, id), 1)).count());
    assertEquals(BloodArtefacts.ids(record), record.getArtefacts());
    assertEquals(0, BloodArtefacts.sync(mage));
    assertEquals("", messages(mage));

    altar();
    record.setPath(BloodPath.TRUE);
    record.getSunk().add("devotee:0");
    assertEquals(8, BloodArtefacts.sync(mage));
    assertTrue(record.getSunk().isEmpty(), "The old path's artefacts leave the pool");
    record.getSunk().addAll(List.of("true:hunt:0", "true:hunt:1"));
    record.setFinalsDone(1);
    messages(mage);
    assertEquals(1, BloodArtefacts.sync(mage));
    assertTrue(messages(mage).contains("blood.artefact.replaced"));
    assertEquals(List.of("true:hunt:1"), record.getSunk());
    record.getArtefacts().add("true:hunt:8");
    assertEquals(0, BloodArtefacts.sync(mage), "A place the config dropped just goes");
    assertEquals(8, record.getArtefacts().size());
    assertEquals("", messages(mage));
    record.setFinalsDone(5);
    try (MockedStatic<AltarPedestals> pedestals = mockStatic(AltarPedestals.class, CALLS_REAL_METHODS)) {
      pedestals.when(() -> AltarPedestals.offer(any(), any(), any())).thenReturn(true);
      int carried = mage.getInventory().all(Material.FERMENTED_SPIDER_EYE).size();
      assertEquals(4, BloodArtefacts.sync(mage));
      assertEquals(carried, mage.getInventory().all(Material.FERMENTED_SPIDER_EYE).size(), "Set out on the pedestals");
    }
    record.setFinalsDone(1);
    record.getArtefacts().clear();
    record.getArtefacts().addAll(BloodArtefacts.ids(record));

    record.setFinalsDone(2);
    BloodArtefacts.syncSoon(mage);
    assertEquals("true:hunt:1", record.getArtefacts().get(1));
    fx.server.getScheduler().performOneTick();
    assertEquals("true:final:1", record.getArtefacts().get(1));
    record.setFinalsDone(3);
    BloodArtefacts.syncSoon(mage);
    mage.disconnect();
    fx.server.getScheduler().performOneTick();
    assertEquals("true:hunt:2", record.getArtefacts().get(2));
  }

  @Test
  void onlyTheOwnersCurrentArtefactsSinkIntoTheirOwnPool() {
    BloodListener listener = new BloodListener();
    altar();
    BloodArtefacts.sync(mage);
    messages(mage);
    List<String> ids = BloodArtefacts.ids(record);
    for (int i = 0; i < ids.size() - 1; i++) {
      listener.onInteract(click(mage, BloodArtefacts.create(record, ids.get(i))));
      assertEquals("blood.artefact.sink.sunk", mage.nextMessage());
      assertTrue(mage.getInventory().getItemInMainHand().getType().isAir());
    }
    assertEquals(7, BloodArtefacts.sunk(record));
    ItemStack two = BloodArtefacts.create(record, ids.get(0));
    two.setAmount(2);
    listener.onInteract(click(mage, two));
    assertEquals("blood.artefact.sink.already", mage.nextMessage());
    assertEquals(1, mage.getInventory().getItemInMainHand().getAmount());
    listener.onInteract(click(mage, BloodArtefacts.create(record, ids.get(7))));
    assertEquals("blood.artefact.sink.all_sunk", mage.nextMessage());
    record.setPath(BloodPath.TRUE);
    listener.onInteract(click(mage, BloodArtefacts.create(record, "devotee:1")));
    assertEquals("blood.artefact.sink.stale", mage.nextMessage(), "The old path's artefact");

    PlayerMock thief = fx.player("thief");
    ItemStack stolen = BloodArtefacts.create(record, "devotee:1");
    listener.onInteract(click(thief, stolen));
    assertEquals("blood.artefact.sink.not_yours", thief.nextMessage());
    assertEquals(1, thief.getInventory().getItemInMainHand().getAmount());
    BloodRecord own = fx.record("thief", BloodStage.FINAL);
    own.setPath(BloodPath.DEVOTEE);
    listener.onInteract(click(thief, BloodArtefacts.create(own, "devotee:1")));
    assertEquals("blood.artefact.sink.not_yours", thief.nextMessage(), "Not the thief's altar");
    BloodAltar mine = BloodAltars.ofCharacter(mageId);
    assertEquals(BloodArtefacts.Sink.NOT_YOURS, BloodArtefacts.sink(mage, mine, BloodArtefacts.create(own, "devotee:1")));
    PlayerMock bare = fx.server.addPlayer();
    assertEquals(BloodArtefacts.Sink.NOT_YOURS, BloodArtefacts.sink(bare, BloodAltars.ofCharacter(mageId), stolen));
    PlayerMock lost = fx.player("lost");
    assertEquals(BloodArtefacts.Sink.NOT_YOURS, BloodArtefacts.sink(lost, BloodAltars.ofCharacter(mageId), stolen));
  }

  @Test
  void staffHandOutWhatIsNotInThePool() {
    PlayerMock bare = fx.server.addPlayer();
    assertEquals(0, BloodArtefacts.regive(bare));
    sinkAll();
    record.getSunk().remove("devotee:3");
    assertEquals(1, BloodArtefacts.regive(mage));
    assertEquals("devotee:3", BloodArtefacts.id(mage.getInventory().getItem(0)));
  }

  @Test
  void pedestalsAndDroppedArtefactsAnswerOnlyToTheirOwner() {
    BloodListener listener = new BloodListener();
    Furniture pedestal = new Furniture("pedestal", target.getLocation(), UUID.randomUUID());
    ItemStack ring = BloodArtefacts.create(record, "devotee:0");
    FurnitureSlotItemTakeEvent plain = new FurnitureSlotItemTakeEvent(mage, pedestal, null, new ItemStack(Material.STONE));
    listener.onSlotTake(plain);
    assertFalse(plain.isCancelled());
    FurnitureSlotItemTakeEvent own = new FurnitureSlotItemTakeEvent(mage, pedestal, null, ring);
    listener.onSlotTake(own);
    assertFalse(own.isCancelled());
    PlayerMock thief = fx.player("thief");
    FurnitureSlotItemTakeEvent stolen = new FurnitureSlotItemTakeEvent(thief, pedestal, null, ring);
    listener.onSlotTake(stolen);
    assertTrue(stolen.isCancelled());
    assertEquals("blood.artefact.not_yours", thief.nextMessage());
    PlayerMock bare = fx.server.addPlayer();
    FurnitureSlotItemTakeEvent nobody = new FurnitureSlotItemTakeEvent(bare, pedestal, null, ring);
    listener.onSlotTake(nobody);
    assertTrue(nobody.isCancelled());

    var lying = fx.world.dropItem(thief.getLocation(), ring);
    var pickup = new org.bukkit.event.entity.EntityPickupItemEvent(thief, lying, 0);
    listener.onPickup(pickup);
    assertFalse(pickup.isCancelled());
    String dead = UUID.randomUUID().toString();
    BloodRecord gone = BloodRecords.getOrCreate(dead, "o");
    gone.setStage(BloodStage.FINAL);
    gone.setPath(BloodPath.DEVOTEE);
    var relic = fx.world.dropItem(thief.getLocation(), BloodArtefacts.create(gone, "devotee:2"));
    try (MockedStatic<Characters> chars = mockStatic(Characters.class, CALLS_REAL_METHODS)) {
      chars.when(() -> Characters.info(UUID.fromString(dead))).thenReturn(AltarServiceTest.info(dead, CharacterStatus.DEAD));
      var relicPickup = new org.bukkit.event.entity.EntityPickupItemEvent(thief, relic, 0);
      listener.onPickup(relicPickup);
      assertTrue(relicPickup.isCancelled());
      assertFalse(relic.isValid());
    }
  }

  @Test
  void resonanceIsTheSunkShareHeldDownByThePool() {
    assertEquals(5, BloodPool.resonance(null));
    assertEquals(5, BloodPool.resonance(record), "Nothing sunk yet: the floor");
    sinkAll();
    assertEquals(20, BloodPool.resonance(record));
    record.getSunk().subList(0, 2).clear();
    assertEquals(15, BloodPool.resonance(record));
    record.setPath(BloodPath.NONE);
    assertEquals(5, BloodPool.resonance(record));

    record.setPath(BloodPath.TRUE);
    record.setStage(BloodStage.ASCENDED);
    sinkAll();
    assertEquals(95, BloodPool.resonance(record));
    assertEquals("full", BloodPool.state(record));
    record.setPool(120.0);
    assertEquals(100, BloodPool.level(record));
    record.setPool(60.0);
    assertEquals(60, BloodPool.resonance(record));
    assertEquals("half", BloodPool.state(record));
    record.setPool(10.0);
    assertEquals("low", BloodPool.state(record));
    record.setPool(0.0);
    assertEquals("dry", BloodPool.state(record));
    assertEquals(5, BloodPool.resonance(record));
    record.setStage(BloodStage.FINAL);
    assertEquals(100, BloodPool.level(record), "Only a complete path drains its pool");
  }

  @Test
  void thePoolDrainsOnceThePathIsCompleteAndSaysWhenPowerSlips() throws Exception {
    PlayerMock other = fx.server.addPlayer();
    PlayerMock waiting = fx.player("waiting");
    fx.record("waiting", BloodStage.ASCENDED);
    record.setPath(BloodPath.TRUE);
    record.setStage(BloodStage.ASCENDED);
    BloodPool.tick();
    assertNull(record.getPool(), "Nothing sunk, nothing drinks");
    sinkAll();
    record.setPool(90.004);
    BloodPool.tick();
    assertTrue(record.getPool() < 90);
    assertEquals("blood.pool.thinned", mage.nextMessage());
    BloodPool.tick();
    assertNull(mage.nextMessage(), "Still the same tier");
    record.setPool(0.005);
    BloodPool.tick();
    assertEquals(0, record.getPool());
    assertEquals("blood.pool.dry", mage.nextMessage());
    BloodPool.tick();
    assertNull(mage.nextMessage());
    assertEquals(0, record.getPool());
    record.setStage(BloodStage.FINAL);
    record.setPool(50.0);
    BloodPool.tick();
    assertEquals(50, record.getPool());
    assertNull(other.nextMessage());
    assertNull(waiting.nextMessage());

    Files.writeString(temp.resolve("d.yml"), "enabled: true\npool:\n  drain_per_hour: 0\n");
    BloodConfig.load(temp.resolve("d.yml").toFile(), fx.logger);
    record.setStage(BloodStage.ASCENDED);
    BloodPool.tick();
    assertEquals(50, record.getPool());
  }

  @Test
  void spellsAboveTheCastersBloodResonanceAreRefused() {
    BloodSpellGate gate = new BloodSpellGate();
    java.util.concurrent.atomic.AtomicLong now = new java.util.concurrent.atomic.AtomicLong(1000);
    BloodSpellGate.clock = now::get;
    PlayerMock bare = fx.server.addPlayer();
    PlayerMock outsider = fx.player("outsider");
    fx.record("outsider", BloodStage.FINAL);
    assertFalse(cast(gate, mage, "FIREBALL"));
    assertFalse(cast(gate, null, "BLOODSWORN_BLOOD_BOIL"));
    assertFalse(cast(gate, bare, "BLOODSWORN_BLOOD_BOIL"));
    assertFalse(cast(gate, outsider, "BLOODSWORN_BLOOD_BOIL"), "No path: never refused");
    assertFalse(cast(gate, mage, "BLOODSWORN_BLOOD_BOLT"), "The floor keeps Minor spells");
    assertTrue(cast(gate, mage, "BLOODSWORN_BLOOD_BOIL"));
    assertEquals("blood.pool.too_thin", mage.nextMessage());
    assertTrue(cast(gate, mage, "BLOODSWORN_BLOOD_BOIL"));
    assertNull(mage.nextMessage(), "Said once every few seconds");
    now.addAndGet(5000);
    assertTrue(cast(gate, mage, "BLOODSWORN_BLOOD_BOIL"));
    assertEquals("blood.pool.too_thin", mage.nextMessage());
    sinkAll();
    assertFalse(cast(gate, mage, "BLOODSWORN_BLOOD_BOIL"));
    BloodConfig.setEnabled(false);
    record.getSunk().clear();
    assertFalse(cast(gate, mage, "BLOODSWORN_BLOOD_BOIL"));
    BloodSpellGate.forget(mage.getUniqueId());

    assertNull(BloodSpellGate.spell(null));
    assertNull(BloodSpellGate.spell(mock(Skill.class)));
  }

  boolean cast(BloodSpellGate gate, PlayerMock player, String spell) {
    PlayerCastSkillEvent event = mock(PlayerCastSkillEvent.class);
    Skill skill = mock(Skill.class);
    SkillHandler<?> handler = mock(SkillHandler.class);
    when(handler.getId()).thenReturn(spell);
    doReturn(handler).when(skill).getHandler();
    when(event.getCast()).thenReturn(skill);
    when(event.getPlayer()).thenReturn(player);
    gate.onCast(event);
    try {
      verify(event).setCancelled(true);
      return true;
    } catch (AssertionError ex) {
      return false;
    }
  }

  @Test
  void theAltarTellsItsOwnerHowFullThePoolIs() {
    BloodAltar altar = altar();
    record.setPath(BloodPath.TRUE);
    record.setStage(BloodStage.ASCENDED);
    AltarService.touch(mage, altar);
    assertEquals("blood.altar.touch.ascended_true_full", mage.nextMessage());
    record.setPool(0.0);
    record.setPath(BloodPath.DEVOTEE);
    AltarService.touch(mage, altar);
    assertEquals("blood.altar.touch.ascended_devotee_dry", mage.nextMessage());
    record.setStage(BloodStage.FINAL);
    AltarService.touch(mage, altar);
    assertEquals("blood.altar.touch.final_devotee", mage.nextMessage());
  }

  @Test
  void staffSeeAndSetThePool() {
    ConsoleCommandSenderMock console = (ConsoleCommandSenderMock) fx.server.getConsoleSender();
    String name = mage.getName();
    PlayerMock bare = fx.player("bare");
    assertTrue(BloodCommand.execute(console, "blood pool".split(" ")));
    assertEquals("blood.admin.usage", console.nextMessage());
    assertTrue(BloodCommand.execute(console, ("blood pool " + bare.getName()).split(" ")));
    assertEquals("blood.admin.no_record", console.nextMessage());
    assertTrue(BloodCommand.execute(console, ("blood pool " + name).split(" ")));
    assertEquals("blood.admin.pool", console.nextMessage());
    assertTrue(BloodCommand.execute(console, ("blood pool " + name + " lots").split(" ")));
    assertEquals("values.invalid_amount", console.nextMessage());
    record.setStage(BloodStage.ASCENDED);
    assertTrue(BloodCommand.execute(console, ("blood pool " + name + " 250").split(" ")));
    assertEquals(100, record.getPool());
    assertEquals("blood.admin.pool", console.nextMessage());
    assertTrue(BloodCommand.execute(console, ("blood info " + name).split(" ")));
    assertEquals("blood.admin.info", console.nextMessage());
    assertEquals("blood.admin.pool", console.nextMessage());
    record.setPath(BloodPath.NONE);
    assertTrue(BloodCommand.execute(console, ("blood info " + name).split(" ")));
    assertEquals("blood.admin.info", console.nextMessage());
    assertNull(console.nextMessage());
    record.setPath(BloodPath.DEVOTEE);
    assertTrue(BloodCommand.execute(console, ("blood give " + name + " artefacts").split(" ")));
    assertEquals("blood.admin.artefacts_given", console.nextMessage());
    assertTrue(BloodArtefacts.is(mage.getInventory().getItem(0)));
    assertTrue(BloodCommand.complete(new String[] {"blood", "give", "Mage", ""}).contains("artefacts"));
  }

  @Test
  void withMythicLibTheSpellGateListensAndTheMinuteTickDrains() {
    MockBukkit.createMockPlugin("MythicLib");
    var plugin = MockBukkit.createMockPlugin("BloodHost");
    try {
      BloodMagic.start(plugin);
      assertTrue(org.bukkit.event.HandlerList.getRegisteredListeners(plugin).stream()
          .anyMatch(listener -> listener.getListener() instanceof BloodSpellGate));
    } finally {
      MagicTickService.clearHandlers();
      BloodMagic.stop();
    }
  }

  @Test
  void recordsKeepTheirArtefactsAndPool() {
    BloodRecord fresh = BloodRecord.create("c", "o");
    assertTrue(fresh.isEmpty());
    fresh.getArtefacts().add("devotee:0");
    assertFalse(fresh.isEmpty());
    fresh.getArtefacts().clear();
    fresh.getSunk().add("devotee:0");
    assertFalse(fresh.isEmpty());
    fresh.getSunk().clear();
    fresh.setPool(-4.0);
    assertEquals(0, fresh.getPool());
    assertFalse(fresh.isEmpty());
    fresh.setPool(Double.NaN);
    fresh.normalize();
    assertEquals(0, fresh.getPool());
    fresh.setPool(null);
    assertNull(fresh.getPool());
    fresh.normalize();
    assertNull(fresh.getPool());
    fresh.setPool(3.0);
    fresh.normalize();
    assertEquals(3, fresh.getPool());
    BloodRecord old = new com.google.gson.Gson().fromJson("{\"artefacts\":null,\"sunk\":null,\"pool\":-3}",
        BloodRecord.class);
    old.normalize();
    assertTrue(old.getArtefacts().isEmpty());
    assertTrue(old.getSunk().isEmpty());
    assertEquals(0, old.getPool());
  }
}
