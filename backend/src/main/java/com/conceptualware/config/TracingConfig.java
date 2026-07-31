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

@Configuration
public class TracingConfig {

    @Bean
    public ObservedAspect observedAspect(ObservationRegistry observationRegistry) {
        return new ObservedAspect(observationRegistry);
    }

    @Bean
    public OncePerRequestFilter traceIdFilter(Tracer tracer) {
        return new OncePerRequestFilter() {
            @Override
            protected void doFilterInternal(HttpServletRequest request,
                                            HttpServletResponse response,
                                            FilterChain filterChain)
                throws ServletException, IOException {

                String traceId = request.getHeader("traceparent");
                if (traceId == null) {
                    traceId = request.getHeader("X-Trace-ID");
                }
                if (traceId == null) {
                    var currentSpan = tracer.currentSpan();
                    traceId = (currentSpan != null && currentSpan.context() != null)
                        ? currentSpan.context().traceId()
                        : UUID.randomUUID().toString().replace("-", "");
                }

                MDC.put("trace_id", traceId);
                MDC.put("span_id",  traceId.substring(0, Math.min(16, traceId.length())));
                MDC.put("service",  "conceptualware-backend");

                response.setHeader("X-Trace-ID", traceId);

                try {
                    filterChain.doFilter(request, response);
                } finally {
                    MDC.clear();
                }
            }
        };
    }
}
