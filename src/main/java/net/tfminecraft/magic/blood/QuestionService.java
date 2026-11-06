package net.tfminecraft.magic.blood;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.function.LongSupplier;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import net.tfminecraft.magic.Messages;
import net.tfminecraft.magic.artifact.sacrifice.SacrificeRegistry;

/**
 * The god's question, as a dialogue. Right-clicking the altar with the blood knife in its chamber
 * starts it: the god asks over a few lines (the mage's own voice cuts in), then waits for the mage to
 * answer aloud in roleplay chat. An answer is asked back once ("say it again") and sealed by the
 * second; until then the other answer may still be given. Leaving the chamber or letting the time run
 * out ends it, and it can be started again. Optional music plays to the mage alone throughout.
 */
public final class QuestionService {

    private static final long UNCLEAR_PAUSE = 6_000L;

    private static final class Session {
        final String altarId;
        final Deque<ChoiceConfig.Line> lines = new ArrayDeque<>();
        long nextLineAt;
        long endsAt;
        long lastUnclearAt;
        long musicAgainAt;
        BloodPath heard;
        BloodPath sealed;

        Session(String altarId) {
            this.altarId = altarId;
        }
    }

    private static final Map<UUID, Session> SESSIONS = new HashMap<>();
    static LongSupplier clock = System::currentTimeMillis;

    private QuestionService() {}

    public static boolean asking(UUID player) {
        return SESSIONS.containsKey(player);
    }

    /** The voices ask; nothing happens while the question is already being asked or cannot be. */
    public static void start(Player player) {
        if (asking(player.getUniqueId()) || !BloodChoice.canAnswer(player)) {
            return;
        }
        BloodAltar altar = BloodAltars.ofCharacter(AltarService.currentCharacter(player));
        if (!inChamber(player, altar.getId())) {
            return;
        }
        Session session = new Session(altar.getId());
        session.lines.addAll(ChoiceConfig.ask());
        session.nextLineAt = clock.getAsLong();
        SESSIONS.put(player.getUniqueId(), session);
        music(player, session, clock.getAsLong());
        BloodFx.play("question", altar.center(), player);
        // In the chamber and at the question, as just checked: the first line is said now.
        step(player, session, clock.getAsLong());
    }

    /** Every second: the next line when it is due, and the end when the mage walks away or waits too long. */
    static void tick() {
        long now = clock.getAsLong();
        for (Iterator<Map.Entry<UUID, Session>> it = SESSIONS.entrySet().iterator(); it.hasNext();) {
            Map.Entry<UUID, Session> entry = it.next();
            Player player = Bukkit.getPlayer(entry.getKey());
            if (player == null) {
                it.remove();
                continue;
            }
            if (!step(player, entry.getValue(), now)) {
                it.remove();
            }
        }
    }

    /** Moves the dialogue on; false when it is over (the caller drops the session). */
    private static boolean step(Player player, Session session, long now) {
        if (session.sealed == null && (!BloodChoice.canAnswer(player) || !inChamber(player, session.altarId))) {
            say(player, ChoiceConfig.walkedAway());
            silence(player);
            return false;
        }
        if (session.musicAgainAt > 0 && now >= session.musicAgainAt) {
            silence(player);
            music(player, session, now);
        }
        if (!session.lines.isEmpty()) {
            if (now >= session.nextLineAt) {
                say(player, session.lines.poll());
                session.nextLineAt = now + ChoiceConfig.lineSeconds() * 1000L;
                if (session.lines.isEmpty() && session.sealed == null && session.endsAt == 0) {
                    player.sendMessage(Messages.get("blood.choice.prompt"));
                    session.endsAt = now + ChoiceConfig.answerSeconds() * 1000L;
                }
            }
            return true;
        }
        if (session.sealed != null) {
            if (now < session.nextLineAt) {
                return true;
            }
            silence(player);
            BloodChoice.answer(player, session.sealed);
            return false;
        }
        if (session.endsAt > 0 && now >= session.endsAt) {
            say(player, ChoiceConfig.walkedAway());
            silence(player);
            return false;
        }
        return true;
    }

    /** Roleplay speech while the god waits: the first answer is asked back, the second seals it. */
    public static void onChat(Player player, String channel, String message) {
        Session session = SESSIONS.get(player.getUniqueId());
        if (session == null || session.sealed != null || !session.lines.isEmpty() || session.endsAt == 0
                || channel == null || !SacrificeRegistry.getRpChannel().equals(channel.trim().toLowerCase(Locale.ROOT))
                || !inChamber(player, session.altarId)) {
            return;
        }
        long now = clock.getAsLong();
        BloodPath path = classify(message);
        if (path == null) {
            if (now - session.lastUnclearAt >= UNCLEAR_PAUSE) {
                session.lastUnclearAt = now;
                say(player, ChoiceConfig.unclear());
            }
            return;
        }
        ChoiceConfig.Answer answer = ChoiceConfig.answer(path);
        session.lines.clear();
        if (path == session.heard) {
            session.sealed = path;
            session.lines.addAll(answer.sealed());
        } else {
            session.heard = path;
            session.lines.addAll(answer.confirm());
            session.endsAt = now + ChoiceConfig.answerSeconds() * 1000L;
        }
        session.nextLineAt = now + 1500L;
    }

    /** Refusal first, so "I will not kneel" refuses; null when the words say neither. */
    static BloodPath classify(String message) {
        String said = " " + normalize(message) + " ";
        for (BloodPath path : new BloodPath[] {BloodPath.TRUE, BloodPath.DEVOTEE}) {
            for (String word : ChoiceConfig.answer(path).words()) {
                if (said.contains(" " + word + " ")) {
                    return path;
                }
            }
        }
        return null;
    }

    /** Lower case, letters and apostrophes only, single spaces. */
    static String normalize(String text) {
        return text.toLowerCase(Locale.ROOT).replaceAll("[^a-z' ]", " ").replaceAll("\\s+", " ").trim();
    }

    static boolean inChamber(Player player, String altarId) {
        BloodAltar altar = AltarRoom.lockedBy(player.getLocation().getBlock());
        return altar != null && altar.getId().equals(altarId);
    }

    private static void say(Player player, ChoiceConfig.Line line) {
        if (line != null) {
            WhisperService.speak(player, line.speaker(), line.text());
        }
    }

    /** Starts the music, and when it loops, when to start it again. */
    private static void music(Player player, Session session, long now) {
        if (ChoiceConfig.music().isEmpty()) {
            return;
        }
        player.playSound(player.getLocation(), ChoiceConfig.music(), ChoiceConfig.musicCategory(),
                ChoiceConfig.musicVolume(), 1f);
        session.musicAgainAt = ChoiceConfig.musicLoopSeconds() > 0 ? now + ChoiceConfig.musicLoopSeconds() * 1000L : 0;
    }

    private static void silence(Player player) {
        if (!ChoiceConfig.music().isEmpty()) {
            player.stopSound(ChoiceConfig.music(), ChoiceConfig.musicCategory());
        }
    }

    /** Ends the player's question, e.g. when they leave. */
    public static void forget(UUID player) {
        SESSIONS.remove(player);
    }

    static void clear() {
        SESSIONS.clear();
    }
}
