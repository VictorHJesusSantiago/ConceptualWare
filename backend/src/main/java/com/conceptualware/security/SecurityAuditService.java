package com.conceptualware.security;

import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedDeque;

/**
 * Concept #21 — Auditoria de Segurança: registra eventos sensíveis (login
 * falho, lockout por força bruta, violação de CSP, etc.) em um ring buffer
 * in-memory para alimentar um dashboard de auditoria simples.
 *
 * Nota: em produção este buffer deveria ser persistido (ex.: coleção MongoDB
 * dedicada com TTL index, ou exportado para o pipeline ELK já presente em
 * observability/). Aqui mantemos in-memory para não adicionar infraestrutura
 * nova — é uma demonstração do conceito, não o sistema de auditoria final.
 */
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
