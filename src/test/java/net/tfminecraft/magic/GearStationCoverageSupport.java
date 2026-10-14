package net.tfminecraft.magic;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.nio.file.*;
import java.util.*;
import net.tfminecraft.magic.charge.TierBands;
import net.tfminecraft.magic.gear.*;
import net.tfminecraft.tlibs.TLibs;
import net.tfminecraft.tlibs.objects.api.BlockAPI;
import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.inventory.*;
import org.junit.jupiter.api.*;
import org.mockito.MockedStatic;

abstract class GearStationCoverageSupport extends GearCoverageSupport {
  MockedStatic<org.mockbukkit.mockbukkit.entity.EntityTypesMock> entityTypes;
  MockedStatic<TLibs> libs;
  BlockAPI blocks;
  net.tfminecraft.tlibs.objects.api.subapi.BlockChecker checker;
  String oldStation;

  @SuppressWarnings("unchecked")
  static Map<String, GearStationStore.Occupancy> occupied() throws Exception {
    var field = GearStationStore.class.getDeclaredField("OCCUPIED");
    field.setAccessible(true);
    return (Map<String, GearStationStore.Occupancy>) field.get(null);
  }

  @BeforeEach
  void setupStation() throws Exception {
    occupied().clear();
    ((Map<?, ?>) field("retainedRows")).clear();
    setField("unreadableStore", false);
    entityTypes =
        mockStatic(org.mockbukkit.mockbukkit.entity.EntityTypesMock.class, CALLS_REAL_METHODS);
    entityTypes
        .when(
            () ->
                org.mockbukkit.mockbukkit.entity.EntityTypesMock.createEntity(
                    eq(ItemDisplay.class), same(server)))
        .thenAnswer(
            a -> {
              var display =
                  spy(
                      new org.mockbukkit.mockbukkit.entity.ItemDisplayMock(
                          server, UUID.randomUUID()));
              doNothing().when(display).setBillboard(any(Display.Billboard.class));
              return display;
            });
    oldStation = GearCache.station;
    GearCache.station = "iaf(test:station)";
    libs = mockStatic(TLibs.class);
    blocks = mock(BlockAPI.class, RETURNS_DEEP_STUBS);
    libs.when(TLibs::getBlockAPI).thenReturn(blocks);
    checker = blocks.getChecker();
    when(checker.checkBlock(any(), anyString())).thenReturn(true);
    TierBands.clear();
    TierBands.register("fire", 1, 1);
  }

  @AfterEach
  void cleanupStation() throws Exception {
    occupied().clear();
    GearCache.station = oldStation;
    entityTypes.close();
    libs.close();
    TierBands.clear();
  }

  static Object field(String name) throws Exception {
    var f = GearStationStore.class.getDeclaredField(name);
    f.setAccessible(true);
    return f.get(null);
  }

  static void setField(String name, Object value) throws Exception {
    var f = GearStationStore.class.getDeclaredField(name);
    f.setAccessible(true);
    f.set(null, value);
  }

  Location station() {
    return new Location(
        server.getWorld("world") == null
            ? server.addSimpleWorld("world")
            : server.getWorld("world"),
        1,
        2,
        3);
  }

  ItemStack attuned() {
    var weapon = item();
    var req = WeaponRequirement.fromItem(weapon);
    req.aura().setCap("fire", 10);
    req.aura().setFill("fire", 10);
    req.persist(weapon);
    return weapon;
  }
  GearStationStore.Occupancy seed(Location location, ItemStack item, UUID account, Map<String, Integer> charged) {
    return seed(location, item, account, charged, false);
  }
  GearStationStore.Occupancy seedRest(Location location, ItemStack item, UUID account) {
    return seed(location, item, account, Map.of(), true);
  }
  GearStationStore.Occupancy seed(Location location, ItemStack item, UUID account, Map<String, Integer> charged, boolean rested) {
    if (location == null || location.getWorld() == null || item == null) return null;
    try {
      var store = GearStationStore.property(); var file = temp.resolve("data/gear-stations.yml");
      Map<Object,Object> doc = Files.exists(file) ? net.tfminecraft.magic.gear.ownership.GearYaml.read(Files.readAllBytes(file)) : new LinkedHashMap<>();
      @SuppressWarnings("unchecked") Map<Object,Object> rows = (Map<Object,Object>)doc.computeIfAbsent("stations", key -> new LinkedHashMap<>());
      for (Object value : rows.values()) if (value instanceof Map<?,?> map && map.get("world") instanceof String && map.get("x") instanceof Integer && map.get("y") instanceof Integer && map.get("z") instanceof Integer && GearStationStore.key(location).equals(net.tfminecraft.magic.gear.ownership.GearPropertyStore.location(map))) {
        @SuppressWarnings("unchecked") var old = (Map<Object,Object>)map; old.put("effect-state", "released");
      }
      UUID id = UUID.randomUUID(); Map<Object,Object> row = new LinkedHashMap<>();
      row.put("world",location.getWorld().getName());row.put("x",location.getBlockX());row.put("y",location.getBlockY());row.put("z",location.getBlockZ());
      row.put("occupancy-id",id.toString()); row.put("effect-state","active");row.put("item-bytes",net.tfminecraft.magic.gear.ownership.GearPropertyStore.b64(net.tfminecraft.magic.gear.ownership.GearItems.bytes(item)));row.put("rested",rested);
      if(account==null) row.put("ownership",Map.of("format",1,"kind","OWNERLESS"));
      else { var ref=characters.ref(account);row.put("owner",account.toString());row.put("ownership",net.tfminecraft.magic.gear.ownership.GearPropertyStore.ownership(new net.tfminecraft.magic.gear.ownership.GearIdentity.Owner(account,ref.characterId()))); }
      if(charged!=null){var costs=new LinkedHashMap<Object,Object>();charged.forEach((path,amount)->costs.put(Integer.toString(costs.size()),Map.of("path",path,"amount",amount)));row.put("charged",costs);}
      rows.put(id.toString(),row); net.tfminecraft.magic.gear.ownership.GearFiles.write(file,net.tfminecraft.magic.gear.ownership.GearYaml.bytes(doc),true);
      GearStationStore.loadViews(); return GearStationStore.get(location);
    }catch(Exception ex){throw new RuntimeException(ex);}
  }
  ItemStack eject(Location location) {
    var row=GearStationStore.get(location); var item=GearStationStore.eject(location);
    if(item!=null)GearStationStore.acknowledgeRelease(location,row.generation());return item;
  }
  ItemStack takeForAbort(Location location) {
    var row=GearStationStore.get(location);var item=GearStationStore.takeForAbort(location);
    if(item!=null)GearStationStore.acknowledgeRelease(location,row.generation());return item;
  }
}
