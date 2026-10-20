package net.tfminecraft.magic.meditation;

/**
 * Meditation cannot raise an element above the sitter's share of that element's aura in the
 * circle (see {@link MeditationSitYield#elementShare}). A character already at or above the
 * share gains nothing; otherwise the hit is cut to the gap.
 */
public final class MeditationCeiling {

    static final double EPSILON = 0.005;

    private MeditationCeiling() {}

    public static double allowed(double current, double share, double elementMax, double offered) {
        if (offered <= 0.0 || share <= 0.0) {
            return 0.0;
        }
        double ceiling = Math.min(share, Math.max(0.0, elementMax));
        double headroom = ceiling - current;
        if (headroom <= EPSILON) {
            return 0.0;
        }
        return Math.min(offered, headroom);
    }
}
