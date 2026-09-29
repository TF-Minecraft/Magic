package net.tfminecraft.magic.tick;

/**
 * Snapshot of elapsed plugin time for one global tick cycle.
 */
public final class MagicTickContext {

    private final long elapsedSeconds;
    private final long previousSeconds;

    public static MagicTickContext of(long elapsedSeconds) {
        return new MagicTickContext(elapsedSeconds);
    }

    MagicTickContext(long elapsedSeconds) {
        this(elapsedSeconds - 1L, elapsedSeconds);
    }

    /** A callback can span part of a second or cross several periodic boundaries. */
    public static MagicTickContext between(long previousSeconds, long elapsedSeconds) {
        return new MagicTickContext(previousSeconds, elapsedSeconds);
    }

    private MagicTickContext(long previousSeconds, long elapsedSeconds) {
        this.elapsedSeconds = elapsedSeconds;
        this.previousSeconds = previousSeconds;
    }

    public long seconds() {
        return elapsedSeconds;
    }

    public long minutes() {
        return elapsedSeconds / 60L;
    }

    public long hours() {
        return elapsedSeconds / 3600L;
    }

    public boolean every(long intervalSeconds) {
        return intervalSeconds > 0
                && Math.floorDiv(elapsedSeconds, intervalSeconds)
                        > Math.floorDiv(previousSeconds, intervalSeconds);
    }

    public boolean everyMinutes(long minutes) {
        return every(minutes * 60L);
    }

    public boolean everyHours(long hours) {
        return every(hours * 3600L);
    }
}
