package com.conceptualware.domain.shared;

public record Percentage(double value) {

    public Percentage {
        if (value < 0.0 || value > 100.0) {
            throw new IllegalArgumentException("Percentage deve estar entre 0 e 100, recebido: " + value);
        }
    }

    public static Percentage of(long completed, long total) {
        if (total <= 0) return new Percentage(0.0);
        return new Percentage(Math.min(100.0, (completed * 100.0) / total));
    }

    public static Percentage zero() {
        return new Percentage(0.0);
    }

    public boolean isComplete() {
        return value >= 100.0;
    }

    public Percentage add(double delta) {
        return new Percentage(Math.min(100.0, Math.max(0.0, value + delta)));
    }
}
