package com.awesome.testing.correlation;

import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.context.Context;
import io.opentelemetry.sdk.trace.data.LinkData;
import io.opentelemetry.sdk.trace.samplers.Sampler;
import io.opentelemetry.sdk.trace.samplers.SamplingResult;

import java.util.List;

/**
 * The sampler of the backend's tracer. With tracing off nothing is sampled, whatever the parent
 * says, so no span is ever recorded or exported. With tracing on a span follows its parent's
 * sampling decision (the gateway's {@code traceparent}, a stored outbox {@code traceparent}); a
 * root span is sampled with the configured probability, except an {@code INTERNAL} root: those
 * are the ticks of {@code @Scheduled} jobs (traffic flush every 100 ms, retry polls), which are not
 * part of any request. Work they pick up runs in its own span parented by the stored trace.
 */
final class TracingSampler implements Sampler {

    private final Sampler root;

    private TracingSampler(double probability) {
        this.root = Sampler.traceIdRatioBased(probability);
    }

    static Sampler create(boolean enabled, double probability) {
        return enabled ? Sampler.parentBased(new TracingSampler(probability)) : Sampler.alwaysOff();
    }

    @Override
    public SamplingResult shouldSample(Context parentContext, String traceId, String name, SpanKind spanKind,
                                       Attributes attributes, List<LinkData> parentLinks) {
        if (spanKind == SpanKind.INTERNAL) {
            return SamplingResult.drop();
        }
        return root.shouldSample(parentContext, traceId, name, spanKind, attributes, parentLinks);
    }

    @Override
    public String getDescription() {
        return "BackendRootSampler{" + root.getDescription() + ", internal roots dropped}";
    }
}
