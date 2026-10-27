package net.tfminecraft.magic.blood;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.UnaryOperator;

import org.bukkit.Location;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import org.mockito.MockedStatic;

import net.tfminecraft.interactiblefurniture.furniture.Furniture;
import net.tfminecraft.interactiblefurniture.furniture.PlacedSlot;

class MeditationTest {
  @TempDir Path temp;
  BloodFixture fx;
  PlayerMock mage;
  String mageId;
  BloodRecord record;
  BloodAltar altar;
  MockedStatic<AltarPedestals> pedestals;
  final List<AltarPedestals.Shown> shown = new ArrayList<>();
  int restyled;

  @BeforeEach
  void setup() {
    fx = new BloodFixture(temp);
    BloodConfig.load(new File("src/main/resources/wildmagic/bloodmagic"), fx.logger);
    mage = fx.player("mage");
    mageId = BloodFixture.id("mage");
    record = fx.record("mage", BloodStage.FINAL);
    record.setPath(BloodPath.DEVOTEE);
    fx.open(-5, 10, -5, 5, 12, 5);
    altar = BloodAltar.create(mageId, mage.getUniqueId().toString(), fx.pocket(0, 10, 0), 0f, 1L);
    BloodAltars.add(altar);
    pedestals = mockStatic(AltarPedestals.class, CALLS_REAL_METHODS);
    pedestals.when(() -> AltarPedestals.artefacts(any())).thenAnswer(i -> List.copyOf(shown));
    pedestals.when(() -> AltarPedestals.restyle(any(), any())).thenAnswer(i -> {
      UnaryOperator<ItemStack> style = i.getArgument(1);
      for (AltarPedestals.Shown each : shown) {
        style.apply(each.item().clone());
      }
      restyled++;
      return null;
    });
  }

  @AfterEach
  void cleanup() {
    BloodMeditation.clear();
    BloodAmbience.clear();
    pedestals.close();
    fx.close();
  }

  void place(BloodRecord owner, String id, double charge) {
    owner.setCharge(id, charge);
    shown.add(new AltarPedestals.Shown(mock(Furniture.class), mock(PlacedSlot.class),
        BloodArtefacts.create(owner, id), new Location(fx.world, 3.5, 11.8, 0.5)));
  }

  void sit(PlayerMock player) {
    ArmorStand seat = fx.world.spawn(player.getLocation(), ArmorStand.class);
    seat.addPassenger(player);
  }

  void tick(int times) {
    for (int i = 0; i < times; i++) {
      BloodMeditation.tick();
    }
  }

  long drops() {
    return fx.world.getEntitiesByClass(ItemDisplay.class).stream().filter(Entity::isValid).count();
  }

  String messages(PlayerMock player) {
    StringBuilder out = new StringBuilder();
    String message;
    while ((message = player.nextMessage()) != null) {
      out.append(message).append('\n');
    }
    return out.toString();
  }

  @Test
  void onlyAMageWithChargedArtefactsOnThePedestalsCanBegin() {
    PlayerMock bare = fx.server.addPlayer();
    assertEquals(BloodMeditation.Begin.NOT_READY, BloodMeditation.start(bare, altar));
    record.setStage(BloodStage.HUNT);
    assertEquals(BloodMeditation.Begin.NOT_READY, BloodMeditation.start(mage, altar));
    record.setStage(BloodStage.FINAL);
    assertEquals(BloodMeditation.Begin.DRY, BloodMeditation.start(mage, altar));
    BloodRecord other = fx.record("other", BloodStage.FINAL);
    other.setPath(BloodPath.DEVOTEE);
    place(other, "devotee:0", 2);
    place(record, "devotee:1", 0);
    place(record, "true:hunt:0", 2);
    assertEquals(BloodMeditation.Begin.DRY, BloodMeditation.start(mage, altar),
        "Someone else's, an empty one and one of another path are no use");
    place(record, "devotee:2", 2);
    record.setResonance(20);
    assertEquals(BloodMeditation.Begin.FULL, BloodMeditation.start(mage, altar));
    record.setResonance(0);
    assertEquals(BloodMeditation.Begin.STARTED, BloodMeditation.start(mage, altar));
    assertTrue(BloodMeditation.isMeditating(mage.getUniqueId()));
    assertEquals(BloodMeditation.Begin.BUSY, BloodMeditation.start(mage, altar));
    mage.assertSoundHeard("ambient.crimson_forest.loop");
    assertFalse(BloodMeditation.swing(bare));
  }

