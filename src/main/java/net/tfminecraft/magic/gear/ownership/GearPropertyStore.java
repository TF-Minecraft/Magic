package net.tfminecraft.magic.gear.ownership;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import net.tfminecraft.magic.gear.ownership.GearIdentity.Owner;

/** Raw property files are authoritative; loaded displays and menus are disposable views. */
public final class GearPropertyStore {
    public static final class Conflict extends IOException { public Conflict(String message) { super(message); } }
    public record Station(Object key, Map<Object, Object> raw) {}
    private final Path root;
    private final GearIdentity identity;
    public GearPropertyStore(Path root, GearIdentity identity) { this.root = root.toAbsolutePath().normalize(); this.identity = identity; }
    public Path root() { return root; }
    public GearIdentity identity() { return identity; }
    private Path stationFile() { return root.resolve("gear-stations.yml"); }

    private Map<Object, Object> document() throws IOException {
        Map<Object, Object> raw = Files.exists(stationFile()) ? GearYaml.read(Files.readAllBytes(stationFile())) : new LinkedHashMap<>();
        if (!raw.containsKey("stations")) raw.put("stations", new LinkedHashMap<>());
        if (!(raw.get("stations") instanceof Map<?, ?>)) throw new Conflict("Station mapping needs review");
        return raw;
    }
    @SuppressWarnings("unchecked")
    private static Map<Object, Object> mapping(Map<Object, Object> document) { return (Map<Object, Object>) document.get("stations"); }
    public List<Station> stations() throws IOException {
        List<Station> rows = new ArrayList<>();
        for (var entry : mapping(document()).entrySet()) if (entry.getValue() instanceof Map<?, ?> map) rows.add(new Station(entry.getKey(), GearYaml.copy(map)));
        var ids = new java.util.HashSet<String>(); var places = new java.util.HashSet<String>();
        for (Station station : rows) {
            Object id = station.raw().get("occupancy-id");
            if (id instanceof String value && !ids.add(value)) throw new Conflict("Duplicate occupancy ID");
            String at = knownLocation(station.raw());
            if (!"released".equals(station.raw().get("effect-state")) && at != null && !places.add(at)) throw new Conflict("Duplicate occupied location");
        }
        return rows;
    }
    public static String location(Map<?, ?> row) throws IOException {
        Object world = row.get("world");
        if (!(world instanceof String text) || text.isBlank() || text.contains(",")) throw new Conflict("Invalid station world");
        return text + "," + coordinate(row.get("x")) + "," + coordinate(row.get("y")) + "," + coordinate(row.get("z"));
    }
    private static String knownLocation(Map<?, ?> row) {
        if (!(row.get("world") instanceof String world) || world.isBlank() || world.contains(",")
                || !(row.get("x") instanceof Integer) || !(row.get("y") instanceof Integer) || !(row.get("z") instanceof Integer)) return null;
        return world + "," + row.get("x") + "," + row.get("y") + "," + row.get("z");
    }
    private static int coordinate(Object value) throws IOException {
        if (!(value instanceof Integer)) throw new Conflict("Invalid station coordinate");
        return (Integer) value;
    }
    public static UUID id(Map<?, ?> row) throws IOException { return uuid(row.get("occupancy-id")); }
    public static Owner owner(Map<?, ?> row) throws IOException {
        if (!(row.get("ownership") instanceof Map<?, ?> own) || !Integer.valueOf(1).equals(own.get("format")) || !"CHARACTER".equals(own.get("kind"))) throw new Conflict("Character ownership needs review");
        return new Owner(uuid(own.get("account")), uuid(own.get("character")));
    }
    public static UUID uuid(Object value) throws IOException {
        try { UUID uuid = UUID.fromString((String) value); if (!uuid.toString().equals(value)) throw new IllegalArgumentException(); return uuid; }
        catch (RuntimeException ex) { throw new Conflict("Invalid canonical UUID"); }
    }
    public static String state(Map<?, ?> row) throws IOException {
        Object state = row.get("effect-state");
        if (!(state instanceof String value) || !List.of("depositing", "active", "charging", "releasing", "held", "released").contains(value)) throw new Conflict("Station effect state needs review");
        return value;
    }
    public Station station(UUID id) throws IOException {
        Station found = null;
        for (Station station : stations()) if (id.toString().equals(station.raw().get("occupancy-id"))) {
            found = station;
        }
        if (found == null) throw new Conflict("Occupancy disappeared"); return found;
    }
    public boolean occupied(String at) throws IOException {
        for (Station station : stations()) if (at.equals(knownLocation(station.raw())) && !"released".equals(station.raw().get("effect-state"))) return true;
        return false;
    }
    public void insert(Map<Object, Object> row) throws IOException {
        String at = location(row); UUID id = id(row); owner(row); state(row);
        if (occupied(at)) throw new Conflict("Station is occupied or held");
        Map<Object, Object> document = document(); mapping(document).put(id.toString(), GearYaml.copy(row));
        saveStations(document);
    }
    public void patch(Station expected, Map<Object, Object> after) throws IOException {
        Map<Object, Object> document = document(); Object current = mapping(document).get(expected.key());
        if (!(current instanceof Map<?, ?> map) || !GearYaml.equal(map, expected.raw())) throw new Conflict("Station changed before publication");
        mapping(document).put(expected.key(), GearYaml.copy(after)); saveStations(document);
    }
    private void saveStations(Map<Object, Object> document) throws IOException { GearFiles.write(stationFile(), GearYaml.bytes(document), true); }
    public void holdInterrupted() throws IOException {
        for (Station station : stations()) if (java.util.Arrays.asList("depositing", "charging", "releasing").contains(station.raw().get("effect-state"))) {
            Map<Object, Object> after = GearYaml.copy(station.raw()); after.put("effect-state", "held"); patch(station, after);
        }
        for (Path file : returnFiles()) {
            JsonObject row = read(file); validateReturn(file, row);
            if ("delivering".equals(text(row, "state"))) { row.addProperty("state", "held"); write(file, row); }
        }
    }

