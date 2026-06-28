package com.conceptualware.infrastructure.observability;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.metrics.*;
import io.opentelemetry.api.trace.*;
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.context.propagation.ContextPropagators;
import io.opentelemetry.context.propagation.TextMapGetter;
import io.opentelemetry.context.propagation.TextMapSetter;
import io.opentelemetry.exporter.otlp.http.trace.OtlpHttpSpanExporter;
import io.opentelemetry.exporter.otlp.http.metrics.OtlpHttpMetricExporter;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.metrics.SdkMeterProvider;
import io.opentelemetry.sdk.metrics.export.PeriodicMetricReader;
import io.opentelemetry.sdk.resources.Resource;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.export.BatchSpanProcessor;
import io.opentelemetry.sdk.trace.samplers.Sampler;
import io.opentelemetry.semconv.resource.attributes.ResourceAttributes;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * Concept #27 — OpenTelemetry (OTel): Distributed Tracing, Metrics, Logs
 *
 * OpenTelemetry is the industry-standard observability framework (CNCF).
 * It provides a vendor-neutral API + SDK for:
 *
 *   Traces:   represent a REQUEST flowing through distributed services.
 *             A trace = tree of spans. Span = unit of work with timing.
 *   Metrics:  numerical measurements over time (counters, gauges, histograms).
 *   Logs:     timestamped text events (correlated to traces via trace_id).
 *
 * Signal correlation:
 *   trace_id links a log entry to the distributed trace it belongs to.
 *   span_id links to the specific span within that trace.
 *   Observability = "What's happening?" (metrics) + "Why?" (traces + logs)
 *
 * Architecture:
 *   App code → OTel SDK → Exporter → Collector → Backend (Jaeger/Tempo/Datadog)
 *
 *   OTel Collector (sidecar/agent):
 *     - Receives spans from multiple SDKs (gRPC/HTTP)
 *     - Processes (filters, batches, transforms)
 *     - Exports to one or more backends simultaneously
 *
 * Key concepts:
 *   Span:          unit of work with: name, kind, start/end time, attributes, events, status
 *   SpanContext:   immutable: traceId(128bit) + spanId(64bit) + traceFlags(sampled?)
 *   Propagation:   inject/extract context across process boundaries (HTTP headers, message queues)
 *   Sampling:      ParentBased (follow parent) | TraceIdRatio (% of traces) | AlwaysOn/Off
 *   Instrumentation: automatic (agents/bytecode) or manual (this class)
 *
 * W3C Trace Context: standard header format
 *   traceparent: 00-{traceId}-{spanId}-{flags}
 *   tracestate:  vendor-specific state
 *
 * OTLP: OpenTelemetry Protocol — gRPC or HTTP/JSON wire format.
 *       All modern backends accept OTLP.
 *       Legacy: Jaeger native, Zipkin B3 (still common, being phased out).
 */
@Service
public class OpenTelemetryService {

    // ── SDK bootstrap ─────────────────────────────────────────────────────────

    /**
     * Initialize the OTel SDK with OTLP exporters.
     * In Spring Boot, this is typically done via the opentelemetry-spring-boot-starter
     * which auto-configures via application.yml. This manual setup is for educational clarity.
     *
     * Call once at application startup (or let the starter do it automatically).
     */
    public static OpenTelemetry buildOpenTelemetry(String serviceName, String otlpEndpoint) {
        // Resource: identifies the service producing telemetry
        Resource resource = Resource.getDefault().merge(
            Resource.create(Attributes.of(
                ResourceAttributes.SERVICE_NAME,      serviceName,
                ResourceAttributes.SERVICE_VERSION,   "1.5.0",
                ResourceAttributes.DEPLOYMENT_ENVIRONMENT, "production",
                AttributeKey.stringKey("service.namespace"), "conceptualware"
            ))
        );

        // Trace exporter: sends spans to OTel Collector via OTLP/HTTP
        OtlpHttpSpanExporter spanExporter = OtlpHttpSpanExporter.builder()
            .setEndpoint(otlpEndpoint + "/v1/traces")
            .setTimeout(Duration.ofSeconds(10))
            .build();

        // Tracer provider: manages tracers and span lifecycle
        SdkTracerProvider tracerProvider = SdkTracerProvider.builder()
            .setResource(resource)
            .setSampler(Sampler.parentBased(Sampler.traceIdRatioBased(0.1)))
            // parentBased: if parent is sampled → sample; else apply ratio
            // ratio 0.1 = 10% of root traces sampled
            .addSpanProcessor(BatchSpanProcessor.builder(spanExporter)
                .setScheduleDelay(5, TimeUnit.SECONDS)
                .setMaxExportBatchSize(512)
                .setMaxQueueSize(2048)
                .build())
            .build();

        // Metric exporter: sends metrics to OTel Collector via OTLP/HTTP
        OtlpHttpMetricExporter metricExporter = OtlpHttpMetricExporter.builder()
            .setEndpoint(otlpEndpoint + "/v1/metrics")
            .build();

        SdkMeterProvider meterProvider = SdkMeterProvider.builder()
            .setResource(resource)
            .registerMetricReader(
                PeriodicMetricReader.builder(metricExporter)
                    .setInterval(Duration.ofSeconds(60))  // export every 60s
                    .build()
            )
            .build();

        // Build SDK with W3C trace context propagation (standard)
        return OpenTelemetrySdk.builder()
            .setTracerProvider(tracerProvider)
            .setMeterProvider(meterProvider)
            .setPropagators(ContextPropagators.create(W3CTraceContextPropagator.getInstance()))
            .buildAndRegisterGlobal();
    }

