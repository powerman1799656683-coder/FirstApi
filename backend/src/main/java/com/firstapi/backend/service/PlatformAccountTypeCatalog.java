package com.firstapi.backend.service;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Single source of truth for platform-accountType rules used by group validation and relay routing.
 */
public final class PlatformAccountTypeCatalog {

    private static final Map<String, List<String>> PLATFORM_TYPES;

    static {
        Map<String, List<String>> map = new LinkedHashMap<>();
        map.put("anthropic", List.of("Claude Code", "Claude Max"));
        map.put("openai", List.of("ChatGPT Plus", "ChatGPT Pro"));
        map.put("gemini", List.of("Gemini Advanced"));
        map.put("antigravity", List.of("Standard"));
        PLATFORM_TYPES = Collections.unmodifiableMap(map);
    }

    private PlatformAccountTypeCatalog() {
    }

    public static String normalizePlatformKey(String platform) {
        if (platform == null) {
            return "";
        }
        String normalized = platform.trim().toLowerCase(Locale.ROOT);
        if (normalized.contains("anthropic") || normalized.contains("claude")) {
            return "anthropic";
        }
        if (normalized.contains("openai") || normalized.contains("chatgpt") || normalized.contains("gpt")) {
            return "openai";
        }
        if (normalized.contains("gemini")) {
            return "gemini";
        }
        if (normalized.contains("antigravity")) {
            return "antigravity";
        }
        return normalized;
    }

    public static List<String> allowedAccountTypes(String platform) {
        return PLATFORM_TYPES.getOrDefault(normalizePlatformKey(platform), List.of());
    }

    public static String defaultAccountType(String platform) {
        List<String> allowed = allowedAccountTypes(platform);
        if (allowed.isEmpty()) {
            return "Standard";
        }
        return allowed.get(0);
    }

    public static String resolveAllowedAccountType(String platform, String accountType) {
        if (accountType == null) {
            return null;
        }
        String trimmed = accountType.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        List<String> allowed = allowedAccountTypes(platform);
        for (String candidate : allowed) {
            if (candidate.equalsIgnoreCase(trimmed)) {
                return candidate;
            }
        }
        return null;
    }

    public static boolean providerMatchesPlatform(String provider, String groupPlatform) {
        if (groupPlatform == null || groupPlatform.trim().isEmpty()) {
            return false;
        }
        String normalizedPlatform = normalizePlatformKey(groupPlatform);
        if (normalizedPlatform.isEmpty()) {
            return false;
        }
        if ("all".equals(normalizedPlatform)) {
            return true;
        }
        String normalizedProvider = provider == null ? "" : provider.trim().toLowerCase(Locale.ROOT);
        if ("claude".equals(normalizedProvider)) {
            return "anthropic".equals(normalizedPlatform);
        }
        return normalizedProvider.equals(normalizedPlatform);
    }
}

