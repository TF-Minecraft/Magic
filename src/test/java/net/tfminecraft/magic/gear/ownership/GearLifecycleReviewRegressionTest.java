package net.tfminecraft.magic.gear.ownership;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.net.URL;
import java.net.URLClassLoader;
import java.util.logging.Level;
import java.util.logging.Logger;
import net.tfminecraft.magic.Magic;
import org.junit.jupiter.api.Test;

class GearLifecycleReviewRegressionTest {
    @Test void participantStartupLogsTheUnwrappedRegistrationAndSchedulingCausesAtSevere() {
        for (boolean scheduling : new boolean[]{false, true}) {
            Magic plugin = mock(Magic.class, RETURNS_DEEP_STUBS); Logger logger = mock(Logger.class); when(plugin.getLogger()).thenReturn(logger);
            var cause = new IllegalStateException(scheduling ? "scheduler rejected participant" : "registration failed");
            if (scheduling) when(plugin.getServer().getScheduler().runTaskTimer(eq(plugin), any(Runnable.class), eq(1L), eq(72_000L))).thenThrow(cause);
            else when(plugin.getServer()).thenThrow(cause);
            GearOwnershipLifecycle.start(plugin);
            verify(logger).log(eq(Level.SEVERE), contains("participant"), same(cause)); verify(logger, never()).warning(anyString());
        }
    }

    @Test void presentApisWithAnIncompatibleParticipantEntrypointAreSevere() throws Exception {
        byte[] incompatible = new net.bytebuddy.ByteBuddy().subclass(Object.class).name(CharacterGearEstate.class.getName()).make().getBytes();
        URL production = GearOwnershipLifecycle.class.getProtectionDomain().getCodeSource().getLocation();
        try (var loader = new URLClassLoader(new URL[]{production}, getClass().getClassLoader()) {
            @Override protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
                if (name.equals(CharacterGearEstate.class.getName()) || name.equals(GearOwnershipLifecycle.class.getName())) {
                    Class<?> type = findLoadedClass(name);
                    if (type == null) type = name.equals(CharacterGearEstate.class.getName()) ? defineClass(name, incompatible, 0, incompatible.length) : findClass(name);
                    if (resolve) resolveClass(type); return type;
                }
                return super.loadClass(name, resolve);
            }
        }) {
            Magic plugin = mock(Magic.class); Logger logger = mock(Logger.class); when(plugin.getLogger()).thenReturn(logger);
            loader.loadClass(GearOwnershipLifecycle.class.getName()).getMethod("start", Magic.class).invoke(null, plugin);
            var cause = org.mockito.ArgumentCaptor.forClass(Throwable.class);
            verify(logger).log(eq(Level.SEVERE), contains("participant"), cause.capture()); assertInstanceOf(NoSuchMethodException.class, cause.getValue());
            verify(logger, never()).warning(anyString());
        }
    }
}
