package net.tfminecraft.magic.gear;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Display.Billboard;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import net.tfminecraft.magic.Magic;
import net.tfminecraft.tlibs.TLibs;

public final class GearStationStore {

    private static final Map<String, Occupancy> OCCUPIED = new HashMap<>();
    private static boolean loading;
    private static final Map<String, Object> retainedRows = new LinkedHashMap<>();
    private static boolean unreadableStore;
    private static net.tfminecraft.magic.gear.ownership.GearPropertyStore property;
    private static Magic propertyPlugin;

    private GearStationStore() {}

    public static final class Occupancy {
        private ItemStack item;
        private UUID displayId;
        private boolean orbSessionActive;
        private final UUID owner;
        private Map<String, Integer> charged;
        private final boolean rested;
        private boolean reattune;
        private UUID generation;
        private net.tfminecraft.magic.gear.ownership.GearIdentity.Owner characterOwner;

        Occupancy(ItemStack item, UUID displayId, UUID owner, Map<String, Integer> charged, boolean rested, boolean reattune) {
            this.item = item;
            this.displayId = displayId;
            this.owner = owner;
            this.charged = charged == null ? null : Map.copyOf(charged);
            this.rested = rested;
            this.reattune = rested && reattune;
        }

        /** Player who prepared the craft. Null for stations saved before owners were recorded. */
        public UUID getOwner() {
            return owner;
        }

        /**
         * Materials taken when the craft was prepared, empty when costs were bypassed.
         * Null for stations saved before charges were recorded.
         */
        public Map<String, Integer> getCharged() {
            return charged;
        }

        /**
         * A weapon the player set back on the station to take another charge.
         * It is not a fresh craft, so taking it off never refunds materials.
         */
        public boolean isRested() {
            return rested;
        }

        /**
         * A rested weapon whose owner paid to wipe its attunement. The old attunement holds
         * until a run catches something; until then {@link #getCharged()} is the paid fee.
         */
        public boolean isReattune() {
            return reattune;
        }

        public ItemStack getItem() {
            return item == null ? null : item.clone();
        }

        public UUID generation() { return generation; }
        public net.tfminecraft.magic.gear.ownership.GearIdentity.Owner characterOwner() { return characterOwner; }

        public void setItem(ItemStack item) {
            this.item = item;
            refreshDisplay();
        }

        public boolean isOrbSessionActive() {
            return orbSessionActive;
        }

        public void setOrbSessionActive(boolean orbSessionActive) {
            this.orbSessionActive = orbSessionActive;
        }

        private void refreshDisplay() {
            if (displayId == null) {
                return;
            }
            Entity entity = Bukkit.getEntity(displayId);
            if (entity instanceof ItemDisplay display) {
                display.setItemStack(item);
            }
        }
    }

    public static net.tfminecraft.magic.gear.ownership.GearPropertyStore property() {
        if (property == null || propertyPlugin != Magic.plugin) {
            propertyPlugin = Magic.plugin;
            property = new net.tfminecraft.magic.gear.ownership.GearPropertyStore(
                    Magic.plugin.getDataFolder().toPath().resolve("data"), net.tfminecraft.magic.gear.ownership.GearIdentity.load());
        }
        return property;
    }

    public static boolean isOccupied(Location location) {
        if (location == null) return false;
        try { return property().occupied(key(location)); }
        catch (IOException ex) { warning(ex); return true; }
    }

    public static Occupancy get(Location location) {
        return location == null ? null : OCCUPIED.get(key(location));
    }

    /** Compatibility entry point still requires the exact current owning character. */
    public static Occupancy occupy(Location location, ItemStack item, UUID account, Map<String, Integer> charged) {
        return place(location, item, account, charged, false);
    }
    public static Occupancy rest(Location location, ItemStack item, UUID account) {
        return place(location, item, account, Map.of(), true);
    }
    private static Occupancy place(Location location, ItemStack item, UUID account, Map<String, Integer> charged, boolean rested) {
        org.bukkit.entity.Player player = account == null ? null : Bukkit.getPlayer(account);
        var token = property().identity().current(player);
        if (token.isEmpty() || location == null || item == null) return null;
        return deposit(player, token.orElseThrow(), location, item, charged, rested, player.getInventory().getContents());
    }

