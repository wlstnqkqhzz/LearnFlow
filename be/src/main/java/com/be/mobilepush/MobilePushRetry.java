package com.be.mobilepush;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.ThreadLocalRandom;

final class MobilePushRetry {
    private MobilePushRetry() {}
    static Duration delay(int attempt, String header, Instant now) {
        Duration result = Duration.ofSeconds(attempt == 1 ? 30 : 120).plusMillis(ThreadLocalRandom.current().nextLong(1000));
        if (header == null) return result;
        try {
            Duration requested = header.trim().matches("[0-9]+")
                    ? Duration.ofSeconds(new java.math.BigInteger(header.trim()).min(java.math.BigInteger.valueOf(86400)).longValue())
                    : Duration.between(now, ZonedDateTime.parse(header, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant());
            if (requested.compareTo(result) > 0) result = requested;
        } catch (RuntimeException ignored) { }
        return result;
    }
    static boolean transientFailure(int status, String error) {
        return status == 429 || status >= 500 && status <= 599 || status == 200 && "MessageRateExceeded".equals(error);
    }
}