  @Test
  void dropsRiseOnTheBeatAndCatchingThemDrawsCharge() {
    sit(mage);
    place(record, "devotee:0", 2.5);
    assertEquals(BloodMeditation.Begin.STARTED, BloodMeditation.start(mage, altar));
    tick(29);
    assertEquals(0, drops());
    tick(1);
    assertEquals(1, drops(), "The first beat sends a drop");
    mage.assertSoundHeard("entity.warden.heartbeat");
    tick(4);
    assertTrue(BloodMeditation.swing(mage), "Nothing in reach yet: the swing does nothing");
    assertEquals(0, record.getResonance());
    // Two beats later the first drop waits in front of the mage; caught between beats.
    tick(71);
    double before = mage.getHealth();
    assertTrue(BloodMeditation.swing(mage));
    assertEquals(1, record.getResonance(), "Off the beat: a little");
    assertEquals(before - 1, mage.getHealth(), "and some of the mage's own blood");
    // The second drop arrives right on a beat.
    tick(45);
    assertTrue(BloodMeditation.swing(mage));
    assertEquals(2.5, record.getResonance(), "On the beat: all that is left in it");
    assertEquals(before - 1, mage.getHealth());
    assertEquals(0, record.getCharge("devotee:0"));
    mage.setHealth(1);
    tick(45);
    assertTrue(BloodMeditation.swing(mage));
    assertEquals(1, mage.getHealth(), "Never the last point of health");
    assertEquals(2.5, record.getResonance(), "A dry artefact's drop gives nothing");
    tick(20);
    BloodRecords.delete(mageId);
    assertTrue(BloodMeditation.swing(mage));
  }

  @Test
  void itEndsWhenTheMageStandsTheArtefactsRunDryOrTheBloodIsFull() throws Exception {
    sit(mage);
    place(record, "devotee:0", 1);
    BloodMeditation.start(mage, altar);
    tick(90);
    BloodMeditation.swing(mage);
    assertEquals(1, record.getResonance());
    messages(mage);
    tick(130);
    assertFalse(BloodMeditation.isMeditating(mage.getUniqueId()));
    assertTrue(messages(mage).contains("blood.meditate.end.dry"));
    assertTrue(restyled > 0, "The drained artefacts stop glowing");

    record.setCharge("devotee:0", 2.5);
    record.setResonance(19);
    BloodMeditation.start(mage, altar);
    tick(90);
    BloodMeditation.swing(mage);
    tick(1);
    assertTrue(messages(mage).contains("blood.meditate.end.full"));
    assertEquals(20, record.getResonance());

    record.setResonance(0);
    BloodMeditation.start(mage, altar);
    tick(35);
    assertEquals(1, drops());
    mage.leaveVehicle();
    tick(1);
    assertTrue(messages(mage).contains("blood.meditate.end.stood"));
    assertEquals(0, drops(), "Its drops go with it");

    sit(mage);
    BloodMeditation.start(mage, altar);
    mage.teleport(new Location(fx.world, 4.5, 11, 0.5));
    sit(mage);
    tick(1);
    assertFalse(BloodMeditation.isMeditating(mage.getUniqueId()), "Walked off");
    mage.teleport(new Location(fx.world, 0.5, 11, 0.5));
    sit(mage);
    BloodMeditation.start(mage, altar);
    fx.characters.start(mage, "elsewhere");
    tick(1);
    assertFalse(BloodMeditation.isMeditating(mage.getUniqueId()), "Switched character");
    fx.characters.start(mage, "mage");
    var away = fx.server.addSimpleWorld("away");
    BloodMeditation.start(mage, altar);
    mage.teleport(new Location(away, 0.5, 11, 0.5));
    sit(mage);
    tick(1);
    assertFalse(BloodMeditation.isMeditating(mage.getUniqueId()), "Another world");
    mage.teleport(new Location(fx.world, 0.5, 11, 0.5));
    sit(mage);
    BloodMeditation.start(mage, altar);
    BloodRecords.delete(mageId);
    tick(1);
    assertFalse(BloodMeditation.isMeditating(mage.getUniqueId()), "Its record went");
    record = fx.record("mage", BloodStage.FINAL);
    record.setPath(BloodPath.DEVOTEE);
    record.setCharge("devotee:0", 2.5);
    BloodMeditation.start(mage, altar);
    BloodAltars.remove(altar);
    tick(1);
    assertFalse(BloodMeditation.isMeditating(mage.getUniqueId()), "Its altar went");
    BloodAltars.add(altar);
    BloodAltar lost = new com.google.gson.Gson().fromJson(
        "{\"id\":\"lost\",\"ownerCharacter\":\"" + mageId + "\",\"world\":\"nowhere\",\"x\":0,\"y\":10,\"z\":0}",
        BloodAltar.class);
    BloodAltars.add(lost);
    BloodMeditation.start(mage, lost);
    tick(1);
    assertFalse(BloodMeditation.isMeditating(mage.getUniqueId()), "Its world is not loaded");
    BloodMeditation.start(mage, altar);
    mage.disconnect();
    tick(1);
    assertFalse(BloodMeditation.isMeditating(mage.getUniqueId()), "Gone");

    PlayerMock again = fx.player("mage");
    sit(again);
    BloodMeditation.start(again, altar);
    BloodMeditation.stop(again.getUniqueId());
    assertFalse(messages(again).contains("blood.meditate.end"), "Stopped quietly");
    BloodMeditation.stop(again.getUniqueId());
    BloodMeditation.start(again, altar);
    BloodMeditation.clear();
    assertFalse(BloodMeditation.isMeditating(again.getUniqueId()));
  }

