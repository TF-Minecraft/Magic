package net.tfminecraft.magic;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import net.tfminecraft.magic.gear.GearStationStore;
import net.tfminecraft.magic.gear.ownership.*;
import net.tfminecraft.tlibs.character.CharacterRef;
import net.tfminecraft.tlibs.character.CharacterSession;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Item;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

class GearStationOwnershipBoundaryTest extends GearStationCoverageSupport {
    Path source() {
        return temp.resolve("data/gear-stations.yml");
    }

    void patch(
            GearStationStore.Occupancy occupancy,
            java.util.function.Consumer<Map<Object, Object>> edit)
            throws IOException {
        var store = GearStationStore.property();
        var before = store.station(occupancy.generation());
        var after = GearYaml.copy(before.raw());
        edit.accept(after);
        store.patch(before, after);
    }

    @Test
    void compatibilityUuidEntryPointsCaptureTheLiveFullOwnerAndRejectMissingOrWrongAccounts()
            throws Exception {
        var location = station();
        var player = server.addPlayer();
        UUID account = player.getUniqueId();
        var initial = characters.ref(account);
        assertNull(GearStationStore.occupy(location, item(), null, Map.of()));
        assertNull(GearStationStore.rest(location, item(), UUID.randomUUID()));
        assertNull(GearStationStore.occupy(null, item(), account, Map.of()));
        assertNull(GearStationStore.occupy(location, null, account, Map.of()));
        assertFalse(Files.exists(source()));
        var foreign = characters.ref(server.addPlayer().getUniqueId());
        characters.sessions.put(account, new CharacterSession(foreign, 7));
        assertNull(GearStationStore.occupy(location, item(), account, Map.of()));
        assertFalse(Files.exists(source()));
        characters.sessions.put(account, new CharacterSession(initial, 8));
        var placed = GearStationStore.occupy(location, item(), account, Map.of());
        assertNotNull(placed);
        assertEquals(
                new GearIdentity.Owner(account, initial.characterId()), placed.characterOwner());
        var b = new CharacterRef(account, UUID.randomUUID());
        characters.refs.put(account, b);
        characters.sessions.put(account, new CharacterSession(b, 9));
        assertNull(GearStationStore.rest(location, item(), account));
        var rested = GearStationStore.rest(location.clone().add(4, 0, 0), item(), account);
        assertNotNull(rested);
        assertTrue(rested.isRested());
        assertEquals(new GearIdentity.Owner(account, b.characterId()), rested.characterOwner());
        assertEquals(initial.characterId(), placed.characterOwner().character());
    }

    @Test
    void malformedOwnershipNeverFallsBackToLegacyAccountWhileExplicitOwnerlessRemainsPublic()
            throws Exception {
        var location = station();
        var account = UUID.randomUUID();
        var original = seed(location, item(), account, Map.of());
        var good = GearStationStore.property().station(original.generation()).raw();
        for (Object authority :
                Arrays.asList(
                        "absent",
                        null,
                        Map.of("format", 2, "kind", "OWNERLESS"),
                        Map.of(
                                "format",
                                1,
                                "kind",
                                "CHARACTER",
                                "account",
                                "bad",
                                "character",
                                UUID.randomUUID().toString()))) {
            patch(
                    original,
                    row -> {
                        row.clear();
                        row.putAll(good);
                        if ("absent".equals(authority)) row.remove("ownership");
                        else row.put("ownership", authority);
                        row.put("unknown-null", null);
                    });
            occupied().clear();
            String bytes = Files.readString(source());
            GearStationStore.loadViews();
            assertNull(GearStationStore.get(location));
            assertTrue(GearStationStore.isOccupied(location));
            GearStationStore.save();
            assertEquals(bytes, Files.readString(source()));
        }
        patch(
                original,
                row -> {
                    row.clear();
                    row.putAll(good);
                    row.put("ownership", Map.of("format", 1, "kind", "OWNERLESS"));
                });
        GearStationStore.loadViews();
        var publicView = GearStationStore.get(location);
        assertNotNull(publicView);
        assertNull(publicView.characterOwner());
        assertEquals(account, publicView.getOwner());
        assertTrue(GearEffects.authorize(server.addPlayer(), publicView));
    }

