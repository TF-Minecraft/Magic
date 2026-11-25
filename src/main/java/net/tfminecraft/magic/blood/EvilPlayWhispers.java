package net.tfminecraft.magic.blood;

import java.lang.reflect.Method;
import java.util.logging.Logger;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.plugin.Plugin;

/**
 * Robbing feeds the whisper meter: RPCharacters fires {@code EvilRpPlayEvent} for every evil play
 * Thievery reports (robbing, pickpocketing, lockpicking, looting a locked grave), and each one that
 * starts a new evil RP session adds {@code sources.robbery}. Once per session, so restarting a lockpick
 * can't farm it. The event is looked up by name, so Magic still runs against an RPCharacters without it.
 */
public final class EvilPlayWhispers implements Listener {

    static final String EVENT = "net.tfminecraft.rpcharacters.evilrp.EvilRpPlayEvent";

    private EvilPlayWhispers() {}

    /** Listens for evil plays when RPCharacters has the event; false (and a log line) when it doesn't. */
    public static boolean register(Plugin plugin) {
        return register(plugin, EVENT, plugin.getLogger());
    }

    static boolean register(Plugin plugin, String eventClass, Logger log) {
        Class<? extends Event> type;
        Method player;
        Method started;
        try {
            type = Class.forName(eventClass).asSubclass(Event.class);
            player = type.getMethod("getPlayer");
            started = type.getMethod("isSessionStarted");
        } catch (ClassNotFoundException | NoSuchMethodException | ClassCastException e) {
            log.info("This RPCharacters has no evil RP play event; robbing won't feed the blood whispers.");
            return false;
        }
        Bukkit.getPluginManager().registerEvent(type, new EvilPlayWhispers(), EventPriority.MONITOR, (listener, event) -> {
            if (!type.isInstance(event)) {
                return;
            }
            try {
                onPlay((Player) player.invoke(event), (Boolean) started.invoke(event));
            } catch (ReflectiveOperationException e) {
                log.warning("Could not read an evil RP play: " + e);
            }
        }, plugin, true);
        return true;
    }

    /** An evil play by the player's current character; only one that starts a session counts. */
    static void onPlay(Player player, boolean sessionStarted) {
        if (!sessionStarted || player == null) {
            return;
        }
        String character = AltarService.currentCharacter(player);
        if (character == null) {
            return;
        }
        WhisperService.addTo(character, player.getUniqueId().toString(), WhisperConfig.robbery(), player,
                OccultistHints.Deed.ROBBERY);
    }
}