  @Test
  void dropsLeftAloneFallBackAndTheMusicLoops() throws Exception {
    sit(mage);
    place(record, "devotee:0", 2.5);
    BloodMeditation.start(mage, altar);
    tick(30);
    assertEquals(1, drops());
    tick(120);
    assertTrue(drops() <= 4);
    mage.clearSounds();
    tick(600);
    mage.assertSoundHeard("ambient.crimson_forest.loop");
    assertTrue(drops() <= 4, "Never more than drops.max at once");

    Files.writeString(temp.resolve("m.yml"), String.join("\n",
        "enabled: true",
        "pool: { worth: { devotee: { 0: 20 } } }",
        "artefacts: { devotee: [ { item: m.a.b } ] }",
        "meditation: { sounds: { music: { sound: x.y, seconds: 0 } } }",
        ""));
    BloodConfig.load(temp.resolve("m.yml").toFile(), fx.logger);
    BloodMeditation.clear();
    shown.clear();
    place(record, "devotee:0", 20);
    BloodMeditation.start(mage, altar);
    mage.clearSounds();
    tick(700);
    assertTrue(mage.getHeardSounds().stream().noneMatch(sound -> sound.getSound().equals("x.y")), "Played once");
  }

  @Test
  void theRoomHumsForItsOwnerOnlyAndHushesForMeditation() throws Exception {
    long now = 1_000_000;
    BloodAmbience.tick(now);
    mage.assertSoundHeard("ambient.soul_sand_valley.loop");
    mage.clearSounds();
    BloodAmbience.tick(now + 1000);
    assertTrue(mage.getHeardSounds().isEmpty(), "Still playing");
    BloodAmbience.tick(now + 31_000);
    mage.assertSoundHeard("ambient.soul_sand_valley.loop");
    PlayerMock stranger = fx.player("stranger");
    BloodAmbience.tick(now + 32_000);
    assertTrue(stranger.getHeardSounds().isEmpty());
    mage.teleport(new Location(fx.world, 40.5, 11, 0.5));
    BloodAmbience.tick(now + 33_000);
    BloodAmbience.tick(now + 34_000);
    mage.teleport(new Location(fx.world, 0.5, 11, 0.5));
    BloodAmbience.tick(now + 35_000);
    sit(mage);
    place(record, "devotee:0", 2.5);
    BloodMeditation.start(mage, altar);
    BloodAmbience.tick(now + 36_000);
    BloodAmbience.pause(mage);
    BloodMeditation.clear();
    BloodAmbience.tick(now + 37_000);
    BloodAmbience.forget(mage.getUniqueId());

    Files.writeString(temp.resolve("r.yml"), "enabled: true\nmeditation: { sounds: { room: { sound: r.s } } }\n");
    BloodConfig.load(temp.resolve("r.yml").toFile(), fx.logger);
    mage.clearSounds();
    BloodAmbience.tick(now + 40_000);
    BloodAmbience.tick(now + 400_000);
    assertEquals(1, mage.getHeardSounds().stream().filter(sound -> sound.getSound().equals("r.s")).count(),
        "No length: it plays once");
    Files.writeString(temp.resolve("q.yml"), "enabled: true\n");
    BloodConfig.load(temp.resolve("q.yml").toFile(), fx.logger);
    mage.clearSounds();
    BloodAmbience.tick(now + 500_000);
    assertTrue(mage.getHeardSounds().isEmpty());
    MeditationConfig.Cue.NONE.play(mage);
    MeditationConfig.Cue.NONE.stop(mage);
  }

  @Test
  void dropsBehindOrOutOfReachCannotBeCaught() {
    sit(mage);
    record.setCharge("devotee:0", 2.5);
    shown.add(new AltarPedestals.Shown(mock(Furniture.class), mock(PlacedSlot.class),
        BloodArtefacts.create(record, "devotee:0"), new Location(fx.world, 0.5, 12.6, 9.5)));
    BloodMeditation.start(mage, altar);
    tick(30);
    new BloodListener().onSwing(new org.bukkit.event.player.PlayerAnimationEvent(mage,
        org.bukkit.event.player.PlayerAnimationType.ARM_SWING));
    assertEquals(0, record.getResonance(), "Too far off");
    mage.setRotation(180, 0);
    tick(1);
    BloodMeditation.swing(mage);
    assertEquals(0, record.getResonance(), "Behind the mage");
  }

  @Test
  void sittingOnTheAltarStartsTheMeditationForItsOwner() {
    ArmorStand seat = fx.world.spawn(new Location(fx.world, 0.5, 10.5, 0.5), ArmorStand.class);
    AltarService.sat(mage, seat);
    assertTrue(messages(mage).contains("blood.meditate.start.dry"));
    record.setPath(BloodPath.NONE);
    AltarService.sat(mage, seat);
    assertFalse(messages(mage).contains("blood.meditate"));
  }
}
