package com.getjobs.worker.manager;

import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlaywrightAccessGateTest {
    @Test
    void runCallsDoNotOverlap() throws InterruptedException {
        PlaywrightAccessGate gate = new PlaywrightAccessGate();
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch firstEntered = new CountDownLatch(1);
        AtomicInteger active = new AtomicInteger();
        AtomicInteger maxActive = new AtomicInteger();

        Runnable action = () -> {
            await(start);
            int current = active.incrementAndGet();
            maxActive.accumulateAndGet(current, Math::max);
            firstEntered.countDown();
            try {
                Thread.sleep(50);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                active.decrementAndGet();
            }
        };

        Thread first = new Thread(() -> gate.run(action));
        Thread second = new Thread(() -> gate.run(action));
        first.start();
        second.start();
        start.countDown();
        firstEntered.await();
        first.join();
        second.join();

        assertEquals(1, maxActive.get());
    }

    @Test
    void runIfIdleSkipsBusyGate() throws InterruptedException {
        PlaywrightAccessGate gate = new PlaywrightAccessGate();
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        AtomicInteger calls = new AtomicInteger();

        Thread holder = new Thread(() -> gate.run(() -> {
            entered.countDown();
            await(release);
        }));
        holder.start();
        entered.await();

        assertFalse(gate.runIfIdle(calls::incrementAndGet));
        assertEquals(0, calls.get());

        release.countDown();
        holder.join();
        assertTrue(gate.runIfIdle(calls::incrementAndGet));
        assertEquals(1, calls.get());
    }

    private static void await(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AssertionError(e);
        }
    }
}
