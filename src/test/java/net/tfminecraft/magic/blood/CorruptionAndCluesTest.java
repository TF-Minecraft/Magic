package net.tfminecraft.magic.blood;

import static org.junit.jupiter.api.Assertions.*;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.data.Lightable;
import org.bukkit.entity.Cow;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.persistence.PersistentDataType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

class CorruptionAndCluesTest {
  @TempDir Path temp;
  BloodFixture fx;
  final AtomicLong now = new AtomicLong(50_000_000L);

  @BeforeEach
  void setup() {
    fx = new BloodFixture(temp);
    AltarCorruption.random = new Random(7);
    AltarCorruption.clock = now::get;
    StolenClues.clock = now::get;
  }

  @AfterEach
  void cleanup() {
    AltarCorruption.random = new Random();
    AltarCorruption.clock = System::currentTimeMillis;
    StolenClues.clock = System::currentTimeMillis;
    StolenClues.clear();
    BloodNotes.clear();
    fx.close();
  }

  /** A 7x3x7 room of air with a stone floor and ceiling around the altar block. */
  Block room() {
    fx.world.loadChunk(0, 0);
    for (int x = -3; x <= 3; x++) {
      for (int z = -3; z <= 3; z++) {
        fx.world.getBlockAt(x, 9, z).setType(Material.STONE);
        fx.world.getBlockAt(x, 13, z).setType(Material.STONE);
        for (int y = 10; y <= 12; y++) {
          fx.world.getBlockAt(x, y, z).setType(Material.AIR);
        }
      }
    }
    return fx.world.getBlockAt(0, 10, 0);
  }

  void config(String yaml) throws Exception {
    Files.writeString(temp.resolve("c.yml"), "enabled: true\n" + yaml);
    BloodConfig.load(temp.resolve("c.yml").toFile(), fx.logger);
  }

  @Test
  void spreadConvertsDecoratesWithinBudgetAndRestoreUndoesIt() throws Exception {
    config(String.join("\n", "corruption:", "  levels:", "    0: { radius: 3, max_blocks: 12 }",
        "  convert:", "    - { min_level: 0, from: [STONE], to: [NETHERRACK] }",
        "  decorate:", "    - { min_level: 0, at: floor, to: [RED_CANDLE] }",
        "    - { min_level: 0, at: ceiling, to: [WEEPING_VINES] }", ""));
    Block center = room();
    center.setType(Material.BARRIER);
    BloodAltar altar = BloodAltar.create("c", "o", center, 0f, 1L);
    BloodAltars.add(altar);
    int made = AltarCorruption.spread(altar, 40);
    assertEquals(12, made);
    assertEquals(made, altar.getCorrupted().size());
    assertEquals(Material.BARRIER, center.getType());
    assertTrue(center.getRelative(0, 1, 0).isEmpty());
    // Candles only grow before the pedestals, on turned ground; any that did are lit.
    for (BloodAltar.CorruptedBlock entry : altar.getCorrupted()) {
      Block block = fx.world.getBlockAt(entry.x(), entry.y(), entry.z());
      assertEquals(entry.placed(), block.getType().name());
      if (block.getType() == Material.RED_CANDLE) {
        assertTrue(((Lightable) block.getBlockData()).isLit());
      }
    }
    assertEquals(0, AltarCorruption.spread(altar, 5));

    BloodAltar.CorruptedBlock first = altar.getCorrupted().get(0);
    Block changedByPlayer = fx.world.getBlockAt(first.x(), first.y(), first.z());
    changedByPlayer.setType(Material.GOLD_BLOCK);
    BloodAltar.CorruptedBlock last = altar.getCorrupted().get(altar.getCorrupted().size() - 1);
    altar.getCorrupted().set(altar.getCorrupted().size() - 1,
        new BloodAltar.CorruptedBlock(last.x(), last.y(), last.z(), "minecraft:not_a_block", last.placed()));
    AltarCorruption.restore(altar);
    assertTrue(altar.getCorrupted().isEmpty());
    assertEquals(Material.GOLD_BLOCK, changedByPlayer.getType());
    assertEquals(Material.AIR, fx.world.getBlockAt(last.x(), last.y(), last.z()).getType());
    for (int x = -3; x <= 3; x++) {
      for (int z = -3; z <= 3; z++) {
        if (!(x == first.x() && z == first.z())) {
          assertNotEquals(Material.NETHERRACK, fx.world.getBlockAt(x, 9, z).getType());
        }
      }
    }
  }

