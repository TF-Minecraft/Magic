package net.tfminecraft.magic.command;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class ValueActionTest {
    @Test void actionArithmeticAndValidation() {
        assertEquals(2, ValueAction.GET.apply(2, 3, 4));
        assertNull(ValueAction.amount("-Infinity"));
        assertNull(ValueAction.parse("bogus"));
        assertFalse(ValueAction.GET.needsAmount());
    }
}
