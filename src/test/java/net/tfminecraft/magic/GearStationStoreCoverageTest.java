package net.tfminecraft.magic;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.nio.file.*;
import java.util.*;
import net.tfminecraft.magic.gear.*;
import net.tfminecraft.magic.gear.gui.OpenStationManager;
import org.bukkit.*;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.*;
import org.bukkit.inventory.*;
import org.junit.jupiter.api.*;

class GearStationStoreCoverageTest extends GearStationCoverageSupport {
  @Test
  void occupancyPersistsOwnerCostsDisplayAndOnlyEjectsAttunedIdleWeapons() throws Exception {
    var location = station();
    assertFalse(GearStationStore.isOccupied(null));
    assertNull(GearStationStore.get(null));
    assertNull(GearStationStore.occupy(null, item(), null, null));
    assertNull(GearStationStore.occupy(location, null, null, null));
    assertNull(GearStationStore.eject(location));
    assertNull(GearStationStore.takeForAbort(location));
    GearStationStore.update(location, item());
    assertFalse(GearStationStore.isAttuned(null));
    var owner = UUID.randomUUID();
    var weapon = item();
    var value = GearStationStore.occupy(location, weapon, owner, Map.of("v.iron_ingot", 2));
    assertTrue(GearStationStore.isOccupied(location));
    assertSame(value, GearStationStore.get(location));
    assertEquals(owner, value.getOwner());
    assertEquals(Map.of("v.iron_ingot", 2), value.getCharged());
    assertSame(weapon, value.getItem());
    assertNull(GearStationStore.eject(location));
    value.setOrbSessionActive(true);
    assertTrue(value.isOrbSessionActive());
    assertNull(GearStationStore.eject(location));
    GearStationStore.update(location, null);
    value.setOrbSessionActive(false);
    var ready = attuned();
    GearStationStore.update(location, ready);
    assertSame(ready, value.getItem());
    assertTrue(GearStationStore.isAttuned(ready));
    assertSame(ready, GearStationStore.eject(location));
    assertFalse(GearStationStore.isOccupied(location));
    assertTrue(Files.exists(temp.resolve("data/gear-stations.yml")));
    value = GearStationStore.occupy(location, weapon, null, null);
    assertNull(value.getOwner());
    assertNull(value.getCharged());
    assertSame(weapon, GearStationStore.takeForAbort(location));
  }

  @Test
  void locationAndOpenStationHelpersCloneStateAndRespectWorlds() throws Exception {
    var location = station();
    assertEquals("", GearStationStore.key(null));
    assertEquals("", GearStationStore.key(new Location(null, 0, 0, 0)));
    assertEquals("world,1,2,3", GearStationStore.key(location));
    assertEquals(location, GearStationStore.locationFromKey("world,1,2,3"));
    for (String key : List.of("invalid", "missing,1,2,3", "world,NaN,2,3"))
      assertNull(GearStationStore.locationFromKey(key));
    var origin = location.clone().add(.5, .5, .5);
    assertEquals(0, GearStationStore.distanceSquaredToCenter(location, origin));
    for (Location missing :
        Arrays.asList(
            null,
            new Location(null, 0, 0, 0),
            new Location(server.addSimpleWorld("other"), 0, 0, 0)))
      assertEquals(Double.MAX_VALUE, GearStationStore.distanceSquaredToCenter(location, missing));
    assertEquals(Double.MAX_VALUE, GearStationStore.distanceSquaredToCenter(null, origin));
    assertEquals(
        Double.MAX_VALUE,
        GearStationStore.distanceSquaredToCenter(new Location(null, 0, 0, 0), origin));
    assertNull(GearStationStore.occupiedWithin(null, 1));
    assertNull(GearStationStore.occupiedWithin(new Location(null, 0, 0, 0), 1));
    assertNull(GearStationStore.occupiedWithin(origin, -1));
    GearStationStore.occupy(location, item(), null, Map.of());
    assertEquals(location, GearStationStore.occupiedWithin(origin, .1));
    assertNull(GearStationStore.occupiedWithin(origin.clone().add(4, 0, 0), .1));
    GearStationStore.occupy(location.clone().add(3, 0, 0), item(), null, Map.of());
    assertEquals(location, GearStationStore.occupiedWithin(origin, 10));
    var p = server.addPlayer();
    OpenStationManager.set(null, location);
    OpenStationManager.set(p, null);
    assertNull(OpenStationManager.get(null));
    assertNull(OpenStationManager.get(p));
    OpenStationManager.set(p, location);
    var copy = OpenStationManager.get(p);
    copy.add(1, 0, 0);
    assertEquals(location, OpenStationManager.get(p));
    OpenStationManager.clear(null);
    OpenStationManager.clear(p);
    assertNull(OpenStationManager.get(p));
  }

