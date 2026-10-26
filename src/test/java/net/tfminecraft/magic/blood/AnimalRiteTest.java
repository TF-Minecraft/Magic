package net.tfminecraft.magic.blood;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.damage.DamageSource;
import org.bukkit.damage.DamageType;
import org.bukkit.entity.Cow;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Pig;
import org.bukkit.entity.Sheep;
import org.bukkit.entity.Zombie;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

class AnimalRiteTest {
  @TempDir Path temp;
  BloodFixture fx;
  final AtomicLong now = new AtomicLong(10_000_000_000L);
  PlayerMock mage;
  String id;
  BloodRecord record;
  BloodAltar altar;
  Block target;

  @BeforeEach
  void setup() {
    fx = new BloodFixture(temp);
    BloodConfig.load(new File("src/main/resources/wildmagic/bloodmagic"), fx.logger);
    AnimalRiteService.clock = now::get;
    StolenClues.clock = now::get;
    FakeHusbandry.use();
    mage = fx.player("mage");
    id = BloodFixture.id("mage");
    record = fx.record("mage", BloodStage.ANIMAL);
    target = fx.room(0, 10, 0);
    AltarService.place(mage, BloodItems.create(BloodItemKind.ALTAR, id), target);
    altar = BloodAltars.ofCharacter(id);
    mage.getInventory().setItemInMainHand(BloodItems.create(BloodItemKind.RITUAL_KNIFE, id));
  }

  @AfterEach
  void cleanup() {
    AnimalRiteService.releaseAll();
    StolenClues.clear();
    BloodNotes.clear();
    FakeHusbandry.restore();
    AnimalRiteService.clock = System::currentTimeMillis;
    StolenClues.clock = System::currentTimeMillis;
    fx.close();
  }

  <T extends LivingEntity> T animal(Class<T> type, UUID ownerAccount) {
    T animal = fx.world.spawn(new Location(fx.world, 1.5, 10, 0.5), type);
    if (ownerAccount != null) {
      FakeHusbandry.OWNERS.put(animal.getUniqueId(),
          List.of(new FakeHusbandry.Owner(ownerAccount, UUID.randomUUID())));
    }
    return animal;
  }

  org.bukkit.event.entity.EntityDamageByEntityEvent hit(org.bukkit.entity.Entity damager, LivingEntity animal) {
    return new org.bukkit.event.entity.EntityDamageByEntityEvent(damager, animal,
        org.bukkit.event.entity.EntityDamageEvent.DamageCause.ENTITY_ATTACK, DamageSource.builder(DamageType.PLAYER_ATTACK).build(), 3);
  }

  /** The mage's knife makes the cut. */
  void cut(LivingEntity animal) {
    var stroke = hit(mage, animal);
    AnimalRiteService.onHit(stroke);
    assertFalse(stroke.isCancelled());
    assertEquals("blood.animal.cut", mage.nextMessage());
  }

  /** The animal bleeds out with its mage beside the altar; the halfway line is read here. */
  void bleedOut() {
    for (int second = 0; second <= AnimalConfig.bleedSeconds(); second++) {
      AnimalRiteService.tick();
      if (second * 2 == AnimalConfig.bleedSeconds() + 1 || second * 2 == AnimalConfig.bleedSeconds()) {
        assertEquals("blood.animal.draining", mage.nextMessage());
      }
      now.addAndGet(1000L);
    }
  }

  EntityDeathEvent death(LivingEntity animal, PlayerMock killer) {
    if (killer != null) {
      ((org.mockbukkit.mockbukkit.entity.LivingEntityMock) animal).setKiller(killer);
    }
    List<ItemStack> drops = new ArrayList<>(List.of(new ItemStack(Material.BEEF)));
    return new EntityDeathEvent(animal, DamageSource.builder(DamageType.GENERIC).build(), drops, 5);
  }

