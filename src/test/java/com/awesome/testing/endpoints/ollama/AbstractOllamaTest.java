package com.awesome.testing.endpoints.ollama;

import com.awesome.testing.DomainHelper;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.*;
import org.wiremock.spring.ConfigureWireMock;
import org.wiremock.spring.EnableWireMock;

import java.util.List;

@EnableWireMock(
        @ConfigureWireMock(
                port = 0,
                portProperties = "wiremock.server.port",
                baseUrlProperties = "wiremock.server.baseUrl"
        )
)
abstract class AbstractOllamaTest extends DomainHelper {

    protected  <T> ResponseEntity<T> executePostForEventStream(
            Object body, HttpHeaders headers, Class<T> responseType, String endpoint) {
        headers.setAccept(List.of(MediaType.TEXT_EVENT_STREAM));
        return executePost(endpoint, body, headers, responseType);
    }

    protected  <T> ResponseEntity<T> executePostForEventStream(
            Object body, HttpHeaders headers, ParameterizedTypeReference<T> responseType, String endpoint) {
        headers.setAccept(List.of(MediaType.TEXT_EVENT_STREAM));
        return executePost(endpoint, body, headers, responseType);
    }
}
