package com.conceptualware.security;

import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedDeque;

@Service
public class SecurityAuditService {

    public enum EventType { LOGIN_FAILURE, ACCOUNT_LOCKOUT, CSP_VIOLATION, CSRF_REJECTED, RATE_LIMIT_EXCEEDED }

    public record AuditEvent(Instant occurredAt, EventType type, String subject, String detail) {}

    private static final int MAX_EVENTS = 500;
    private final Deque<AuditEvent> events = new ConcurrentLinkedDeque<>();

    public void record(EventType type, String subject, String detail) {
        events.addFirst(new AuditEvent(Instant.now(), type, subject, detail));
        while (events.size() > MAX_EVENTS) {
            events.removeLast();
        }
    }

    public List<AuditEvent> recentEvents(int limit) {
        return events.stream().limit(Math.max(1, Math.min(limit, MAX_EVENTS))).toList();
    }

    public long countByType(EventType type) {
        return events.stream().filter(e -> e.type() == type).count();
    }
}
