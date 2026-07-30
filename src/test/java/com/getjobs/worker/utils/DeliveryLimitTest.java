package com.getjobs.worker.utils;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DeliveryLimitTest {
    @Test
    void defaultsToUnlimited() {
        assertEquals(Integer.MAX_VALUE, DeliveryLimit.parse(null));
        assertEquals(Integer.MAX_VALUE, DeliveryLimit.parse(" "));
    }

    @Test
    void acceptsZeroAndPositiveLimits() {
        assertEquals(0, DeliveryLimit.parse("0"));
        assertEquals(1, DeliveryLimit.parse(" 1 "));
    }

    @Test
    void readsJvmProperty() {
        System.setProperty("getjobs.delivery.max", "1");
        try {
            assertEquals(1, DeliveryLimit.configuredMax());
        } finally {
            System.clearProperty("getjobs.delivery.max");
        }
    }

    @Test
    void rejectsUnsafeValues() {
        assertThrows(IllegalArgumentException.class, () -> DeliveryLimit.parse("-1"));
        assertThrows(IllegalArgumentException.class, () -> DeliveryLimit.parse("many"));
    }
}