    public static Occupancy deposit(org.bukkit.entity.Player player, net.tfminecraft.magic.gear.ownership.GearIdentity.Token token,
            Location location, ItemStack item, Map<String, Integer> charged, boolean rested, ItemStack[] afterInventory) {
        return deposit(player, token, location, item, charged, rested, afterInventory,
                net.tfminecraft.magic.gear.ownership.GearEffects.frame(player.getInventory().getContents()));
    }
    public static Occupancy deposit(org.bukkit.entity.Player player, net.tfminecraft.magic.gear.ownership.GearIdentity.Token token,
            Location location, ItemStack item, Map<String, Integer> charged, boolean rested, ItemStack[] afterInventory, List<String> beforeInventory) {
        return net.tfminecraft.magic.gear.ownership.GearEffects.deposit(player, token, location, item, charged, rested, afterInventory, beforeInventory);
    }

    /**
     * Caches the item read back from the row, as a restart would. Paper can write a freshly
     * built item's components in another order than its re-read copy, so caching the original
     * would make {@link #expected} refuse the station until the next restart.
     */
    public static Occupancy install(Location location, Map<Object, Object> row) throws IOException {
        UUID owner = row.get("owner") instanceof String text ? uuidOf(text) : null;
        ItemStack item = decodeItem(row);
        UUID displayId = spawnDisplay(location, item);
        Occupancy occupancy = new Occupancy(item, displayId, owner, readChargedRaw(row), Boolean.TRUE.equals(row.get("rested")), Boolean.TRUE.equals(row.get("reattune")));
        occupancy.generation = net.tfminecraft.magic.gear.ownership.GearPropertyStore.id(row);
        if (row.get("ownership") instanceof Map<?, ?> authority && "CHARACTER".equals(authority.get("kind")))
            occupancy.characterOwner = net.tfminecraft.magic.gear.ownership.GearPropertyStore.owner(row);
        OCCUPIED.put(key(location), occupancy);
        save();
        return occupancy;
    }

