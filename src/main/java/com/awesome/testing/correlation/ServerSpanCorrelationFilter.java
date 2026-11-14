package com.awesome.testing.correlation;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Optional;

/**
 * With tracing on, puts the server span's trace and span id into the MDC (and the request's
 * correlation ids) for the rest of the request. It runs right after Spring's
 * {@code ServerHttpObservationFilter} ({@code HIGHEST_PRECEDENCE + 1}), which starts that span as
 * the child of the incoming {@code traceparent}. {@link CorrelationFilter} itself stays first, so
 * Logbook's secure filter (also {@code HIGHEST_PRECEDENCE + 1}) logs under the request's ids either
 * way. With tracing off this filter only passes the request on.
 */
@Component
@Order(ServerSpanCorrelationFilter.ORDER)
public class ServerSpanCorrelationFilter extends OncePerRequestFilter {

    /** Right after the server observation filter. */
    public static final int ORDER = Ordered.HIGHEST_PRECEDENCE + 2;

    private final CorrelationTracing tracing;

    public ServerSpanCorrelationFilter(CorrelationTracing tracing) {
        super();
        this.tracing = tracing;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Optional<CorrelationIds> traced = request.getAttribute(CorrelationFilter.ATTRIBUTE) instanceof CorrelationIds ids
                ? tracing.forServerSpan(ids)
                : Optional.empty();
        if (traced.isEmpty()) {
            chain.doFilter(request, response);
            return;
        }
        request.setAttribute(CorrelationFilter.ATTRIBUTE, traced.get());
        try (CorrelationIds.Scope ignored = traced.get().open()) {
            chain.doFilter(request, response);
        }
    }

    @Override
    protected boolean shouldNotFilterAsyncDispatch() {
        return false;
    }

    @Override
    protected boolean shouldNotFilterErrorDispatch() {
        return false;
    }
}
