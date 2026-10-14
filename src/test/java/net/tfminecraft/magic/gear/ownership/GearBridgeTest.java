package net.tfminecraft.magic.gear.ownership;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Logger;
import net.tfminecraft.magic.Magic;
import net.tfminecraft.tlibs.character.CharacterIdentity;
import net.tfminecraft.tlibs.character.CharacterInfo;
import net.tfminecraft.tlibs.character.CharacterRef;
import net.tfminecraft.tlibs.character.CharacterSession;
import net.tfminecraft.tlibs.character.CharacterStatus;
import org.bukkit.entity.Player;
import org.bukkit.plugin.ServicePriority;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;

class GearBridgeTest {
    ServerMock server;
    Player player;
    CharacterIdentity provider;
    CharacterSession session;
    GearIdentity bridge;

    @BeforeEach void setup() {
        server = MockBukkit.mock();
        player = server.addPlayer();
        provider = mock(CharacterIdentity.class);
        server.getServicesManager().register(CharacterIdentity.class, provider,
                MockBukkit.createMockPlugin(), ServicePriority.Highest);
        session = new CharacterSession(new CharacterRef(player.getUniqueId(), UUID.randomUUID()), 17);
        when(provider.currentSession(player)).thenReturn(Optional.of(session));
        when(provider.isCurrent(session)).thenReturn(true);
        when(provider.info(session.ref().characterId())).thenReturn(Optional.of(info(session.ref(), CharacterStatus.ALIVE)));
        bridge = GearIdentity.load();
    }

    @AfterEach void teardown() { MockBukkit.unmock(); }

    static CharacterInfo info(CharacterRef ref, CharacterStatus status) {
        return new CharacterInfo(ref, "Gear owner", status, null, Set.of(), 0, Instant.EPOCH);
    }

    @Test void realFacadeRequiresCurrentLiveFullReferenceAndExactSessionGeneration() {
        assertInstanceOf(GearCharacterBridge.class, bridge);
        var captured = GearCharacterBridge.token(session);
        assertEquals(Optional.of(captured), bridge.current(player));
        assertTrue(bridge.current(captured, player));
        assertFalse(bridge.current(null, player));
        assertFalse(bridge.current(new GearIdentity.Token(captured.owner(), 18), player));
        assertFalse(bridge.current(new GearIdentity.Token(new GearIdentity.Owner(UUID.randomUUID(), captured.owner().character()), 17), player));
        assertFalse(bridge.current(new GearIdentity.Token(new GearIdentity.Owner(captured.owner().account(), UUID.randomUUID()), 17), player));
        when(provider.isCurrent(session)).thenReturn(false);
        assertTrue(bridge.current(player).isEmpty());
        when(provider.isCurrent(session)).thenReturn(true);
        when(provider.info(session.ref().characterId())).thenReturn(Optional.empty());
        assertTrue(bridge.current(player).isEmpty());
        when(provider.info(session.ref().characterId())).thenReturn(Optional.of(info(new CharacterRef(UUID.randomUUID(), session.ref().characterId()), CharacterStatus.ALIVE)));
        assertTrue(bridge.current(player).isEmpty());
        for (CharacterStatus status : new CharacterStatus[]{CharacterStatus.DEAD, CharacterStatus.MISSING}) {
            when(provider.info(session.ref().characterId())).thenReturn(Optional.of(info(session.ref(), status)));
            assertTrue(bridge.current(player).isEmpty());
        }
        var foreign = new CharacterSession(new CharacterRef(UUID.randomUUID(), session.ref().characterId()), 17);
        when(provider.currentSession(player)).thenReturn(Optional.of(foreign));
        assertTrue(bridge.current(player).isEmpty());
        when(provider.currentSession(player)).thenReturn(Optional.empty());
        assertTrue(bridge.current(player).isEmpty());
        assertTrue(bridge.current(null).isEmpty());
    }

    @Test void reviewedLegacyHeirPreservesDeadRecordedOwnerAndNeverUsesActiveCharacter() {
        assertFalse(bridge.heirsReady());
        assertTrue(bridge.heir(null).isEmpty());
        assertTrue(bridge.heir(player.getUniqueId()).isEmpty());
        when(provider.legacyHeirsReady()).thenReturn(true);
        assertTrue(bridge.heirsReady());
        var dead = new CharacterRef(player.getUniqueId(), UUID.randomUUID());
        when(provider.info(dead.characterId())).thenReturn(Optional.of(info(dead, CharacterStatus.DEAD)));
        when(provider.legacyHeir(player.getUniqueId())).thenReturn(Optional.of(dead));
        assertEquals(Optional.of(new GearIdentity.Owner(dead.ownerId(), dead.characterId())), bridge.heir(player.getUniqueId()));
        assertNotEquals(bridge.current(player).orElseThrow().owner(), bridge.heir(player.getUniqueId()).orElseThrow());
        when(provider.legacyHeir(player.getUniqueId())).thenReturn(Optional.of(new CharacterRef(UUID.randomUUID(), dead.characterId())));
        assertTrue(bridge.heir(player.getUniqueId()).isEmpty());
        server.getServicesManager().unregister(CharacterIdentity.class, provider);
        assertFalse(bridge.heirsReady());
        assertTrue(bridge.heir(player.getUniqueId()).isEmpty());
        assertTrue(bridge.current(player).isEmpty());
    }

    @Test void actualOlderJarKeepsIdentityAndEstateClassesIsolatedAndHeld() throws Exception {
        Path repository = Path.of(System.getProperty("maven.repo.local",
                Path.of(System.getProperty("user.home"), ".m2", "repository").toString()));
        Path older = repository.resolve("me/plugins/tlibs/2.3.0/tlibs-2.3.0.jar");
        assertTrue(Files.isRegularFile(older), "Install the pinned older compatibility fixture");
        URL production = GearIdentity.class.getProtectionDomain().getCodeSource().getLocation();
        try (var loader = new URLClassLoader(new URL[]{production, older.toUri().toURL()}, getClass().getClassLoader()) {
            @Override protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
                if (name.startsWith("net.tfminecraft.tlibs.") || name.startsWith("net.tfminecraft.magic.gear.ownership.")) {
                    Class<?> type = findLoadedClass(name);
                    if (type == null) type = findClass(name);
                    if (resolve) resolveClass(type);
                    return type;
                }
                return super.loadClass(name, resolve);
            }
        }) {
            Class<?> identity = loader.loadClass(GearIdentity.class.getName());
            Object held = identity.getMethod("load").invoke(null);
            assertEquals(Optional.empty(), identity.getMethod("current", Player.class).invoke(held, player));
            assertEquals(false, identity.getMethod("current", loader.loadClass(GearIdentity.Token.class.getName()), Player.class).invoke(held, null, player));
            assertEquals(false, identity.getMethod("heirsReady").invoke(held));
            assertEquals(Optional.empty(), identity.getMethod("heir", UUID.class).invoke(held, player.getUniqueId()));
            assertThrows(ClassNotFoundException.class, () -> loader.loadClass("net.tfminecraft.tlibs.character.estate.Estates"));
            Magic plugin = mock(Magic.class);
            Logger logger = mock(Logger.class);
            when(plugin.getLogger()).thenReturn(logger);
            loader.loadClass(GearOwnershipLifecycle.class.getName()).getMethod("start", Magic.class).invoke(null, plugin);
            verify(logger).warning(contains("held until current TLibs"));
            verify(plugin, never()).getServer();
        }
    }
}
