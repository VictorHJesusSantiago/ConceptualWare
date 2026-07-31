package com.conceptualware.infrastructure.web;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Service
@Slf4j
public class IdempotencyService {

    private static final Duration RETENTION = Duration.ofHours(24);
    private static final int MAX_ENTRIES = 10_000;

    private record Entry(String responseBody, Instant createdAt, boolean completed) {}

    private final Map<String, Entry> entries = new ConcurrentHashMap<>();

    public Optional<String> beginOrReplay(String idempotencyKey) {
        purgeExpired();

        Entry existing = entries.putIfAbsent(idempotencyKey,
            new Entry(null, Instant.now(), false));

        if (existing == null) {
            return Optional.empty();
        }
        if (!existing.completed()) {
            throw new IdempotencyConflictException(
                "Uma requisição com este Idempotency-Key ainda está em processamento.");
        }
        log.debug("Replay idempotente para chave {}", idempotencyKey);
        return Optional.of(existing.responseBody());
    }

    public void complete(String idempotencyKey, String responseBody) {
        entries.put(idempotencyKey, new Entry(responseBody, Instant.now(), true));
    }

    public void release(String idempotencyKey) {
        entries.remove(idempotencyKey);
    }

    private void purgeExpired() {
        Instant cutoff = Instant.now().minus(RETENTION);
        entries.entrySet().removeIf(e -> e.getValue().createdAt().isBefore(cutoff));

        if (entries.size() > MAX_ENTRIES) {
            entries.entrySet().stream()
                .sorted(Map.Entry.comparingByValue(
                    java.util.Comparator.comparing(Entry::createdAt)))
                .limit(entries.size() - MAX_ENTRIES)
                .map(Map.Entry::getKey)
                .forEach(entries::remove);
        }
    }

    public int size() {
        return entries.size();
    }

    public static class IdempotencyConflictException extends RuntimeException {
        public IdempotencyConflictException(String message) {
            super(message);
        }
    }
}
