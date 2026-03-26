package com.awesome.testing.config;

import org.junit.jupiter.api.Test;
import org.springdoc.core.customizers.ServerBaseUrlCustomizer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpRequest;

import java.net.URI;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class SpringDocProxyConfigTest {

    private final ServerBaseUrlCustomizer customizer = new SpringDocProxyConfig().serverBaseUrlCustomizer();

    @Test
    void shouldUseForwardedHostPortAndProtoForLocalGateway() {
        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.HOST, "localhost:8081");
        headers.add("X-Forwarded-Host", "localhost:8081");
        headers.add("X-Forwarded-Port", "8081");
        headers.add("X-Forwarded-Proto", "http");

        HttpRequest request = new TestHttpRequest(URI.create("http://backend:4001/v3/api-docs"), headers);

        assertThat(customizer.customize("http://localhost", request)).isEqualTo("http://localhost:8081");
    }

    @Test
    void shouldDropDefaultHttpsPortForProductionProxy() {
        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.HOST, "awesome.byst.re");
        headers.add("X-Forwarded-Host", "awesome.byst.re");
        headers.add("X-Forwarded-Port", "443");
        headers.add("X-Forwarded-Proto", "https");

        HttpRequest request = new TestHttpRequest(URI.create("http://backend:4001/v3/api-docs"), headers);

        assertThat(customizer.customize("http://awesome.byst.re", request)).isEqualTo("https://awesome.byst.re");
    }

    private record TestHttpRequest(URI getURI, HttpHeaders getHeaders) implements HttpRequest {
        @Override
        public HttpMethod getMethod() {
            return HttpMethod.GET;
        }

        @Override
        public URI getURI() {
            return getURI;
        }

        @Override
        public HttpHeaders getHeaders() {
            return getHeaders;
        }

        @Override
        public Map<String, Object> getAttributes() {
            return Map.of();
        }
    }
}
