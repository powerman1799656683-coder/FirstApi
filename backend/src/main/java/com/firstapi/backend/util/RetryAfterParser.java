package com.firstapi.backend.util;

import com.firstapi.backend.model.RelayResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

/**
 * Shared utility for parsing the {@code retry-after} HTTP header.
 * Supports integer seconds and HTTP-date (RFC 7231) formats.
 */
public final class RetryAfterParser {

    private static final Logger LOGGER = LoggerFactory.getLogger(RetryAfterParser.class);
    private static final int DEFAULT_COOLDOWN_SECONDS = 120;
    private static final int MAX_COOLDOWN_SECONDS = 86400; // 1 day

    private RetryAfterParser() {}

    /**
     * Parses retry-after from a RelayResult's response headers.
     *
     * @return cooldown duration in seconds; falls back to 120s on missing/unparsable header
     */
    public static int parseSeconds(RelayResult result) {
        if (result == null) {
            return DEFAULT_COOLDOWN_SECONDS;
        }
        return parseSeconds(result.getResponseHeader("retry-after"));
    }

    /**
     * Parses a retry-after header value.
     *
     * @param retryAfter raw header value (may be null)
     * @return cooldown duration in seconds
     */
    public static int parseSeconds(String retryAfter) {
        if (retryAfter == null || retryAfter.trim().isEmpty()) {
            return DEFAULT_COOLDOWN_SECONDS;
        }
        retryAfter = retryAfter.trim();

        // Try integer seconds first
        try {
            int seconds = Integer.parseInt(retryAfter);
            return seconds > 0 ? Math.min(seconds, MAX_COOLDOWN_SECONDS) : 1;
        } catch (NumberFormatException ignored) {}

        // Try HTTP-date (RFC 7231): e.g. "Sun, 06 Nov 1994 08:49:37 GMT"
        try {
            ZonedDateTime retryAt = ZonedDateTime.parse(retryAfter, DateTimeFormatter.RFC_1123_DATE_TIME);
            long seconds = retryAt.toInstant().getEpochSecond() - Instant.now().getEpochSecond();
            return seconds > 0 ? (int) Math.min(seconds, MAX_COOLDOWN_SECONDS) : 1;
        } catch (DateTimeParseException ignored) {}

        LOGGER.warn("Unparsable retry-after header: '{}', using default {}s", retryAfter, DEFAULT_COOLDOWN_SECONDS);
        return DEFAULT_COOLDOWN_SECONDS;
    }
}