    // ── Tracer ─────────────────────────────────────────────────────────────────

    private final Tracer tracer;
    private final Meter  meter;

    // ── Metrics instruments ────────────────────────────────────────────────────
    // Counter:   monotonically increasing (total requests, errors)
    // Gauge:     current value (queue depth, active connections)
    // Histogram: distribution of values (latency, payload size)

    private final LongCounter     requestCounter;
    private final LongCounter     errorCounter;
    private final DoubleHistogram latencyHistogram;
    private final ObservableLongGauge activeConceptsGauge;

    public OpenTelemetryService() {
        OpenTelemetry otel = GlobalOpenTelemetry.get();

        this.tracer = otel.getTracer("com.conceptualware.backend", "1.5.0");
        this.meter  = otel.getMeter("com.conceptualware.backend");

        this.requestCounter = meter.counterBuilder("conceptualware.requests.total")
            .setDescription("Total number of concept execution requests")
            .setUnit("{request}")
            .build();

        this.errorCounter = meter.counterBuilder("conceptualware.errors.total")
            .setDescription("Total number of errors")
            .setUnit("{error}")
            .build();

        this.latencyHistogram = meter.histogramBuilder("conceptualware.request.duration")
            .setDescription("Duration of concept execution requests")
            .setUnit("ms")
            .build();

        // Observable gauge — reports value on each collection interval
        this.activeConceptsGauge = meter.gaugeBuilder("conceptualware.concepts.active")
            .setDescription("Number of currently active concept executions")
            .ofLongs()
            .buildWithCallback(measurement ->
                measurement.record(getActiveConceptCount(),
                    Attributes.of(AttributeKey.stringKey("env"), "production")));
    }

    // ── Manual tracing ─────────────────────────────────────────────────────────

    /**
     * Instrument a concept execution with a distributed trace span.
     *
     * Span kinds:
     *   SERVER:   handling an incoming request (start of server-side work)
     *   CLIENT:   making an outgoing request (database, external API)
     *   INTERNAL: internal operation within a service (default)
     *   PRODUCER: sending a message to a queue
     *   CONSUMER: receiving a message from a queue
     */
    public <T> T traceConceptExecution(String conceptId, String userId, Supplier<T> work) {
        Span span = tracer.spanBuilder("concept.execute")
            .setSpanKind(SpanKind.INTERNAL)
            .setAttribute("concept.id",   conceptId)
            .setAttribute("user.id",      userId)
            .setAttribute("service.name", "backend")
            .startSpan();

        long startMs = System.currentTimeMillis();

        try (Scope scope = span.makeCurrent()) {
            // scope.close() restores previous context on exit
            // Nested work can access current span via Span.current()

            span.addEvent("execution.started",
                Attributes.of(AttributeKey.stringKey("concept"), conceptId));

            T result = work.get();

            span.setStatus(StatusCode.OK);
            span.addEvent("execution.completed");

            long latency = System.currentTimeMillis() - startMs;
            recordSuccess(conceptId, latency);

            return result;

        } catch (Exception e) {
            // Record exception on span (shows in Jaeger/Tempo with stack trace)
            span.recordException(e, Attributes.of(
                AttributeKey.stringKey("concept.id"), conceptId
            ));
            span.setStatus(StatusCode.ERROR, e.getMessage());
            recordError(conceptId, e.getClass().getSimpleName());
            throw e;

        } finally {
            span.end();   // always end span — even on exception
        }
    }