  @Test
  void stationRoundTripReusesTaggedDisplayAndClearsSavedState() throws Exception {
    GearStationStore.load();
    var location = station();
    GearStationStore.occupy(location, item(), UUID.randomUUID(), Map.of("v.iron_ingot", 2));
    assertEquals(1, location.getWorld().getEntitiesByClass(ItemDisplay.class).size());
    GearStationStore.load();
    assertTrue(GearStationStore.isOccupied(location));
    assertEquals(1, location.getWorld().getEntitiesByClass(ItemDisplay.class).size());
    GearStationStore.reconcile(null);
    GearStationStore.reconcile(location.getChunk());
    GearStationStore.shutdown();
    assertFalse(GearStationStore.isOccupied(location));
    assertTrue(
        location.getWorld().getEntitiesByClass(ItemDisplay.class).stream()
            .noneMatch(Entity::isValid),
        "no valid displays after shutdown");
    GearStationStore.load();
    assertTrue(GearStationStore.isOccupied(location));
    GearStationStore.clearAll();
    assertTrue(
        YamlConfiguration.loadConfiguration(temp.resolve("data/gear-stations.yml").toFile())
            .getKeys(false)
            .isEmpty(),
        "empty saved data after clear");
    GearStationStore.load();
    assertFalse(GearStationStore.isOccupied(location));
  }

  @Test
  void unknownWorldAndInvalidRowsSurviveAnOtherwiseSuccessfulSave() throws Exception {
    var config = new YamlConfiguration();
    config.set("stations.offline.world", "offline");
    config.set("stations.offline.x", 1);
    config.set("stations.offline.item", item());
    config.set("stations.scalar", "invalid but retained");
    var file = temp.resolve("data/gear-stations.yml");
    Files.createDirectories(file.getParent());
    config.save(file.toFile());
    GearStationStore.load();
    GearStationStore.save();
    var saved = YamlConfiguration.loadConfiguration(file.toFile());
    assertEquals("offline", saved.getString("stations.offline.world"));
    assertEquals("invalid but retained", saved.getString("stations.scalar"));
  }

  @Test
  void malformedStoreIsPreservedBeforeTheNextSave() throws Exception {
    var file = temp.resolve("data/gear-stations.yml");
    Files.createDirectories(file.getParent());
    String broken = "stations: [broken";
    Files.writeString(file, broken);
    GearStationStore.load();
    GearStationStore.save();
    try (var files = Files.list(file.getParent())) {
      assertTrue(
          files
              .filter(p -> p.getFileName().toString().contains("rejected"))
              .anyMatch(
                  p -> {
                    try {
                      return Files.readString(p).equals(broken);
                    } catch (Exception e) {
                      throw new RuntimeException(e);
                    }
                  }));
    }
  }

