package net.tfminecraft.magic.blood;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

/** Vague notes owed to players, e.g. an owner whose animal was taken. Kept until delivered. */
public final class BloodNotes {

    /** For an account, and only its named character when one is set. */
    public record Note(String account, String character, String text, long deliverAfter) {}

    private static final List<Note> NOTES = new ArrayList<>();
    private static BloodStore store;

    private BloodNotes() {}

    public static void init(BloodStore source) {
        NOTES.clear();
        store = source;
        NOTES.addAll(source.loadNotes());
    }

    public static void clear() {
        NOTES.clear();
        store = null;
    }

    public static void add(Note note) {
        NOTES.add(note);
        save();
    }

    public static List<Note> all() {
        return new ArrayList<>(NOTES);
    }

    /** Hands the player every note owed to them; ignoreDelay on a fresh login. */
    public static void deliver(Player player, String character, long now, boolean ignoreDelay) {
        boolean changed = false;
        String account = player.getUniqueId().toString();
        for (Iterator<Note> it = NOTES.iterator(); it.hasNext();) {
            Note note = it.next();
            boolean mine = note.account().equalsIgnoreCase(account)
                    && (note.character() == null || note.character().equalsIgnoreCase(character));
            if (mine && (ignoreDelay || now >= note.deliverAfter())) {
                player.sendMessage(note.text());
                it.remove();
                changed = true;
            }
        }
        if (changed) {
            save();
        }
    }

    /** Minute tick: notes whose delay ran out reach owners who are online. */
    static void tick(long now) {
        if (NOTES.isEmpty()) {
            return;
        }
        for (Player player : Bukkit.getOnlinePlayers()) {
            String character = AltarService.currentCharacter(player);
            if (character != null) {
                deliver(player, character, now, false);
            }
        }
    }

    private static void save() {
        if (store != null) {
            store.saveNotes(new ArrayList<>(NOTES));
        }
    }
}