    @ParameterizedTest
    @ValueSource(strings = {"generation", "state", "location", "owner", "item", "cache"})
    void staleOccupancySnapshotsCannotRewriteOrReleaseChangedAuthority(String change)
            throws Exception {
        var location = station();
        var occupancy = seed(location, item(), UUID.randomUUID(), Map.of());
        if (change.equals("cache")) occupancy.setItem(null);
        else
            patch(
                    occupancy,
                    row -> {
                        switch (change) {
                            case "generation" ->
                                    row.put("occupancy-id", UUID.randomUUID().toString());
                            case "state" -> row.put("effect-state", "held");
                            case "location" -> row.put("x", 5);
                            case "owner" ->
                                    row.put(
                                            "ownership",
                                            GearPropertyStore.ownership(
                                                    new GearIdentity.Owner(
                                                            UUID.randomUUID(), UUID.randomUUID())));
                            default ->
                                    row.put(
                                            "item-bytes",
                                            GearPropertyStore.b64(
                                                    GearItems.bytes(
                                                            new ItemStack(Material.DIAMOND))));
                        }
                    });
        String before = Files.readString(source());
        assertThrows(IOException.class, () -> GearStationStore.expected(location, occupancy));
        assertFalse(GearStationStore.update(location, occupancy, attuned()));
        assertNull(GearStationStore.takeForAbort(location));
        assertEquals(before, Files.readString(source()));
    }

    @Test
    void replacingAViewInvalidatesItsCapturedInstanceBeforeAnyItemWrite() throws Exception {
        var location = station();
        var old = seed(location, item(), UUID.randomUUID(), Map.of());
        patch(
                old,
                row ->
                        row.put(
                                "ownership",
                                GearPropertyStore.ownership(
                                        new GearIdentity.Owner(
                                                UUID.randomUUID(), UUID.randomUUID()))));
        GearStationStore.loadViews();
        assertNotSame(old, GearStationStore.get(location));
        String before = Files.readString(source());
        assertFalse(GearStationStore.update(location, old, attuned()));
        assertFalse(GearStationStore.update(location, GearStationStore.get(location), null));
        assertEquals(before, Files.readString(source()));
    }

    @Test
    void releaseAcknowledgementRequiresExactLocationStateGenerationAndDurableWrite()
            throws Exception {
        var location = station();
        var occupancy = seedRest(location, item(), UUID.randomUUID());
        String active = Files.readString(source());
        assertFalse(GearStationStore.acknowledgeRelease(location, occupancy.generation()));
        assertEquals(active, Files.readString(source()));
        assertNotNull(GearStationStore.takeForAbort(location));
        String releasing = Files.readString(source());
        assertNull(GearStationStore.get(location));
        assertFalse(
                GearStationStore.acknowledgeRelease(
                        location.clone().add(2, 0, 0), occupancy.generation()));
        assertFalse(GearStationStore.acknowledgeRelease(location, UUID.randomUUID()));
        assertEquals(releasing, Files.readString(source()));
        try (var files = mockStatic(GearFiles.class, CALLS_REAL_METHODS)) {
            files.when(() -> GearFiles.write(eq(source()), any(byte[].class), eq(true)))
                    .thenThrow(new IOException("release acknowledgement disk failure"));
            assertFalse(GearStationStore.acknowledgeRelease(location, occupancy.generation()));
            assertEquals(releasing, Files.readString(source()));
        }
        assertTrue(GearStationStore.acknowledgeRelease(location, occupancy.generation()));
        assertEquals(
                "released",
                GearStationStore.property()
                        .station(occupancy.generation())
                        .raw()
                        .get("effect-state"));
        assertFalse(GearStationStore.acknowledgeRelease(location, occupancy.generation()));
    }

    @Test
    void staleOwnerCacheSaveAndStorageFailurePreserveTheAuthoritativeRow() throws Exception {
        var location = station();
        var occupancy = seed(location, item(), UUID.randomUUID(), Map.of());
        patch(
                occupancy,
                row ->
                        row.put(
                                "ownership",
                                GearPropertyStore.ownership(
                                        new GearIdentity.Owner(
                                                UUID.randomUUID(), UUID.randomUUID()))));
        String changedOwner = Files.readString(source());
        try (var files = mockStatic(GearFiles.class, CALLS_REAL_METHODS)) {
            GearStationStore.save();
            files.verify(
                    () -> GearFiles.write(any(Path.class), any(byte[].class), anyBoolean()),
                    never());
            assertEquals(changedOwner, Files.readString(source()));
        }
        GearStationStore.loadViews();
        var current = GearStationStore.get(location);
        assertNotSame(occupancy, current);
        try (var files = mockStatic(GearFiles.class, CALLS_REAL_METHODS)) {
            files.when(() -> GearFiles.write(eq(source()), any(byte[].class), eq(true)))
                    .thenThrow(new IOException("save disk failure"));
            assertDoesNotThrow(GearStationStore::save);
            assertEquals(changedOwner, Files.readString(source()));
            assertEquals(item(), current.getItem());
        }
    }