  @Test
  void animalsAreTaggedSoTheyAlwaysWakeAndCannotBeTakenTwice() throws Exception {
    UUID keeper = UUID.randomUUID();
    Cow cow = animal(Cow.class, keeper);
    assertEquals(AnimalRiteService.Bind.OK, AnimalRiteService.bind(mage, cow));
    assertTrue(cow.getPersistentDataContainer().has(AnimalRiteService.boundKey()));
    PlayerMock rival = fx.player("rival");
    String rivalId = BloodFixture.id("rival");
    fx.record("rival", BloodStage.ANIMAL);
    BloodAltars.add(BloodAltar.create(rivalId, rival.getUniqueId().toString(), fx.pocket(2, 10, 0), 0f, 1L));
    rival.getInventory().setItemInMainHand(BloodItems.create(BloodItemKind.RITUAL_KNIFE, rivalId));
    assertEquals(AnimalRiteService.Bind.BUSY, AnimalRiteService.bind(rival, cow));

    AnimalRiteService.wakeLoaded(List.of(cow, fx.world.spawn(new Location(fx.world, 1, 10, 1),
        org.bukkit.entity.ItemDisplay.class), animal(Cow.class, null)));
    assertFalse(cow.hasAI());
    AnimalRiteService.releaseAll();
    assertTrue(cow.hasAI());
    assertFalse(cow.getPersistentDataContainer().has(AnimalRiteService.boundKey()));

    Cow stale = animal(Cow.class, keeper);
    stale.setAI(false);
    stale.getPersistentDataContainer().set(AnimalRiteService.boundKey(),
        org.bukkit.persistence.PersistentDataType.LONG, now.get() + 5000);
    AnimalRiteService.wakeLoaded(List.of(stale));
    assertTrue(stale.hasAI());

    Sheep sheep = animal(Sheep.class, keeper);
    assertEquals(AnimalRiteService.Bind.OK, AnimalRiteService.bind(mage, sheep));
    now.addAndGet(21_000L);
    AnimalRiteService.wakeLoaded(List.of(sheep));
    assertTrue(sheep.hasAI());
    assertFalse(AnimalRiteService.isBound(sheep.getUniqueId()));

    Pig pig = spy(animal(Pig.class, keeper));
    doReturn(false).when(pig).teleport(any(Location.class));
    assertEquals(AnimalRiteService.Bind.TOO_FAR, AnimalRiteService.bind(mage, pig));
    assertTrue(pig.hasAI());

    java.nio.file.Files.writeString(temp.resolve("off.yml"), "enabled: false\n");
    BloodConfig.load(temp.resolve("off.yml").toFile(), fx.logger);
    assertEquals(AnimalRiteService.Bind.NOT_STAGE, AnimalRiteService.bind(mage, animal(Cow.class, keeper)));
  }

  @Test
  void bindingChecksStageKnifeAltarSpeciesCooldownOwnerAndRange() {
    UUID stranger = UUID.randomUUID();
    Cow cow = animal(Cow.class, stranger);
    record.setStage(BloodStage.CHOICE);
    assertEquals(AnimalRiteService.Bind.NOT_STAGE, AnimalRiteService.bind(mage, cow));
    record.setStage(BloodStage.ANIMAL);
    mage.getInventory().setItemInMainHand(BloodItems.create(BloodItemKind.RITUAL_KNIFE, "someone"));
    assertEquals(AnimalRiteService.Bind.NOT_KNIFE, AnimalRiteService.bind(mage, cow));
    mage.getInventory().setItemInMainHand(new ItemStack(Material.IRON_SWORD));
    assertEquals(AnimalRiteService.Bind.NOT_KNIFE, AnimalRiteService.bind(mage, cow));
    mage.getInventory().setItemInMainHand(BloodItems.create(BloodItemKind.RITUAL_KNIFE, id));
    BloodAltars.remove(altar);
    assertEquals(AnimalRiteService.Bind.NO_ALTAR, AnimalRiteService.bind(mage, cow));
    BloodAltars.add(altar);
    assertEquals(AnimalRiteService.Bind.NOT_SPECIES, AnimalRiteService.bind(mage, animal(Zombie.class, stranger)));
    record.addSpecies("cow");
    assertEquals(AnimalRiteService.Bind.KNOWN, AnimalRiteService.bind(mage, cow));
    record.getSpecies().clear();
    record.setLastAnimalAt(now.get() - 3_600_000L);
    assertEquals(AnimalRiteService.Bind.COOLDOWN, AnimalRiteService.bind(mage, cow));
    record.setLastAnimalAt(now.get() - 25 * 3_600_000L);
    assertEquals(AnimalRiteService.Bind.NOT_STOLEN, AnimalRiteService.bind(mage, animal(Pig.class, null)));
    assertEquals(AnimalRiteService.Bind.NOT_STOLEN, AnimalRiteService.bind(mage, animal(Pig.class, mage.getUniqueId())));
    Cow far = animal(Cow.class, stranger);
    far.teleport(new Location(fx.world, 30, 10, 30));
    assertEquals(AnimalRiteService.Bind.TOO_FAR, AnimalRiteService.bind(mage, far));
    var otherWorld = fx.server.addSimpleWorld("elsewhere");
    far.teleport(new Location(otherWorld, 0, 64, 0));
    assertEquals(AnimalRiteService.Bind.TOO_FAR, AnimalRiteService.bind(mage, far));

    assertEquals(AnimalRiteService.Bind.OK, AnimalRiteService.bind(mage, cow));
    assertFalse(cow.hasAI());
    assertTrue(AnimalRiteService.isBound(cow.getUniqueId()));
    assertEquals(11.0, cow.getLocation().getY(), 0.01);
    assertEquals(AnimalRiteService.Bind.BUSY, AnimalRiteService.bind(mage, animal(Pig.class, stranger)));
  }