    /** Existing plans fix their reviewed recipient; changing mappings never redirects a retry. */
    public void migrate() throws IOException {
        if (!identity.heirsReady()) return;
        for (Path file : files(root.resolve("gear-ownership/migration"))) {
            JsonObject journal = journal(file, "migration", null, null);
            for (var entry : entries(journal)) {
                if ("moved".equals(text(entry, "phase")) && sourceMatches(entry, "before")) throw new Conflict("Completed legacy source was restored");
                preflight(entry);
            }
            for (var entry : entries(journal)) apply(file, journal, entry, () -> identity.heirsReady(), false);
        }
        JsonObject journal = new JsonObject(); journal.addProperty("format", 1); journal.addProperty("kind", "migration");
        UUID run = UUID.randomUUID(); journal.addProperty("run", run.toString()); journal.add("entries", new JsonArray());
        for (Station station : stations()) {
            if (station.raw().containsKey("ownership")) continue;
            if (!(station.key() instanceof String || station.key() instanceof Integer)) { classify(station, "HELD"); continue; }
            Object legacy = station.raw().get("owner");
            if (legacy == null) { classify(station, "OWNERLESS"); continue; }
            UUID account;
            try { account = uuid(legacy); location(station.raw()); }
            catch (IOException ex) { classify(station, "HELD"); continue; }
            var heir = identity.heir(account);
            if (heir.isEmpty() || !account.equals(heir.orElseThrow().account())) continue;
            Map<Object, Object> after = GearYaml.copy(station.raw()); UUID id = UUID.randomUUID();
            after.put("occupancy-id", id.toString()); after.put("ownership", ownership(heir.orElseThrow())); after.put("effect-state", "active");
            JsonObject entry = plan("station", "station:" + location(after) + "@" + id, id, "gear-stations.yml", "gear-stations.yml", GearYaml.bytes(station.raw()), GearYaml.bytes(after));
            entry.addProperty("selector", b64(GearYaml.bytes(Map.of("key", station.key())))); entry.addProperty("from-account", account.toString()); ref(entry, "to", heir.orElseThrow());
            journal.getAsJsonArray("entries").add(entry);
        }
        if (entries(journal).isEmpty()) return;
        for (var entry : entries(journal)) backup(payload(entry, "before"));
        Path file = root.resolve("gear-ownership/migration").resolve(run + ".json"); write(file, journal);
        journal(file, "migration", null, null);
        for (var entry : entries(journal)) apply(file, journal, entry, () -> identity.heirsReady(), false);
    }
    private void classify(Station station, String kind) throws IOException {
        Map<Object, Object> after = GearYaml.copy(station.raw()); after.put("ownership", Map.of("format", 1, "kind", kind));
        after.put("occupancy-id", UUID.randomUUID().toString()); after.put("effect-state", "HELD".equals(kind) ? "held" : "active"); patch(station, after);
    }
    public static Map<Object, Object> ownership(Owner owner) {
        Map<Object, Object> authority = new LinkedHashMap<>();
        authority.put("format", 1); authority.put("kind", "CHARACTER");
        authority.put("account", owner.account().toString()); authority.put("character", owner.character().toString());
        return authority;
    }

    public Path returnFile(Owner owner, UUID id) { return root.resolve("gear-returns").resolve(owner.character().toString()).resolve(id + ".json"); }
    public void createReturn(Owner owner, UUID id, List<String> items, String state) throws IOException {
        for (Path existing : returnFiles()) if (existing.getFileName().toString().equals(id + ".json")) throw new Conflict("Return operation ID already exists");
        JsonObject row = new JsonObject(); row.addProperty("format", 1); row.addProperty("id", id.toString()); setReturnOwner(row, owner); row.addProperty("state", state);
        JsonArray payload = new JsonArray(); items.forEach(payload::add); row.add("items", payload); validateReturn(returnFile(owner, id), row);
        GearFiles.write(returnFile(owner, id), GearJson.bytes(row), false);
    }
    public List<JsonObject> returns(Owner owner) throws IOException {
        List<JsonObject> rows = new ArrayList<>();
        for (Path file : returnFiles()) {
            JsonObject row = read(file); validateReturn(file, row);
            if (returnOwner(row).equals(owner)) rows.add(row);
        }
        return rows;
    }
    public void saveReturn(JsonObject expected, JsonObject after) throws IOException {
        Path file = returnFile(returnOwner(expected), uuid(text(expected, "id"))); validateReturn(file, after);
        if (!java.util.Arrays.equals(GearJson.bytes(read(file)), GearJson.bytes(expected))) throw new Conflict("Return changed before publication"); write(file, after);
    }
    private List<Path> returnFiles() throws IOException {
        List<Path> paths = new ArrayList<>(); for (Path folder : files(root.resolve("gear-returns"))) paths.addAll(files(folder));
        Map<UUID, List<Path>> ids = new LinkedHashMap<>();
        for (Path file : paths) { JsonObject row = read(file); validateReturn(file, row); ids.computeIfAbsent(uuid(text(row, "id")), ignored -> new ArrayList<>()).add(file); }
        for (var entry : ids.entrySet()) if (entry.getValue().size() > 1 && !fixedPublicationPair(entry.getKey(), entry.getValue())) throw new Conflict("Duplicate global return operation ID");
        return paths;
    }
    private boolean fixedPublicationPair(UUID id, List<Path> copies) throws IOException {
        if (copies.size() != 2) return false;
        for (Path file : files(root.resolve("gear-ownership/estates"))) {
            JsonObject raw = read(file); JsonObject journal = journal(file, "estate", uuid(text(raw, "death")), ref(raw, "from"));
            for (JsonObject entry : entries(journal)) if ("return".equals(text(entry, "type")) && id.toString().equals(text(entry, "id")) && !"moved".equals(text(entry, "phase")) && !Files.exists(receiptFile(uuid(text(entry, "death")), text(entry, "property")))) {
                Path source = path(text(entry, "source")), target = path(text(entry, "target"));
                if (copies.contains(source) && copies.contains(target) && hash(Files.readAllBytes(source)).equals(text(entry, "before-hash")) && hash(Files.readAllBytes(target)).equals(text(entry, "after-hash"))) return true;
            }
        }
        return false;
    }
    public static Owner returnOwner(JsonObject row) throws IOException { return new Owner(uuid(text(row, "owner-account")), uuid(text(row, "owner-character"))); }
    public static void setReturnOwner(JsonObject row, Owner owner) { row.addProperty("owner-account", owner.account().toString()); row.addProperty("owner-character", owner.character().toString()); }
    public void validateReturn(Path file, JsonObject row) throws IOException {
        try {
        if (!integer(row, "format", 1) || !file.equals(returnFile(returnOwner(row), uuid(text(row, "id")))) || !List.of("pending", "delivering", "held", "delivered").contains(text(row, "state")) || !row.get("items").isJsonArray()) throw new Conflict("Return schema/full owner/path needs review");
        for (var item : row.getAsJsonArray("items")) bytes(item.getAsString());
        } catch (RuntimeException ex) { throw new Conflict("Malformed return item/envelope"); }
    }

