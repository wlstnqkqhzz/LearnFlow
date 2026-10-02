package com.be.statistics.dto;

import com.be.global.exception.*;
import jakarta.validation.constraints.*;
import java.time.*;
import java.time.temporal.ChronoUnit;
import org.springframework.format.annotation.DateTimeFormat;

public record StatisticsFilter(
        @NotNull @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
        @NotNull @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
        @Positive Long courseId, @Positive Long departmentId) {
    public static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

    public Range resolve(Clock clock) {
        if (startDate == null || endDate == null || startDate.getYear() < 1000
                || endDate.isBefore(startDate) || endDate.isAfter(LocalDate.now(clock.withZone(SEOUL)))
                || ChronoUnit.DAYS.between(startDate, endDate) >= 366
                || courseId != null && courseId <= 0 || departmentId != null && departmentId <= 0)
            throw new BusinessException(ErrorCode.INVALID_STATISTICS_RANGE);
        return new Range(this, utc(startDate), utc(endDate.plusDays(1)),
                (int) ChronoUnit.DAYS.between(startDate, endDate) + 1);
    }

    private static LocalDateTime utc(LocalDate date) {
        return date.atStartOfDay(SEOUL).withZoneSameInstant(ZoneOffset.UTC).toLocalDateTime();
    }
    public record Range(StatisticsFilter filter, LocalDateTime startUtc, LocalDateTime endUtcExclusive, int days) {}
}
