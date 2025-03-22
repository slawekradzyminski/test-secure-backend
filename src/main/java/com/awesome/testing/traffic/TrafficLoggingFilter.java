package com.awesome.testing.traffic;

import com.awesome.testing.dto.traffic.TrafficEventDto;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.Instant;
import java.util.concurrent.ConcurrentLinkedQueue;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
@RequiredArgsConstructor
public class TrafficLoggingFilter implements Filter {

    private final ConcurrentLinkedQueue<TrafficEventDto> trafficQueue;

    @Override
    public void doFilter(ServletRequest req, ServletResponse res, FilterChain chain)
            throws IOException, ServletException {

        if (req instanceof HttpServletRequest httpReq && res instanceof HttpServletResponse httpRes) {
            long start = System.currentTimeMillis();
            
            // Call the next filter in the chain
            chain.doFilter(req, res);
            
            long duration = System.currentTimeMillis() - start;

            // Build the event
            TrafficEventDto event = TrafficEventDto.builder()
                    .method(httpReq.getMethod())
                    .path(httpReq.getRequestURI())
                    .status(httpRes.getStatus())
                    .durationMs(duration)
                    .timestamp(Instant.now())
                    .build();

            // Add to queue
            trafficQueue.add(event);
        } else {
            chain.doFilter(req, res);
        }
    }
} 