    public static ItemStack eject(Location location) {
        Occupancy occupancy = get(location);
        if (occupancy == null || occupancy.isOrbSessionActive() || occupancy.isReattune() || (!isAttuned(occupancy.getItem()) && !occupancy.isRested())) return null;
        return release(location);
    }
    /** A durable release intent precedes either inventory settlement or a public world drop. */
    public static ItemStack takeForAbort(Location location) { return release(location); }
    private static ItemStack release(Location location) {
        Occupancy occupancy = get(location); if (occupancy == null) return null;
        try {
            if (occupancy.characterOwner() != null) property().requireUnlocked(occupancy.characterOwner());
            var station = expected(location, occupancy);
            var after = net.tfminecraft.magic.gear.ownership.GearYaml.copy(station.raw());
            after.put("effect-state", "releasing");
            after.put("release-intent", Map.of("operation", UUID.randomUUID().toString(), "item", net.tfminecraft.magic.gear.ownership.GearPropertyStore.b64(net.tfminecraft.magic.gear.ownership.GearItems.bytes(occupancy.getItem()))));
            property().patch(station, after);
            clear(location);
            return occupancy.getItem();
        } catch (IOException ex) { warning(ex); return null; }
    }
    public static boolean acknowledgeRelease(Location location, UUID generation) {
        try {
            var station = property().station(generation);
            if (!key(location).equals(net.tfminecraft.magic.gear.ownership.GearPropertyStore.location(station.raw())) || !"releasing".equals(station.raw().get("effect-state"))) throw new IOException("Release target changed");
            var after = net.tfminecraft.magic.gear.ownership.GearYaml.copy(station.raw()); after.put("effect-state", "released"); property().patch(station, after);
            archive(false); return true;
        } catch (IOException ex) { warning(ex); return false; }
    }
    /** Settled rows leave the live file; a failure only leaves them for the next attempt. */
    static void archive(boolean report) {
        try {
            var result = property().archiveReleased();
            if (report && result.archived() > 0) Magic.plugin.getLogger().info("[Magic] Archived " + result.archived() + " released gear station rows to data/gear-stations-archive.");
            if (!result.kept().isEmpty()) Magic.plugin.getLogger().warning("[Magic] Kept released gear station rows in the live file: " + String.join(", ", result.kept()));
        } catch (IOException | RuntimeException ex) { Magic.plugin.getLogger().warning("[Magic] Could not archive released gear stations: " + ex.getMessage()); }
    }
    public static net.tfminecraft.magic.gear.ownership.GearPropertyStore.Station expected(Location location, Occupancy occupancy) throws IOException {
        if (occupancy.getItem() == null) throw new IOException("Cached gear item is unavailable");
        var station = property().station(occupancy.generation());
        if (!java.util.Arrays.asList("active", "charging").contains(station.raw().get("effect-state"))
                || !key(location).equals(net.tfminecraft.magic.gear.ownership.GearPropertyStore.location(station.raw()))
                || !java.util.Objects.equals(occupancy.characterOwner(), ownerOrNull(station.raw()))
                || !java.util.Arrays.equals(net.tfminecraft.magic.gear.ownership.GearItems.bytes(decodeItem(station.raw())), net.tfminecraft.magic.gear.ownership.GearItems.bytes(occupancy.getItem()))) throw new IOException("Occupancy authority or item changed");
        return station;
    }
    private static net.tfminecraft.magic.gear.ownership.GearIdentity.Owner ownerOrNull(Map<?, ?> row) throws IOException {
        if (row.get("ownership") instanceof Map<?, ?> authority && "OWNERLESS".equals(authority.get("kind")) && Integer.valueOf(1).equals(authority.get("format"))) return null;
        return net.tfminecraft.magic.gear.ownership.GearPropertyStore.owner(row);
    }

    /** An unattuned weapon never leaves the station, so band 0 gear cannot exist in the world. */
    public static boolean isAttuned(ItemStack item) {
        return item != null && WeaponRequirement.fromItem(item).highestBand() > 0;
    }

    public static void update(Location location, ItemStack item) {
        Occupancy occupancy = get(location);
        if (occupancy == null || item == null) return;
        update(location, occupancy, item);
    }
    public static boolean update(Location location, Occupancy captured, ItemStack item) {
        return update(location, captured, item, false);
    }
    /** Writes the re-attuned weapon and spends the paid fee, so it is a plain rested weapon again. */
    public static boolean completeReattune(Location location, Occupancy captured, ItemStack item) {
        return captured != null && captured.isReattune() && update(location, captured, item, true);
    }
    private static boolean update(Location location, Occupancy captured, ItemStack item, boolean reattuned) {
        if (get(location) != captured || item == null) return false;
        try {
            var station = expected(location, captured);
            var after = net.tfminecraft.magic.gear.ownership.GearYaml.copy(station.raw());
            after.put("item-bytes", net.tfminecraft.magic.gear.ownership.GearPropertyStore.b64(net.tfminecraft.magic.gear.ownership.GearItems.bytes(item)));
            after.put("effect-state", "active");
            if (reattuned) { after.remove("reattune"); after.put("charged", new LinkedHashMap<>()); }
            ItemStack cached = decodeItem(after); // the re-read copy, as in install
            property().patch(station, after);
            if (reattuned) { captured.reattune = false; captured.charged = Map.of(); }
            captured.setItem(cached); captured.setOrbSessionActive(false); return true;
        } catch (IOException ex) { warning(ex); return false; }
    }

    public static void shutdown() {
        save();
        for (Map.Entry<String, Occupancy> entry : OCCUPIED.entrySet()) {
            Location location = locationFromKey(entry.getKey());
            Occupancy occupancy = entry.getValue();
            if (location != null) {
                removeDisplays(location, null, occupancy.displayId);
            } else if (occupancy.displayId != null) {
                Entity entity = Bukkit.getEntity(occupancy.displayId);
                if (entity != null) {
                    entity.remove();
                }
            }
        }
        OCCUPIED.clear();
    }

