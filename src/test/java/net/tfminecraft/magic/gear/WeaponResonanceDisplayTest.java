package net.tfminecraft.magic.gear;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class WeaponResonanceDisplayTest {

    @Test
    void includeAuraEntryWhenCapOrFillPositive() {
        assertTrue(WeaponResonanceDisplay.includeAuraEntry(10.0, 0.0));
        assertTrue(WeaponResonanceDisplay.includeAuraEntry(0.0, 5.0));
        assertTrue(WeaponResonanceDisplay.includeAuraEntry(3.0, 3.0));
    }

    @Test
    void excludeAuraEntryWhenCapAndFillZero() {
        assertFalse(WeaponResonanceDisplay.includeAuraEntry(0.0, 0.0));
        assertFalse(WeaponResonanceDisplay.includeAuraEntry(-1.0, 0.0));
    }
}
