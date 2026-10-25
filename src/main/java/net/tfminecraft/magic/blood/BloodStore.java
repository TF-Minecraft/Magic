package net.tfminecraft.magic.blood;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Logger;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

/**
 * Blood path files: one JSON per character plus one altars file. Both stay small, are read
 * once at startup and written whole through a temp file, so a click never re-reads disk.
 * A file that cannot be read is moved aside, never overwritten.
 */
public final class BloodStore {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Type ALTAR_LIST = new TypeToken<List<BloodAltar>>() {}.getType();
    private static final Type NOTE_LIST = new TypeToken<List<BloodNotes.Note>>() {}.getType();
    private static final Type BOND_MAP = new TypeToken<Map<String, Integer>>() {}.getType();

    private final File characters;
    private final File altars;
    private final File notes;
    private final File bonds;
    private final Logger log;

    public BloodStore(File root, Logger log) {
        this.characters = new File(root, "characters");
        this.altars = new File(root, "altars.json");
        this.notes = new File(root, "notes.json");
        this.bonds = new File(root, "bonds");
        this.log = log;
    }

    public List<BloodRecord> loadRecords() {
        List<BloodRecord> out = new ArrayList<>();
        File[] files = characters.listFiles((dir, name) -> name.endsWith(".json"));
        if (files == null) {
            return out;
        }
        for (File file : files) {
            BloodRecord record = read(file, BloodRecord.class);
            if (record == null) {
                continue;
            }
            if (record.getCharacterId() == null || record.getCharacterId().isBlank()) {
                record.setCharacterId(file.getName().substring(0, file.getName().length() - 5));
            }
            record.normalize();
            out.add(record);
        }
        return out;
    }

    /** Writes the record, or deletes its file when nothing in it is worth keeping. */
    public boolean save(BloodRecord record) {
        File file = new File(characters, record.getCharacterId() + ".json");
        if (record.isEmpty()) {
            try {
                Files.deleteIfExists(file.toPath());
                return true;
            } catch (IOException ex) {
                log.severe("[Magic] Failed to delete blood record " + file.getName() + ": " + ex.getMessage());
                return false;
            }
        }
        return write(file, GSON.toJson(record));
    }

    public List<BloodAltar> loadAltars() {
        List<BloodAltar> out = new ArrayList<>();
        if (!altars.isFile()) {
            return out;
        }
        List<BloodAltar> loaded = read(altars, ALTAR_LIST);
        if (loaded == null) {
            return out;
        }
        for (BloodAltar altar : loaded) {
            if (altar != null && altar.isValid()) {
                out.add(altar);
            } else {
                log.warning("[Magic] Skipped an incomplete altar in altars.json");
            }
        }
        return out;
    }

    public boolean saveAltars(List<BloodAltar> list) {
        return write(altars, GSON.toJson(list, ALTAR_LIST));
    }

    public List<BloodNotes.Note> loadNotes() {
        List<BloodNotes.Note> out = new ArrayList<>();
        if (!notes.isFile()) {
            return out;
        }
        List<BloodNotes.Note> loaded = read(notes, NOTE_LIST);
        if (loaded != null) {
            for (BloodNotes.Note note : loaded) {
                if (note != null && note.account() != null && note.text() != null) {
                    out.add(note);
                }
            }
        }
        return out;
    }

    public boolean saveNotes(List<BloodNotes.Note> list) {
        return write(notes, GSON.toJson(list, NOTE_LIST));
    }

    /** Minutes the character spent near each other character; empty when there is no file yet. */
    public Map<String, Integer> loadBonds(String characterId) {
        File file = new File(bonds, characterId + ".json");
        Map<String, Integer> out = new HashMap<>();
        if (!file.isFile()) {
            return out;
        }
        Map<String, Integer> loaded = read(file, BOND_MAP);
        if (loaded != null) {
            loaded.forEach((other, minutes) -> {
                if (minutes != null && minutes > 0) {
                    out.put(other, minutes);
                }
            });
        }
        return out;
    }

    public boolean saveBonds(String characterId, Map<String, Integer> minutes) {
        return write(new File(bonds, characterId + ".json"), GSON.toJson(minutes, BOND_MAP));
    }

    private <T> T read(File file, Type type) {
        try {
            T value = GSON.fromJson(Files.readString(file.toPath(), StandardCharsets.UTF_8), type);
            if (value == null) {
                throw new IOException("empty file");
            }
            return value;
        } catch (IOException | RuntimeException ex) {
            log.severe("[Magic] Failed to read " + file.getName() + ": " + ex.getMessage());
            File rejected = new File(file.getParentFile(), file.getName() + ".rejected-" + UUID.randomUUID());
            try {
                Files.move(file.toPath(), rejected.toPath());
                log.warning("[Magic] Preserved unreadable blood file at " + rejected.getName());
            } catch (IOException moveFailure) {
                log.severe("[Magic] Could not preserve " + file.getName() + ": " + moveFailure.getMessage());
            }
            return null;
        }
    }

    private boolean write(File file, String json) {
        Path target = file.toPath();
        try {
            Files.createDirectories(target.toAbsolutePath().getParent());
            Path temp = target.resolveSibling(file.getName() + ".tmp");
            Files.writeString(temp, json, StandardCharsets.UTF_8);
            Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            return true;
        } catch (IOException ex) {
            log.severe("[Magic] Failed to write " + file.getName() + ": " + ex.getMessage());
            return false;
        }
    }
}
