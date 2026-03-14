package com.firstapi.backend.util;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

public final class TimeSupport {

    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy/MM/dd");
    private static final DateTimeFormatter DATE_TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss");

    private TimeSupport() {}

    public static String today() {
        return LocalDate.now(ZONE).format(DATE_FORMAT);
    }

    public static String nowDateTime() {
        return LocalDateTime.now(ZONE).format(DATE_TIME_FORMAT);
    }

    public static String plusMonths(String date, int months) {
        LocalDate baseDate = date == null || date.trim().isEmpty()
                ? LocalDate.now(ZONE)
                : LocalDate.parse(date, DATE_FORMAT);
        return baseDate.plusMonths(months).format(DATE_FORMAT);
    }
}