    @Test
    void missingDisplayAndCachedItemNeverEraseDurableGear() throws Exception {
        var location = station();
        var occupancy = seed(location, item(), null, Map.of());
        var display = occupancy.getClass().getDeclaredField("displayId");
        display.setAccessible(true);
        display.set(occupancy, null);
        String before = Files.readString(source());
        occupancy.setItem(null);
        assertThrows(IOException.class, () -> GearStationStore.expected(location, occupancy));
        GearStationStore.save();
        assertEquals(before, Files.readString(source()));
        occupied().clear();
        GearStationStore.loadViews();
        assertEquals(item(), GearStationStore.get(location).getItem());
        assertEquals(occupancy.generation(), GearStationStore.get(location).generation());
    }

    @Test
    void unloadedRowsAndLegacyBukkitItemsRemainRecoverableWithoutOwnerRecapture() throws Exception {
        var location = station();
        var occupancy = seed(location, item(), UUID.randomUUID(), Map.of());
        var owner = occupancy.characterOwner();
        patch(occupancy, row -> row.put("world", "unloaded-fixture"));
        occupied().clear();
        String unloaded = Files.readString(source());
        GearStationStore.loadViews();
        GearStationStore.save();
        assertTrue(occupied().isEmpty());
        assertEquals(unloaded, Files.readString(source()));
        YamlConfiguration legacy = new YamlConfiguration();
        legacy.set("item", item());
        Object serialized =
                GearYaml.read(
                                legacy.saveToString()
                                        .getBytes(java.nio.charset.StandardCharsets.UTF_8))
                        .get("item");
        patch(
                occupancy,
                row -> {
                    row.put("world", location.getWorld().getName());
                    row.remove("item-bytes");
                    row.put("item", serialized);
                });
        String oldItem = Files.readString(source());
        GearStationStore.loadViews();
        var loaded = GearStationStore.get(location);
        assertNotNull(loaded);
        assertEquals(item(), loaded.getItem());
        assertEquals(owner, loaded.characterOwner());
        assertEquals(oldItem, Files.readString(source()));
        for (Object bad :
                Arrays.asList(null, "unsupported-item", Map.of("type", "NOT_A_MATERIAL"))) {
            patch(occupancy, row -> row.put("item", bad));
            occupied().clear();
            String retained = Files.readString(source());
            GearStationStore.loadViews();
            GearStationStore.save();
            assertNull(GearStationStore.get(location));
            assertEquals(retained, Files.readString(source()));
        }
    }

    @Test
    void legacyInformationalOwnerFieldsCannotReplaceFullCharacterAuthority() throws Exception {
        var location = station();
        var occupancy = seed(location, item(), UUID.randomUUID(), Map.of());
        var owner = occupancy.characterOwner();
        for (Object legacyOwner :
                Arrays.asList(null, 7, "malformed", UUID.randomUUID().toString())) {
            patch(
                    occupancy,
                    row -> {
                        if (legacyOwner == null) row.remove("owner");
                        else row.put("owner", legacyOwner);
                    });
            occupied().clear();
            GearStationStore.loadViews();
            var loaded = GearStationStore.get(location);
            assertNotNull(loaded);
            assertEquals(owner, loaded.characterOwner());
            assertEquals(
                    legacyOwner instanceof String text && !text.equals("malformed")
                            ? UUID.fromString((String) legacyOwner)
                            : null,
                    loaded.getOwner());
        }
    }

    @Test
    void unreadableOccupancySourceReservesTheStationAndPreservesItsBytes() throws Exception {
        var location = station();
        var occupancy = seed(location, item(), UUID.randomUUID(), Map.of());
        String broken = "stations: [unterminated";
        Files.writeString(source(), broken);
        assertTrue(GearStationStore.isOccupied(location));
        assertFalse(GearStationStore.update(location, occupancy, attuned()));
        assertNull(GearStationStore.takeForAbort(location));
        GearStationStore.save();
        assertEquals(broken, Files.readString(source()));
        assertSame(occupancy, GearStationStore.get(location));
    }