  @Test
  void candlesStandOnlyBeforeThePedestalsAndTheRoomTurnsInPatches() throws Exception {
    Block center = room();
    assertTrue(AltarCorruption.candleSpot(center.getRelative(3, 0, 0), center));
    assertTrue(AltarCorruption.candleSpot(center.getRelative(0, 0, -3), center));
    assertTrue(AltarCorruption.candleSpot(center.getRelative(-2, 0, 2), center));
    assertFalse(AltarCorruption.candleSpot(center.getRelative(2, 0, 0), center));
    assertFalse(AltarCorruption.candleSpot(center.getRelative(3, 1, 0), center));
    CorruptionConfig.Rule candles = new CorruptionConfig.Rule(0, java.util.Set.of(), CorruptionConfig.Placement.FLOOR,
        java.util.List.of(Material.RED_CANDLE));
    CorruptionConfig.Rule mixed = new CorruptionConfig.Rule(0, java.util.Set.of(), CorruptionConfig.Placement.FLOOR,
        java.util.List.of(Material.RED_CANDLE, Material.REDSTONE_WIRE));
    assertNull(AltarCorruption.material(candles, center.getRelative(1, 0, 1), center));
    assertEquals(Material.RED_CANDLE, AltarCorruption.material(candles, center.getRelative(2, 0, 2), center));
    for (int i = 0; i < 10; i++) {
      assertEquals(Material.REDSTONE_WIRE, AltarCorruption.material(mixed, center.getRelative(1, 0, 1), center));
    }
    AltarCorruption.random = new Random(1) {
      @Override public double nextDouble() { return 0; }
    };
    java.util.Set<Long> none = java.util.Set.of();
    assertTrue(AltarCorruption.score(center.getRelative(1, 0, 0), center, none)
        > AltarCorruption.score(center.getRelative(3, 0, 0), center, none));
    Block far = center.getRelative(3, 0, 0);
    BloodAltar altar = BloodAltar.create("p", "o", center, 0f, 1L);
    altar.getCorrupted().add(new BloodAltar.CorruptedBlock(far.getX(), far.getY() - 1, far.getZ(), "minecraft:stone", "NETHERRACK"));
    config(String.join("\n", "corruption:", "  levels:", "    0: { radius: 3, max_blocks: 40 }",
        "  decorate:", "    - { min_level: 0, at: floor, to: [RED_CANDLE] }", ""));
    for (int i = 0; i < 30; i++) {
      AltarCorruption.spread(altar, 1);
    }
    for (BloodAltar.CorruptedBlock entry : altar.getCorrupted()) {
      if (entry.placed().equals("RED_CANDLE")) {
        assertTrue(AltarCorruption.candleSpot(fx.world.getBlockAt(entry.x(), entry.y(), entry.z()), center), entry.toString());
      }
    }
  }

  @Test
  void pedestalsAndTheAltarAreNeverDecorated() {
    Block center = room();
    CorruptionConfig.Rule floor = new CorruptionConfig.Rule(0, java.util.Set.of(), CorruptionConfig.Placement.FLOOR,
        java.util.List.of(Material.RED_CANDLE));
    CorruptionConfig.Rule ceiling = new CorruptionConfig.Rule(0, java.util.Set.of(), CorruptionConfig.Placement.CEILING,
        java.util.List.of(Material.WEEPING_VINES));
    Block onStone = center.getRelative(2, 0, 0);
    assertTrue(AltarCorruption.fits(floor, onStone));
    Block pedestal = center.getRelative(3, 0, 0);
    pedestal.setType(Material.BARRIER);
    assertFalse(AltarCorruption.fits(floor, pedestal.getRelative(0, 1, 0)));
    Block under = center.getRelative(-2, 2, 0);
    assertTrue(AltarCorruption.fits(ceiling, under));
    under.getRelative(0, 1, 0).setType(Material.BARRIER);
    assertFalse(AltarCorruption.fits(ceiling, under));
  }

