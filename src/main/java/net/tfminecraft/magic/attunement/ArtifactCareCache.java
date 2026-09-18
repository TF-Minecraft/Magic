package net.tfminecraft.magic.attunement;

public final class ArtifactCareCache {

    public static final double DEFAULT_OFF_PER_HOUR = 1.0 / 24.0;
    public static final double DEFAULT_RECOVER_PER_HOUR = 1.0 / 168.0;

    public static boolean muffledEnabled = false;
    public static String displayFurnitureId = "artifact_display";
    public static int usersTtlDays = 7;
    public static double muffledRecoverPerHour = DEFAULT_RECOVER_PER_HOUR;
    public static double muffledOffPerHour = DEFAULT_OFF_PER_HOUR;

    private ArtifactCareCache() {}

    public static long usersTtlMs() {
        int days = Math.max(1, usersTtlDays);
        return days * 24L * 3600L * 1000L;
    }
}
