package com.conceptualware.api.rest;

import com.conceptualware.security.CsrfTokenService;
import com.conceptualware.security.SecurityAuditService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Concept #21 — Segurança: CSRF token issuance, CSP violation reporting,
 *   dashboard de auditoria (agregação de eventos sensíveis).
 */
@RestController
@RequestMapping("/api/v1/security")
@RequiredArgsConstructor
@Slf4j
public class SecurityController {

    private final CsrfTokenService csrfTokenService;
    private final SecurityAuditService auditService;

    // ── GET /api/v1/security/csrf-token ───────────────────────────────────────

    @GetMapping("/csrf-token")
    public ResponseEntity<Map<String, String>> issueCsrfToken() {
        String token = csrfTokenService.generateToken();
        return ResponseEntity.ok()
            // Não HttpOnly de propósito: o cliente precisa ler para reenviar no header (double-submit).
            .header("Set-Cookie", "csrf-token=" + token + "; Path=/; SameSite=Strict; Secure")
            .body(Map.of("csrfToken", token));
    }

    // ── POST /api/v1/security/csp-report ──────────────────────────────────────

    /**
     * Endpoint compatível com o formato "report-to"/"report-uri" do Content-Security-Policy.
     * Configure no header CSP: Content-Security-Policy-Report-Only: ...; report-uri /api/v1/security/csp-report
     */
    @PostMapping("/csp-report")
    public ResponseEntity<Void> cspReport(@RequestBody(required = false) Map<String, Object> report,
                                           HttpServletRequest request) {
        String detail = report != null ? String.valueOf(report.get("csp-report")) : "empty-report";
        log.warn("CSP violation reported from {}: {}", request.getRemoteAddr(), detail);
        auditService.record(SecurityAuditService.EventType.CSP_VIOLATION, request.getRemoteAddr(), detail);
        return ResponseEntity.noContent().build();
    }

    // ── GET /api/v1/security/audit ────────────────────────────────────────────

    @GetMapping("/audit")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, Object>> auditDashboard(
            @RequestParam(defaultValue = "100") int limit) {
        List<SecurityAuditService.AuditEvent> recent = auditService.recentEvents(limit);

        Map<SecurityAuditService.EventType, Long> counts = new EnumMap<>(SecurityAuditService.EventType.class);
        for (SecurityAuditService.EventType type : SecurityAuditService.EventType.values()) {
            counts.put(type, auditService.countByType(type));
        }

        return ResponseEntity.ok(Map.of(
            "totalEvents", recent.size(),
            "countsByType", counts,
            "recentEvents", recent
        ));
    }
}