  @Test
  void reconvertingKeepsTheFirstOriginal() throws Exception {
    config(String.join("\n", "corruption:", "  levels:", "    0: { radius: 1, max_blocks: 200 }",
        "  convert:", "    - { min_level: 0, from: [STONE, NETHERRACK], to: [NETHERRACK] }", ""));
    Block center = room();
    BloodAltar altar = BloodAltar.create("c", "o", center, 0f, 1L);
    for (int i = 0; i < 40; i++) {
      AltarCorruption.spread(altar, 1);
    }
    long distinct = altar.getCorrupted().stream().map(e -> e.x() + "," + e.y() + "," + e.z()).distinct().count();
    assertEquals(distinct, altar.getCorrupted().size());
    assertTrue(altar.getCorrupted().stream().allMatch(e -> e.original().contains("stone")));
  }

  @Test
  void nothingHappensWithoutRulesLoadedChunksOrRoom() throws Exception {
    Block center = room();
    BloodAltar altar = BloodAltar.create("c", "o", center, 0f, 1L);
    assertEquals(0, AltarCorruption.spread(altar, 3));
    config(String.join("\n", "corruption:", "  convert:", "    - { min_level: 0, from: [GOLD_BLOCK], to: [NETHERRACK] }", ""));
    assertEquals(0, AltarCorruption.spread(altar, 3));
    BloodAltar lost = BloodAltar.create("c", "o", center, 0f, 1L);
    var world = BloodAltar.class.getDeclaredField("world");
    world.setAccessible(true);
    world.set(lost, "gone");
    assertEquals(0, AltarCorruption.spread(lost, 3));
    lost.getCorrupted().add(new BloodAltar.CorruptedBlock(0, 0, 0, "minecraft:stone", "STONE"));
    AltarCorruption.restore(lost);
    assertTrue(lost.getCorrupted().isEmpty());
    BloodAltar far = BloodAltar.create("c", "o", fx.world.getBlockAt(5000, 10, 5000), 0f, 1L);
    fx.world.unloadChunk(5000 >> 4, 5000 >> 4);
    assertEquals(0, AltarCorruption.spread(far, 3));
    var corrupted = BloodAltar.class.getDeclaredField("corrupted");
    corrupted.setAccessible(true);
    corrupted.set(far, null);
    assertTrue(far.getCorrupted().isEmpty());
  }

  @Test
  void edgeOfLoadedChunksIsLeftAlone() throws Exception {
    config(String.join("\n", "corruption:", "  levels:", "    0: { radius: 3, max_blocks: 40 }",
        "  decorate:", "    - { min_level: 0, at: floor, to: [COBWEB] }", ""));
    Block center = room();
    fx.world.unloadChunk(-1, 0);
    fx.world.unloadChunk(-1, -1);
    fx.world.unloadChunk(0, -1);
    BloodAltar altar = BloodAltar.create("c", "o", center, 0f, 1L);
    AltarCorruption.spread(altar, 30);
    assertTrue(altar.getCorrupted().stream().allMatch(e -> e.x() >= 0 && e.z() >= 0));
  }

  @Test
  void ambientSpreadWaitsItsTurnAndSavesOnlyOnChange() throws Exception {
    config(String.join("\n", "corruption:", "  ambient_every_minutes: 30", "  levels:",
        "    0: { radius: 3, max_blocks: 2 }", "  convert:", "    - { min_level: 0, from: [STONE], to: [NETHERRACK] }", ""));
    assertEquals(1, CorruptionConfig.ambientBlocks());
    Block center = room();
    BloodAltar altar = BloodAltar.create("c", "o", center, 0f, 1L);
    BloodAltars.add(altar);
    AltarCorruption.ambient();
    assertEquals(1, altar.getCorrupted().size());
    assertEquals(now.get(), altar.getLastSpreadAt());
    AltarCorruption.ambient();
    assertEquals(1, altar.getCorrupted().size());
    now.addAndGet(31 * 60_000L);
    AltarCorruption.ambient();
    now.addAndGet(31 * 60_000L);
    AltarCorruption.ambient();
    assertEquals(2, altar.getCorrupted().size());
  }

