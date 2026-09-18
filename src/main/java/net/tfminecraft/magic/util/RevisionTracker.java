package net.tfminecraft.magic.util;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import net.tfminecraft.magic.Magic;

/**
 * Content-hash revision numbers for gear parts and archetypes, persisted to
 * data/revisions.json. A new id starts at 1, an unchanged hash keeps its number,
 * and a changed hash increments it so stamped items read as outdated.
 */
public final class RevisionTracker {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Object LOCK = new Object();

    private final Map<String, Entry> parts = new HashMap<>();
    private final Map<String, Entry> archetypes = new HashMap<>();
    private File file;
    private boolean dirty;

    private static final class Entry {
        int revision;
        String hash;

        Entry(int revision, String hash) {
            this.revision = revision;
            this.hash = hash;
        }
    }

    public void load(File dataFolder) {
        synchronized (LOCK) {
            parts.clear();
            archetypes.clear();
            dirty = false;
            file = new File(dataFolder, "data/revisions.json");
            if (!file.exists()) {
                return;
            }
            try (FileReader reader = new FileReader(file, StandardCharsets.UTF_8)) {
                JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
                loadSection(root, "parts", parts);
                loadSection(root, "archetypes", archetypes);
            } catch (Exception ex) {
                Magic.plugin.getLogger().warning(
                        "[Magic] Failed to load revisions.json: " + ex.getMessage());
            }
        }
    }

    private static void loadSection(JsonObject root, String key, Map<String, Entry> target) {
        if (!root.has(key) || !root.get(key).isJsonObject()) {
            return;
        }
        JsonObject section = root.getAsJsonObject(key);
        for (String id : section.keySet()) {
            JsonObject entry = section.getAsJsonObject(id);
            int revision = entry.has("revision") ? entry.get("revision").getAsInt() : 1;
            String hash = entry.has("hash") ? entry.get("hash").getAsString() : "";
            target.put(id.toLowerCase(Locale.ROOT), new Entry(revision, hash));
        }
    }

    public void flush() {
        synchronized (LOCK) {
            if (!dirty || file == null) {
                return;
            }
            try {
                file.getParentFile().mkdirs();
                JsonObject root = new JsonObject();
                root.add("parts", sectionToJson(parts));
                root.add("archetypes", sectionToJson(archetypes));
                try (FileWriter writer = new FileWriter(file, StandardCharsets.UTF_8)) {
                    writer.write(GSON.toJson(root));
                }
                dirty = false;
            } catch (Exception ex) {
                Magic.plugin.getLogger().warning(
                        "[Magic] Failed to save revisions.json: " + ex.getMessage());
            }
        }
    }

    private static JsonObject sectionToJson(Map<String, Entry> source) {
        TreeMap<String, Entry> sorted = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        sorted.putAll(source);
        JsonObject section = new JsonObject();
        for (Map.Entry<String, Entry> e : sorted.entrySet()) {
            JsonObject entry = new JsonObject();
            entry.addProperty("revision", e.getValue().revision);
            entry.addProperty("hash", e.getValue().hash);
            section.add(e.getKey().toLowerCase(Locale.ROOT), entry);
        }
        return section;
    }

    public int resolvePart(String id, String contentHash) {
        return resolve(id, contentHash, parts);
    }

    public int resolveArchetype(String id, String contentHash) {
        return resolve(id, contentHash, archetypes);
    }

    /** Live revision, or 0 when the id is unknown. */
    public int partRevision(String id) {
        return revisionOf(id, parts);
    }

    /** Live revision, or 0 when the id is unknown. */
    public int archetypeRevision(String id) {
        return revisionOf(id, archetypes);
    }

    private static int revisionOf(String id, Map<String, Entry> map) {
        if (id == null || id.isBlank()) {
            return 0;
        }
        synchronized (LOCK) {
            Entry entry = map.get(id.trim().toLowerCase(Locale.ROOT));
            return entry == null ? 0 : entry.revision;
        }
    }

    private int resolve(String rawId, String contentHash, Map<String, Entry> map) {
        if (rawId == null || rawId.isBlank()) {
            return 0;
        }
        String id = rawId.trim().toLowerCase(Locale.ROOT);
        String hash = contentHash == null ? "" : contentHash;
        synchronized (LOCK) {
            Entry existing = map.get(id);
            if (existing == null) {
                map.put(id, new Entry(1, hash));
                dirty = true;
                return 1;
            }
            if (hash.equals(existing.hash)) {
                return existing.revision;
            }
            existing.revision++;
            existing.hash = hash;
            dirty = true;
            return existing.revision;
        }
    }

    public static String sha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest((input == null ? "" : input).getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte b : hash) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException(ex);
        }
    }
}