    public static void clearAll() {
        shutdown();
        save();
    }

    public static void load() {
        loading = true; OCCUPIED.clear(); retainedRows.clear(); unreadableStore = false;
        try {
            property().holdInterrupted(); property().migrate(); archive(true); loadViews();
        } catch (IOException ex) { unreadableStore = true; warning(ex); }
        finally { loading = false; }
    }
    public static void loadViews() throws IOException {
        for (var station : property().stations()) {
            Map<Object, Object> row = station.raw();
            if (!java.util.Arrays.asList("active", "charging").contains(row.get("effect-state"))) continue;
            try {
                String key = net.tfminecraft.magic.gear.ownership.GearPropertyStore.location(row);
                UUID id = net.tfminecraft.magic.gear.ownership.GearPropertyStore.id(row); ownerOrNull(row);
                Location location = locationFromKey(key); if (location == null) continue;
                ItemStack item = decodeItem(row); if (item == null || item.getType().isAir()) throw new IOException("Gear item cannot be loaded");
                Occupancy existing = OCCUPIED.get(key);
                if (existing != null && id.equals(existing.generation()) && java.util.Objects.equals(existing.characterOwner(), ownerOrNull(row))) continue;
                if (existing != null) removeDisplays(location, null, existing.displayId);
                UUID display = ensureDisplay(location, item, uuidOf(row.get("display") instanceof String text ? text : null));
                Occupancy occupancy = new Occupancy(item, display, uuidOf(row.get("owner") instanceof String text ? text : null), readChargedRaw(row), Boolean.TRUE.equals(row.get("rested")), Boolean.TRUE.equals(row.get("reattune")));
                occupancy.generation = id; occupancy.characterOwner = ownerOrNull(row); OCCUPIED.put(key, occupancy);
                if (furniture(location) == Furniture.ABSENT) abandon(location);
            } catch (IOException | RuntimeException ex) { warning(ex); }
        }
    }
    public static ItemStack decodeItem(Map<?, ?> row) throws IOException {
        try {
            if (row.containsKey("item-bytes")) return net.tfminecraft.magic.gear.ownership.GearItems.item(net.tfminecraft.magic.gear.ownership.GearPropertyStore.bytes((String) row.get("item-bytes")));
            YamlConfiguration config = new YamlConfiguration();
            config.loadFromString(new String(net.tfminecraft.magic.gear.ownership.GearYaml.bytes(Map.of("item", row.get("item"))), java.nio.charset.StandardCharsets.UTF_8));
            ItemStack item = config.getItemStack("item"); if (item == null) throw new IOException("Missing gear item"); return item;
        } catch (RuntimeException | InvalidConfigurationException ex) { throw new IOException("Gear item needs recovery", ex); }
    }
    private static Map<String, Integer> readChargedRaw(Map<?, ?> row) {
        if (!(row.get("charged") instanceof Map<?, ?> costs)) return null;
        Map<String, Integer> result = new HashMap<>();
        for (Object value : costs.values()) {
            if (!(value instanceof Map<?, ?> entry) || !(entry.get("path") instanceof String path) || path.isBlank()
                    || !(entry.get("amount") instanceof Integer amount) || amount <= 0) return null;
            long total = (long) result.getOrDefault(path, 0) + amount;
            if (total > Integer.MAX_VALUE) return null;
            result.put(path, (int) total);
        }
        return result;
    }