  @Test
  void configsParseAndRejectBadEntries() throws Exception {
    config(String.join("\n", "animal:", "  species_required: 0", "  cooldown_hours: -1", "  bind_range: 0",
        "  bind_seconds: 0", "  species: [COW, UNICORN]", "  trace:", "    hours: -2", "  note_delay_minutes: -1",
        "corruption:", "  levels:", "    x: { radius: 1 }", "  blocks_per_sacrifice: -1", "  ambient_every_minutes: 0",
        "  convert:", "    - { from: [NOT_A_BLOCK], to: [STONE] }", "    - { min_level: 1, from: [STONE], to: [DIAMOND] }",
        "    - { min_level: 2, from: [STONE], to: [DIRT] }", "  decorate:", "    - { to: 'notalist' }",
        "    - { at: ceiling, to: [WEEPING_VINES] }", ""));
    assertEquals(1, AnimalConfig.speciesRequired());
    assertEquals(0, AnimalConfig.cooldownMillis());
    assertEquals(1, AnimalConfig.bindRange());
    assertEquals(1, AnimalConfig.bindSeconds());
    assertTrue(AnimalConfig.isSpecies(org.bukkit.entity.EntityType.COW));
    assertEquals(0, AnimalConfig.traceMillis());
    assertEquals(0, AnimalConfig.noteDelayMillis());
    assertEquals("v.redstone", AnimalConfig.traceModel());
    assertTrue(AnimalConfig.requireStolen());
    assertTrue(AnimalConfig.clearDrops());
    assertEquals(new CorruptionConfig.Reach(2, 4), CorruptionConfig.reach(9));
    assertEquals(0, CorruptionConfig.blocksPerSacrifice());
    assertEquals(1, CorruptionConfig.ambientEveryMinutes());
    assertEquals(1, CorruptionConfig.rules(0).size());
    assertEquals(CorruptionConfig.Placement.CEILING, CorruptionConfig.rules(0).get(0).on());
    assertEquals(2, CorruptionConfig.rules(2).size());

    config("corruption:\n  levels:\n    3: { radius: 5, max_blocks: 9 }\n");
    assertEquals(new CorruptionConfig.Reach(5, 9), CorruptionConfig.reach(1));
    assertEquals(new CorruptionConfig.Reach(5, 9), CorruptionConfig.reach(4));
    config("enabled: true\n");
    assertEquals(new CorruptionConfig.Reach(2, 4), CorruptionConfig.reach(0));
    assertTrue(CorruptionConfig.rules(5).isEmpty());

    assertTrue(BloodConfig.load(new File("src/main/resources/wildmagic/bloodmagic"), fx.logger));
    assertEquals(7, AnimalConfig.speciesRequired());
    assertEquals(24 * 3_600_000L, AnimalConfig.cooldownMillis());
    assertEquals(new CorruptionConfig.Reach(3, 14), CorruptionConfig.reach(2));
    assertFalse(CorruptionConfig.rules(4).isEmpty());
  }

  @Test
  void husbandryOwnersComeFromCookingOrNobody() {
    UUID animal = UUID.randomUUID();
    assertTrue(HusbandryOwners.of(animal).isEmpty());
    FakeHusbandry.use();
    UUID account = UUID.randomUUID();
    FakeHusbandry.OWNERS.put(animal, List.of(new FakeHusbandry.Owner(account, null),
        new FakeHusbandry.Owner(null, UUID.randomUUID())));
    assertEquals(List.of(new HusbandryOwners.Owner(account, null)), HusbandryOwners.of(animal));
    HusbandryOwners.serviceClass = FakeHusbandry.Broken.class.getName();
    assertTrue(HusbandryOwners.of(animal).isEmpty());
    FakeHusbandry.restore();
  }

