package com.conceptualware.config;

import io.micrometer.observation.ObservationRegistry;
import io.micrometer.observation.aop.ObservedAspect;
import io.micrometer.tracing.Tracer;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;

/**
 * Concept #27 — Observabilidade: Rastreamento Distribuído (Distributed Tracing)
 *   OpenTelemetry (OTel) — vendor-neutral instrumentation standard
 *   Trace ID e Span ID — propagation context
 *   Correlação de logs por Trace ID — MDC injection
 *   Span — unit of work within a trace
 *   APM (Application Performance Monitoring) — automatic method timing via @Observed
 *
 * Architecture: micrometer-tracing-bridge-otel bridges Micrometer's tracing API
 * to OpenTelemetry SDK, enabling export to Jaeger/Zipkin via OTLP.
 */
@Configuration
public class TracingConfig {

    /**
     * Enable @Observed annotation on any Spring bean for automatic span creation.
     * Concept #27 — APM, Span
     */
    @Bean
    public ObservedAspect observedAspect(ObservationRegistry observationRegistry) {
        return new ObservedAspect(observationRegistry);
    }

    /**
     * HTTP filter that:
     *   1. Extracts or generates a Trace ID from the incoming request
     *   2. Injects it into MDC so every log line in this request carries it
     *   3. Adds it to the response header for client-side correlation
     *
     * Concept #27 — Trace ID, Span ID, Correlação de logs por Trace ID
     */
    @Bean
    public OncePerRequestFilter traceIdFilter(Tracer tracer) {
        return new OncePerRequestFilter() {
            @Override
            protected void doFilterInternal(HttpServletRequest request,
                                            HttpServletResponse response,
                                            FilterChain filterChain)
                throws ServletException, IOException {

                // Extract W3C TraceContext header or fall back to X-Trace-ID header
                String traceId = request.getHeader("traceparent");
                if (traceId == null) {
                    traceId = request.getHeader("X-Trace-ID");
                }
                if (traceId == null) {
                    // If no incoming trace, generate one (root span)
                    var currentSpan = tracer.currentSpan();
                    traceId = (currentSpan != null && currentSpan.context() != null)
                        ? currentSpan.context().traceId()
                        : UUID.randomUUID().toString().replace("-", "");
                }

                // Inject into MDC — every log line will include trace_id (Concept #27)
                MDC.put("trace_id", traceId);
                MDC.put("span_id",  traceId.substring(0, Math.min(16, traceId.length())));
                MDC.put("service",  "conceptualware-backend");

                // Propagate to downstream services via response header
                response.setHeader("X-Trace-ID", traceId);

                try {
                    filterChain.doFilter(request, response);
                } finally {
                    MDC.clear(); // always clean up thread-local MDC
                }
            }
        };
    }
}
