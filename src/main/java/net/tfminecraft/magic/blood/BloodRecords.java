package net.tfminecraft.magic.blood;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Every blood record, kept in memory and written through on each change. */
public final class BloodRecords {

    private static final Map<String, BloodRecord> RECORDS = new HashMap<>();
    private static BloodStore store;

    private BloodRecords() {}

    public static void init(BloodStore source) {
        RECORDS.clear();
        store = source;
        for (BloodRecord record : source.loadRecords()) {
            RECORDS.put(key(record.getCharacterId()), record);
        }
    }

    public static void clear() {
        RECORDS.clear();
        store = null;
    }

    public static BloodRecord get(String characterId) {
        return characterId != null ? RECORDS.get(key(characterId)) : null;
    }

    /** The character's record, created in memory when it has none yet. */
    public static BloodRecord getOrCreate(String characterId, String ownerUuid) {
        return RECORDS.computeIfAbsent(key(characterId), id -> BloodRecord.create(characterId, ownerUuid));
    }

    /** Writes the record; an empty record leaves memory and disk. */
    public static boolean save(BloodRecord record) {
        if (record.isEmpty()) {
            RECORDS.remove(key(record.getCharacterId()));
        }
        return store == null || store.save(record);
    }

    /** Forgets the character's whole blood path. */
    public static boolean delete(String characterId) {
        BloodRecord record = RECORDS.remove(key(characterId));
        if (record == null) {
            return true;
        }
        BloodRecord empty = BloodRecord.create(record.getCharacterId(), record.getOwnerUuid());
        return store == null || store.save(empty);
    }

    public static List<BloodRecord> all() {
        return new ArrayList<>(RECORDS.values());
    }

    private static String key(String characterId) {
        return characterId.toLowerCase(Locale.ROOT);
    }
}
