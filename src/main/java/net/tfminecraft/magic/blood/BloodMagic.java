package net.tfminecraft.magic.blood;

import java.io.File;
import java.util.logging.Logger;

import org.bukkit.plugin.Plugin;

import net.tfminecraft.magic.manager.MagicTickService;
import net.tfminecraft.magic.tick.MagicTickContext;

/** Starts and stops the blood path: data, listener, and the dead-owner sweep. */
public final class BloodMagic {

    private BloodMagic() {}

    /** Loads data once at enable. Config is loaded separately so a reload never re-reads data. */
    public static void start(Plugin plugin) {
        BloodStore store = new BloodStore(new File(plugin.getDataFolder(), "data/bloodmagic"), plugin.getLogger());
        BloodRecords.init(store);
        BloodAltars.init(store);
        plugin.getServer().getPluginManager().registerEvents(new BloodListener(), plugin);
        MagicTickService.register(BloodMagic::tick);
        plugin.getServer().getScheduler().runTask(plugin, BloodMagic::settle);
    }

    /** First tick after enable: purify altars of owners who died while we were down, redraw the rest. */
    static void settle() {
        AltarService.sweepDead();
        for (BloodAltar altar : BloodAltars.all()) {
            AltarService.refresh(altar);
        }
    }

    static void tick(MagicTickContext context) {
        if (context.everyMinutes(BloodConfig.purifyCheckMinutes())) {
            AltarService.sweepDead();
        }
    }

    public static boolean loadConfig(File dataFolder, Logger log) {
        return BloodConfig.load(new File(dataFolder, "bloodmagic.yml"), log);
    }

    public static void stop() {
        BloodRecords.clear();
        BloodAltars.clear();
    }
}
