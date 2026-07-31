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
import io.opentelemetry.semconv.ResourceAttributes;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

@Service
public class OpenTelemetryService {

    public static OpenTelemetry buildOpenTelemetry(String serviceName, String otlpEndpoint) {
        Resource resource = Resource.getDefault().merge(
            Resource.create(Attributes.of(
                ResourceAttributes.SERVICE_NAME,      serviceName,
                ResourceAttributes.SERVICE_VERSION,   "1.5.0",
                ResourceAttributes.DEPLOYMENT_ENVIRONMENT, "production",
                AttributeKey.stringKey("service.namespace"), "conceptualware"
            ))
        );

        OtlpHttpSpanExporter spanExporter = OtlpHttpSpanExporter.builder()
            .setEndpoint(otlpEndpoint + "/v1/traces")
            .setTimeout(Duration.ofSeconds(10))
            .build();

        SdkTracerProvider tracerProvider = SdkTracerProvider.builder()
            .setResource(resource)
            .setSampler(Sampler.parentBased(Sampler.traceIdRatioBased(0.1)))
            .addSpanProcessor(BatchSpanProcessor.builder(spanExporter)
                .setScheduleDelay(5, TimeUnit.SECONDS)
                .setMaxExportBatchSize(512)
                .setMaxQueueSize(2048)
                .build())
            .build();

        OtlpHttpMetricExporter metricExporter = OtlpHttpMetricExporter.builder()
            .setEndpoint(otlpEndpoint + "/v1/metrics")
            .build();

        SdkMeterProvider meterProvider = SdkMeterProvider.builder()
            .setResource(resource)
            .registerMetricReader(
                PeriodicMetricReader.builder(metricExporter)
                    .setInterval(Duration.ofSeconds(60))
                    .build()
            )
            .build();

        return OpenTelemetrySdk.builder()
            .setTracerProvider(tracerProvider)
            .setMeterProvider(meterProvider)
            .setPropagators(ContextPropagators.create(W3CTraceContextPropagator.getInstance()))
            .buildAndRegisterGlobal();
    }

    private final Tracer tracer;
    private final Meter  meter;

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

        this.activeConceptsGauge = meter.gaugeBuilder("conceptualware.concepts.active")
            .setDescription("Number of currently active concept executions")
            .ofLongs()
            .buildWithCallback(measurement ->
                measurement.record(getActiveConceptCount(),
                    Attributes.of(AttributeKey.stringKey("env"), "production")));
    }

    public <T> T traceConceptExecution(String conceptId, String userId, Supplier<T> work) {
        Span span = tracer.spanBuilder("concept.execute")
            .setSpanKind(SpanKind.INTERNAL)
            .setAttribute("concept.id",   conceptId)
            .setAttribute("user.id",      userId)
            .setAttribute("service.name", "backend")
            .startSpan();

        long startMs = System.currentTimeMillis();

        try (Scope scope = span.makeCurrent()) {

            span.addEvent("execution.started",
                Attributes.of(AttributeKey.stringKey("concept"), conceptId));

            T result = work.get();

            span.setStatus(StatusCode.OK);
            span.addEvent("execution.completed");

            long latency = System.currentTimeMillis() - startMs;
            recordSuccess(conceptId, latency);

            return result;

        } catch (Exception e) {
            span.recordException(e, Attributes.of(
                AttributeKey.stringKey("concept.id"), conceptId
            ));
            span.setStatus(StatusCode.ERROR, e.getMessage());
            recordError(conceptId, e.getClass().getSimpleName());
            throw e;

        } finally {
            span.end();
        }
    }

    public <T> T traceDbOperation(String operation, String collection, Supplier<T> work) {
        Span span = tracer.spanBuilder("db." + operation)
            .setSpanKind(SpanKind.CLIENT)
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

    public void injectTraceContext(Map<String, String> headers) {
        TextMapSetter<Map<String, String>> setter = Map::put;
        GlobalOpenTelemetry.getPropagators()
            .getTextMapPropagator()
            .inject(Context.current(), headers, setter);
    }

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

    public Span startServerSpan(String operationName, Map<String, String> incomingHeaders) {
        Context parentContext = extractTraceContext(incomingHeaders);
        return tracer.spanBuilder(operationName)
            .setSpanKind(SpanKind.SERVER)
            .setParent(parentContext)
            .startSpan();
    }

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

    private long getActiveConceptCount() { return 0L; }

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
