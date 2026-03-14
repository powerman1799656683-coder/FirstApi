package com.firstapi.backend.util;

public final class ValidationSupport {

    private ValidationSupport() {}

    public static String requireNotBlank(String value, String message) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(message);
        }
        return value.trim();
    }

    public static int requirePositive(Integer value, String message) {
        if (value == null || value.intValue() <= 0) {
            throw new IllegalArgumentException(message);
        }
        return value.intValue();
    }

    public static int requireNonNegative(Integer value, String message) {
        if (value == null || value.intValue() < 0) {
            throw new IllegalArgumentException(message);
        }
        return value.intValue();
    }

    public static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    public static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}