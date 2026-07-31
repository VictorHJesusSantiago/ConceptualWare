package com.conceptualware.infrastructure.security;

import com.conceptualware.security.CsrfTokenService;
import com.conceptualware.security.SecurityAuditService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Set;

@Component
@RequiredArgsConstructor
@Slf4j
public class CsrfDoubleSubmitFilter extends OncePerRequestFilter {

    private static final String CSRF_HEADER = "X-CSRF-Token";
    private static final String CSRF_COOKIE = "csrf-token";

    private static final Set<String> SAFE_METHODS = Set.of("GET", "HEAD", "OPTIONS", "TRACE");

    private static final Set<String> EXEMPT_PATHS = Set.of(
        "/api/v1/security/csrf-token",
        "/api/v1/security/csp-report",
        "/api/v1/auth/login",
        "/api/v1/auth/register"
    );

    private final CsrfTokenService csrfTokenService;
    private final SecurityAuditService securityAuditService;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                     FilterChain filterChain) throws ServletException, IOException {

        if (!requiresCsrfCheck(request)) {
            filterChain.doFilter(request, response);
            return;
        }

        String headerToken = request.getHeader(CSRF_HEADER);
        String cookieToken = readCsrfCookie(request);

        boolean valid = headerToken != null
            && cookieToken != null
            && constantTimeEquals(headerToken, cookieToken)
            && csrfTokenService.isValid(headerToken);

        if (!valid) {
            securityAuditService.record(SecurityAuditService.EventType.CSRF_REJECTED,
                request.getRemoteAddr(), request.getMethod() + " " + request.getRequestURI());
            log.warn("Requisição rejeitada por falha de CSRF: {} {}", request.getMethod(), request.getRequestURI());

            response.setStatus(HttpStatus.FORBIDDEN.value());
            response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
            response.getWriter().write("""
                {"type":"https://conceptualware.dev/problems/csrf-failure",\
                "title":"Validação CSRF falhou",\
                "status":403,\
                "detail":"Header X-CSRF-Token ausente, divergente do cookie ou expirado."}""");
            return;
        }

        filterChain.doFilter(request, response);
    }

    private boolean requiresCsrfCheck(HttpServletRequest request) {
        if (SAFE_METHODS.contains(request.getMethod())) return false;
        if (EXEMPT_PATHS.contains(request.getRequestURI())) return false;

        String authorization = request.getHeader("Authorization");
        if (authorization != null && authorization.startsWith("Bearer ")) return false;

        return readCsrfCookie(request) != null;
    }

    private String readCsrfCookie(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) return null;
        for (Cookie cookie : cookies) {
            if (CSRF_COOKIE.equals(cookie.getName())) return cookie.getValue();
        }
        return null;
    }

    private boolean constantTimeEquals(String a, String b) {
        if (a.length() != b.length()) return false;
        int result = 0;
        for (int i = 0; i < a.length(); i++) result |= a.charAt(i) ^ b.charAt(i);
        return result == 0;
    }
}
