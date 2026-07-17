package com.getjobs.worker.manager;

import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;

final class PlaywrightAccessGate {
    private final ReentrantLock lock = new ReentrantLock(true);

    // ponytail: shared Playwright lock; split into per-context instances if cross-platform concurrency becomes a requirement
    void run(Runnable action) {
        lock.lock();
        try {
            action.run();
        } finally {
            lock.unlock();
        }
    }

    <T> T call(Supplier<T> action) {
        lock.lock();
        try {
            return action.get();
        } finally {
            lock.unlock();
        }
    }

    boolean runIfIdle(Runnable action) {
        if (!lock.tryLock()) {
            return false;
        }
        try {
            action.run();
            return true;
        } finally {
            lock.unlock();
        }
    }
}
