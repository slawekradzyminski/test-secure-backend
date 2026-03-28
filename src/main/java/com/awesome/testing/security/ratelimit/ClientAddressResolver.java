package com.awesome.testing.security.ratelimit;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
@RequiredArgsConstructor
public class ClientAddressResolver {

    private final RateLimitProperties properties;

    public String resolve(HttpServletRequest request) {
        if (properties.isTrustForwardedHeaders()) {
            String forwardedFor = request.getHeader("X-Forwarded-For");
            if (StringUtils.hasText(forwardedFor)) {
                String first = forwardedFor.split(",")[0].trim();
                if (StringUtils.hasText(first)) {
                    return first;
                }
            }
        }

        String remoteAddr = request.getRemoteAddr();
        return StringUtils.hasText(remoteAddr) ? remoteAddr : "unknown";
    }
}
