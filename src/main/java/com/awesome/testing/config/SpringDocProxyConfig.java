package com.awesome.testing.config;

import org.springdoc.core.customizers.ServerBaseUrlCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpRequest;

@Configuration
public class SpringDocProxyConfig {

    @Bean
    public ServerBaseUrlCustomizer serverBaseUrlCustomizer() {
        return (serverBaseUrl, request) -> {
            String scheme = firstHeader(request, "X-Forwarded-Proto");
            if (scheme == null || scheme.isBlank()) {
                scheme = request.getURI().getScheme();
            }

            String forwardedHost = firstHeader(request, "X-Forwarded-Host");
            String hostWithPort = forwardedHost;
            if (hostWithPort == null || hostWithPort.isBlank()) {
                hostWithPort = firstHeader(request, HttpHeaders.HOST);
            }
            if (hostWithPort == null || hostWithPort.isBlank()) {
                hostWithPort = request.getURI().getHost();
            }

            String host = hostWithPort;
            String port = firstHeader(request, "X-Forwarded-Port");

            if (hostWithPort != null && hostWithPort.startsWith("[")) {
                int closingBracketIndex = hostWithPort.indexOf(']');
                if (closingBracketIndex > -1) {
                    host = hostWithPort.substring(0, closingBracketIndex + 1);
                    if (port == null || port.isBlank()) {
                        int separatorIndex = hostWithPort.indexOf(':', closingBracketIndex);
                        if (separatorIndex > -1 && separatorIndex + 1 < hostWithPort.length()) {
                            port = hostWithPort.substring(separatorIndex + 1);
                        }
                    }
                }
            } else if (hostWithPort != null && hostWithPort.contains(":")) {
                String[] hostParts = hostWithPort.split(":", 2);
                host = hostParts[0];
                if (port == null || port.isBlank()) {
                    port = hostParts[1];
                }
            }

            if (port == null || port.isBlank()) {
                int uriPort = request.getURI().getPort();
                port = uriPort > 0 ? Integer.toString(uriPort) : null;
            }

            if (host == null || host.isBlank()) {
                return serverBaseUrl;
            }

            if (isDefaultPort(scheme, port)) {
                return scheme + "://" + host;
            }

            if (port == null || port.isBlank()) {
                return scheme + "://" + host;
            }

            return scheme + "://" + host + ":" + port;
        };
    }

    private static boolean isDefaultPort(String scheme, String port) {
        return ("http".equalsIgnoreCase(scheme) && "80".equals(port))
                || ("https".equalsIgnoreCase(scheme) && "443".equals(port));
    }

    private static String firstHeader(HttpRequest request, String headerName) {
        return request.getHeaders().getFirst(headerName);
    }
}
