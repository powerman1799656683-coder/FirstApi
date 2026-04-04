package com.firstapi.backend.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoField;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Detects ChatGPT-style usage limit messages in response bodies.
 * <p>
 * ChatGPT (OAuth) returns non-standard error responses like:
 * "You've hit your usage limit. ... try again at Mar 27th, 2026 9:18 AM"
 * <p>
 * These are NOT in the standard OpenAI API JSON error format and need
 * special detection logic.
 */
public final class ChatGptUsageLimitDetector {

    private static final Logger LOGGER = LoggerFactory.getLogger(ChatGptUsageLimitDetector.class);

    private ChatGptUsageLimitDetector() {}

    /** Keywords that indicate a ChatGPT usage limit (checked case-insensitively). */
    private static final String[] USAGE_LIMIT_KEYWORDS = {
            "hit your usage limit",
            "usage limit",
            "you've reached your limit",
            "reached your limit",
            "purchase more credits",
            "upgrade to pro",
            "you are sending messages too quickly",
            "rate limit reached",
            "too many requests"
    };

    /**
     * Pattern to extract recovery time from messages like:
     * "try again at Mar 27th, 2026 9:18 AM"
     * "try again at March 27, 2026 9:18 AM"
     * "try again at 2026-03-27 09:18"
     * Also handles "or try again at ...", "try again after ..."
     */
    private static final Pattern RETRY_AT_PATTERN = Pattern.compile(
            "try\\s+again\\s+(?:at|after)\\s+(.+?)(?:\\.|$)",
            Pattern.CASE_INSENSITIVE
    );

    /**
     * Pattern for Codex-style messages:
     * "Your rate limit resets on Mar 27, 2026, 9:18 AM."
     */
    private static final Pattern RESETS_ON_PATTERN = Pattern.compile(
            "resets\\s+on\\s+(.+?)(?:\\.|$)",
            Pattern.CASE_INSENSITIVE
    );

    /**
     * Formatters for various date patterns found in ChatGPT responses.
     * "Mar 27th, 2026 9:18 AM" → need to strip ordinal suffixes (st, nd, rd, th)
     */
    private static final DateTimeFormatter[] DATE_FORMATTERS = {
            // Mar 27, 2026 9:18 AM
            new DateTimeFormatterBuilder()
                    .parseCaseInsensitive()
                    .appendPattern("MMM d, yyyy h:mm a")
                    .toFormatter(Locale.ENGLISH),
            // March 27, 2026 9:18 AM
            new DateTimeFormatterBuilder()
                    .parseCaseInsensitive()
                    .appendPattern("MMMM d, yyyy h:mm a")
                    .toFormatter(Locale.ENGLISH),
            // Mar 27, 2026 9:18:00 AM
            new DateTimeFormatterBuilder()
                    .parseCaseInsensitive()
                    .appendPattern("MMM d, yyyy h:mm:ss a")
                    .toFormatter(Locale.ENGLISH),
            // 2026-03-27 09:18
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"),
            // 2026-03-27T09:18
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm"),
            // Mar 27 2026 9:18 AM (no comma)
            new DateTimeFormatterBuilder()
                    .parseCaseInsensitive()
                    .appendPattern("MMM d yyyy h:mm a")
                    .toFormatter(Locale.ENGLISH),
            // Mar 27, 2026, 9:18 AM (Codex-style, extra comma after year)
            new DateTimeFormatterBuilder()
                    .parseCaseInsensitive()
                    .appendPattern("MMM d, yyyy, h:mm a")
                    .toFormatter(Locale.ENGLISH),
    };

    /**
     * Checks whether the response body contains a ChatGPT usage limit message.
     * Works with both JSON and plain-text bodies.
     */
    public static boolean isUsageLimitResponse(String body) {
        if (body == null || body.isEmpty()) {
            return false;
        }
        String lower = body.toLowerCase(Locale.ROOT);
        for (String keyword : USAGE_LIMIT_KEYWORDS) {
            if (lower.contains(keyword)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Extracts the recovery time from a ChatGPT usage limit response body.
     * Parses patterns like "try again at Mar 27th, 2026 9:18 AM".
     *
     * @return seconds until recovery, or -1 if no recovery time found
     */
    public static int parseRecoveryCooldownSeconds(String body) {
        if (body == null || body.isEmpty()) {
            return -1;
        }
        Matcher matcher = RETRY_AT_PATTERN.matcher(body);
        if (!matcher.find()) {
            // Try Codex-style "resets on ..." pattern
            matcher = RESETS_ON_PATTERN.matcher(body);
            if (!matcher.find()) {
                return -1;
            }
        }
        String rawDate = matcher.group(1).trim();
        // Strip ordinal suffixes: 27th → 27, 1st → 1, 2nd → 2, 3rd → 3
        String cleaned = rawDate.replaceAll("(\\d+)(?:st|nd|rd|th)", "$1");
        // Also strip trailing punctuation
        cleaned = cleaned.replaceAll("[,;:!?]+$", "").trim();

        LocalDateTime recoveryTime = tryParseDate(cleaned);
        if (recoveryTime == null) {
            LOGGER.warn("Could not parse ChatGPT recovery time: '{}'", rawDate);
            return -1;
        }

        long secondsUntil = java.time.Duration.between(LocalDateTime.now(), recoveryTime).getSeconds();
        if (secondsUntil <= 0) {
            // Recovery time already passed, use a minimal cooldown
            return 60;
        }
        // Cap at 7 days
        return (int) Math.min(secondsUntil, 7 * 24 * 3600);
    }

    private static LocalDateTime tryParseDate(String text) {
        for (DateTimeFormatter fmt : DATE_FORMATTERS) {
            try {
                return LocalDateTime.parse(text, fmt);
            } catch (DateTimeParseException ignored) {
            }
        }
        return null;
    }
}
