package net.tfminecraft.magic.tick;

/**
 * Snapshot of elapsed plugin time for one global tick cycle.
 */
public final class MagicTickContext {

    private final long elapsedSeconds;

    public static MagicTickContext of(long elapsedSeconds) {
        return new MagicTickContext(elapsedSeconds);
    }

    MagicTickContext(long elapsedSeconds) {
        this.elapsedSeconds = elapsedSeconds;
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
        return intervalSeconds > 0 && elapsedSeconds % intervalSeconds == 0;
    }

    public boolean everyMinutes(long minutes) {
        return every(minutes * 60L);
    }

    public boolean everyHours(long hours) {
        return every(hours * 3600L);
    }
}
