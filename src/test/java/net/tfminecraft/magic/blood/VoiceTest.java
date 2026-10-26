package net.tfminecraft.magic.blood;

import static org.junit.jupiter.api.Assertions.*;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Random;

import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.ArmorStand;
import org.bukkit.event.entity.EntityMountEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import net.md_5.bungee.api.ChatColor;
import net.tfminecraft.magic.Messages;

class VoiceTest {
  @TempDir Path temp;
  BloodFixture fx;

  @BeforeEach
  void setup() {
    fx = new BloodFixture(temp);
    assertTrue(BloodConfig.load(new File("src/main/resources/wildmagic/bloodmagic"), fx.logger));
  }

  @AfterEach
  void cleanup() {
    WhisperService.random = new Random();
    fx.close();
  }

  @SuppressWarnings("deprecation")
  static String plain(String message) {
    return ChatColor.stripColor(message);
  }

  @Test
  void coloursBlendFromStartToEndAndKeepTheStyle() {
    assertTrue(VoiceColours.valid(" #aBc123 "));
    assertFalse(VoiceColours.valid("red"));
    assertFalse(VoiceColours.valid(null));
    assertEquals(List.of("#000000"), VoiceColours.at(List.of(), List.of("#000000"), 0.5));
    assertEquals(List.of("#ffffff"), VoiceColours.at(List.of("#ffffff"), List.of(), 0.5));
    assertEquals(List.of("#000000", "#808080"),
        VoiceColours.at(List.of("#000000"), List.of("#000000", "#ffffff"), 0.5));
    assertEquals(List.of("#ffffff", "#ffffff"),
        VoiceColours.at(List.of("#000000"), List.of("#ffffff", "#ffffff"), 7));
    String line = VoiceColours.gradient("a b", List.of("#ff0000", "#0000ff"), "§o");
    assertTrue(line.startsWith(ChatColor.of("#ff0000") + "§oa "));
    assertTrue(line.endsWith(ChatColor.of("#0000ff") + "§ob"));
    assertEquals(ChatColor.of("#00ff00") + "x", VoiceColours.gradient("x", List.of("#00ff00"), ""));
  }

  @Test
  void theVoicesDriftAlongThePath() {
    PlayerMock mage = fx.player("mage");
    BloodRecord record = fx.record("mage", BloodStage.ANIMAL);
    WhisperConfig.Voice god = WhisperConfig.voice(WhisperConfig.Speaker.GOD);
    assertTrue(god.coloured());
    assertEquals(0, WhisperService.pathProgress(null));
    assertEquals(0, WhisperService.pathProgress(record));
    String early = WhisperService.voiced(mage, WhisperConfig.Speaker.GOD, "child");
    assertTrue(early.startsWith(ChatColor.of(god.start().get(0)).toString()));
    assertEquals("child", plain(early));
    for (String kind : List.of("cow", "pig", "sheep", "goat")) {
      record.addSpecies(kind);
    }
    double middle = WhisperService.pathProgress(record);
    assertTrue(middle > 0 && middle < 1);
    assertNotEquals(early, WhisperService.voiced(mage, WhisperConfig.Speaker.GOD, "child"));
    record.setStage(BloodStage.ASCENDED);
    assertEquals(1, WhisperService.pathProgress(record));
    assertTrue(WhisperService.voiced(mage, WhisperConfig.Speaker.GOD, "child")
        .startsWith(ChatColor.of(god.end().get(0)).toString()));
    record.setStage(BloodStage.FINAL);
    record.setFinalsDone(99);
    assertEquals(1, WhisperService.pathProgress(record));

    PlayerMock stranger = fx.server.addPlayer();
    assertTrue(WhisperService.voiced(stranger, WhisperConfig.Speaker.SELF, "me")
        .startsWith(ChatColor.of(WhisperConfig.voice(WhisperConfig.Speaker.SELF).start().get(0)).toString()));
  }