    /**
     * When a chunk loads, a saved station with no furniture drops its weapon. A station
     * that is still there keeps a single display, including one left behind in an
     * unloaded chunk.
     */
    public static void reconcile(Chunk chunk) {
        if (loading || chunk == null) {
            return;
        }
        List<String> keys = new ArrayList<>();
        for (String stationKey : OCCUPIED.keySet()) {
            Location location = locationFromKey(stationKey);
            if (location == null || location.getWorld() != chunk.getWorld()) {
                continue;
            }
            if ((location.getBlockX() >> 4) == chunk.getX() && (location.getBlockZ() >> 4) == chunk.getZ()) {
                keys.add(stationKey);
            }
        }
        boolean changed = false;
        for (String stationKey : keys) {
            Location location = locationFromKey(stationKey);
            Occupancy occupancy = OCCUPIED.get(stationKey);
            // Spawning a preceding display can invoke plugin listeners which remove stations/worlds.
            if (location == null || occupancy == null || occupancy.getItem() == null) {
                continue;
            }
            if (furniture(location) == Furniture.ABSENT) {
                abandon(location);
                continue;
            }
            UUID displayId = ensureDisplay(location, occupancy.getItem(), occupancy.displayId);
            if (!displayId.equals(occupancy.displayId)) {
                occupancy.displayId = displayId;
                changed = true;
            }
        }
        if (changed) {
            save();
        }
    }

    /**
     * Occupied station whose block center is within {@code maxDistance} of {@code origin}.
     * 0.75 reaches a frame in this block and stops short of the next block's center.
     */
    public static Location occupiedWithin(Location origin, double maxDistance) {
        if (origin == null || origin.getWorld() == null || maxDistance < 0) {
            return null;
        }
        double maxSquared = maxDistance * maxDistance;
        Location best = null;
        double bestDistance = Double.MAX_VALUE;
        for (String stationKey : OCCUPIED.keySet()) {
            Location location = locationFromKey(stationKey);
            double distance = distanceSquaredToCenter(location, origin);
            if (distance > maxSquared || distance >= bestDistance) {
                continue;
            }
            bestDistance = distance;
            best = location;
        }
        return best;
    }

    /** Squared distance from {@code at} to the center of {@code station}'s block. */
    public static double distanceSquaredToCenter(Location station, Location at) {
        if (station == null || at == null || station.getWorld() == null || at.getWorld() == null
                || station.getWorld() != at.getWorld()) {
            return Double.MAX_VALUE;
        }
        double dx = at.getX() - (station.getBlockX() + 0.5);
        double dy = at.getY() - (station.getBlockY() + 0.5);
        double dz = at.getZ() - (station.getBlockZ() + 0.5);
        return (dx * dx) + (dy * dy) + (dz * dz);
    }

    public static void save() {
        if (unreadableStore) return;
        Map<Object, Object> displays = new HashMap<>();
        try { for (var row : property().stations()) displays.put(row.raw().get("occupancy-id"), row.raw().get("display")); }
        catch (IOException ex) { warning(ex); return; }
        for (var entry : new ArrayList<>(OCCUPIED.entrySet())) {
            Occupancy occupancy = entry.getValue();
            if (occupancy.displayId == null || occupancy.displayId.toString().equals(displays.get(String.valueOf(occupancy.generation())))) continue;
            try {
                var station = property().station(occupancy.generation());
                if (!java.util.Objects.equals(occupancy.characterOwner(), ownerOrNull(station.raw()))) continue;
                if (occupancy.characterOwner() != null) property().requireUnlocked(occupancy.characterOwner());
                var after = net.tfminecraft.magic.gear.ownership.GearYaml.copy(station.raw());
                after.put("display", occupancy.displayId.toString());
                property().patch(station, after);
            } catch (IOException ex) { warning(ex); }
        }
    }
    public static void warning(Exception ex) { Magic.plugin.getLogger().warning("[Magic] Gear property held: " + ex.getMessage()); }

    private static void clear(Location location) {
        Occupancy occupancy = OCCUPIED.remove(key(location));
        removeDisplays(location, null, occupancy == null ? null : occupancy.displayId);
    }

    /** Public world release is evidence-backed; an interrupted effect is held at restart. */
    private static void abandon(Location location) {
        Occupancy occupancy = get(location); if (occupancy == null) return;
        net.tfminecraft.magic.gear.orb.GearOrbService.abort(location);
        ItemStack item = takeForAbort(location);
        if (item != null) {
            drop(location, item); dropReattuneFee(location, occupancy); acknowledgeRelease(location, occupancy.generation());
            Magic.plugin.getLogger().warning("[Magic] Gear station at " + key(location) + " has no furniture. Dropped the weapon and removed its display.");
        }
    }

