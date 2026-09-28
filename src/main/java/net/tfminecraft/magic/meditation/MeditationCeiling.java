package net.tfminecraft.magic.meditation;

/**
 * Meditation cannot raise an element above the aura of that element stored in the circle.
 * A character already at or above that total gains nothing; otherwise the hit is cut to the gap.
 */
public final class MeditationCeiling {

    static final double EPSILON = 0.005;

    private MeditationCeiling() {}

    public static double allowed(double current, double circlePower, double elementMax, double offered) {
        if (offered <= 0.0 || circlePower <= 0.0) {
            return 0.0;
        }
        double ceiling = Math.min(circlePower, Math.max(0.0, elementMax));
        double headroom = ceiling - current;
        if (headroom <= EPSILON) {
            return 0.0;
        }
        return Math.min(offered, headroom);
    }
}
