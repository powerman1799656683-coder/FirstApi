package com.firstapi.backend.util;

public final class ValidationSupport {

    private static final java.util.regex.Pattern SCRIPT_TAG = java.util.regex.Pattern.compile(
            "<\\s*script[^>]*>.*?<\\s*/\\s*script\\s*>", java.util.regex.Pattern.CASE_INSENSITIVE | java.util.regex.Pattern.DOTALL);
    private static final java.util.regex.Pattern EVENT_HANDLER = java.util.regex.Pattern.compile(
            "\\bon\\w+\\s*=", java.util.regex.Pattern.CASE_INSENSITIVE);
    private static final java.util.regex.Pattern HTML_TAG = java.util.regex.Pattern.compile("<[^>]+>");

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

    /**
     * 清除字符串中的 HTML/Script 标签和事件处理器，防止 XSS 攻击。
     * 用于用户输入字段的净化。
     */
    public static String sanitize(String value) {
        if (value == null) {
            return null;
        }
        String result = SCRIPT_TAG.matcher(value).replaceAll("");
        result = EVENT_HANDLER.matcher(result).replaceAll("");
        result = HTML_TAG.matcher(result).replaceAll("");
        return result.trim();
    }

    /**
     * requireNotBlank + sanitize 的组合方法，校验非空并净化 XSS。
     */
    public static String requireNotBlankSanitized(String value, String message) {
        String sanitized = sanitize(requireNotBlank(value, message));
        if (sanitized.isEmpty()) {
            throw new IllegalArgumentException(message);
        }
        return sanitized;
    }
}