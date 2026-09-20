package com.awesome.testing.traffic.protocol;

import jakarta.servlet.AsyncEvent;
import jakarta.servlet.AsyncListener;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import com.awesome.testing.traffic.TrafficSession;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

@Component
@Profile("graphql")
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
@RequiredArgsConstructor
public class GraphQlTrafficFilter extends OncePerRequestFilter {
    static final String ATTRIBUTE = GraphQlTrafficFilter.class.getName();
    private final ProtocolTrafficRecorder recorder;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !"/api/v1/graphql".equals(request.getServletPath());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        ProtocolTrafficRecorder.Trace trace = recorder.start(request.getHeader(TrafficSession.HEADER));
        if (trace == null) {
            chain.doFilter(request, response);
            return;
        }
        AtomicReference<Summary> summary = new AtomicReference<>(new Summary("request", "ERROR", List.of("TRANSPORT_ERROR")));
        request.setAttribute(ATTRIBUTE, summary);
        response.setHeader("X-Correlation-Id", trace.id());
        Runnable finish = () -> {
            Summary result = summary.get();
            if (response.getStatus() >= 400 && "SUCCESS".equals(result.outcome())) {
                result = new Summary(result.operation(), "ERROR", List.of("TRANSPORT_ERROR"));
            }
            recorder.complete(trace, "GRAPHQL", "/api/v1/graphql", response.getStatus(),
                    result.operation(), result.outcome(), result.codes());
        };
        try {
            chain.doFilter(request, response);
        } finally {
            if (request.isAsyncStarted()) {
                try {
                    request.getAsyncContext().addListener(new AsyncListener() {
                        @Override public void onComplete(AsyncEvent event) { finish.run(); }
                        @Override public void onTimeout(AsyncEvent event) { summary.set(new Summary("request", "ERROR", List.of("TIMEOUT"))); }
                        @Override public void onError(AsyncEvent event) { summary.set(new Summary("request", "ERROR", List.of("TRANSPORT_ERROR"))); }
                        @Override public void onStartAsync(AsyncEvent event) { event.getAsyncContext().addListener(this); }
                    });
                } catch (IllegalStateException completedBeforeRegistration) {
                    finish.run();
                }
            } else {
                finish.run();
            }
        }
    }

    record Summary(String operation, String outcome, List<String> codes) { }
}