  @Test
  void wildAnimalsCountWhenTheConfigDoesNotRequireTheft() throws Exception {
    Files.writeString(temp.resolve("w.yml"), "enabled: true\nanimal:\n  require_stolen: false\n  species: [PIG]\n");
    BloodConfig.load(temp.resolve("w.yml").toFile(), fx.logger);
    Pig pig = animal(Pig.class, null);
    assertEquals(AnimalRiteService.Bind.OK, AnimalRiteService.bind(mage, pig));
    cut(pig);
    bleedOut();
    assertTrue(record.hasSpecies("pig"));
    assertTrue(BloodNotes.all().isEmpty());
  }

  @Test
  void onlyTheOwnKnifeCutsAndNothingElseTouchesIt() {
    Cow cow = animal(Cow.class, null);
    PlayerMock other = fx.player("other");
    org.bukkit.entity.Zombie zombie = fx.world.spawn(new Location(fx.world, 1, 10, 1), org.bukkit.entity.Zombie.class);
    var loose = hit(mage, cow);
    AnimalRiteService.onHit(loose);
    assertEquals(3, loose.getDamage());
    assertFalse(loose.isCancelled());

    record.setLastAnimalAt(0);
    FakeHusbandry.OWNERS.put(cow.getUniqueId(), List.of(new FakeHusbandry.Owner(UUID.randomUUID(), UUID.randomUUID())));
    assertEquals(AnimalRiteService.Bind.OK, AnimalRiteService.bind(mage, cow));
    for (org.bukkit.entity.Entity damager : List.of(other, zombie)) {
      var foreign = hit(damager, cow);
      AnimalRiteService.onHit(foreign);
      assertTrue(foreign.isCancelled());
    }
    mage.getInventory().setItemInMainHand(new ItemStack(Material.IRON_SWORD));
    var plain = hit(mage, cow);
    AnimalRiteService.onHit(plain);
    assertTrue(plain.isCancelled());
    mage.getInventory().setItemInMainHand(BloodItems.create(BloodItemKind.RITUAL_KNIFE, BloodFixture.id("other")));
    var borrowed = hit(mage, cow);
    AnimalRiteService.onHit(borrowed);
    assertTrue(borrowed.isCancelled());

    mage.getInventory().setItemInMainHand(BloodItems.create(BloodItemKind.RITUAL_KNIFE, id));
    var stroke = hit(mage, cow);
    new BloodListener().onAnimalHit(stroke);
    assertFalse(stroke.isCancelled());
    assertTrue(stroke.getDamage() < 1);
    assertEquals("blood.animal.cut", mage.nextMessage());
    assertTrue(AnimalRiteService.isBound(cow.getUniqueId()));
    var again = hit(mage, cow);
    AnimalRiteService.onHit(again);
    assertTrue(again.isCancelled());

    var onPlayer = hit(mage, other);
    AnimalRiteService.onHit(onPlayer);
    assertEquals(3, onPlayer.getDamage());
  }

  @Test
  void aLedAnimalLosesItsLeadToTheMage() {
    // MockBukkit cannot leash; the cow says it is led.
    Cow cow = spy(animal(Cow.class, UUID.randomUUID()));
    doReturn(true).when(cow).isLeashed();
    assertFalse(mage.getInventory().contains(Material.LEAD));
    assertEquals(AnimalRiteService.Bind.OK, AnimalRiteService.bind(mage, cow));
    org.mockito.Mockito.verify(cow).setLeashHolder(null);
    assertTrue(mage.getInventory().contains(Material.LEAD));
  }

