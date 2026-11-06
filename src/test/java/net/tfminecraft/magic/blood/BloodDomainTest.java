package net.tfminecraft.magic.blood;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Path;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class BloodDomainTest {
  @TempDir Path temp;

  @Test
  void enumsParseCaseInsensitivelyAndRejectUnknownNames() {
    assertEquals(BloodStage.HUNT, BloodStage.parse(" hunt "));
    assertNull(BloodStage.parse("nope"));
    assertNull(BloodStage.parse(null));
    assertTrue(BloodStage.FINAL.atLeast(BloodStage.ANIMAL));
    assertFalse(BloodStage.NONE.atLeast(BloodStage.WHISPERED));
    assertEquals(BloodPath.DEVOTEE, BloodPath.parse("Devotee"));
    assertNull(BloodPath.parse("other"));
    assertNull(BloodPath.parse(null));
    assertEquals(BloodItemKind.RITUAL_KNIFE, BloodItemKind.parse(" RITUAL_knife"));
    assertNull(BloodItemKind.parse("sword"));
    assertNull(BloodItemKind.parse(null));
    assertEquals("blood_knife", BloodItemKind.BLOOD_KNIFE.key());
    assertEquals("v.iron_sword", BloodItemKind.RITUAL_KNIFE.fallbackPath());
  }

  @Test
  void recordNormalizesBrokenFieldsAndKnowsWhenItIsEmpty() throws Exception {
    BloodRecord record = new BloodRecord();
    for (String field : new String[] {"stage", "path", "species", "sacrifices", "finalTargets"}) {
      var f = BloodRecord.class.getDeclaredField(field);
      f.setAccessible(true);
      f.set(record, null);
    }
    var whisper = BloodRecord.class.getDeclaredField("whisper");
    whisper.setAccessible(true);
    whisper.set(record, Double.NaN);
    var line = BloodRecord.class.getDeclaredField("whisperLine");
    line.setAccessible(true);
    line.set(record, -3);
    record.setAltarId(" ");
    record.normalize();
    assertEquals(BloodStage.NONE, record.getStage());
    assertEquals(BloodPath.NONE, record.getPath());
    assertTrue(record.getSpecies().isEmpty());
    assertEquals(0, record.getWhisper());
    assertEquals(0, record.getWhisperLine());
    assertNull(record.getAltarId());
    assertTrue(record.isEmpty());
    whisper.set(record, -1.0);
    record.normalize();
    assertEquals(0, record.getWhisper());
    record.setAltarId("a");
    record.normalize();
    assertEquals("a", record.getAltarId());

    BloodRecord made = BloodRecord.create("c", "o");
    assertEquals("c", made.getCharacterId());
    assertEquals("o", made.getOwnerUuid());
    made.setCharacterId("d");
    assertEquals("d", made.getCharacterId());
    made.setWhisperLine(4);
    made.setStage(BloodStage.NONE);
    assertEquals(4, made.getWhisperLine());
    made.setStage(BloodStage.WHISPERED);
    assertEquals(0, made.getWhisperLine());
    made.setWhisperLine(-1);
    assertEquals(0, made.getWhisperLine());
    made.setWhisper(-5);
    assertEquals(0, made.getWhisper());
    made.setWhisper(3);
    assertEquals(3, made.getWhisper());
    made.addSpecies("COW");
    made.addSpecies("cow");
    made.addSpecies(null);
    assertTrue(made.hasSpecies("Cow"));
    assertFalse(made.hasSpecies(null));
    assertEquals(1, made.getSpecies().size());
    made.setLastAnimalAt(9);
    assertEquals(9, made.getLastAnimalAt());
    made.setHuntPoints(-2);
    made.setFinalsDone(-2);
    assertEquals(0, made.getHuntPoints());
    assertEquals(0, made.getFinalsDone());
    made.setPath(BloodPath.TRUE);
    assertEquals(BloodPath.TRUE, made.getPath());

    assertEmptyOnlyWhenBlank(BloodRecord.create("x", "o"));
  }

  private static void assertEmptyOnlyWhenBlank(BloodRecord blank) {
    assertTrue(blank.isEmpty());
    BloodRecord r = BloodRecord.create("x", "o");
    r.setStage(BloodStage.WHISPERED);
    assertFalse(r.isEmpty());
    r = BloodRecord.create("x", "o");
    r.setPath(BloodPath.TRUE);
    assertFalse(r.isEmpty());
    r = BloodRecord.create("x", "o");
    r.setWhisper(1);
    assertFalse(r.isEmpty());
    r = BloodRecord.create("x", "o");
    r.addSpecies("pig");
    assertFalse(r.isEmpty());
    r = BloodRecord.create("x", "o");
    r.setAltarId("a");
    assertFalse(r.isEmpty());
    r = BloodRecord.create("x", "o");
    r.setHuntPoints(1);
    assertFalse(r.isEmpty());
    r = BloodRecord.create("x", "o");
    r.setFinalsDone(1);
    assertFalse(r.isEmpty());
    r = BloodRecord.create("x", "o");
    r.getSacrifices().add(new BloodRecord.Sacrifice("c", "a", "HUNT", 1, 0));
    assertFalse(r.isEmpty());
    r = BloodRecord.create("x", "o");
    r.getFinalTargets().add("c");
    assertFalse(r.isEmpty());
    assertEquals("c", r.currentTarget());
    r = BloodRecord.create("x", "o");
    assertNull(r.currentTarget());
    r.setPendingBacklash(true);
    assertFalse(r.isEmpty());
  }

  @Test
  void altarKeysLocationAndValidity() throws Exception {
    try (BloodFixture fx = new BloodFixture(temp)) {
      var block = fx.world.getBlockAt(17, 10, -3);
      BloodAltar altar = BloodAltar.create("char", "owner", block, 90f, 5L);
      assertTrue(altar.isValid());
      assertEquals("char", altar.getOwnerCharacter());
      assertEquals("owner", altar.getOwnerUuid());
      assertEquals("cave", altar.getWorld());
      assertEquals(17, altar.getX());
      assertEquals(10, altar.getY());
      assertEquals(-3, altar.getZ());
      assertEquals(90f, altar.getYaw());
      assertEquals(5L, altar.getPlacedAt());
      assertEquals("cave,17,10,-3", altar.blockKey());
      assertEquals(BloodAltar.key(block), altar.blockKey());
      assertEquals("cave,1,-1", altar.chunkKey());
      assertEquals(block, altar.block());
      assertEquals(17.5, altar.center().getX());
      altar.setLevel(-2);
      assertEquals(0, altar.getLevel());
      assertNull(altar.getDisplayId());
      UUID display = UUID.randomUUID();
      altar.setDisplayId(display);
      assertEquals(display, altar.getDisplayId());
      altar.setDisplayId(null);
      assertNull(altar.getDisplayId());
      var displayField = BloodAltar.class.getDeclaredField("displayId");
      displayField.setAccessible(true);
      displayField.set(altar, "not-a-uuid");
      assertNull(altar.getDisplayId());

      var world = BloodAltar.class.getDeclaredField("world");
      world.setAccessible(true);
      world.set(altar, "gone");
      assertNull(altar.block());
      assertNull(altar.center());
      world.set(altar, " ");
      assertFalse(altar.isValid());
      world.set(altar, null);
      assertFalse(altar.isValid());
      BloodAltar empty = new BloodAltar();
      assertFalse(empty.isValid());
      var id = BloodAltar.class.getDeclaredField("id");
      id.setAccessible(true);
      id.set(empty, " ");
      assertFalse(empty.isValid());
      id.set(empty, "x");
      assertFalse(empty.isValid());
      var owner = BloodAltar.class.getDeclaredField("ownerCharacter");
      owner.setAccessible(true);
      owner.set(empty, " ");
      assertFalse(empty.isValid());
      owner.set(empty, "c");
      assertFalse(empty.isValid());
    }
  }

  @Test
  void progressCountsSpeciesAnswerHuntAndFinals() {
    BloodConfig.reset();
    assertEquals(0, BloodProgress.of(null));
    BloodRecord record = BloodRecord.create("c", "o");
    record.addSpecies("cow");
    record.addSpecies("pig");
    assertEquals(2, BloodProgress.of(record));
    record.setPath(BloodPath.TRUE);
    record.setHuntPoints(9);
    assertEquals(2 + 1 + 7, BloodProgress.of(record));
    record.setHuntPoints(3);
    record.setFinalsDone(2);
    assertEquals(2 + 1 + 3 + 4, BloodProgress.of(record));
    record.setPath(BloodPath.DEVOTEE);
    assertEquals(2 + 1 + 7 + 4, BloodProgress.of(record));
    assertEquals(0, BloodProgress.level(record));
  }
}
