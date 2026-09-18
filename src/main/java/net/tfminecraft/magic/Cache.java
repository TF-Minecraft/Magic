package net.tfminecraft.magic;

import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;

import io.lumine.mythic.lib.skill.trigger.TriggerType;

/**
 * Runtime flags from config.yml.
 */
public final class Cache {

    public static boolean debug = false;
    public static String defaultCastMode = "flow";
    public static double defaultResonance = 0.0;
    public static double defaultResonanceDecayPerHour = -0.02;
    public static double defaultEquilibrium = 0.0;
    public static double equilibriumPassiveCorruptPerHour = 0.05;
    public static double equilibriumCorruptCompoundStrength = 1.25;
    public static double equilibriumTranquilityDecayPerHour = 0.03;
    public static long tickIntervalTicks = 20L;
    /** Real seconds that equal one in-game hour for per-hour rates. 3600 is real time; 1 is test speed. */
    public static double secondsPerHour = 3600.0;
    public static boolean artifactGlint = true;
    public static double artifactAuraCap = 150.0;
    public static boolean loggingEnabled = true;
    public static boolean wipeLog = true;
    /** Real mana spent divided by this equals drift. 1000 means 40 mana -> 0.04. */
    public static double castDriftManaDivisor = 1000.0;
    public static double castDriftMin = 0.01;
    /** Seconds between repeat chat lines explaining why a weapon refused a spell. */
    public static long refuseChatMillis = 30000L;

    public static Set<String> runeTypes = new HashSet<>();
    public static Set<TriggerType> runeKeybinds = new LinkedHashSet<>();

    public static double tickIntervalSeconds() {
        return Math.max(1L, tickIntervalTicks) / 20.0;
    }

    public static double tickHours() {
        double scale = secondsPerHour > 0 ? secondsPerHour : 3600.0;
        return tickIntervalSeconds() / scale;
    }

    private Cache() {}
}