    public void requireReady(Owner owner) throws IOException {
        if (!identity.heirsReady()) throw new IOException("Reviewed legacy ownership is not ready");
        migrate();
        for (Station station : stations()) {
            Map<Object, Object> row = station.raw();
            if (!row.containsKey("ownership") && owner.account().toString().equals(row.get("owner"))) throw new Conflict("Legacy station still needs reviewed ownership");
            if (row.get("ownership") instanceof Map<?, ?> own && "CHARACTER".equals(own.get("kind"))) {
                Owner resolved = owner(row);
                if (owner.equals(resolved) && !List.of("active", "released").contains(state(row))) throw new Conflict("Inventory/world intent needs staff recovery");
            } else if (owner.account().toString().equals(row.get("owner"))) throw new Conflict("Malformed station ownership needs review");
        }
        for (JsonObject row : returns(owner)) if (List.of("held", "delivering").contains(text(row, "state"))) throw new Conflict("Return inventory intent needs staff recovery");
    }
    public void requireUnlocked(Owner owner) throws IOException {
        for (Path file : files(root.resolve("gear-ownership/migration"))) {
            JsonObject journal = journal(file, "migration", null, null);
            for (JsonObject entry : entries(journal)) if (!"moved".equals(text(entry, "phase")) && owner.equals(ref(entry, "to"))) throw new Conflict("Fixed legacy publication needs recovery");
        }
        for (Path file : files(root.resolve("gear-ownership/estates"))) {
            JsonObject raw = read(file); JsonObject journal = journal(file, "estate", uuid(text(raw, "death")), ref(raw, "from"));
            checkReceipts(journal, uuid(text(raw, "death")), ref(raw, "from"));
            checkCompletions(journal, uuid(text(raw, "death")), ref(raw, "from"));
            for (JsonObject entry : entries(journal)) if (!"moved".equals(text(entry, "phase")) && (owner.equals(ref(entry, "from")) || owner.equals(ref(entry, "to")))) throw new Conflict("Fixed estate publication needs recovery");
        }
    }
    public List<String> transfer(UUID death, String decision, Owner from, Owner to, BooleanSupplier guard) throws IOException {
        long revision = decision(decision, death, from, to);
        Path file = root.resolve("gear-ownership/estates").resolve(death + ".json"); JsonObject journal = estate(file, death, from);
        checkReceipts(journal, death, from);
        checkCompletions(journal, death, from);
        if (completed(journal, death, decision, revision, from)) return moved(journal);
        boolean continuing = false; var plannedIds = new java.util.HashSet<String>();
        for (JsonObject entry : entries(journal)) if (entry.get("revision").getAsLong() == revision) { continuing = true; plannedIds.add(text(entry, "property")); }
        if (continuing && !plannedIds.containsAll(properties(from))) throw new Conflict("New source holdings require a newer estate revision");
        for (JsonObject entry : entries(journal)) if (!"moved".equals(text(entry, "phase")) && !decision.equals(text(entry, "decision"))) {
            if (!untouched(entry)) throw new Conflict("Older partial estate publication needs recovery");
        }
        var kept = new JsonArray(); for (JsonObject entry : entries(journal)) if ("moved".equals(text(entry, "phase")) || decision.equals(text(entry, "decision"))) kept.add(entry);
        journal.add("entries", kept);
        if (!from.equals(to)) {
            for (Station station : stations()) {
                Map<Object, Object> row = station.raw();
                if (!(row.get("ownership") instanceof Map<?, ?> own) || !"CHARACTER".equals(own.get("kind")) || !from.equals(owner(row)) || "released".equals(state(row))) continue;
                if (!"active".equals(state(row))) throw new Conflict("Station effect is unresolved");
                UUID id = id(row); String property = "station:" + location(row) + "@" + id;
                if (has(journal, property)) continue;
                Map<Object, Object> after = GearYaml.copy(row); Map<Object, Object> authority = new LinkedHashMap<>((Map<?, ?>) after.get("ownership")); authority.put("account", to.account().toString()); authority.put("character", to.character().toString()); after.put("ownership", authority);
                JsonObject entry = plan("station", property, id, "gear-stations.yml", "gear-stations.yml", GearYaml.bytes(row), GearYaml.bytes(after)); entry.addProperty("selector", b64(GearYaml.bytes(Map.of("key", station.key())))); estateEnvelope(entry, death, decision, revision, from, to); kept.add(entry);
            }
            for (JsonObject row : returns(from)) {
                if ("delivered".equals(text(row, "state"))) continue;
                if (!"pending".equals(text(row, "state"))) throw new Conflict("Return effect is unresolved");
                UUID id = uuid(text(row, "id")); String property = "return:" + id; if (has(journal, property)) continue;
                JsonObject after = row.deepCopy(); setReturnOwner(after, to);
                JsonObject entry = plan("return", property, id, relative(returnFile(from, id)), relative(returnFile(to, id)), GearJson.bytes(row), GearJson.bytes(after)); estateEnvelope(entry, death, decision, revision, from, to); kept.add(entry);
            }
        }
        for (var entry : entries(journal)) backup(payload(entry, "before"));
        write(file, journal); journal(file, "estate", death, from);
        for (var entry : entries(journal)) preflight(entry);
        for (JsonObject entry : entries(journal)) if (!"moved".equals(text(entry, "phase"))) apply(file, journal, entry, guard, true);
        complete(file, journal, death, decision, revision, from, guard);
        return moved(journal);
    }
    public List<String> retained(UUID death, String decision, Owner from, BooleanSupplier guard) throws IOException {
        Path file = root.resolve("gear-ownership/estates").resolve(death + ".json"); JsonObject journal = estate(file, death, from); checkReceipts(journal, death, from);
        checkCompletions(journal, death, from);
        Captured request = captured(decision, death, from);
        if (request.to() != null) throw new Conflict("Retained decision has a character recipient");
        if (completed(journal, death, decision, request.revision(), from)) return moved(journal);
        JsonArray retainedEntries = new JsonArray();
        for (JsonObject entry : entries(journal)) {
            if ("moved".equals(text(entry, "phase"))) retainedEntries.add(entry);
            else if (entry.get("revision").getAsLong() >= request.revision() || !untouched(entry)) throw new Conflict("Partial estate publication needs recovery");
        }
        journal.add("entries", retainedEntries);
        complete(file, journal, death, decision, request.revision(), from, guard);
        return moved(journal);
    }
    /** Reads all historical evidence before the caller performs legacy migration or effects. */
    public void validateEstate(UUID death, String decision, Owner from) throws IOException {
        JsonObject journal = estate(root.resolve("gear-ownership/estates").resolve(death + ".json"), death, from);
        checkReceipts(journal, death, from); checkCompletions(journal, death, from);
        Captured request = captured(decision, death, from); completed(journal, death, decision, request.revision(), from);
    }
    private JsonObject estate(Path file, UUID death, Owner from) throws IOException {
        if (Files.exists(file)) return journal(file, "estate", death, from);
        JsonObject raw = new JsonObject(); raw.addProperty("format", 1); raw.addProperty("kind", "estate"); raw.addProperty("death", death.toString()); ref(raw, "from", from); raw.add("entries", new JsonArray()); raw.add("completions", new JsonObject()); return raw;
    }

