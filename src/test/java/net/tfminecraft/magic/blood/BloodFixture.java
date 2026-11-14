package net.tfminecraft.magic.blood;

import static org.mockito.Mockito.*;

import java.io.File;
import java.nio.file.Path;
import java.util.UUID;
import java.util.logging.Logger;

import net.tfminecraft.magic.CharacterTestSupport;
import net.tfminecraft.magic.Magic;
import net.tfminecraft.magic.Messages;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import org.mockbukkit.mockbukkit.world.WorldMock;
import org.mockito.MockedStatic;

/** MockBukkit server, a stone cave world, a mocked Magic plugin and echoing messages. */
final class BloodFixture implements AutoCloseable {
  final ServerMock server;
  final WorldMock world;
  final CharacterTestSupport characters;
  final MockedStatic<Messages> messages;
  final Logger logger = Logger.getAnonymousLogger();
  final Path data;

  BloodFixture(Path data) {
    this.data = data;
    server = MockBukkit.mock(new SoundServer());
    world = new DisplayWorld(server);
    world.setName("cave");
    server.addWorld(world);
    Magic.plugin = mock(Magic.class);
    when(Magic.plugin.namespace()).thenReturn("magic");
    when(Magic.plugin.getName()).thenReturn("Magic");
    when(Magic.plugin.getServer()).thenReturn(server);
    when(Magic.plugin.getLogger()).thenReturn(logger);
    when(Magic.plugin.getDataFolder()).thenReturn(data.toFile());
    when(Magic.plugin.isEnabled()).thenReturn(true);
    characters = new CharacterTestSupport();
    messages = mockStatic(Messages.class, i -> i.getMethod().getReturnType() == void.class ? null : i.getArgument(0));
    BloodConfig.reset();
    BloodConfig.setEnabled(true);
    BloodStore store = new BloodStore(new File(data.toFile(), "blood"), logger);
    BloodRecords.init(store);
    BloodAltars.init(store);
    AltarPedestals.init(store);
    AltarRegions.setQuery(location -> java.util.List.of());
    BondService.writer = Runnable::run;
  }

  /** A server whose players can hear sounds bound to an entity (the blood music follows the mage). */
  static final class SoundServer extends ServerMock {
    private int made;

    @Override
    public PlayerMock addPlayer() {
      return addPlayer("Player" + made);
    }

    @Override
    public PlayerMock addPlayer(String name) {
      made++;
      PlayerMock player = new SoundPlayer(this, name, UUID.nameUUIDFromBytes(("player:" + name + ":" + made).getBytes()));
      addPlayer(player);
      return player;
    }
  }

  /** MockBukkit does not implement playSound bound to an entity; this player hears it where the entity is. */
  static final class SoundPlayer extends PlayerMock {
    SoundPlayer(ServerMock server, String name, UUID id) {
      super(server, name, id);
    }

    @Override
    public void playSound(org.bukkit.entity.Entity entity, String sound, org.bukkit.SoundCategory category,
        float volume, float pitch) {
      playSound(entity.getLocation(), sound, category, volume, pitch);
    }
  }

  /** An item display that also keeps its billboard and brightness, which MockBukkit leaves unimplemented. */
  static final class Display extends org.mockbukkit.mockbukkit.entity.ItemDisplayMock {
    private org.bukkit.entity.Display.Billboard billboard = org.bukkit.entity.Display.Billboard.FIXED;
    private org.bukkit.entity.Display.Brightness brightness;

    Display(ServerMock server, UUID id) {
      super(server, id);
    }

    @Override
    public void setBillboard(org.bukkit.entity.Display.Billboard billboard) {
      this.billboard = billboard;
    }

    @Override
    public org.bukkit.entity.Display.Billboard getBillboard() {
      return billboard;
    }

    @Override
    public void setBrightness(org.bukkit.entity.Display.Brightness brightness) {
      this.brightness = brightness;
    }

    @Override
    public org.bukkit.entity.Display.Brightness getBrightness() {
      return brightness;
    }
  }

  /** MockBukkit cannot build display entities through World.spawn; this world can. */
  static final class DisplayWorld extends WorldMock {
    private final ServerMock server;

    DisplayWorld(ServerMock server) {
      super(Material.STONE, -64, 320, 64);
      this.server = server;
    }

    @Override
    public <T extends org.bukkit.entity.Entity> T spawn(Location location, Class<T> type,
        java.util.function.Consumer<? super T> function,
        org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason reason) {
      if (type != org.bukkit.entity.ItemDisplay.class) {
        return super.spawn(location, type, function, reason);
      }
      var display = new Display(server, UUID.randomUUID());
      display.setLocation(location);
      server.registerEntity(display);
      T typed = type.cast(display);
      if (function != null) {
        function.accept(typed);
      }
      return typed;
    }

    @Override
    public <T extends org.bukkit.entity.Entity> T spawn(Location location, Class<T> type) {
      return spawn(location, type, null, org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason.CUSTOM);
    }

    @Override
    public <T extends org.bukkit.entity.Entity> T spawn(Location location, Class<T> type, boolean randomize,
        java.util.function.Consumer<? super T> function) {
      return spawn(location, type, function, org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason.CUSTOM);
    }
  }

  /** A player playing the labelled character. */
  PlayerMock player(String label) {
    PlayerMock player = server.addPlayer();
    player.teleport(new Location(world, 0.5, 11, 0.5));
    characters.start(player, label);
    return player;
  }

  /**
   * An open chamber around the pocket at x, y, z, wide and high enough for the altar's meditation
   * room, with a doorway out through the east wall.
   */
  Block room(int x, int y, int z) {
    int half = AltarRoom.halfWidth();
    open(x - half, y, z - half, x + half, y + BloodConfig.roomHeight() - 1, z + half);
    open(x + half + 1, y, z + 2, x + half + 1, y + 1, z + 2);
    return pocket(x, y, z);
  }

  static String id(String label) {
    return CharacterTestSupport.id(label);
  }

  /** An air pocket at y with stone floor and the world's stone above it, sky light dark. */
  Block pocket(int x, int y, int z) {
    world.loadChunk(x >> 4, z >> 4);
    Block block = world.getBlockAt(x, y, z);
    block.setType(Material.AIR);
    world.getBlockAt(x, y - 1, z).setType(Material.STONE);
    ((org.mockbukkit.mockbukkit.block.BlockMock) block).setLightFromSky((byte) 0);
    return block;
  }

  /** Hollows out the stone between the corners, so bodies have open air between them and the altar. */
  void open(int x1, int y1, int z1, int x2, int y2, int z2) {
    for (int x = x1; x <= x2; x++) {
      for (int z = z1; z <= z2; z++) {
        world.loadChunk(x >> 4, z >> 4);
        for (int y = y1; y <= y2; y++) {
          world.getBlockAt(x, y, z).setType(Material.AIR);
        }
      }
    }
  }

  BloodRecord record(String label, BloodStage stage) {
    BloodRecord record = BloodRecords.getOrCreate(id(label), UUID.randomUUID().toString());
    record.setStage(stage);
    BloodRecords.save(record);
    return record;
  }

  @Override
  public void close() {
    AltarRegions.setQuery(null);
    BloodRecords.clear();
    BloodAltars.clear();
    AltarPedestals.clear();
    BloodConfig.reset();
    messages.close();
    characters.unregister();
    Magic.plugin = null;
    MockBukkit.unmock();
  }
}