    /**
     * Add a child span for a database operation.
     * Uses SpanKind.CLIENT to signal "outgoing call" to tracing backends.
     */
    public <T> T traceDbOperation(String operation, String collection, Supplier<T> work) {
        Span span = tracer.spanBuilder("db." + operation)
            .setSpanKind(SpanKind.CLIENT)
            // Standard DB semantic conventions (OTel spec)
            .setAttribute("db.system",     "mongodb")
            .setAttribute("db.name",       "conceptualware")
            .setAttribute("db.operation",  operation)
            .setAttribute("db.mongodb.collection", collection)
            .startSpan();

        try (Scope scope = span.makeCurrent()) {
            T result = work.get();
            span.setStatus(StatusCode.OK);
            return result;
        } catch (Exception e) {
            span.recordException(e);
            span.setStatus(StatusCode.ERROR, e.getMessage());
            throw e;
        } finally {
            span.end();
        }
    }

    // ── Context propagation ───────────────────────────────────────────────────

    /**
     * Inject trace context into outgoing HTTP headers.
     * Produces: `traceparent: 00-<traceId>-<spanId>-01`
     *
     * Called before making an outgoing HTTP request to another service.
     * The receiving service extracts this header to continue the same trace.
     */
    public void injectTraceContext(Map<String, String> headers) {
        TextMapSetter<Map<String, String>> setter = Map::put;
        GlobalOpenTelemetry.getPropagators()
            .getTextMapPropagator()
            .inject(Context.current(), headers, setter);
    }

    /**
     * Extract trace context from incoming HTTP headers.
     * Restores the distributed trace so this service continues it.
     *
     * Called at the entry point of an HTTP request handler.
     */
    public Context extractTraceContext(Map<String, String> headers) {
        TextMapGetter<Map<String, String>> getter = new TextMapGetter<>() {
            @Override public Iterable<String> keys(Map<String, String> carrier) {
                return carrier.keySet();
            }
            @Override public String get(Map<String, String> carrier, String key) {
                return carrier.get(key);
            }
        };
        return GlobalOpenTelemetry.getPropagators()
            .getTextMapPropagator()
            .extract(Context.current(), headers, getter);
    }

    /**
     * Start a SERVER span that continues an incoming distributed trace.
     * Pattern used in HTTP interceptors / servlet filters.
     */
    public Span startServerSpan(String operationName, Map<String, String> incomingHeaders) {
        Context parentContext = extractTraceContext(incomingHeaders);
        return tracer.spanBuilder(operationName)
            .setSpanKind(SpanKind.SERVER)
            .setParent(parentContext)
            .startSpan();
    }

    // ── Metric recording ───────────────────────────────────────────────────────

    private void recordSuccess(String conceptId, long latencyMs) {
        requestCounter.add(1, Attributes.of(
            AttributeKey.stringKey("concept"), conceptId,
            AttributeKey.stringKey("status"),  "success"
        ));
        latencyHistogram.record(latencyMs, Attributes.of(
            AttributeKey.stringKey("concept"), conceptId
        ));
    }

    private void recordError(String conceptId, String errorType) {
        requestCounter.add(1, Attributes.of(
            AttributeKey.stringKey("concept"), conceptId,
            AttributeKey.stringKey("status"),  "error"
        ));
        errorCounter.add(1, Attributes.of(
            AttributeKey.stringKey("concept"),    conceptId,
            AttributeKey.stringKey("error.type"), errorType
        ));
    }

    // Placeholder for active concept tracking
    private long getActiveConceptCount() { return 0L; }

    // ── Baggage ───────────────────────────────────────────────────────────────
    /**
     * Baggage: key-value pairs propagated alongside trace context.
     * Useful for passing non-sensitive metadata (user tier, region)
     * without putting it in span attributes of every span.
     *
     * Note: Baggage is sent to ALL downstream services — avoid secrets.
     */
    public Context withBaggage(String key, String value) {
        return io.opentelemetry.api.baggage.Baggage.current()
            .toBuilder()
            .put(key, value)
            .build()
            .storeInContext(Context.current());
    }

    public String getBaggage(String key) {
        return io.opentelemetry.api.baggage.Baggage.current().getEntryValue(key);
    }
}