  @Test
  void metadataAndCostHelpersHandleLegacyAndMalformedValues() throws Exception {
    assertNull(invoke(GearStationStore.class, "uuidOf", new Class[] {String.class}, (Object) null));
    assertNull(invoke(GearStationStore.class, "uuidOf", new Class[] {String.class}, " "));
    assertNull(invoke(GearStationStore.class, "uuidOf", new Class[] {String.class}, "bad"));
    var id = UUID.randomUUID();
    assertEquals(
        id, invoke(GearStationStore.class, "uuidOf", new Class[] {String.class}, id.toString()));
    var config = new YamlConfiguration();
    assertNull(
        invoke(
            GearStationStore.class,
            "readCharged",
            new Class[] {org.bukkit.configuration.ConfigurationSection.class},
            config));
    config.set("charged.0.amount", 1);
    config.set("charged.1.path", " ");
    config.set("charged.1.amount", 1);
    config.set("charged.2.path", "v.stone");
    config.set("charged.2.amount", 0);
    config.set("charged.3.path", "v.stone");
    config.set("charged.3.amount", 2);
    config.set("charged.4.path", "v.stone");
    config.set("charged.4.amount", 3);
    assertEquals(
        Map.of("v.stone", 5),
        invoke(
            GearStationStore.class,
            "readCharged",
            new Class[] {org.bukkit.configuration.ConfigurationSection.class},
            config));
    var noWorld = new Location(null, 0, 0, 0);
    assertNull(invoke(GearStationStore.class, "findTagged", new Class[] {Location.class}, noWorld));
    assertNull(
        invoke(
            GearStationStore.class,
            "ensureDisplay",
            new Class[] {Location.class, ItemStack.class, UUID.class},
            noWorld,
            item(),
            null));
    GearStationStore.occupy(noWorld, item(), null, null).setItem(item());
    GearStationStore.save();
    GearStationStore.shutdown();
    invoke(
        GearStationStore.class,
        "removeDisplays",
        new Class[] {Location.class, UUID.class, UUID.class},
        null,
        null,
        null);
    invoke(
        GearStationStore.class,
        "drop",
        new Class[] {Location.class, ItemStack.class},
        noWorld,
        item());
    var loc = station();
    invoke(
        GearStationStore.class, "drop", new Class[] {Location.class, ItemStack.class}, loc, null);
    var parent = temp.resolve("blocked");
    Files.writeString(parent, "file");
    when(Magic.plugin.getDataFolder()).thenReturn(parent.toFile());
    assertDoesNotThrow(GearStationStore::save);
  }

  @Test
  void rejectedRowsKeepNumericKeysAndQuarantineFailureCannotOverwrite() throws Exception {
    var config = new YamlConfiguration();
    config.set("stations.0.world", "offline");
    config.set("stations.0.item", item());
    config.set("stations.2.world", "world");
    config.set("stations.2.x", 2);
    var file = temp.resolve("data/gear-stations.yml");
    Files.createDirectories(file.getParent());
    config.save(file.toFile());
    var location = station();
    GearStationStore.load();
    GearStationStore.occupy(location, item(), null, null);
    var saved = YamlConfiguration.loadConfiguration(file.toFile());
    assertEquals("offline", saved.getString("stations.0.world"));
    assertEquals("world", saved.getString("stations.1.world"));
    assertEquals("world", saved.getString("stations.2.world"));
    Files.writeString(file, "stations: [broken");
    GearStationStore.load();
    try (var files = mockStatic(Files.class, CALLS_REAL_METHODS)) {
      files
          .when(() -> Files.move(eq(file), any(Path.class)))
          .thenThrow(new java.io.IOException("read only"));
      GearStationStore.save();
      assertEquals("stations: [broken", Files.readString(file));
    }
    GearStationStore.save();
    assertFalse((boolean) field("unreadableStore"));
  }

