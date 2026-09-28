package net.tfminecraft.magic.util;

import org.bukkit.Location;
import org.bukkit.entity.Player;

/**
 * Recent positions of a moving orb, one per tick, so a click can be judged against where
 * the player actually saw it.
 *
 * <p>A player sees an orb about half their ping late and their click reaches us another
 * half ping later, so by the time the swing lands the orb has moved on by roughly one
 * round trip. Rewinding by the player's own ping keeps the game the same for everyone
 * instead of widening the hit radius for all.
 */
public final class OrbTrail {

    /** Ticks kept per orb. Also the hard ceiling on any configured rewind. */
    public static final int CAPACITY = 20;

    private final Location[] positions = new Location[CAPACITY];
    private int head = -1;
    private int size;

    /** Records this tick's position. Call once per tick, after the orb has moved. */
    public void push(Location location) {
        head = (head + 1) % CAPACITY;
        positions[head] = location.clone();
        if (size < CAPACITY) {
            size++;
        }
    }

    /**
     * Position {@code ticksAgo} ticks back, clamped to the oldest one kept, or null when
     * nothing has been recorded yet.
     */
    public Location ticksAgo(int ticksAgo) {
        if (size == 0) {
            return null;
        }
        int back = Math.max(0, Math.min(ticksAgo, size - 1));
        return positions[Math.floorMod(head - back, CAPACITY)];
    }

    /**
     * Ticks to rewind for this player's ping, as the two whole ticks either side of it so
     * a ping between ticks is judged against both frames the player could have seen.
     * Low ping players get {@code [0, 0]} or {@code [0, 1]}, so their game is unchanged.
     */
    public static int[] rewindTicks(Player player, int maxTicks) {
        int ping = player == null ? 0 : Math.max(0, player.getPing());
        return rewindTicks(ping, maxTicks);
    }

    public static int[] rewindTicks(int pingMs, int maxTicks) {
        int cap = Math.max(0, Math.min(maxTicks, CAPACITY - 1));
        double ticks = Math.min(cap, Math.max(0, pingMs) / 50.0);
        int low = (int) Math.floor(ticks);
        int high = Math.min(cap, (int) Math.ceil(ticks));
        return new int[] {low, high};
    }
}
