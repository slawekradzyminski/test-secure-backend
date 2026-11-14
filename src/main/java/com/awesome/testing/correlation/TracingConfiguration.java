package com.awesome.testing.correlation;

import io.micrometer.tracing.Tracer;
import io.micrometer.tracing.otel.bridge.Slf4JEventListener;
import io.micrometer.tracing.propagation.Propagator;
import io.opentelemetry.context.propagation.ContextPropagators;
import io.opentelemetry.context.propagation.TextMapPropagator;
import io.opentelemetry.exporter.otlp.http.trace.OtlpHttpSpanExporter;
import io.opentelemetry.exporter.otlp.http.trace.OtlpHttpSpanExporterBuilder;
import io.opentelemetry.sdk.trace.export.SpanExporter;
import io.opentelemetry.sdk.trace.samplers.Sampler;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.micrometer.tracing.autoconfigure.TracingProperties;
import org.springframework.boot.micrometer.tracing.opentelemetry.autoconfigure.otlp.OtlpTracingProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

import java.util.Locale;

/**
 * Distributed tracing (docs/TRACING.md), switched by {@code app.tracing.enabled}
 * ({@code TRACING_ENABLED}, default false) at runtime.
 *
 * <p>The GraalVM native image fixes the bean graph at build time, so nothing here is conditional:
 * these beans replace Spring Boot's auto-configured ones of the same type and decide inside their
 * factory methods. Tracing off means the sampler records nothing, propagation is a no-op (no
 * {@code traceparent} is read or injected by instrumentation) and no exporter or HTTP client exists,
 * so {@link CorrelationFilter} and {@link CorrelationIds} behave exactly as without a tracer. The
 * tracer never writes the MDC, on or off. Spring Boot's own OTLP exporter is switched off in
 * application.yml ({@code management.tracing.export.otlp.enabled=false}) because it would build its
 * HTTP client eagerly; {@link #otlpSpanExporter} reads the same {@code management.opentelemetry.tracing.export.otlp.*}
 * properties.
 */
@Slf4j
@Configuration(proxyBeanMethods = false)
public class TracingConfiguration {

    private final boolean enabled;

    public TracingConfiguration(@Value("${app.tracing.enabled:false}") boolean enabled) {
        this.enabled = enabled;
    }

    @Bean
    Sampler tracingSampler(TracingProperties tracingProperties) {
        float probability = tracingProperties.getSampling().getProbability();
        if (enabled) {
            log.info("Tracing enabled (sampling probability {})", probability);
        }
        return TracingSampler.create(enabled, probability);
    }

    /** W3C {@code traceparent} only while tracing is on; a no-op otherwise. */
    @Bean
    ContextPropagators tracingContextPropagators(ObjectProvider<TextMapPropagator> textMapPropagators) {
        return enabled
                ? ContextPropagators.create(TextMapPropagator.composite(textMapPropagators.orderedStream().toList()))
                : ContextPropagators.noop();
    }

    /** OTLP over HTTP to {@code TRACING_OTLP_ENDPOINT}; a no-op exporter while tracing is off. */
    @Bean
    SpanExporter otlpSpanExporter(OtlpTracingProperties properties) {
        String endpoint = properties.getEndpoint();
        if (!enabled || !StringUtils.hasText(endpoint)) {
            return SpanExporter.composite();
        }
        OtlpHttpSpanExporterBuilder builder = OtlpHttpSpanExporter.builder()
                .setEndpoint(endpoint)
                .setTimeout(properties.getTimeout())
                .setConnectTimeout(properties.getConnectTimeout())
                .setCompression(properties.getCompression().name().toLowerCase(Locale.ROOT));
        properties.getHeaders().forEach(builder::addHeader);
        return builder.build();
    }

    /**
     * Micrometer's MDC listener, silenced: the trace and span ids in the MDC are always set by the
     * correlation code ({@link CorrelationFilter}, {@link ServerSpanCorrelationFilter},
     * {@link CorrelationTracing#resume}), with tracing on from the server or work span.
     */
    @Bean
    Slf4JEventListener tracingMdcListener() {
        return new Slf4JEventListener() {
            @Override
            public void onEvent(Object event) {
                // The MDC belongs to CorrelationIds.
            }
        };
    }

    @Bean
    CorrelationTracing correlationTracing(Tracer tracer, Propagator propagator) {
        return new CorrelationTracing(enabled, tracer, propagator);
    }
}
