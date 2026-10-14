package net.tfminecraft.magic.gear.ownership;

import net.tfminecraft.magic.Magic;

/** Optional estate types are never linked on the older TLibs runtime. */
public final class GearOwnershipLifecycle {
    private GearOwnershipLifecycle() {}
    public static void start(Magic plugin) {
        try {
            Class.forName("net.tfminecraft.tlibs.character.estate.Estates");
            Class.forName("net.tfminecraft.magic.gear.ownership.CharacterGearEstate").getMethod("start", Magic.class).invoke(null, plugin);
        } catch (ClassNotFoundException | LinkageError unavailable) {
            plugin.getLogger().warning("[Magic] Character gear ownership is held until current TLibs estate APIs are available.");
        } catch (java.lang.reflect.InvocationTargetException failure) {
            plugin.getLogger().log(java.util.logging.Level.SEVERE, "[Magic] Character gear estate participant failed to start.", failure.getCause());
        } catch (ReflectiveOperationException | RuntimeException failure) {
            plugin.getLogger().log(java.util.logging.Level.SEVERE, "[Magic] Character gear estate participant failed to start.", failure);
        }
    }
}