    private static UUID ensureDisplay(Location location, ItemStack item, UUID savedDisplay) {
        World world = location.getWorld();
        if (world == null) {
            return null;
        }
        if (!world.isChunkLoaded(location.getBlockX() >> 4, location.getBlockZ() >> 4)) {
            location.getChunk().load();
        }
        ItemDisplay existing = displayOrNull(savedDisplay);
        if (existing == null) {
            existing = findTagged(location);
        }
        if (existing != null) {
            existing.setItemStack(item);
            mark(existing, location);
            removeDisplays(location, existing.getUniqueId(), null);
            return existing.getUniqueId();
        }
        removeDisplays(location, null, savedDisplay);
        return spawnDisplay(location, item);
    }

    private static ItemDisplay displayOrNull(UUID displayId) {
        if (displayId == null) {
            return null;
        }
        Entity entity = Bukkit.getEntity(displayId);
        if (entity instanceof ItemDisplay display && display.isValid()) {
            return display;
        }
        return null;
    }

    private static ItemDisplay findTagged(Location location) {
        World world = location.getWorld();
        if (world == null) {
            return null;
        }
        String stationKey = key(location);
        for (Entity entity : world.getNearbyEntities(displayLocation(location), 0.5, 0.5, 0.5)) {
            if (entity instanceof ItemDisplay display && stationKey.equals(markOf(display))) {
                return display;
            }
        }
        return null;
    }

    /**
     * Removes this station's displays. A kept display is the one still in use.
     * Untagged displays are removed only when the item is a mage weapon, so a
     * furniture display sitting nearby is left alone.
     */
    private static void removeDisplays(Location location, UUID keep, UUID savedDisplay) {
        if (location == null || location.getWorld() == null) {
            return;
        }
        World world = location.getWorld();
        try {
            if (!world.isChunkLoaded(location.getBlockX() >> 4, location.getBlockZ() >> 4)) {
                location.getChunk().load();
            }
        } catch (RuntimeException ex) {
            Magic.plugin.getLogger().warning("[Magic] Could not load gear station chunk at "
                    + key(location) + ": " + ex.getMessage());
        }
        String stationKey = key(location);
        for (Entity entity : world.getNearbyEntities(displayLocation(location), 0.5, 0.5, 0.5)) {
            if (!(entity instanceof ItemDisplay display)) {
                continue;
            }
            if (keep != null && keep.equals(display.getUniqueId())) {
                continue;
            }
            if (stationKey.equals(markOf(display)) || isGearDisplay(display)) {
                display.remove();
            }
        }
        if (savedDisplay != null && (keep == null || !keep.equals(savedDisplay))) {
            Entity entity = Bukkit.getEntity(savedDisplay);
            if (entity != null) {
                entity.remove();
            }
        }
    }

    private static boolean isGearDisplay(ItemDisplay display) {
        ItemStack stack = display.getItemStack();
        if (!stack.hasItemMeta()) {
            return false;
        }
        ItemMeta meta = stack.getItemMeta();
        return meta.getPersistentDataContainer().has(GearKeys.archetype(), PersistentDataType.STRING)
                || meta.getPersistentDataContainer().has(GearKeys.parts(), PersistentDataType.STRING);
    }

    private static String markOf(ItemDisplay display) {
        return display.getPersistentDataContainer().get(GearKeys.stationDisplay(), PersistentDataType.STRING);
    }

    private static void mark(ItemDisplay display, Location location) {
        display.getPersistentDataContainer().set(
                GearKeys.stationDisplay(), PersistentDataType.STRING, key(location));
    }