    private record Captured(long revision, String kind, Owner to) {}
    private static Captured captured(String value, UUID death, Owner from) throws IOException {
        var suffix = java.util.regex.Pattern.compile(", revision=(0|[1-9][0-9]*)]$").matcher(value);
        String prefix = "EstateDecision[deathId=" + death + ", testator=" + reference(from) + ", diedAt=";
        int start = value.indexOf(", property="), end = value.indexOf(", guildLocks=", start);
        if (!value.startsWith(prefix) || !suffix.find() || start < 0 || end < 0) throw new Conflict("Captured estate envelope differs");
        long revision;
        try { revision = Long.parseLong(suffix.group(1)); } catch (NumberFormatException ex) { throw new Conflict("Unsupported estate revision"); }
        String property = value.substring(start + ", property=".length(), end);
        if (property.equals("Waiting[]")) return new Captured(revision, "WAITING", null);
        if (property.equals("Frozen[]")) return new Captured(revision, "FROZEN", null);
        var character = java.util.regex.Pattern.compile("Character\\[ref=CharacterRef\\[ownerId=([^,]+), characterId=([^]]+)]]").matcher(property);
        if (!character.matches()) throw new Conflict("Unsupported property recipient");
        return new Captured(revision, "CHARACTER", new Owner(uuid(character.group(1)), uuid(character.group(2))));
    }
    private List<String> properties(Owner from) throws IOException {
        var ids = new java.util.TreeSet<String>();
        for (Station station : stations()) if (station.raw().get("ownership") instanceof Map<?, ?> own && "CHARACTER".equals(own.get("kind")) && from.equals(owner(station.raw())) && !"released".equals(state(station.raw()))) ids.add("station:" + location(station.raw()) + "@" + id(station.raw()));
        for (JsonObject row : returns(from)) if (!"delivered".equals(text(row, "state"))) ids.add("return:" + text(row, "id"));
        return new ArrayList<>(ids);
    }
    private Path completionFile(UUID death, long revision) { return root.resolve("gear-ownership/completions").resolve(death.toString()).resolve(revision + ".json"); }
    private static List<String> strings(JsonObject record, String field) throws IOException {
        try {
            List<String> result = new ArrayList<>(); for (var id : record.getAsJsonArray(field)) {
                if (!id.isJsonPrimitive() || !id.getAsJsonPrimitive().isString()) throw new Conflict("Completion property ID is not text");
                String value = id.getAsString();
                if (value.startsWith("return:")) uuid(value.substring(7));
                else if (value.startsWith("station:")) {
                    int at = value.lastIndexOf('@'); if (at < 8) throw new Conflict("Invalid station receipt ID"); uuid(value.substring(at + 1));
                    String[] parts = value.substring(8, at).split(",", -1); if (parts.length != 4 || parts[0].isBlank()) throw new Conflict("Invalid station completion location");
                    for (int i = 1; i < parts.length; i++) { int coordinate = Integer.parseInt(parts[i]); if (!Integer.toString(coordinate).equals(parts[i])) throw new Conflict("Noncanonical station completion coordinate"); }
                }
                else throw new Conflict("Unknown completion property type");
                result.add(value);
            }
            if (!result.equals(new ArrayList<>(new java.util.TreeSet<>(result)))) throw new Conflict("Completion IDs are duplicated or unordered");
            return result;
        } catch (RuntimeException ex) { throw new Conflict("Malformed completion IDs"); }
    }
    private static List<String> movedAt(JsonObject journal, long revision) throws IOException {
        var ids = new java.util.TreeSet<String>();
        for (JsonObject entry : entries(journal)) if ("moved".equals(text(entry, "phase")) && entry.get("revision").getAsLong() <= revision) ids.add(text(entry, "property"));
        return new ArrayList<>(ids);
    }
    private void checkCompletions(JsonObject journal, UUID death, Owner from) throws IOException {
        try {
            JsonObject completions = journal.getAsJsonObject("completions"); if (completions == null) throw new Conflict("Completion inventory is missing");
            var expected = new java.util.HashSet<Path>();
            for (var completion : completions.entrySet()) {
                JsonObject row = completion.getValue().getAsJsonObject(); Captured decision = captured(text(row, "decision"), death, from); long revision = decision.revision(); Path file = completionFile(death, revision); expected.add(file);
                if (!completion.getKey().equals(Long.toString(revision)) || !integer(row, "format", 1) || !"completion".equals(text(row, "kind")) || !death.equals(uuid(text(row, "death"))) || !from.equals(ref(row, "from")) || !integer(row, "revision", revision) || !decision.kind().equals(text(row, "recipient-kind"))) throw new Conflict("Completion full envelope differs");
                if (decision.to() == null ? row.has("to-account") || row.has("to-character") : !decision.to().equals(ref(row, "to"))) throw new Conflict("Completion full recipient differs");
                List<String> moved = strings(row, "moved"), retained = strings(row, "retained");
                for (JsonObject entry : entries(journal)) if (entry.get("revision").getAsLong() == revision && !text(row, "decision").equals(text(entry, "decision"))) throw new Conflict("Historical completion and captured entries disagree");
                if (!moved.equals(movedAt(journal, revision)) || !row.getAsJsonObject("counts").keySet().equals(java.util.Set.of("gear")) || !integer(row.getAsJsonObject("counts"), "gear", moved.size()) || retained.stream().anyMatch(moved::contains)) throw new Conflict("Completion counts/property inventory differs");
                if (!Files.exists(file) || !java.util.Arrays.equals(Files.readAllBytes(file), GearJson.bytes(row))) throw new Conflict("Independent completion evidence differs");
            }
            if (!expected.equals(new java.util.HashSet<>(files(root.resolve("gear-ownership/completions").resolve(death.toString()))))) throw new Conflict("Completion inventory disappeared or grew");
        } catch (RuntimeException ex) { throw new Conflict("Malformed completion evidence"); }
    }
    private boolean completed(JsonObject journal, UUID death, String decision, long revision, Owner from) throws IOException {
        for (JsonObject entry : entries(journal)) if (entry.get("revision").getAsLong() == revision && !decision.equals(text(entry, "decision"))) throw new Conflict("Same revision captured a different full decision");
        JsonObject completions = journal.getAsJsonObject("completions"); JsonObject record = completions.getAsJsonObject(Long.toString(revision));
        for (String previous : completions.keySet()) if (Long.parseLong(previous) > revision) throw new Conflict("Estate revision went backwards");
        if (record == null) return false;
        if (!decision.equals(text(record, "decision")) || !strings(record, "retained").equals(properties(from))) throw new Conflict("Completed revision changed its decision or source holdings");
        return true;
    }
    private void complete(Path file, JsonObject journal, UUID death, String decision, long revision, Owner from, BooleanSupplier guard) throws IOException {
        Captured recipient = captured(decision, death, from);
        JsonObject record = new JsonObject(); record.addProperty("format", 1); record.addProperty("kind", "completion"); record.addProperty("death", death.toString()); ref(record, "from", from); record.addProperty("revision", revision); record.addProperty("decision", decision); record.addProperty("recipient-kind", recipient.kind()); if (recipient.to() != null) ref(record, "to", recipient.to());
        JsonArray moved = new JsonArray(); movedAt(journal, revision).forEach(moved::add); record.add("moved", moved); JsonArray retained = new JsonArray(); properties(from).forEach(retained::add); record.add("retained", retained); JsonObject counts = new JsonObject(); counts.addProperty("gear", moved.size()); record.add("counts", counts);
        if (recipient.to() != null && !recipient.to().equals(from) && !retained.isEmpty()) throw new Conflict("New source holdings require a newer estate revision");
        if (!guard.getAsBoolean()) throw new IOException("Estate changed before completion publication");
        if (!strings(record, "retained").equals(properties(from))) throw new Conflict("Source holdings changed while checking completion guard");
        checkReceipts(journal, death, from);
        immutable(completionFile(death, revision), GearJson.bytes(record)); journal.getAsJsonObject("completions").add(Long.toString(revision), record); write(file, journal);
    }
    private boolean untouched(JsonObject entry) throws IOException {
        return "planned".equals(text(entry, "phase")) && sourceMatches(entry, "before")
                && ("station".equals(text(entry, "type")) || !Files.exists(path(text(entry, "target"))));
    }
    private static void estateEnvelope(JsonObject entry, UUID death, String decision, long revision, Owner from, Owner to) { entry.addProperty("death", death.toString()); entry.addProperty("decision", decision); entry.addProperty("revision", revision); ref(entry, "from", from); ref(entry, "to", to); }
    private JsonObject journal(Path file, String kind, UUID death, Owner from) throws IOException {
        try {
            JsonObject raw = read(file);
            if (!integer(raw, "format", 1) || !kind.equals(text(raw, "kind"))) throw new Conflict("Unsupported ownership journal");
            if ("estate".equals(kind)) { if (!death.equals(uuid(text(raw, "death"))) || !from.equals(ref(raw, "from")) || !file.getFileName().toString().equals(death + ".json")) throw new Conflict("Estate journal full source differs"); }
            else if (!file.getFileName().toString().equals(uuid(text(raw, "run")) + ".json")) throw new Conflict("Migration run differs");
            var seen = new java.util.HashSet<String>(); var selectors = new java.util.HashSet<Object>(); var ids = new java.util.HashSet<String>();
            for (JsonObject entry : entries(raw)) {
                validatePlan(entry, kind, death, from);
                if (!seen.add(text(entry, "property")) || !ids.add(text(entry, "type") + ":" + text(entry, "id")) || "station".equals(text(entry, "type")) && !selectors.add(selector(entry))) throw new Conflict("Duplicate property/selector plan");
            }
            return raw;
        } catch (RuntimeException ex) { throw new Conflict("Malformed ownership journal"); }
    }
    @SuppressWarnings("unchecked")
    private void validatePlan(JsonObject entry, String kind, UUID death, Owner from) throws IOException {
        String type = text(entry, "type"), property = text(entry, "property"); UUID id = uuid(text(entry, "id")); Owner to = ref(entry, "to");
        if (!List.of("planned", "published", "moved").contains(text(entry, "phase"))) throw new Conflict("Invalid ownership phase");
        byte[] before = payload(entry, "before"), after = payload(entry, "after");
        if ("estate".equals(kind) && (from.equals(to) || !death.equals(uuid(text(entry, "death"))) || !from.equals(ref(entry, "from")) || !integer(entry, "revision", decision(text(entry, "decision"), death, from, to)))) throw new Conflict("Estate captured envelope differs");
        if ("station".equals(type)) {
            Map<Object, Object> a = GearYaml.read(before), b = GearYaml.read(after); selector(entry);
            if (!path(text(entry, "source")).equals(stationFile()) || !path(text(entry, "target")).equals(stationFile()) || !id.equals(id(b)) || !property.equals("station:" + location(b) + "@" + id) || !to.equals(owner(b))) throw new Conflict("Station payload/path/recipient differs");
            Map<Object, Object> expected = GearYaml.copy(a);
            if ("migration".equals(kind)) {
                UUID account = uuid(a.get("owner")); if (!account.toString().equals(text(entry, "from-account")) || !account.equals(to.account()) || a.containsKey("ownership") || a.containsKey("occupancy-id") || a.containsKey("effect-state")) throw new Conflict("Migration source authority differs");
                expected.put("occupancy-id", id.toString()); expected.put("ownership", ownership(to)); expected.put("effect-state", "active");
            } else {
                if (!from.equals(owner(a)) || !id.equals(id(a)) || !"active".equals(state(a))) throw new Conflict("Estate station source authority differs");
                Map<Object, Object> own = new LinkedHashMap<>((Map<Object, Object>) expected.get("ownership")); own.put("account", to.account().toString()); own.put("character", to.character().toString()); expected.put("ownership", own);
            }
            if (!GearYaml.equal(expected, b)) throw new Conflict("Ownership plan changes station payload");
        } else if ("return".equals(type) && "estate".equals(kind)) {
            JsonObject a = GearJson.parse(new String(before, StandardCharsets.UTF_8)), b = GearJson.parse(new String(after, StandardCharsets.UTF_8));
            validateReturn(returnFile(from, id), a); validateReturn(returnFile(to, id), b);
            if (entry.has("selector") || !property.equals("return:" + id) || !path(text(entry, "source")).equals(returnFile(from, id)) || !path(text(entry, "target")).equals(returnFile(to, id)) || !"pending".equals(text(a, "state"))) throw new Conflict("Return source/target differs");
            JsonObject expected = a.deepCopy(); setReturnOwner(expected, to); if (!java.util.Arrays.equals(GearJson.bytes(expected), GearJson.bytes(b))) throw new Conflict("Ownership plan changes return payload");
        } else throw new Conflict("Unsupported property plan type");
        Path backup = root.resolve("gear-ownership/backups").resolve(hash(before) + ".bin");
        if (!Files.exists(backup) || !java.util.Arrays.equals(Files.readAllBytes(backup), before)) throw new Conflict("Immutable source archive differs");
    }
    private void preflight(JsonObject entry) throws IOException {
        if ("moved".equals(text(entry, "phase"))) return;
        if ("station".equals(text(entry, "type"))) {
            if (!sourceMatches(entry, "before") && !sourceMatches(entry, "after")) throw new Conflict("Station current frame differs");
        } else {
            Path source = path(text(entry, "source")), target = path(text(entry, "target"));
            if (Files.exists(source) && !sourceMatches(entry, "before")) throw new Conflict("Return current source differs");
            if (!Files.exists(source) && "planned".equals(text(entry, "phase"))) throw new Conflict("Return current source disappeared");
            if (Files.exists(target) && !hash(Files.readAllBytes(target)).equals(text(entry, "after-hash"))) throw new Conflict("Return current target differs");
            if (!Files.exists(source) && !Files.exists(target)) throw new Conflict("Return copies disappeared");
        }
    }
    private void apply(Path file, JsonObject journal, JsonObject entry, BooleanSupplier guard, boolean estate) throws IOException {
        if ("moved".equals(text(entry, "phase"))) return;
        byte[] after = payload(entry, "after");
        if ("station".equals(text(entry, "type"))) {
            Map<Object, Object> document = document(); Object key = selector(entry); Object current = mapping(document).get(key);
            if (!(current instanceof Map<?, ?> map)) throw new Conflict("Station source disappeared");
            boolean before = GearYaml.equal(map, GearYaml.read(payload(entry, "before"))), already = GearYaml.equal(map, GearYaml.read(after));
            if (!before && !already) throw new Conflict("Station changed after planning");
            if (!guard.getAsBoolean()) throw new IOException("Ownership guard changed before station publication");
            // A provider callback can change another row; publish against the latest whole file.
            document = document(); current = mapping(document).get(key);
            if (!(current instanceof Map<?, ?> latest) || !GearYaml.equal(latest, GearYaml.read(payload(entry, before ? "before" : "after")))) throw new Conflict("Station changed while checking ownership guard");
            if (before) { mapping(document).put(key, GearYaml.read(after)); saveStations(document); }
            entry.addProperty("phase", "published"); write(file, journal);
        } else {
            Path source = path(text(entry, "source")), target = path(text(entry, "target"));
            if (Files.exists(source) && !hash(Files.readAllBytes(source)).equals(text(entry, "before-hash"))) throw new Conflict("Return source changed after planning");
            if (!Files.exists(source) && "planned".equals(text(entry, "phase"))) throw new Conflict("Return source disappeared");
            if (Files.exists(target)) { if (!hash(Files.readAllBytes(target)).equals(text(entry, "after-hash"))) throw new Conflict("Return target conflicts"); }
            else { if (!guard.getAsBoolean()) throw new IOException("Ownership guard changed before return publication"); GearFiles.write(target, after, false); }
            entry.addProperty("phase", "published"); write(file, journal);
            if (!guard.getAsBoolean()) throw new IOException("Ownership guard changed before return source removal");
            if (Files.exists(source) && !hash(Files.readAllBytes(source)).equals(text(entry, "before-hash"))) throw new Conflict("Return changed while checking removal guard");
            GearFiles.delete(source);
        }
        if (estate) {
            if (!guard.getAsBoolean()) throw new IOException("Ownership guard changed before receipt publication");
            JsonObject receipt = entry.deepCopy(); receipt.addProperty("phase", "moved"); receipt.addProperty("format", 1); receipt.addProperty("kind", "receipt");
            immutable(receiptFile(uuid(text(entry, "death")), text(entry, "property")), GearJson.bytes(receipt));
        }
        entry.addProperty("phase", "moved"); write(file, journal);
    }
    private void checkReceipts(JsonObject journal, UUID death, Owner from) throws IOException {
        Map<String, JsonObject> receipts = new LinkedHashMap<>();
        for (Path file : files(root.resolve("gear-ownership/receipts").resolve(death.toString()))) {
            JsonObject receipt = read(file);
            if (!integer(receipt, "format", 1) || !"receipt".equals(text(receipt, "kind")) || !"moved".equals(text(receipt, "phase")) || !file.equals(receiptFile(death, text(receipt, "property")))) throw new Conflict("Immutable receipt envelope differs");
            validatePlan(receipt, "estate", death, from); receipts.put(text(receipt, "property"), receipt);
        }
        for (JsonObject entry : entries(journal)) {
            JsonObject receipt = receipts.remove(text(entry, "property"));
            if (receipt == null) { if ("moved".equals(text(entry, "phase"))) throw new Conflict("Completed receipt disappeared"); continue; }
            JsonObject expected = entry.deepCopy(); expected.addProperty("phase", "moved"); expected.addProperty("format", 1); expected.addProperty("kind", "receipt");
            if (!java.util.Arrays.equals(GearJson.bytes(receipt), GearJson.bytes(expected))) throw new Conflict("Journal and independent receipt disagree");
            if (sourceAuthority(entry, from)) throw new Conflict("Completed source property was restored");
            entry.addProperty("phase", "moved");
        }
        if (!receipts.isEmpty()) throw new Conflict("Completed journal property IDs disappeared");
    }
    private boolean sourceAuthority(JsonObject entry, Owner from) throws IOException {
        if ("return".equals(text(entry, "type"))) return Files.exists(path(text(entry, "source")));
        UUID id = uuid(text(entry, "id"));
        for (Station station : stations()) if (id.toString().equals(station.raw().get("occupancy-id")) && from.equals(owner(station.raw()))) return true;
        return false;
    }
    private boolean sourceMatches(JsonObject entry, String part) throws IOException {
        if ("station".equals(text(entry, "type"))) {
            Object row = mapping(document()).get(selector(entry)); return row instanceof Map<?, ?> map && GearYaml.equal(map, GearYaml.read(payload(entry, part)));
        }
        Path source = path(text(entry, "source")); return Files.exists(source) && hash(Files.readAllBytes(source)).equals(text(entry, part + "-hash"));
    }
    private Path receiptFile(UUID death, String property) { return root.resolve("gear-ownership/receipts").resolve(death.toString()).resolve(hash(property.getBytes(StandardCharsets.UTF_8)) + ".json"); }
    private void backup(byte[] before) throws IOException { immutable(root.resolve("gear-ownership/backups").resolve(hash(before) + ".bin"), before); }
    private static void immutable(Path file, byte[] bytes) throws IOException {
        if (Files.exists(file)) { if (!java.util.Arrays.equals(Files.readAllBytes(file), bytes)) throw new Conflict("Immutable ownership evidence changed"); }
        else GearFiles.write(file, bytes, false);
    }
    private Path path(String relative) throws IOException {
        Path path = root.resolve(relative).normalize(); if (!path.startsWith(root) || !relative(path).equals(relative)) throw new Conflict("Ownership path escapes expected storage");
        for (Path ancestor = path; ; ancestor = ancestor.getParent()) {
            if (Files.isSymbolicLink(ancestor)) throw new Conflict("Ownership path is a symlink");
            if (ancestor.equals(root)) break;
        }
        return path;
    }
    private String relative(Path path) { return root.relativize(path).toString(); }
    private static Object selector(JsonObject entry) throws IOException {
        Map<Object, Object> selector = GearYaml.read(bytes(text(entry, "selector"))); Object key = selector.get("key");
        if (selector.size() != 1 || !(key instanceof String || key instanceof Integer)) throw new Conflict("Station selector needs review"); return key;
    }
    private static JsonObject plan(String type, String property, UUID id, String source, String target, byte[] before, byte[] after) {
        JsonObject entry = new JsonObject(); entry.addProperty("type", type); entry.addProperty("property", property); entry.addProperty("id", id.toString()); entry.addProperty("source", source); entry.addProperty("target", target); entry.addProperty("phase", "planned");
        for (var payload : Map.of("before", before, "after", after).entrySet()) { entry.addProperty(payload.getKey(), b64(payload.getValue())); entry.addProperty(payload.getKey() + "-hash", hash(payload.getValue())); } return entry;
    }
    private static byte[] payload(JsonObject entry, String field) throws IOException { byte[] bytes = bytes(text(entry, field)); if (!hash(bytes).equals(text(entry, field + "-hash"))) throw new Conflict("Ownership payload hash differs"); return bytes; }
    public static long decision(String value, UUID death, Owner from, Owner to) throws IOException {
        var revision = java.util.regex.Pattern.compile(", revision=(0|[1-9][0-9]*)]$").matcher(value);
        if (!value.startsWith("EstateDecision[deathId=" + death + ", testator=" + reference(from) + ", diedAt=") || !value.contains(", property=Character[ref=" + reference(to) + "], guildLocks=") || !revision.find()) throw new Conflict("Captured full estate decision differs");
        try { return Long.parseLong(revision.group(1)); } catch (NumberFormatException ex) { throw new Conflict("Unsupported estate revision"); }
    }
    private static String reference(Owner owner) { return "CharacterRef[ownerId=" + owner.account() + ", characterId=" + owner.character() + "]"; }
    private static boolean integer(JsonObject raw, String key, long expected) { return raw.has(key) && raw.get(key).toString().equals(Long.toString(expected)); }
    public static String text(JsonObject raw, String key) throws IOException {
        if (!raw.has(key) || !raw.get(key).isJsonPrimitive() || !raw.getAsJsonPrimitive(key).isString()) throw new Conflict("Missing text field: " + key); return raw.get(key).getAsString();
    }
    public static List<JsonObject> entries(JsonObject journal) { List<JsonObject> result = new ArrayList<>(); for (var entry : journal.getAsJsonArray("entries")) result.add(entry.getAsJsonObject()); return result; }
    private static boolean has(JsonObject journal, String property) throws IOException { for (JsonObject entry : entries(journal)) if (property.equals(text(entry, "property"))) return true; return false; }
    private static List<String> moved(JsonObject journal) throws IOException { List<String> result = new ArrayList<>(); for (JsonObject entry : entries(journal)) if ("moved".equals(text(entry, "phase"))) result.add(text(entry, "property")); return result; }
    private static void ref(JsonObject raw, String prefix, Owner owner) { raw.addProperty(prefix + "-account", owner.account().toString()); raw.addProperty(prefix + "-character", owner.character().toString()); }
    private static Owner ref(JsonObject raw, String prefix) throws IOException { return new Owner(uuid(text(raw, prefix + "-account")), uuid(text(raw, prefix + "-character"))); }
    public static String b64(byte[] bytes) { return Base64.getEncoder().encodeToString(bytes); }
    public static byte[] bytes(String value) throws IOException { try { return Base64.getDecoder().decode(value); } catch (IllegalArgumentException ex) { throw new Conflict("Invalid item/payload bytes"); } }
    public static String hash(byte[] bytes) { try { return java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(bytes)); } catch (java.security.NoSuchAlgorithmException ex) { throw new AssertionError(ex); } }
    public static JsonObject read(Path file) throws IOException { return GearJson.parse(Files.readString(file, StandardCharsets.UTF_8)); }
    public static void write(Path file, JsonObject raw) throws IOException { GearFiles.write(file, GearJson.bytes(raw), true); }
    private static List<Path> files(Path folder) throws IOException {
        if (!Files.exists(folder)) return List.of();
        List<Path> files = new ArrayList<>();
        try (var paths = Files.list(folder)) {
            for (Path path : paths.sorted().toList()) {
                String name = path.getFileName().toString();
                if (name.startsWith("magic-") && name.endsWith(".tmp")) {
                    if (!Files.isRegularFile(path, java.nio.file.LinkOption.NOFOLLOW_LINKS)) throw new Conflict("Temporary-looking gear path is not a regular file");
                    continue;
                }
                files.add(path);
            }
        }
        return files;
    }
}
