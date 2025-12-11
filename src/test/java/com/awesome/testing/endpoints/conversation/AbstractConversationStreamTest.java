package com.awesome.testing.endpoints.conversation;

import org.springframework.cloud.contract.wiremock.AutoConfigureWireMock;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.util.List;

@AutoConfigureWireMock(port = 0)
abstract class AbstractConversationStreamTest extends AbstractConversationControllerTest {

    protected <T> ResponseEntity<T> executePostForEventStream(
            String endpoint,
            Object body,
            HttpHeaders headers,
            Class<T> responseType) {
        headers.setAccept(List.of(MediaType.TEXT_EVENT_STREAM));
        return restTemplate.exchange(
                endpoint,
                HttpMethod.POST,
                new HttpEntity<>(body, headers),
                responseType
        );
    }

    protected <T> ResponseEntity<T> executePostForEventStream(
            String endpoint,
            Object body,
            HttpHeaders headers,
            ParameterizedTypeReference<T> responseType) {
        headers.setAccept(List.of(MediaType.TEXT_EVENT_STREAM));
        return restTemplate.exchange(
                endpoint,
                HttpMethod.POST,
                new HttpEntity<>(body, headers),
                responseType
        );
    }
}
