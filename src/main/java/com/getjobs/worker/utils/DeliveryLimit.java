package com.getjobs.worker.utils;

public final class DeliveryLimit {
    private static final String PROPERTY = "getjobs.delivery.max";
    private static final String ENVIRONMENT = "GET_JOBS_MAX_DELIVERIES";

    private DeliveryLimit() {
    }

    public static int configuredMax() {
        String value = System.getProperty(PROPERTY);
        return parse(value == null || value.isBlank() ? System.getenv(ENVIRONMENT) : value);
    }

    static int parse(String value) {
        if (value == null || value.isBlank()) {
            return Integer.MAX_VALUE;
        }
        try {
            int max = Integer.parseInt(value.trim());
            if (max < 0) {
                throw new IllegalArgumentException("投递上限不能小于 0");
            }
            return max;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("投递上限必须是整数: " + value, e);
        }
    }
}