  @Test
  void absentFurnitureDropsOnceAndUnknownFurnitureRetainsWeapons() throws Exception {
    var location = station();
    GearStationStore.occupy(location, item(), null, null);
    doReturn(false).when(checker).checkBlock(any(), anyString());
    location.getBlock().setType(Material.BARRIER);
    GearStationStore.load();
    assertTrue(GearStationStore.isOccupied(location));
    when(blocks.getChecker().checkBlock(any(), anyString()))
        .thenThrow(new IllegalStateException("API unavailable"));
    GearStationStore.reconcile(location.getChunk());
    assertTrue(GearStationStore.isOccupied(location));
    doReturn(false).when(checker).checkBlock(any(), anyString());
    location.getBlock().setType(Material.AIR);
    GearStationStore.load();
    assertFalse(GearStationStore.isOccupied(location));
    assertEquals(1, location.getWorld().getEntitiesByClass(Item.class).size());
    GearStationStore.load();
    assertEquals(1, location.getWorld().getEntitiesByClass(Item.class).size());
    doReturn(true).when(checker).checkBlock(any(), anyString());
    GearStationStore.occupy(location, item(), null, null);
    var display =
        location.getWorld().getEntitiesByClass(ItemDisplay.class).stream()
            .filter(Entity::isValid)
            .findFirst()
            .orElseThrow();
    display.remove();
    GearStationStore.reconcile(location.getChunk());
    assertTrue(GearStationStore.isOccupied(location));
    doReturn(false).when(checker).checkBlock(any(), anyString());
    GearStationStore.reconcile(location.getChunk());
    assertFalse(GearStationStore.isOccupied(location));
  }

  @Test
  void displayReconciliationKeepsFurnitureRemovesDuplicatesAndRecoversMissingIds()
      throws Exception {
    var location = station();
    var world = location.getWorld();
    var at = location.clone().add(.5, 1.15, .5);
    var nonDisplay = world.spawn(at, org.bukkit.entity.ArmorStand.class);
    var furniture = world.spawn(at, ItemDisplay.class);
    furniture.setItemStack(new ItemStack(Material.STONE));
    var gear = world.spawn(at, ItemDisplay.class);
    var weapon = item();
    tag(weapon, GearKeys.parts(), org.bukkit.persistence.PersistentDataType.STRING, "part");
    gear.setItemStack(weapon);
    var archetype = world.spawn(at, ItemDisplay.class);
    var second = item();
    tag(second, GearKeys.archetype(), org.bukkit.persistence.PersistentDataType.STRING, "staff");
    archetype.setItemStack(second);
    var plain = world.spawn(at, ItemDisplay.class);
    plain.setItemStack(item());
    assertNull(
        invoke(GearStationStore.class, "findTagged", new Class[] {Location.class}, location));
    GearStationStore.occupy(location, item(), null, null);
    assertTrue(furniture.isValid());
    assertTrue(nonDisplay.isValid());
    assertTrue(plain.isValid());
    assertFalse(gear.isValid());
    assertFalse(archetype.isValid());
    var marked =
        world.getEntitiesByClass(ItemDisplay.class).stream()
            .filter(d -> d.getPersistentDataContainer().has(GearKeys.stationDisplay()))
            .findFirst()
            .orElseThrow();
    assertSame(
        marked,
        invoke(GearStationStore.class, "findTagged", new Class[] {Location.class}, location));
    assertEquals(
        marked.getUniqueId(),
        invoke(
            GearStationStore.class,
            "ensureDisplay",
            new Class[] {Location.class, ItemStack.class, UUID.class},
            location,
            item(),
            UUID.randomUUID()));
    invoke(
        GearStationStore.class,
        "removeDisplays",
        new Class[] {Location.class, UUID.class, UUID.class},
        location,
        marked.getUniqueId(),
        marked.getUniqueId());
    assertTrue(marked.isValid());
    invoke(
        GearStationStore.class,
        "removeDisplays",
        new Class[] {Location.class, UUID.class, UUID.class},
        location,
        marked.getUniqueId(),
        nonDisplay.getUniqueId());
    assertFalse(nonDisplay.isValid());
    invoke(
        GearStationStore.class,
        "removeDisplays",
        new Class[] {Location.class, UUID.class, UUID.class},
        location,
        null,
        UUID.randomUUID());
    assertFalse(marked.isValid());
    assertSame(
        plain,
        invoke(
            GearStationStore.class,
            "displayOrNull",
            new Class[] {UUID.class},
            plain.getUniqueId()));
  }

