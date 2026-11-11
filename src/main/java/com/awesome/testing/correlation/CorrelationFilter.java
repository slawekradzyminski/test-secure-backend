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

/**
 * First filter of every request: keeps a valid incoming {@code X-Request-Id} and the trace id of a
 * valid {@code traceparent} (the gateway and the other services send both), generates what is
 * missing or invalid, starts a new span, puts {@code requestId}, {@code traceId} and
 * {@code spanId} into the MDC for the request, returns {@code X-Request-Id} and clears the MDC
 * afterwards. Async and error dispatches of the same request reuse its ids.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CorrelationFilter extends OncePerRequestFilter {

    static final String ATTRIBUTE = CorrelationFilter.class.getName() + ".ids";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        CorrelationIds ids = request.getAttribute(ATTRIBUTE) instanceof CorrelationIds existing
                ? existing
                : CorrelationIds.fromHeaders(request.getHeader(CorrelationIds.REQUEST_ID_HEADER),
                        request.getHeader(CorrelationIds.TRACEPARENT_HEADER));
        request.setAttribute(ATTRIBUTE, ids);
        if (!response.isCommitted()) {
            response.setHeader(CorrelationIds.REQUEST_ID_HEADER, ids.requestId());
        }
        try (CorrelationIds.Scope ignored = ids.open()) {
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
