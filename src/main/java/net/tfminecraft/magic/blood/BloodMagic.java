package net.tfminecraft.magic.blood;

import java.io.File;
import java.util.logging.Logger;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import net.tfminecraft.magic.manager.MagicTickService;
import net.tfminecraft.magic.tick.MagicTickContext;

/** Starts and stops the blood path: data, listener, and the dead-owner sweep. */
public final class BloodMagic {

    private static final long AMBIENT_SECONDS = 3;
    private static final double AMBIENT_RANGE = 24;

    private BloodMagic() {}

    /** Loads data once at enable. Config is loaded separately so a reload never re-reads data. */
    public static void start(Plugin plugin) {
        BloodStore store = new BloodStore(new File(plugin.getDataFolder(), "data/bloodmagic"), plugin.getLogger());
        BloodRecords.init(store);
        BloodAltars.init(store);
        BloodNotes.init(store);
        BondService.init(store);
        plugin.getServer().getPluginManager().registerEvents(new BloodListener(), plugin);
        MagicTickService.register(BloodMagic::tick);
        CarryService.startTicking(plugin);
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
        if (!BloodConfig.isEnabled()) {
            // Switched off: rites end without a death, bound animals wake, drags let go.
            CharacterRiteService.clear();
            AnimalRiteService.releaseAll();
            return;
        }
        if (context.every(AMBIENT_SECONDS)) {
            ambient();
        }
        WhisperService.tick(context);
        AnimalRiteService.tick();
        CharacterRiteService.tick();
        if (context.everyMinutes(HuntConfig.sampleMinutes())) {
            BondService.sample();
        }
        if (context.everyMinutes(10)) {
            BondService.flush();
            for (Player player : Bukkit.getOnlinePlayers()) {
                FinalService.check(player);
            }
        }
        if (context.everyMinutes(1)) {
            AltarCorruption.ambient();
            BloodNotes.tick(System.currentTimeMillis());
            StolenClues.tick();
        }
    }

    /** Altars breathe while someone is near: per-level drips, mist and sounds (fx altar_ambient_N). */
    static void ambient() {
        for (BloodAltar altar : BloodAltars.all()) {
            Location center = altar.center();
            if (center == null || !center.isChunkLoaded()) {
                continue;
            }
            for (Player player : center.getWorld().getPlayers()) {
                if (player.getLocation().distanceSquared(center) <= AMBIENT_RANGE * AMBIENT_RANGE) {
                    BloodFx.play("altar_ambient_" + altar.getLevel(), center);
                    break;
                }
            }
        }
    }

    public static boolean loadConfig(File dataFolder, Logger log) {
        return BloodConfig.load(new File(dataFolder, "bloodmagic.yml"), log);
    }

    public static void stop() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            BloodBoons.clear(player);
        }
        CarryService.clear();
        CharacterRiteService.clear();
        BondService.clear();
        AnimalRiteService.releaseAll();
        StolenClues.clear();
        BloodNotes.clear();
        WhisperService.clear();
        BloodRecords.clear();
        BloodAltars.clear();
    }
}