  @Test
  void unloadedWorldMissingItemsAndChunksAreHandledWithoutLosingLiveDisplays() throws Exception {
    var location = station();
    var occupancy = GearStationStore.occupy(location, item(), null, null);
    var display =
        location.getWorld().getEntitiesByClass(ItemDisplay.class).stream()
            .filter(Entity::isValid)
            .findFirst()
            .orElseThrow();
    display.remove();
    occupancy.setItem(item());
    occupancy.setItem(null);
    GearStationStore.save();
    assertTrue(
        YamlConfiguration.loadConfiguration(temp.resolve("data/gear-stations.yml").toFile())
            .getKeys(false)
            .isEmpty());
    occupancy.setItem(item());
    GearStationStore.reconcile(location.getWorld().getChunkAt(1, 0));
    GearStationStore.reconcile(location.getWorld().getChunkAt(0, 1));
    GearStationStore.reconcile(server.addSimpleWorld("other").getChunkAt(0, 0));
    GearStationStore.occupy(new Location(null, 0, 0, 0), item(), null, null);
    GearStationStore.reconcile(location.getChunk());
    GearStationStore.occupiedWithin(location.clone().add(.5, .5, .5), Double.MAX_VALUE);
    when(checker.checkBlock(any(), anyString()))
        .thenAnswer(
            a -> {
              GearStationStore.reconcile(location.getChunk());
              return true;
            });
    GearStationStore.load();
    assertNull(
        invoke(GearStationStore.class, "displayOrNull", new Class[] {UUID.class}, (Object) null));
    var armor = location.getWorld().spawn(location, ArmorStand.class);
    assertNull(
        invoke(
            GearStationStore.class,
            "displayOrNull",
            new Class[] {UUID.class},
            armor.getUniqueId()));
    GearStationStore.get(location).setItem(null);
    doReturn(false).when(checker).checkBlock(any(), anyString());
    GearStationStore.reconcile(location.getChunk());
    assertFalse(GearStationStore.isOccupied(location));
    doReturn(true).when(checker).checkBlock(any(), anyString());
    var isolated = new Location(server.addSimpleWorld("unloaded"), 1, 2, 3);
    GearStationStore.occupy(isolated, item(), null, null);
    assertTrue(server.unloadWorld(isolated.getWorld(), false));
    GearStationStore.shutdown();
  }

  @Test
  void displayBoundaryHandlesChunkLoadFailuresAndEmptyDisplays() throws Exception {
    var world = mock(World.class);
    when(world.getName()).thenReturn("boundary");
    var chunk = mock(Chunk.class);
    when(world.getChunkAt(anyInt(), anyInt())).thenReturn(chunk);
    when(world.getChunkAt(any(Location.class))).thenReturn(chunk);
    when(chunk.load()).thenThrow(new IllegalStateException("cannot load"));
    var loc = new Location(world, 1, 2, 3);
    invoke(
        GearStationStore.class,
        "removeDisplays",
        new Class[] {Location.class, UUID.class, UUID.class},
        loc,
        null,
        null);
    verify(chunk).load();
    var display = mock(ItemDisplay.class);
    when(display.getUniqueId()).thenReturn(UUID.randomUUID());
    when(display.getPersistentDataContainer())
        .thenReturn(item().getItemMeta().getPersistentDataContainer());
    when(display.getItemStack()).thenReturn(new ItemStack(Material.AIR));
    assertEquals(
        false,
        invoke(GearStationStore.class, "isGearDisplay", new Class[] {ItemDisplay.class}, display));
    doReturn(true).when(chunk).load();
    when(world.getNearbyEntities(any(Location.class), anyDouble(), anyDouble(), anyDouble()))
        .thenReturn(List.of(display));
    display
        .getPersistentDataContainer()
        .set(
            GearKeys.stationDisplay(),
            org.bukkit.persistence.PersistentDataType.STRING,
            GearStationStore.key(loc));
    assertEquals(
        display.getUniqueId(),
        invoke(
            GearStationStore.class,
            "ensureDisplay",
            new Class[] {Location.class, ItemStack.class, UUID.class},
            loc,
            item(),
            null));
  }