  @Test
  void theRiteMusicIsConfigurable() throws Exception {
    assertEquals("tfmc_blood:rite.bleed", AnimalConfig.music());
    assertEquals(org.bukkit.SoundCategory.RECORDS, AnimalConfig.musicCategory());
    assertEquals(0.8f, AnimalConfig.musicVolume());
    Files.writeString(temp.resolve("m.yml"), String.join("\n", "enabled: true", "animal:", "  rite:", "    music:",
        "      sound: ' '", "      category: nope", "      volume: -2", ""));
    BloodConfig.load(temp.resolve("m.yml").toFile(), fx.logger);
    assertEquals("", AnimalConfig.music());
    assertEquals(org.bukkit.SoundCategory.RECORDS, AnimalConfig.musicCategory());
    assertEquals(0f, AnimalConfig.musicVolume());
    Files.writeString(temp.resolve("m2.yml"), String.join("\n", "enabled: true", "animal:", "  rite:", "    music:",
        "      sound: x:y", "      category: ambient", ""));
    BloodConfig.load(temp.resolve("m2.yml").toFile(), fx.logger);
    assertEquals(org.bukkit.SoundCategory.AMBIENT, AnimalConfig.musicCategory());
    // Without music nothing is played or stopped.
    Files.writeString(temp.resolve("m3.yml"), "enabled: true\nanimal:\n  require_stolen: false\n  species: [COW]\n");
    BloodConfig.load(temp.resolve("m3.yml").toFile(), fx.logger);
    Cow cow = animal(Cow.class, null);
    AnimalRiteService.bind(mage, cow);
    cut(cow);
    bleedOut();
    assertTrue(record.hasSpecies("cow"));
  }

  @Test
  void steppingAwaySwitchingOrLeavingSpillsIt() {
    UUID keeper = UUID.randomUUID();
    Cow cow = animal(Cow.class, keeper);
    AnimalRiteService.bind(mage, cow);
    cut(cow);
    AnimalRiteService.tick();
    mage.teleport(new Location(fx.world, 9.5, 10, 0.5));
    AnimalRiteService.tick();
    assertEquals("blood.animal.left", mage.nextMessage());
    assertFalse(AnimalRiteService.isBound(cow.getUniqueId()));
    assertTrue(cow.getPersistentDataContainer().has(AnimalRiteService.drainedKey()));
    assertTrue(record.getSpecies().isEmpty());

    mage.teleport(new Location(fx.world, 0.5, 11, 0.5));
    Cow second = animal(Cow.class, keeper);
    AnimalRiteService.bind(mage, second);
    cut(second);
    fx.characters.start(mage, "switched");
    AnimalRiteService.tick();
    assertEquals("blood.animal.left", mage.nextMessage());
    fx.characters.start(mage, "mage");

    Cow third = animal(Cow.class, keeper);
    AnimalRiteService.bind(mage, third);
    cut(third);
    var otherWorld = fx.server.addSimpleWorld("elsewhere");
    mage.teleport(new Location(otherWorld, 0, 64, 0));
    AnimalRiteService.tick();
    assertEquals("blood.animal.left", mage.nextMessage());
    mage.teleport(new Location(fx.world, 0.5, 11, 0.5));

    Cow fourth = animal(Cow.class, keeper);
    AnimalRiteService.bind(mage, fourth);
    cut(fourth);
    AnimalRiteService.releaseFor(mage.getUniqueId());
    assertTrue(fourth.getPersistentDataContainer().has(AnimalRiteService.drainedKey()));

    Cow fifth = animal(Cow.class, keeper);
    AnimalRiteService.bind(mage, fifth);
    cut(fifth);
    fifth.remove();
    AnimalRiteService.tick();
    assertFalse(AnimalRiteService.isBound(fifth.getUniqueId()));

    Cow sixth = animal(Cow.class, keeper);
    AnimalRiteService.bind(mage, sixth);
    cut(sixth);
    mage.disconnect();
    AnimalRiteService.tick();
    assertFalse(AnimalRiteService.isBound(sixth.getUniqueId()));
    assertTrue(record.getSpecies().isEmpty());
  }

