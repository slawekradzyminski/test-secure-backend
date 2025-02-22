package com.awesome.testing.endpoints.ollama;

import com.awesome.testing.DomainHelper;
import org.springframework.cloud.contract.wiremock.AutoConfigureWireMock;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.*;

import java.util.List;

@AutoConfigureWireMock(port = 0)
abstract class AbstractOllamaTest extends DomainHelper {

    protected  <T> ResponseEntity<T> executePostForEventStream(
            Object body, HttpHeaders headers, Class<T> responseType, String endpoint) {
        headers.setAccept(List.of(MediaType.TEXT_EVENT_STREAM));
        return restTemplate.exchange(
                endpoint,
                HttpMethod.POST,
                new HttpEntity<>(body, headers),
                responseType);
    }

    protected  <T> ResponseEntity<T> executePostForEventStream(
            Object body, HttpHeaders headers, ParameterizedTypeReference<T> responseType, String endpoint) {
        headers.setAccept(List.of(MediaType.TEXT_EVENT_STREAM));
        return restTemplate.exchange(
                endpoint,
                HttpMethod.POST,
                new HttpEntity<>(body, headers),
                responseType);
    }
}