package com.getjobs.worker.boss;

public class BossAuthenticationExpiredException extends RuntimeException {
    public BossAuthenticationExpiredException(String message) {
        super(message);
    }
}
