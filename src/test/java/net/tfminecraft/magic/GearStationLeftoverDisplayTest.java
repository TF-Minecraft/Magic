package net.tfminecraft.magic;

import static org.junit.jupiter.api.Assertions.*;

import java.util.*;
import java.util.logging.*;
import net.tfminecraft.magic.gear.*;
import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.inventory.*;
import org.bukkit.persistence.PersistentDataType;
import org.junit.jupiter.api.*;

/** Station displays are settled once a chunk's entities have loaded. */
class GearStationLeftoverDisplayTest extends GearStationCoverageSupport {
  final List<String> logged = new ArrayList<>();
  Handler handler;

  @BeforeEach
  void captureLog() {
    handler =
        new Handler() {
          @Override
          public void publish(LogRecord record) {
            logged.add(record.getMessage());
          }

          @Override
          public void flush() {}

          @Override
          public void close() {}
        };
    Magic.plugin.getLogger().addHandler(handler);
  }

  @AfterEach
  void releaseLog() {
    Magic.plugin.getLogger().removeHandler(handler);
  }

  static ItemStack gear() {
    var weapon = item();
    tag(weapon, GearKeys.archetype(), PersistentDataType.STRING, "staff");
    return weapon;
  }

  ItemDisplay display(Location at, ItemStack item, String stationTag) {
    var display = at.getWorld().spawn(at, ItemDisplay.class);
    display.setItemStack(item);
    if (stationTag != null) {
      display
          .getPersistentDataContainer()
          .set(GearKeys.stationDisplay(), PersistentDataType.STRING, stationTag);
    }
    return display;
  }

  static Location above(Location station) {
    return station.clone().add(.5, 1.15, .5);
  }

  @Test
  void displaysOfAStationThatIsGoneAreRemovedAndOtherDisplaysStay() throws Exception {
    var location = station();
    var key = GearStationStore.key(location);
    var stand = location.getWorld().spawn(above(location), ArmorStand.class);
    var tagged = display(above(location), item(), key);
    var legacy = display(above(location), gear(), null);
    var pedestal = display(above(location), gear(), null);
    pedestal
        .getPersistentDataContainer()
        .set(new NamespacedKey("interactiblefurniture", "furniture_display"), PersistentDataType.STRING, "x");
    var plain = display(above(location), item(), null);
    var offset = display(location.clone().add(.45, 1.87, .45), gear(), null);

    GearStationStore.reconcile(location.getChunk());

    assertFalse(tagged.isValid());
    assertFalse(legacy.isValid());
    assertTrue(stand.isValid());
    assertTrue(pedestal.isValid(), "another plugin's display is left alone");
    assertTrue(plain.isValid(), "not a mage weapon");
    assertTrue(offset.isValid(), "not at a station's display spot");
    assertTrue(
        logged.contains("[Magic] Removed 2 leftover displays at gear station " + key + " (no station there)."),
        logged.toString());
  }

  @Test
  void aStationKeepsItsSavedDisplayAndLosesExtraCopies() throws Exception {
    var location = station();
    var key = GearStationStore.key(location);
    var occupancy = seed(location, item(), null, null);
    var first = (ItemDisplay) Bukkit.getEntity(displayId(occupancy));
    var copy = display(above(location), item(), key);
    var legacy = display(above(location), gear(), null);
    var saved = display(above(location), item(), key);
    setDisplayId(occupancy, saved.getUniqueId());

    GearStationStore.reconcile(location.getChunk());

    assertTrue(saved.isValid());
    assertFalse(first.isValid());
    assertFalse(copy.isValid());
    assertFalse(legacy.isValid());
    assertEquals(saved.getUniqueId(), displayId(occupancy));
    assertTrue(logged.contains("[Magic] Removed 3 extra displays at gear station " + key + "."), logged.toString());
    assertTrue(logged.stream().noneMatch(line -> line.contains("leftover")), logged.toString());
  }

  @Test
  void aStationWithoutItsDisplayAdoptsAnOlderUntaggedOne() throws Exception {
    var location = station();
    var key = GearStationStore.key(location);
    var occupancy = seed(location, item(), null, null);
    location.getWorld().getEntitiesByClass(ItemDisplay.class).forEach(Entity::remove);
    var legacy = display(above(location), gear(), null);

    GearStationStore.reconcile(location.getChunk());

    assertTrue(legacy.isValid());
    assertEquals(legacy.getUniqueId(), displayId(occupancy));
    assertEquals(
        key, legacy.getPersistentDataContainer().get(GearKeys.stationDisplay(), PersistentDataType.STRING));
    assertEquals(occupancy.getItem(), legacy.getItemStack());
    assertTrue(logged.isEmpty(), logged.toString());
    assertTrue(
        java.nio.file.Files.readString(temp.resolve("data/gear-stations.yml"))
            .contains(legacy.getUniqueId().toString()),
        "the adopted display is saved");
  }

  @Test
  void aStationStillHeldInTheSavedFileKeepsItsDisplay() throws Exception {
    var location = station();
    seed(location, item(), null, null);
    occupied().clear(); // row not loaded (held, failed to load, or before startup load)
    var display =
        location.getWorld().getEntitiesByClass(ItemDisplay.class).stream()
            .filter(Entity::isValid)
            .findFirst()
            .orElseThrow();

    GearStationStore.reconcile(location.getChunk());

    assertTrue(display.isValid());
    assertTrue(logged.isEmpty(), logged.toString());
  }

  @Test
  void anUnreadableStoreRemovesNothing() throws Exception {
    var location = station();
    var tagged = display(above(location), item(), GearStationStore.key(location));
    var file = temp.resolve("data/gear-stations.yml");
    java.nio.file.Files.createDirectories(file.getParent());
    java.nio.file.Files.writeString(file, "stations: [broken");

    GearStationStore.reconcile(location.getChunk());

    assertTrue(tagged.isValid());
    assertTrue(logged.stream().noneMatch(line -> line.contains("Removed")), logged.toString());
  }

  @Test
  void anOldUntaggedDisplayIsFoundAtAHeightWhereFlooringWouldMissIt() throws Exception {
    var location = station().clone();
    location.setY(15);
    var legacy = display(above(location), gear(), null);

    GearStationStore.reconcile(location.getChunk());

    assertFalse(legacy.isValid());
    assertTrue(
        logged.contains(
            "[Magic] Removed 1 leftover displays at gear station "
                + GearStationStore.key(location)
                + " (no station there)."),
        logged.toString());
  }

  static UUID displayId(GearStationStore.Occupancy occupancy) throws Exception {
    var field = GearStationStore.Occupancy.class.getDeclaredField("displayId");
    field.setAccessible(true);
    return (UUID) field.get(occupancy);
  }

  static void setDisplayId(GearStationStore.Occupancy occupancy, UUID id) throws Exception {
    var field = GearStationStore.Occupancy.class.getDeclaredField("displayId");
    field.setAccessible(true);
    field.set(occupancy, id);
  }
}