  @Test
  void theOwnKnifeKillCountsLeavesCluesAndCorrupts() {
    UUID keeper = UUID.randomUUID();
    Cow cow = animal(Cow.class, keeper);
    StolenClues.leashed(cow);
    cow.teleport(new Location(fx.world, 1.5, 10, 0.5));
    assertEquals(AnimalRiteService.Bind.OK, AnimalRiteService.bind(mage, cow));
    cut(cow);
    AnimalRiteService.tick();
    assertFalse(record.hasSpecies("cow"));
    now.addAndGet(8000L);
    AnimalRiteService.tick();
    assertEquals("blood.animal.draining", mage.nextMessage());
    AnimalRiteService.tick();
    assertNull(mage.nextMessage());
    now.addAndGet(AnimalConfig.bleedSeconds() * 1000L - 8000L);
    AnimalRiteService.tick();
    assertTrue(record.hasSpecies("cow"));
    assertEquals(now.get(), record.getLastAnimalAt());
    assertEquals("blood.animal.accepted", mage.nextMessage());
    assertTrue(cow.getPersistentDataContainer().has(AnimalRiteService.drainedKey()));
    EntityDeathEvent event = death(cow, null);
    AnimalRiteService.onDeath(event);
    assertTrue(event.getDrops().isEmpty());
    assertEquals(0, event.getDroppedExp());
    assertEquals(1, BloodNotes.all().size());
    assertEquals(keeper.toString(), BloodNotes.all().get(0).account());
    assertFalse(AnimalRiteService.isBound(cow.getUniqueId()));
    AnimalRiteService.onDeath(death(cow, mage));
  }

  @Test
  void theSeventhKindFinishesTheStageAndGivesTheBloodKnife() throws Exception {
    Files.writeString(temp.resolve("one.yml"), "enabled: true\nanimal:\n  species_required: 1\n  clear_drops: false\n  species: [COW]\n");
    BloodConfig.load(temp.resolve("one.yml").toFile(), fx.logger);
    Cow cow = animal(Cow.class, UUID.randomUUID());
    AnimalRiteService.bind(mage, cow);
    cut(cow);
    bleedOut();
    EntityDeathEvent event = death(cow, null);
    AnimalRiteService.onDeath(event);
    assertFalse(event.getDrops().isEmpty());
    assertEquals(BloodStage.CHOICE, record.getStage());
    assertTrue(mage.getInventory().containsAtLeast(BloodItems.create(BloodItemKind.BLOOD_KNIFE, id), 1));
    assertEquals("blood.animal.complete", mage.nextMessage());
    assertNotNull(WhisperService.nextVoice(mage.getUniqueId()));
    WhisperService.clear();
  }

  @Test
  void anyOtherDeathSpoilsTheRite() {
    UUID keeper = UUID.randomUUID();
    Cow cow = animal(Cow.class, keeper);
    AnimalRiteService.bind(mage, cow);
    AnimalRiteService.onDeath(death(cow, null));
    assertEquals("blood.animal.spoiled", mage.nextMessage());
    assertFalse(record.hasSpecies("cow"));

    PlayerMock other = fx.player("other");
    cow = animal(Cow.class, keeper);
    AnimalRiteService.bind(mage, cow);
    AnimalRiteService.onDeath(death(cow, other));
    assertEquals("blood.animal.spoiled", mage.nextMessage());

    cow = animal(Cow.class, keeper);
    AnimalRiteService.bind(mage, cow);
    mage.getInventory().setItemInMainHand(new ItemStack(Material.STICK));
    AnimalRiteService.onDeath(death(cow, mage));
    assertEquals("blood.animal.spoiled", mage.nextMessage());

    mage.getInventory().setItemInMainHand(BloodItems.create(BloodItemKind.RITUAL_KNIFE, id));
    cow = animal(Cow.class, keeper);
    AnimalRiteService.bind(mage, cow);
    mage.getInventory().setItemInMainHand(BloodItems.create(BloodItemKind.RITUAL_KNIFE, "x"));
    AnimalRiteService.onDeath(death(cow, mage));
    assertEquals("blood.animal.spoiled", mage.nextMessage());

    mage.getInventory().setItemInMainHand(BloodItems.create(BloodItemKind.RITUAL_KNIFE, id));
    cow = animal(Cow.class, keeper);
    AnimalRiteService.bind(mage, cow);
    fx.characters.start(mage, "switched");
    AnimalRiteService.onDeath(death(cow, mage));
    assertEquals("blood.animal.spoiled", mage.nextMessage());
    fx.characters.start(mage, "mage");

    cow = animal(Cow.class, keeper);
    AnimalRiteService.bind(mage, cow);
    mage.disconnect();
    AnimalRiteService.onDeath(death(cow, null));
    assertTrue(record.getSpecies().isEmpty());
  }

