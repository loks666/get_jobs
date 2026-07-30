package com.getjobs.worker.boss;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BossNavigationRaceTest {

    @Test
    void recognizesNavigationRaceInCauseChain() {
        RuntimeException error = new RuntimeException(
                "wrapper",
                new RuntimeException("Execution context was destroyed, most likely because of a navigation")
        );

        assertTrue(Boss.isNavigationRace(error));
        assertFalse(Boss.isNavigationRace(new RuntimeException("selector not found")));
    }
}