  @Test
  void animalLinesFollowTheKindsAndBothVoicesSpeak() {
    PlayerMock mage = fx.player("mage");
    BloodRecord record = fx.record("mage", BloodStage.ANIMAL);
    List<String> self = WhisperConfig.voice(WhisperConfig.Speaker.SELF).lines(BloodStage.ANIMAL);
    List<String> god = WhisperConfig.voice(WhisperConfig.Speaker.GOD).lines(BloodStage.ANIMAL);
    WhisperService.random = new Random() {
      boolean flip;
      @Override public boolean nextBoolean() { return flip = !flip; }
      @Override public int nextInt(int bound) { return 0; }
    };
    WhisperService.speakNext(mage, record);
    assertEquals(self.get(0), plain(mage.nextMessage()));
    WhisperService.speakNext(mage, record);
    assertEquals(god.get(0), plain(mage.nextMessage()));
    for (String kind : List.of("cow", "pig", "sheep", "goat", "horse", "rabbit", "chicken")) {
      record.addSpecies(kind);
    }
    assertEquals(1.0, WhisperService.stageProgress(record));
    WhisperService.speakNext(mage, record);
    int window = (self.size() + 2) / 3;
    assertEquals(self.get(self.size() - window), plain(mage.nextMessage()));

    assertEquals(0, WhisperService.pick(5, -1));
    assertEquals(0, WhisperService.pick(1, 0.5));
    assertEquals(6, WhisperService.pick(9, 2.0));
    record.setStage(BloodStage.HUNT);
    assertEquals(-1, WhisperService.stageProgress(record));
  }

  @Test
  void messagesMarkedWithAVoiceAreSaidInIt() {
    PlayerMock mage = fx.player("mage");
    fx.record("mage", BloodStage.ANIMAL);
    fx.messages.when(() -> Messages.getRaw("g")).thenReturn("{god}Not yet, child");
    fx.messages.when(() -> Messages.getRaw("s")).thenReturn("{self}Not them");
    WhisperService.tell(mage, "g");
    String said = mage.nextMessage();
    assertEquals("Not yet, child", plain(said));
    assertTrue(said.startsWith(ChatColor.of(WhisperConfig.voice(WhisperConfig.Speaker.GOD).start().get(0)).toString()));
    WhisperService.tell(mage, "s");
    assertEquals("Not them", plain(mage.nextMessage()));
    WhisperService.tell(mage, "blood.altar.inert");
    assertEquals("blood.altar.inert", mage.nextMessage());
    fx.messages.when(() -> Messages.getRaw("n")).thenReturn(null);
    WhisperService.tell(mage, "n");
    assertEquals("n", mage.nextMessage());
  }

  @Test
  void colourlessVoicesUseTheirStyleAsBefore() throws Exception {
    Files.writeString(temp.resolve("plain.yml"), String.join("\n",
        "enabled: true",
        "voices:",
        "  god:",
        "    style: '#111111'",
        "    colors:",
        "      start: [red]",
        "      end: []",
        "    lines:",
        "      animal: [hush]",
        ""));
    assertTrue(BloodConfig.load(temp.resolve("plain.yml").toFile(), fx.logger));
    assertFalse(WhisperConfig.voice(WhisperConfig.Speaker.GOD).coloured());
    PlayerMock mage = fx.player("mage");
    BloodRecord record = fx.record("mage", BloodStage.ANIMAL);
    WhisperService.speakNext(mage, record);
    assertEquals(net.tfminecraft.magic.util.MagicText.format("#111111hush"), mage.nextMessage());
  }

  @Test
  void sittingOnTheAltarAnswersItsOwnerAndChillsOthers() {
    PlayerMock mage = fx.player("mage");
    PlayerMock stranger = fx.player("stranger");
    Block target = fx.pocket(0, 10, 0);
    BloodAltars.add(BloodAltar.create(BloodFixture.id("mage"), "o", target, 0f, 1L));
    BloodListener listener = new BloodListener();

    ArmorStand onTop = fx.world.spawn(new Location(fx.world, 0.5, 11.2, 0.5), ArmorStand.class);
    listener.onMount(new EntityMountEvent(mage, onTop));
    assertEquals("blood.altar.sit", mage.nextMessage());
    ArmorStand inside = fx.world.spawn(new Location(fx.world, 0.5, 10.6, 0.5), ArmorStand.class);
    listener.onMount(new EntityMountEvent(stranger, inside));
    assertEquals("blood.altar.sit_stranger", stranger.nextMessage());
    PlayerMock nobody = fx.server.addPlayer();
    listener.onMount(new EntityMountEvent(nobody, onTop));
    assertEquals("blood.altar.sit_stranger", nobody.nextMessage());

    ArmorStand elsewhere = fx.world.spawn(new Location(fx.world, 8.5, 11.2, 8.5), ArmorStand.class);
    listener.onMount(new EntityMountEvent(mage, elsewhere));
    assertNull(mage.nextMessage());
    listener.onMount(new EntityMountEvent(onTop, elsewhere));
  }
}