  @Test
  void staleEntityLookupAndRemovedUnloadedDisplayAreHarmless() throws Exception {
    var invalid = mock(ItemDisplay.class);
    var id = UUID.randomUUID();
    when(invalid.getUniqueId()).thenReturn(id);
    when(invalid.isValid()).thenReturn(false);
    try (var bukkit = mockStatic(Bukkit.class, CALLS_REAL_METHODS)) {
      bukkit.when(() -> Bukkit.getEntity(id)).thenReturn(invalid);
      assertNull(invoke(GearStationStore.class, "displayOrNull", new Class[] {UUID.class}, id));
    }
    var location = new Location(server.addSimpleWorld("gone"), 0, 0, 0);
    GearStationStore.occupy(location, item(), null, null);
    location.getWorld().getEntitiesByClass(ItemDisplay.class).forEach(Entity::remove);
    assertTrue(server.unloadWorld(location.getWorld(), false));
    GearStationStore.shutdown();
    assertTrue(occupied().isEmpty());
  }

  @Test
  void reconciliationToleratesStationsRemovedBySpawnListeners() throws Exception {
    var first = station();
    var second = first.clone().add(3, 0, 0);
    GearStationStore.occupy(first, item(), null, null);
    GearStationStore.occupy(second, item(), null, null);
    first.getWorld().getEntitiesByClass(ItemDisplay.class).forEach(Entity::remove);
    var fired = new java.util.concurrent.atomic.AtomicBoolean();
    server
        .getPluginManager()
        .registerEvent(
            org.bukkit.event.entity.EntitySpawnEvent.class,
            new org.bukkit.event.Listener() {},
            org.bukkit.event.EventPriority.NORMAL,
            (listener, event) -> {
              var entity = ((org.bukkit.event.entity.EntitySpawnEvent) event).getEntity();
              if (entity instanceof ItemDisplay && fired.compareAndSet(false, true)) {
                String kept =
                    entity
                        .getPersistentDataContainer()
                        .get(
                            GearKeys.stationDisplay(),
                            org.bukkit.persistence.PersistentDataType.STRING);
                GearStationStore.takeForAbort(
                    GearStationStore.key(first).equals(kept) ? second : first);
              }
            },
            Magic.plugin);
    assertDoesNotThrow(() -> GearStationStore.reconcile(first.getChunk()));
    assertTrue(fired.get());
    assertEquals(1, occupied().size());
    invoke(
        GearStationStore.class,
        "abandon",
        new Class[] {Location.class},
        first.clone().add(8, 0, 0));
  }

  @Test
  void reconciliationToleratesWorldUnloadFromSpawnListeners() throws Exception {
    var first = station();
    var second = first.clone().add(3, 0, 0);
    GearStationStore.occupy(first, item(), null, null);
    GearStationStore.occupy(second, item(), null, null);
    first.getWorld().getEntitiesByClass(ItemDisplay.class).forEach(Entity::remove);
    var fired = new java.util.concurrent.atomic.AtomicBoolean();
    server
        .getPluginManager()
        .registerEvent(
            org.bukkit.event.entity.EntitySpawnEvent.class,
            new org.bukkit.event.Listener() {},
            org.bukkit.event.EventPriority.NORMAL,
            (listener, event) -> {
              if (((org.bukkit.event.entity.EntitySpawnEvent) event).getEntity()
                      instanceof ItemDisplay
                  && fired.compareAndSet(false, true))
                assertTrue(server.unloadWorld(first.getWorld(), false));
            },
            Magic.plugin);
    assertDoesNotThrow(() -> GearStationStore.reconcile(first.getChunk()));
    assertTrue(fired.get());
    assertNull(server.getWorld("world"));
  }
}