  @Test
  void stolenAnimalsLeaveATraceAndNotesThatArriveLater() {
    PlayerMock keeper = fx.player("keeper");
    String keeperChar = BloodFixture.id("keeper");
    Cow cow = fx.world.spawn(new Location(fx.world, 0.5, 10, -20.5), Cow.class);
    cow.setCustomName("Bessie");
    StolenClues.leashed(cow);
    assertTrue(StolenClues.remembers(cow.getUniqueId()));
    Location altar = new Location(fx.world, 20.5, 10.5, -0.5);
    StolenClues.leave(cow, List.of(new HusbandryOwners.Owner(keeper.getUniqueId(), UUID.fromString(keeperChar)),
        new HusbandryOwners.Owner(UUID.randomUUID(), null)), altar);
    assertFalse(StolenClues.remembers(cow.getUniqueId()));
    List<ItemDisplay> traces = fx.world.getEntitiesByClass(ItemDisplay.class).stream()
        .filter(d -> d.getPersistentDataContainer().has(StolenClues.traceKey(), PersistentDataType.LONG)).toList();
    assertEquals(1, traces.size());
    StolenClues.tick();
    assertTrue(traces.get(0).isValid());
    assertEquals(2, BloodNotes.all().size());
    assertEquals("blood.animal.note", BloodNotes.all().get(0).text());

    BloodNotes.tick(now.get());
    assertNull(keeper.nextMessage());
    BloodNotes.tick(now.get() + 31 * 60_000L);
    assertEquals("blood.animal.note", keeper.nextMessage());
    assertEquals(1, BloodNotes.all().size());

    Cow wild = fx.world.spawn(new Location(fx.world, 0.5, 10, 0.5), Cow.class);
    StolenClues.leave(wild, List.of(new HusbandryOwners.Owner(keeper.getUniqueId(), null)), altar);
    assertEquals("blood.animal.note_lost", BloodNotes.all().get(1).text());
    BloodNotes.deliver(keeper, "another", now.get(), true);
    assertEquals("blood.animal.note_lost", keeper.nextMessage());
    BloodNotes.deliver(keeper, "another", now.get(), true);
    assertNull(keeper.nextMessage());
    BloodNotes.add(new BloodNotes.Note(keeper.getUniqueId().toString(), "someone-else", "not yours", 0));
    BloodNotes.deliver(keeper, "another", now.get(), true);
    assertNull(keeper.nextMessage());

    Cow travelled = fx.world.spawn(new Location(fx.world, 0.5, 10, 0.5), Cow.class);
    StolenClues.leashed(travelled);
    Location elsewhere = new Location(fx.server.addSimpleWorld("far"), 0, 64, 0);
    StolenClues.leave(travelled, List.of(), elsewhere);

    now.addAndGet(7 * 3_600_000L);
    StolenClues.tick();
    assertFalse(traces.get(0).isValid());
    Cow forgotten = fx.world.spawn(new Location(fx.world, 0.5, 10, 0.5), Cow.class);
    StolenClues.leashed(forgotten);
    StolenClues.tick();
    assertTrue(StolenClues.remembers(forgotten.getUniqueId()));
    now.addAndGet(2 * 3_600_000L);
    StolenClues.tick();
    assertFalse(StolenClues.remembers(forgotten.getUniqueId()));
  }

  @Test
  void compassPointsFollowMinecraftAxes() {
    Location origin = new Location(fx.world, 0, 0, 0);
    assertEquals("south", StolenClues.direction(origin, new Location(fx.world, 0, 0, 10)));
    assertEquals("north", StolenClues.direction(origin, new Location(fx.world, 0, 0, -10)));
    assertEquals("east", StolenClues.direction(origin, new Location(fx.world, 10, 0, 0)));
    assertEquals("west", StolenClues.direction(origin, new Location(fx.world, -10, 0, 0)));
    assertEquals("northeast", StolenClues.direction(origin, new Location(fx.world, 10, 0, -10)));
    assertEquals("southwest", StolenClues.direction(origin, new Location(fx.world, -10, 0, 10)));
  }

  @Test
  void notesSurviveARestartAndBadEntriesAreDropped() throws Exception {
    BloodStore store = new BloodStore(temp.resolve("n").toFile(), fx.logger);
    BloodNotes.init(store);
    assertTrue(BloodNotes.all().isEmpty());
    BloodNotes.add(new BloodNotes.Note(UUID.randomUUID().toString(), null, "hi", 0));
    Path file = temp.resolve("n/notes.json");
    Files.writeString(file, Files.readString(file).replaceFirst("\\[", "[null, {\"text\":\"x\"}, {\"account\":\"a\"},"));
    BloodNotes.init(store);
    assertEquals(1, BloodNotes.all().size());
    Files.writeString(file, "[");
    BloodNotes.init(store);
    assertTrue(BloodNotes.all().isEmpty());
    BloodNotes.tick(0);
    PlayerMock nobody = fx.server.addPlayer();
    BloodNotes.add(new BloodNotes.Note(nobody.getUniqueId().toString(), null, "lost", 0));
    BloodNotes.tick(0);
    assertNull(nobody.nextMessage());
    BloodNotes.clear();
    BloodNotes.add(new BloodNotes.Note("x", null, "unsaved", 0));
    assertEquals(1, BloodNotes.all().size());
  }
}
