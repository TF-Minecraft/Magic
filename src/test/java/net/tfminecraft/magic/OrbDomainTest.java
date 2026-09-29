package net.tfminecraft.magic;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.UUID;
import net.tfminecraft.magic.gear.orb.GearOrb;
import net.tfminecraft.magic.meditation.*;
import net.tfminecraft.magic.util.OrbTrail;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

class OrbDomainTest {
  @Test
  void trailRetainsTwentySnapshotsWithoutAliasingInput() {
    var trail = new OrbTrail();
    assertNull(trail.ticksAgo(0));
    var location = new Location(null, 0, 0, 0);
    for (int i = 0; i < 25; i++) {
      location.setX(i);
      trail.push(location);
    }
    location.setX(999);
    assertEquals(24, trail.ticksAgo(0).getX());
    assertEquals(24, trail.ticksAgo(-1).getX());
    assertEquals(5, trail.ticksAgo(19).getX());
    assertNull(trail.ticksAgo(20));
  }

  @Test
  void pingRewindBoundsFramesAndNeverExceedsCapacity() {
    assertArrayEquals(new int[] {0, 0}, OrbTrail.rewindTicks((Player) null, 20));
    assertArrayEquals(new int[] {0, 0}, OrbTrail.rewindTicks(-1, 20));
    assertArrayEquals(new int[] {0, 1}, OrbTrail.rewindTicks(25, 20));
    assertArrayEquals(new int[] {0, 1}, OrbTrail.rewindTicks(50, 20));
    assertArrayEquals(new int[] {1, 2}, OrbTrail.rewindTicks(75, 20));
    assertArrayEquals(new int[] {18, 19}, OrbTrail.rewindTicks(Integer.MAX_VALUE, 100));
    assertArrayEquals(new int[] {0, 0}, OrbTrail.rewindTicks(100, -1));
    Player player = mock(Player.class);
    when(player.getPing()).thenReturn(100);
    assertArrayEquals(new int[] {1, 2}, OrbTrail.rewindTicks(player, 20));
  }

  @Test
  void meditationOrbLifecycleSeparatesIntroLifeReturnAndConsumption() {
    Location source = new Location(null, 1, 2, 3);
    UUID id = UUID.randomUUID();
    var orb = new MeditationOrb(source, true, false, 1, source, 2, 3, 4, 5, 6, 2, id);
    source.setX(99);
    assertEquals(1, orb.getLocation().getX());
    assertEquals(1, orb.getSpawnLocation().getX());
    assertEquals(id, orb.getSourceId());
    assertTrue(orb.isFlow());
    orb.setFlow(false);
    assertFalse(orb.isFlow());
    assertFalse(orb.isStarter());
    assertEquals(1, orb.getAngle());
    orb.setAngle(2);
    assertEquals(2, orb.getAngle());
    assertEquals(3, orb.getRadius());
    assertEquals(4, orb.getHeightBias());
    assertEquals(5, orb.getAngleSpeed());
    assertEquals(6, orb.getBobPhase());
    orb.addBobPhase(.5);
    assertEquals(6.5, orb.getBobPhase());
    assertEquals(2, orb.getIntroMax());
    assertEquals(2, orb.getIntroRemaining());
    orb.tickIntro();
    orb.tickIntro();
    orb.tickIntro();
    assertEquals(0, orb.getIntroRemaining());
    assertFalse(orb.tickReturn());
    assertFalse(orb.tickLife());
    assertTrue(orb.tickLife());
    assertTrue(orb.tickLife());
    assertSame(orb.getLocation(), orb.getReturnFrom());
    orb.setLocation(new Location(null, 4, 5, 6));
    assertEquals(4, orb.getLocation(0).getX());
    assertNull(orb.getLocation(1));
    orb.beginReturn();
    assertTrue(orb.isReturning());
    assertEquals(2, orb.getReturnMax());
    assertEquals(2, orb.getReturnRemaining());
    assertEquals(4, orb.getReturnFrom().getX());
    orb.beginReturn();
    assertFalse(orb.tickLife());
    assertFalse(orb.tickReturn());
    assertTrue(orb.tickReturn());
    assertTrue(orb.tickReturn());
    orb.setConsumed(true);
    assertTrue(orb.isConsumed());
    assertFalse(orb.tickReturn());
    assertFalse(orb.tickLife());
  }

  @Test
  void starterOrbsNeverExpireOrReturnAndUseCurrentPositionForHits() {
    var start = new MeditationOrb(new Location(null, 0, 0, 0), true, true, 0);
    assertTrue(start.isStarter());
    assertSame(start.getLocation(), start.getLocation(19));
    assertFalse(start.tickLife());
    start.beginReturn();
    assertFalse(start.isReturning());
    var orb = new MeditationOrb(new Location(null, 0, 0, 0), true, false, 0);
    assertFalse(orb.tickLife());
    orb.setConsumed(true);
    orb.beginReturn();
    assertFalse(orb.isReturning());
    orb.setConsumed(false);
    orb.beginReturn();
    assertEquals(Math.max(1, MeditationCache.orbIntroTicks), orb.getReturnMax());
  }

  @Test
  void gearOrbLifetimeAndPositionHistoryAreIndependent() {
    Location at = new Location(null, 1, 2, 3);
    var orb = new GearOrb(at, true, 1, 2, 3, 4, 5, 1, 2);
    at.setX(9);
    assertEquals(1, orb.getAnchor().getX());
    assertTrue(orb.isGood());
    assertEquals(1, orb.getAngle());
    orb.setAngle(2);
    assertEquals(2, orb.getAngle());
    assertEquals(2, orb.getRadius());
    assertEquals(3, orb.getHeightBias());
    assertEquals(4, orb.getAngleSpeed());
    assertEquals(5, orb.getBobPhase());
    orb.addBobPhase(1);
    assertEquals(6, orb.getBobPhase());
    assertEquals(1, orb.getIntroMax());
    assertEquals(1, orb.getIntroRemaining());
    orb.tickIntro();
    orb.tickIntro();
    assertEquals(0, orb.getIntroRemaining());
    orb.setLocation(new Location(null, 4, 5, 6));
    assertEquals(4, orb.getLocation().getX());
    assertEquals(4, orb.getLocation(0).getX());
    assertNull(orb.getLocation(1));
    assertFalse(orb.tickLife());
    assertTrue(orb.tickLife());
    assertTrue(orb.tickLife());
    assertFalse(orb.isConsumed());
    orb.setConsumed(true);
    assertTrue(orb.isConsumed());
    assertFalse(orb.tickLife());
  }
}