    /** A station that loses its furniture drops a pending re-attunement's fee with the weapon. */
    public static void dropReattuneFee(Location location, Occupancy occupancy) {
        if (location == null || occupancy == null || !occupancy.isReattune()) return;
        if (occupancy.getCharged() == null) {
            Magic.plugin.getLogger().warning("[Magic] Gear station at " + key(location) + " lost a re-attunement fee with no readable record.");
            return;
        }
        for (Map.Entry<String, Integer> cost : occupancy.getCharged().entrySet()) {
            int remaining = cost.getValue();
            while (remaining > 0) {
                ItemStack stack = net.tfminecraft.magic.util.ItemRef.build(cost.getKey());
                if (stack == null || stack.getType().isAir()) {
                    Magic.plugin.getLogger().warning("[Magic] Gear station at " + key(location) + " could not drop " + remaining + " " + cost.getKey() + " of a re-attunement fee.");
                    break;
                }
                int amount = Math.min(remaining, stack.getMaxStackSize()); stack.setAmount(amount); remaining -= amount;
                drop(location, stack);
            }
        }
    }

    private static void drop(Location location, ItemStack item) {
        if (location.getWorld() == null || item == null) {
            return;
        }
        location.getWorld().dropItem(location.clone().add(0.5, 1.0, 0.5), item.clone());
    }

    private enum Furniture {
        PRESENT,
        ABSENT,
        UNKNOWN
    }

    /**
     * Present when the station furniture still occupies the block. Absent only when
     * the barrier hitbox is gone, so a lookup miss on a still-standing station does
     * not drop the weapon.
     */
    private static Furniture furniture(Location location) {
        Block block = location.getBlock();
        try {
            if (TLibs.getBlockAPI().getChecker().checkBlock(block, GearCache.station)) {
                return Furniture.PRESENT;
            }
        } catch (RuntimeException ex) {
            return Furniture.UNKNOWN;
        }
        if (block.getType() != Material.BARRIER) {
            return Furniture.ABSENT;
        }
        return Furniture.UNKNOWN;
    }

    private static UUID spawnDisplay(Location location, ItemStack item) {
        World world = location.getWorld();
        if (world == null) {
            return null;
        }
        Location at = displayLocation(location);
        ItemDisplay display = world.spawn(at, ItemDisplay.class, spawned -> {
            spawned.setItemStack(item);
            spawned.setBillboard(Billboard.CENTER);
            spawned.setPersistent(true);
            spawned.setInterpolationDuration(0);
            mark(spawned, location);
            Transformation transform = spawned.getTransformation();
            spawned.setTransformation(new Transformation(
                    transform.getTranslation(),
                    new AxisAngle4f(),
                    new Vector3f(0.6f, 0.6f, 0.6f),
                    new AxisAngle4f()));
        });
        return display.getUniqueId();
    }

    private static Location displayLocation(Location location) {
        return location.clone().add(0.5, 1.15, 0.5);
    }

    private static UUID uuidOf(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private static Map<String, Integer> readCharged(ConfigurationSection section) {
        ConfigurationSection chargedSection = section.getConfigurationSection("charged");
        if (chargedSection == null) {
            return null;
        }
        Map<String, Integer> charged = new LinkedHashMap<>();
        for (String index : chargedSection.getKeys(false)) {
            String path = chargedSection.getString(index + ".path");
            int amount = chargedSection.getInt(index + ".amount");
            if (path != null && !path.isBlank() && amount > 0) {
                charged.merge(path, amount, Integer::sum);
            }
        }
        return charged;
    }

    public static String key(Location location) {
        if (location == null || location.getWorld() == null) {
            return "";
        }
        return location.getWorld().getName() + "," + location.getBlockX() + ","
                + location.getBlockY() + "," + location.getBlockZ();
    }

    public static Location locationFromKey(String key) {
        String[] bits = key.split(",");
        if (bits.length != 4) {
            return null;
        }
        World world = Bukkit.getWorld(bits[0]);
        if (world == null) {
            return null;
        }
        try {
            return new Location(world, Integer.parseInt(bits[1]), Integer.parseInt(bits[2]), Integer.parseInt(bits[3]));
        } catch (NumberFormatException ex) {
            return null;
        }
    }

}
