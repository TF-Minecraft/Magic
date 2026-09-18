package net.tfminecraft.magic.util;

import java.math.BigDecimal;
import java.math.RoundingMode;

public final class MagicNumbers {

    public static final int CLAIM_DECIMALS = 6;

    private MagicNumbers() {}

    public static double round(double value, int places) {
        if (Double.isNaN(value) || Double.isInfinite(value)) {
            return 0.0;
        }
        if (places < 0) {
            throw new IllegalArgumentException("places must be >= 0");
        }
        return BigDecimal.valueOf(value)
                .setScale(places, RoundingMode.HALF_UP)
                .doubleValue();
    }

    public static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    public static String format(double value) {
        return format(value, 2);
    }

    public static String format(double value, int places) {
        return stripTrailingZeros(round(value, places), places);
    }

    public static String formatRatePerHour(double ratePerHour) {
        return format(ratePerHour) + "/h";
    }

    private static String stripTrailingZeros(double value, int places) {
        String raw = BigDecimal.valueOf(value)
                .setScale(places, RoundingMode.HALF_UP)
                .stripTrailingZeros()
                .toPlainString();
        if ("-0".equals(raw)) {
            return "0";
        }
        return raw;
    }
}