    @Test
    void installPreservesExplicitOwnerlessAndCannotPublishAnUnknownAuthority() throws Exception {
        var location = station();
        var occupancy = seed(location, item(), null, Map.of());
        patch(occupancy, row -> row.put("owner", 7));
        var ownerless = GearStationStore.property().station(occupancy.generation()).raw();
        var installed = GearStationStore.install(location, item(), ownerless);
        assertNull(installed.getOwner());
        assertNull(installed.characterOwner());
        assertEquals(occupancy.generation(), installed.generation());
        assertDoesNotThrow(() -> GearStationStore.expected(location, installed));
        patch(installed, row -> row.put("ownership", "unrecognized-envelope"));
        String held = Files.readString(source());
        var raw = GearStationStore.property().station(installed.generation()).raw();
        var unknown = GearStationStore.install(location, item(), raw);
        assertNull(unknown.characterOwner());
        assertThrows(IOException.class, () -> GearStationStore.expected(location, unknown));
        assertFalse(GearStationStore.update(location, unknown, attuned()));
        assertEquals(held, Files.readString(source()));
    }

    @ParameterizedTest
    @ValueSource(strings = {"null", "air"})
    void unavailableDecodedGearRemainsInTheRawInventory(String decoded) throws Exception {
        var location = station();
        var occupancy = seed(location, item(), UUID.randomUUID(), Map.of());
        var owner = occupancy.characterOwner();
        String before = Files.readString(source());
        occupied().clear();
        codec.when(() -> GearItems.item(any()))
                .thenReturn(decoded.equals("null") ? null : new ItemStack(Material.AIR));
        GearStationStore.loadViews();
        GearStationStore.save();
        assertNull(GearStationStore.get(location));
        assertTrue(GearStationStore.isOccupied(location));
        assertEquals(before, Files.readString(source()));
        assertEquals(
                owner,
                GearPropertyStore.owner(
                        GearStationStore.property().station(occupancy.generation()).raw()));
    }

    @Test
    void unloadedCacheWithNoDisplayRemainsDurableAcrossReconcileAndShutdown() throws Exception {
        var location = station();
        var occupancy = seed(location, item(), UUID.randomUUID(), Map.of());
        var display = occupancy.getClass().getDeclaredField("displayId");
        display.setAccessible(true);
        display.set(occupancy, null);
        String before = Files.readString(source());
        assertTrue(server.unloadWorld(location.getWorld(), false));
        var other = server.addSimpleWorld("other");
        GearStationStore.reconcile(other.getChunkAt(0, 0));
        assertSame(occupancy, occupied().get("world,1,2,3"));
        GearStationStore.shutdown();
        assertTrue(occupied().isEmpty());
        assertEquals(before, Files.readString(source()));
        assertEquals(
                occupancy.characterOwner(),
                GearPropertyStore.owner(
                        GearStationStore.property().station(occupancy.generation()).raw()));
    }

    @Test
    void equallyCloseViewsAndMissingWorldCleanupCannotMutateProperty() throws Exception {
        var first = station();
        var second = first.clone().add(2, 0, 0);
        var a = seed(first, item(), UUID.randomUUID(), Map.of());
        var b = seed(second, item(), UUID.randomUUID(), Map.of());
        String before = Files.readString(source());
        var origin = first.clone().add(1.5, .5, .5);
        var selected = GearStationStore.occupiedWithin(origin, 2);
        assertTrue(selected.equals(first) || selected.equals(second));
        assertEquals(1, GearStationStore.distanceSquaredToCenter(selected, origin));
        var noWorld = new Location(null, 1, 2, 3);
        assertNull(
                invoke(
                        GearStationStore.class,
                        "spawnDisplay",
                        new Class[] {Location.class, ItemStack.class},
                        noWorld,
                        item()));
        invoke(
                GearStationStore.class,
                "removeDisplays",
                new Class[] {Location.class, UUID.class, UUID.class},
                noWorld,
                null,
                null);
        invoke(
                GearStationStore.class,
                "clear",
                new Class[] {Location.class},
                first.clone().add(9, 0, 0));
        assertSame(a, GearStationStore.get(first));
        assertSame(b, GearStationStore.get(second));
        assertEquals(before, Files.readString(source()));
    }

    @Test
    void missingFurnitureCannotDropGearAfterItsRawEffectStateChanges() throws Exception {
        var location = station();
        var occupancy = seed(location, item(), UUID.randomUUID(), Map.of());
        patch(occupancy, row -> row.put("effect-state", "held"));
        String before = Files.readString(source());
        invoke(GearStationStore.class, "abandon", new Class[] {Location.class}, location);
        assertSame(occupancy, GearStationStore.get(location));
        assertTrue(location.getWorld().getEntitiesByClass(Item.class).isEmpty());
        assertEquals(before, Files.readString(source()));
    }
}
