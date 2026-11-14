package net.tfminecraft.magic.blood;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import net.tfminecraft.magic.integration.RpCharactersBridge;
import net.tfminecraft.tlibs.character.CharacterInfo;
import net.tfminecraft.tlibs.character.CharacterStatus;
import net.tfminecraft.tlibs.character.Characters;

/**
 * How much other characters matter to a character, never shown to players. Built from RP
 * conversations (RPCharacters), time spent near each other (sampled here for every character,
 * so history exists before anyone becomes a mage) and a will heir who is another player's
 * character the two really spent time with. Characters of the same account never count.
 */
public final class BondService {

    /** Another character and how much they matter. */
    public record Ranked(String character, double score) {}

    private static final Map<String, Map<String, Integer>> MINUTES = new HashMap<>();
    private static final Set<String> DIRTY = new HashSet<>();
    private static BloodStore store;
    /**
     * The periodic flush writes every changed character's file, which on a full server is dozens at
     * once: they go to this thread, in order, so the main thread never waits on the disk (tests run
     * them in place).
     */
    static final Executor BACKGROUND = Executors.newSingleThreadExecutor(task -> {
        Thread thread = new Thread(task, "Magic blood bonds");
        thread.setDaemon(true);
        return thread;
    });
    static Executor writer = BACKGROUND;
    private static CompletableFuture<Void> writing = CompletableFuture.completedFuture(null);

    private BondService() {}

    public static void init(BloodStore source) {
        MINUTES.clear();
        DIRTY.clear();
        store = source;
    }

    /** Writes pending changes, waits for them, and forgets everything. */
    public static void clear() {
        flush();
        writing.join();
        MINUTES.clear();
        store = null;
    }

    private static Map<String, Integer> of(String character) {
        return MINUTES.computeIfAbsent(character.toLowerCase(Locale.ROOT),
                id -> store != null ? store.loadBonds(id) : new HashMap<>());
    }

    /** Loads a character's bonds while it is played. */
    public static void load(String character) {
        of(character);
    }

    /** Saves and drops a character's bonds when it stops being played. */
    public static void unload(String character) {
        String key = character.toLowerCase(Locale.ROOT);
        if (DIRTY.remove(key) && store != null) {
            store.saveBonds(key, MINUTES.get(key));
        }
        MINUTES.remove(key);
    }

    public static int minutes(String character, String other) {
        return of(character).getOrDefault(other.toLowerCase(Locale.ROOT), 0);
    }

    static void add(String character, String other, int minutes) {
        String key = character.toLowerCase(Locale.ROOT);
        of(character).merge(other.toLowerCase(Locale.ROOT), minutes, Integer::sum);
        DIRTY.add(key);
    }

    /** Every sample: characters near each other each gain the sample's minutes with the other. */
    static void sample() {
        List<Player> players = new ArrayList<>();
        List<String> characters = new ArrayList<>();
        for (Player player : Bukkit.getOnlinePlayers()) {
            String character = AltarService.currentCharacter(player);
            if (character != null) {
                players.add(player);
                characters.add(character);
            }
        }
        double radius = HuntConfig.nearRadius();
        int minutes = HuntConfig.sampleMinutes();
        for (int i = 0; i < players.size(); i++) {
            for (int j = i + 1; j < players.size(); j++) {
                Player a = players.get(i);
                Player b = players.get(j);
                if (a.getWorld() == b.getWorld() && a.getLocation().distanceSquared(b.getLocation()) <= radius * radius) {
                    add(characters.get(i), characters.get(j), minutes);
                    add(characters.get(j), characters.get(i), minutes);
                }
            }
        }
    }

    /** Writes every changed character's bonds, off the main thread (copies taken now). */
    public static void flush() {
        BloodStore target = store;
        if (target != null) {
            for (String key : DIRTY) {
                Map<String, Integer> copy = new HashMap<>(MINUTES.get(key));
                writing = writing.thenRunAsync(() -> target.saveBonds(key, copy), writer)
                        .exceptionally(failure -> {
                            target.warn("[Magic] Failed to write the bonds of " + key + ": " + failure.getMessage());
                            return null;
                        });
            }
        }
        DIRTY.clear();
    }

    /** Living characters of other accounts the mage has any bond with, closest first. */
    public static List<Ranked> ranking(Player mage) {
        String me = AltarService.currentCharacter(mage);
        List<Ranked> out = new ArrayList<>();
        if (me == null) {
            return out;
        }
        Map<String, Integer> conversations = RpCharactersBridge.conversationCounts(mage);
        Map<String, Integer> near = of(me);
        String heir = RpCharactersBridge.willHeir(mage);
        Set<String> candidates = new LinkedHashSet<>();
        conversations.keySet().forEach(id -> candidates.add(id.toLowerCase(Locale.ROOT)));
        candidates.addAll(near.keySet());
        if (heir != null) {
            candidates.add(heir.toLowerCase(Locale.ROOT));
        }
        candidates.remove(me.toLowerCase(Locale.ROOT));
        for (String other : candidates) {
            if (!livingOtherAccount(other, mage.getUniqueId())) {
                continue;
            }
            int talks = count(conversations, other);
            double hours = near.getOrDefault(other, 0) / 60.0;
            double score = HuntConfig.conversationWeight() * talks + HuntConfig.hourWeight() * hours;
            if (heir != null && heir.equalsIgnoreCase(other)
                    && (hours >= HuntConfig.heirMinHours() || talks >= HuntConfig.heirMinConversations())) {
                score += HuntConfig.heirBonus();
            }
            if (score > 0) {
                out.add(new Ranked(other, score));
            }
        }
        out.sort(Comparator.comparingDouble(Ranked::score).reversed().thenComparing(Ranked::character));
        return out;
    }

    private static int count(Map<String, Integer> conversations, String other) {
        for (Map.Entry<String, Integer> entry : conversations.entrySet()) {
            if (entry.getKey().equalsIgnoreCase(other)) {
                return entry.getValue();
            }
        }
        return 0;
    }

    /** 1-based closeness rank of the other character for the mage, or 0 when they share no bond. */
    public static int rank(Player mage, String other) {
        List<Ranked> ranking = ranking(mage);
        for (int i = 0; i < ranking.size(); i++) {
            if (ranking.get(i).character().equalsIgnoreCase(other)) {
                return i + 1;
            }
        }
        return 0;
    }

    static boolean livingOtherAccount(String character, UUID account) {
        Optional<CharacterInfo> info;
        try {
            info = Characters.info(UUID.fromString(character));
        } catch (IllegalArgumentException ex) {
            return false;
        }
        return info.isPresent() && info.get().status() == CharacterStatus.ALIVE
                && !info.get().ref().ownerId().equals(account);
    }
}
