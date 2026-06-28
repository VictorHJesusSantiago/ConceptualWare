package com.conceptualware.infrastructure;

import com.conceptualware.infrastructure.observability.OpenTelemetryService;
import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.context.Context;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for OpenTelemetry concepts:
 * - Span creation, naming, kind, status
 * - Exception recording on spans
 * - Trace context propagation (W3C traceparent header injection/extraction)
 * - Metrics instruments (structural — InMemory exporter covers spans)
 *
 * Uses the OTel SDK's in-memory exporter so tests run without a real collector.
 */
@DisplayName("OpenTelemetry Instrumentation")
class OpenTelemetryServiceTest {

    private InMemorySpanExporter spanExporter;
    private OpenTelemetry         otel;
    private OpenTelemetryService  service;

    @BeforeEach
    void setUp() {
        GlobalOpenTelemetry.resetForTest();

        spanExporter = InMemorySpanExporter.create();

        SdkTracerProvider tracerProvider = SdkTracerProvider.builder()
            .addSpanProcessor(SimpleSpanProcessor.create(spanExporter))
            .build();

        otel = OpenTelemetrySdk.builder()
            .setTracerProvider(tracerProvider)
            .buildAndRegisterGlobal();

        service = new OpenTelemetryService();
    }

    @AfterEach
    void tearDown() {
        GlobalOpenTelemetry.resetForTest();
        spanExporter.reset();
    }

    // ── Span creation ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("traceConceptExecution: creates a span with correct name and attributes")
    void testSpanCreated() {
        service.traceConceptExecution("sorting.quicksort", "user-123", () -> "done");

        List<SpanData> spans = spanExporter.getFinishedSpanItems();
        assertEquals(1, spans.size());

        SpanData span = spans.get(0);
        assertEquals("concept.execute", span.getName());
        assertEquals(SpanKind.INTERNAL, span.getKind());
        assertEquals("sorting.quicksort", span.getAttributes().get(
            io.opentelemetry.api.common.AttributeKey.stringKey("concept.id")));
        assertEquals("user-123", span.getAttributes().get(
            io.opentelemetry.api.common.AttributeKey.stringKey("user.id")));
    }

    @Test
    @DisplayName("traceConceptExecution: span status is OK on success")
    void testSpanStatusOk() {
        service.traceConceptExecution("dp.fibonacci", "user-42", () -> 42);

        SpanData span = spanExporter.getFinishedSpanItems().get(0);
        assertEquals(StatusCode.OK, span.getStatus().getStatusCode());
    }

    @Test
    @DisplayName("traceConceptExecution: span records exception and ERROR status on failure")
    void testSpanRecordsException() {
        assertThrows(RuntimeException.class, () ->
            service.traceConceptExecution("graph.bfs", "user-99", () -> {
                throw new RuntimeException("execution failed");
            })
        );

        SpanData span = spanExporter.getFinishedSpanItems().get(0);
        assertEquals(StatusCode.ERROR, span.getStatus().getStatusCode());
        assertFalse(span.getEvents().isEmpty(), "Exception event must be recorded on span");
    }

    @Test
    @DisplayName("traceConceptExecution: span always ends (finally block)")
    void testSpanAlwaysEnds() {
        try {
            service.traceConceptExecution("sort.merge", "u1", () -> {
                throw new IllegalArgumentException("oops");
            });
        } catch (Exception ignored) {}

        List<SpanData> spans = spanExporter.getFinishedSpanItems();
        assertEquals(1, spans.size(), "Span must be ended even when exception is thrown");
        // A finished span has a non-zero end epoch
        assertTrue(spans.get(0).getEndEpochNanos() > 0);
    }

    // ── DB span ───────────────────────────────────────────────────────────────

    @Test
    @DisplayName("traceDbOperation: creates a CLIENT span with DB semantic attributes")
    void testDbSpan() {
        service.traceDbOperation("find", "concepts", () -> List.of());

        List<SpanData> spans = spanExporter.getFinishedSpanItems();
        assertEquals(1, spans.size());

        SpanData span = spans.get(0);
        assertEquals("db.find", span.getName());
        assertEquals(SpanKind.CLIENT, span.getKind());
        assertEquals("mongodb", span.getAttributes().get(
            io.opentelemetry.api.common.AttributeKey.stringKey("db.system")));
        assertEquals("concepts", span.getAttributes().get(
            io.opentelemetry.api.common.AttributeKey.stringKey("db.mongodb.collection")));
    }

    // ── Context propagation ───────────────────────────────────────────────────

    @Test
    @DisplayName("injectTraceContext: adds W3C traceparent header")
    void testInjectTraceContext() {
        // Start a span so there IS a current context to inject
        var tracer = otel.getTracer("test");
        Span span = tracer.spanBuilder("test-root").startSpan();
        try (var scope = span.makeCurrent()) {
            Map<String, String> headers = new HashMap<>();
            service.injectTraceContext(headers);

            assertTrue(headers.containsKey("traceparent"),
                "W3C traceparent header must be injected");
            // Format: 00-{32hex}-{16hex}-{2hex}
            assertTrue(headers.get("traceparent").startsWith("00-"),
                "traceparent must follow W3C format: 00-traceId-spanId-flags");
        } finally {
            span.end();
        }
    }

    @Test
    @DisplayName("extractTraceContext: restores context from traceparent header")
    void testExtractTraceContext() {
        // Build a known traceparent header
        String traceId = "4bf92f3577b34da6a3ce929d0e0e4736";
        String spanId  = "00f067aa0ba902b7";
        Map<String, String> headers = Map.of(
            "traceparent", "00-" + traceId + "-" + spanId + "-01"
        );

        Context ctx = service.extractTraceContext(headers);
        assertNotNull(ctx, "Extracted context must not be null");

        // Verify the trace context was restored by starting a child span in it
        var tracer = otel.getTracer("test");
        Span child = tracer.spanBuilder("child").setParent(ctx).startSpan();
        child.end();

        SpanData childData = spanExporter.getFinishedSpanItems().get(0);
        assertEquals(traceId, childData.getTraceId(),
            "Child span must share the extracted trace ID");
    }

    @Test
    @DisplayName("startServerSpan: creates SERVER span continuing the incoming trace")
    void testStartServerSpan() {
        String traceId = "5bf92f3577b34da6a3ce929d0e0e4737";
        String spanId  = "01f067aa0ba902b8";
        Map<String, String> headers = Map.of(
            "traceparent", "00-" + traceId + "-" + spanId + "-01"
        );

        Span span = service.startServerSpan("POST /api/concepts/execute", headers);
        span.end();

        SpanData data = spanExporter.getFinishedSpanItems().get(0);
        assertEquals("POST /api/concepts/execute", data.getName());
        assertEquals(SpanKind.SERVER, data.getKind());
        assertEquals(traceId, data.getTraceId(),
            "Server span must continue the incoming distributed trace");
    }
}
