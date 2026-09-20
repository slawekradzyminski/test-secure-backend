package com.awesome.testing.graphql;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Profile;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

/** Bound JSON before parsing, including requests without Content-Length. */
@Component
@Profile("graphql")
@Order(Ordered.HIGHEST_PRECEDENCE + 20)
public class GraphQlRequestLimitFilter extends OncePerRequestFilter {
    private static final int MAX_BYTES = 65536;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !"/api/v1/graphql".equals(request.getServletPath());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        byte[] body = request.getInputStream().readNBytes(MAX_BYTES + 1);
        if (body.length > MAX_BYTES) {
            response.setStatus(413);
            response.setContentType("application/json");
            response.getWriter().write("{\"errors\":[{\"message\":\"GraphQL request exceeds 64 KiB\"}]}");
            return;
        }
        if (new String(body, StandardCharsets.UTF_8).stripLeading().startsWith("[")) {
            response.setStatus(400);
            response.setContentType("application/json");
            response.getWriter().write("{\"errors\":[{\"message\":\"GraphQL HTTP batching is not supported\"}]}");
            return;
        }
        chain.doFilter(new HttpServletRequestWrapper(request) {
            @Override
            public ServletInputStream getInputStream() {
                return new BufferedInput(body);
            }
        }, response);
    }

    private static final class BufferedInput extends ServletInputStream {
        private final ByteArrayInputStream input;

        private BufferedInput(byte[] body) {
            super();
            input = new ByteArrayInputStream(body);
        }

        @Override
        public int read() {
            return input.read();
        }

        @Override
        public boolean isFinished() {
            return input.available() == 0;
        }

        @Override
        public boolean isReady() {
            return true;
        }

        @Override
        public void setReadListener(ReadListener listener) {
            throw new IllegalStateException("Buffered GraphQL request uses synchronous JSON decoding");
        }
    }
}
