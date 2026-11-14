package net.tfminecraft.magic.blood;

/** How far along a character is, as one number the altar's look follows. */
public final class BloodProgress {

    private BloodProgress() {}

    /**
     * One per animal species, one for answering the god, one per hunt point (devotees count
     * the whole hunt, which their answer skips) and two per final sacrifice.
     */
    public static int of(BloodRecord record) {
        if (record == null) {
            return 0;
        }
        int progress = record.getSpecies().size();
        if (record.getPath() != BloodPath.NONE) {
            progress += 1;
        }
        progress += record.getPath() == BloodPath.DEVOTEE
                ? BloodConfig.huntPointsRequired()
                : Math.min(record.getHuntPoints(), BloodConfig.huntPointsRequired());
        progress += 2 * record.getFinalsDone();
        return progress;
    }

    public static int level(BloodRecord record) {
        return BloodConfig.level(of(record));
    }
}
