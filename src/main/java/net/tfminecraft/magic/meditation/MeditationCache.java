package net.tfminecraft.magic.meditation;

public final class MeditationCache {

    public static String pedestalId = "pedestal";
    public static String pedestalSlot = "*";
    public static int cardinalOffset = 4;
    public static int diagonalOffset = 3;
    public static double startOrbRange = 2.5;
    public static double orbHitRadius = 0.5;
    public static double orbClickRange = 12.0;
    public static int mentalCostPerHit = 1;
    public static int surgeLockSeconds = 10;
    public static int maxLiveOrbs = 8;
    public static long spawnIntervalTicks = 40L;
    public static long orbitPeriodTicks = 80L;
    public static int orbIntroTicks = 20;
    public static int orbLifetimeTicks = 100;
    public static double orbitRadiusMin = 1.6;
    public static double orbitRadiusMax = 2.8;
    public static double orbitHeightMin = 0.6;
    public static double orbitHeightMax = 1.4;
    public static double orbitSpeedJitterMin = 0.75;
    public static double orbitSpeedJitterMax = 1.25;
    public static double orbitBob = 0.15;
    public static double flowEquilibriumMin = 0.02;
    public static double flowEquilibriumMax = 0.06;
    public static double surgeEquilibriumMin = 0.02;
    public static double surgeEquilibriumMax = 0.10;
    public static double resonancePerHit = 4.0;
    public static double orbitRadius = 2.4;

    private MeditationCache() {}

    public static final class ArtifactDef {
        public final String elementId;
        public final double power;

        public ArtifactDef(String elementId, double power) {
            this.elementId = elementId;
            this.power = power;
        }
    }
}
