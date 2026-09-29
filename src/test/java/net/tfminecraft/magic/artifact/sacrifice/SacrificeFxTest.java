package net.tfminecraft.magic.artifact.sacrifice;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.*;
import net.tfminecraft.interactiblefurniture.furniture.Furniture;
import net.tfminecraft.magic.Magic;
import net.tfminecraft.magic.artifact.shrine.ShrineScore;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.*;
import org.mockbukkit.mockbukkit.*;

class SacrificeFxTest {
  ServerMock server;
  World world;
  Furniture furniture;
  SacrificeRiteSession session;

  @BeforeEach
  void setup() {
    server = MockBukkit.mock();
    world = mock(World.class);
    furniture = mock(Furniture.class);
    when(furniture.getLoc()).thenReturn(new Location(world, 0, 64, 0));
    when(furniture.getOriginBlockLocation()).thenReturn(Optional.empty());
    session =
        new SacrificeRiteSession(
            UUID.randomUUID(),
            UUID.randomUUID(),
            furniture,
            "main",
            "fire",
            new ShrineScore(null),
            0);
    Magic.plugin = mock(Magic.class);
    when(Magic.plugin.isEnabled()).thenReturn(true);
    when(Magic.plugin.getServer()).thenReturn(server);
    SacrificeRegistry.clear();
  }

  @AfterEach
  void cleanup() {
    SacrificeRiteService.clearAll();
    SacrificeRiteFx.stopIfIdle();
    Magic.plugin = null;
    MockBukkit.unmock();
  }

  @Test
  void resolutionSurgesOnlyForConfiguredSuccessfulTiers() {
    var at = new Location(world, 2, 64, 2);
    SacrificeRiteFx.resolve(null, at);
    SacrificeRiteFx.resolve(session, at);
    session.markResolved(true, "soul", 1);
    SacrificeRiteFx.resolve(session, at);
    verifyNoInteractions(world);
    SacrificeRegistry.setFx(
        new SacrificeFxDef(
            Set.of("soul"),
            Sound.ENTITY_LIGHTNING_BOLT_THUNDER,
            1,
            1,
            Sound.ENTITY_GENERIC_EXPLODE,
            1,
            1));
    SacrificeRiteFx.resolve(session, at);
    verify(world, times(2))
        .spawnParticle(
            eq(Particle.DUST),
            any(Location.class),
            eq(36),
            anyDouble(),
            anyDouble(),
            anyDouble(),
            any(Particle.DustOptions.class));
    verify(world, times(2))
        .playSound(any(Location.class), any(Sound.class), anyFloat(), anyFloat());
    when(furniture.getLoc()).thenReturn(null);
    SacrificeRiteFx.resolve(session, null);
    SacrificeRiteFx.resolve(session, new Location(null, 0, 0, 0));
    when(furniture.getLoc()).thenReturn(at);
    SacrificeRegistry.setFx(new SacrificeFxDef(Set.of("soul"), null, 1, 1, null, 1, 1));
    doThrow(new IllegalArgumentException("unsupported"))
        .when(world)
        .spawnParticle(
            eq(Particle.SOUL),
            any(Location.class),
            anyInt(),
            anyDouble(),
            anyDouble(),
            anyDouble(),
            anyDouble());
    SacrificeRiteFx.resolve(session, at);
  }

  @Test
  void runningRiteAnimatesPullsAndStopsAfterServiceBecomesIdle() {
    var victim = mock(Player.class);
    when(victim.isOnline()).thenReturn(true);
    when(victim.getLocation()).thenAnswer(i -> new Location(world, 2, 64, 2));
    try (var rites = mockStatic(SacrificeRiteService.class);
        var bukkit = mockStatic(Bukkit.class, CALLS_REAL_METHODS)) {
      rites.when(SacrificeRiteService::hasSessions).thenReturn(true);
      rites.when(SacrificeRiteService::sessions).thenReturn(List.of(session));
      bukkit.when(() -> Bukkit.getPlayer(session.getVictimId())).thenReturn(victim);
      SacrificeRiteFx.ensureRunning();
      SacrificeRiteFx.ensureRunning();
      server.getScheduler().performTicks(4);
      SacrificeRiteFx.stopIfIdle();
      server.getScheduler().cancelTasks(Magic.plugin);
      SacrificeRiteFx.ensureRunning();
      verify(world, atLeastOnce())
          .spawnParticle(
              eq(Particle.DUST),
              any(Location.class),
              eq(1),
              anyDouble(),
              anyDouble(),
              anyDouble(),
              any(Particle.DustOptions.class));
      bukkit.when(() -> Bukkit.getPlayer(session.getVictimId())).thenReturn(null);
      server.getScheduler().performTicks(2);
      bukkit.when(() -> Bukkit.getPlayer(session.getVictimId())).thenReturn(victim);
      when(victim.isOnline()).thenReturn(false);
      server.getScheduler().performTicks(2);
      when(victim.isOnline()).thenReturn(true);
      when(furniture.getLoc()).thenReturn(null);
      server.getScheduler().performTicks(2);
      when(furniture.getLoc()).thenReturn(new Location(mock(World.class), 0, 0, 0));
      server.getScheduler().performTicks(2);
      rites.when(SacrificeRiteService::hasSessions).thenReturn(false);
      server.getScheduler().performTicks(2);
      Magic.plugin = null;
      SacrificeRiteFx.ensureRunning();
    }
  }
}
