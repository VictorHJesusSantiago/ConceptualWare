package com.conceptualware.domain.shared;

import java.time.Instant;
import java.time.Duration;

/**
 * Concept #12 — DDD: Value Object — intervalo temporal imutável e auto-validado.
 */
public record DateRange(Instant start, Instant end) {

    public DateRange {
        if (start == null || end == null) {
            throw new IllegalArgumentException("start e end não podem ser nulos");
        }
        if (end.isBefore(start)) {
            throw new IllegalArgumentException("end (" + end + ") não pode ser anterior a start (" + start + ")");
        }
    }

    public static DateRange lastDays(int days) {
        Instant now = Instant.now();
        return new DateRange(now.minus(Duration.ofDays(days)), now);
    }

    public boolean contains(Instant instant) {
        return !instant.isBefore(start) && !instant.isAfter(end);
    }

    public Duration duration() {
        return Duration.between(start, end);
    }

    public boolean overlaps(DateRange other) {
        return !this.end.isBefore(other.start) && !other.end.isBefore(this.start);
    }
}
