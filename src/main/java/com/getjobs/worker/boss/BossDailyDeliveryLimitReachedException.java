package com.getjobs.worker.boss;

/** Indicates that Boss has rejected further communication for today. */
public class BossDailyDeliveryLimitReachedException extends RuntimeException {
    public BossDailyDeliveryLimitReachedException(String message) {
        super(message);
    }
}
