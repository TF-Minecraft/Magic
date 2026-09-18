package net.tfminecraft.magic.listener;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class SpellTierGateTest {

    @Test
    void allowsWhenBothMeetSpellTier() {
        assertEquals(SpellTierGate.Refuse.NONE, SpellTierGate.refuse(true, 2, 2, 2));
    }

    @Test
    void highStaffAndLowPlayerCanCastTierOne() {
        assertEquals(SpellTierGate.Refuse.NONE, SpellTierGate.refuse(true, 4, 1, 1));
    }

    @Test
    void staffTooWeak() {
        assertEquals(SpellTierGate.Refuse.WEAPON, SpellTierGate.refuse(true, 1, 4, 3));
    }

    @Test
    void playerTooLow() {
        assertEquals(SpellTierGate.Refuse.SPELL, SpellTierGate.refuse(true, 4, 1, 3));
    }

    @Test
    void foreignWhenWeaponHasNoElement() {
        assertEquals(SpellTierGate.Refuse.FOREIGN, SpellTierGate.refuse(false, 0, 4, 1));
    }
}