  @Test
  void completionNeedsTheRecordAndAltarStill() {
    UUID keeper = UUID.randomUUID();
    Cow cow = animal(Cow.class, keeper);
    AnimalRiteService.bind(mage, cow);
    cut(cow);
    record.setStage(BloodStage.CHOICE);
    bleedOut();
    assertTrue(record.getSpecies().isEmpty());
    record.setStage(BloodStage.ANIMAL);
    cow = animal(Cow.class, keeper);
    AnimalRiteService.bind(mage, cow);
    cut(cow);
    BloodAltars.remove(altar);
    AnimalRiteService.tick();
    assertEquals("blood.animal.left", mage.nextMessage());
    assertTrue(record.getSpecies().isEmpty());
    BloodAltars.add(altar);
    cow = animal(Cow.class, keeper);
    AnimalRiteService.bind(mage, cow);
    cut(cow);
    BloodRecords.delete(id);
    bleedOut();
  }

  @Test
  void boundAnimalsTwitchBreakFreeAndAreReleased() {
    UUID keeper = UUID.randomUUID();
    Cow cow = animal(Cow.class, keeper);
    AnimalRiteService.bind(mage, cow);
    AnimalRiteService.tick();
    assertTrue(AnimalRiteService.isBound(cow.getUniqueId()));
    now.addAndGet(21_000L);
    AnimalRiteService.tick();
    assertFalse(AnimalRiteService.isBound(cow.getUniqueId()));
    assertTrue(cow.hasAI());
    assertEquals("blood.animal.escaped", mage.nextMessage());

    record.getSpecies().clear();
    Cow second = animal(Cow.class, keeper);
    AnimalRiteService.bind(mage, second);
    mage.disconnect();
    now.addAndGet(21_000L);
    AnimalRiteService.tick();
    assertTrue(second.hasAI());

    PlayerMock again = fx.player("mage");
    again.getInventory().setItemInMainHand(BloodItems.create(BloodItemKind.RITUAL_KNIFE, id));
    Cow third = animal(Cow.class, keeper);
    AnimalRiteService.bind(again, third);
    third.remove();
    AnimalRiteService.tick();
    assertTrue(AnimalRiteService.isBound(third.getUniqueId()));
    now.addAndGet(21_000L);
    AnimalRiteService.tick();
    assertFalse(AnimalRiteService.isBound(third.getUniqueId()));

    Cow fourth = animal(Cow.class, keeper);
    AnimalRiteService.bind(again, fourth);
    fourth.setHealth(0);
    AnimalRiteService.tick();
    assertFalse(AnimalRiteService.isBound(fourth.getUniqueId()));

    Cow fifth = animal(Cow.class, keeper);
    AnimalRiteService.bind(again, fifth);
    AnimalRiteService.releaseFor(UUID.randomUUID());
    assertTrue(AnimalRiteService.isBound(fifth.getUniqueId()));
    AnimalRiteService.releaseFor(again.getUniqueId());
    assertTrue(fifth.hasAI());
    Cow sixth = animal(Cow.class, keeper);
    AnimalRiteService.bind(again, sixth);
    sixth.remove();
    AnimalRiteService.releaseFor(again.getUniqueId());
    Cow seventh = animal(Cow.class, keeper);
    AnimalRiteService.bind(again, seventh);
    Cow eighth = animal(Cow.class, keeper);
    seventh.remove();
    AnimalRiteService.releaseAll();
    Cow ninth = animal(Cow.class, keeper);
    AnimalRiteService.bind(again, ninth);
    AnimalRiteService.releaseAll();
    assertTrue(ninth.hasAI());
    assertNull(AnimalRiteService.bindingOf(again.getUniqueId()));
    assertNotNull(eighth);
  }
}
