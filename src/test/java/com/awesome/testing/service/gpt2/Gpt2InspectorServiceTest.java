package com.awesome.testing.service.gpt2;

import com.awesome.testing.controller.exception.CustomException;
import com.awesome.testing.dto.gpt2.Gpt2EmbeddingSpaceRequestDto;
import com.awesome.testing.dto.gpt2.Gpt2InspectorStatusDto;
import com.awesome.testing.dto.gpt2.Gpt2TraceRequestDto;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import tools.jackson.databind.JsonNode;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class Gpt2InspectorServiceTest {

    private HttpServer server;
    private Gpt2InspectorService service;

    @BeforeEach
    void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.start();
        service = new Gpt2InspectorService();
        ReflectionTestUtils.setField(service, "baseUrl", "http://127.0.0.1:" + server.getAddress().getPort());
    }

    @AfterEach
    void tearDown() {
        server.stop(0);
    }

    @Test
    void reportsReadyInspectorMetadata() {
        respond("/health", 200, """
                {"status":"ready","modelLabel":"openai-community/gpt2","modelRevision":"607a30d7",
                 "layerCount":12,"headCount":12,"maxTokens":32}
                """);

        Gpt2InspectorStatusDto result = service.status();

        assertThat(result.isAvailable()).isTrue();
        assertThat(result.getMode()).isEqualTo("full-local");
        assertThat(result.getModelRevision()).isEqualTo("607a30d7");
        assertThat(result.getLayerCount()).isEqualTo(12);
        assertThat(result.getHeadCount()).isEqualTo(12);
        assertThat(result.getMaxTokens()).isEqualTo(32);
    }

    @Test
    void treatsAnUnhealthySidecarAsUnavailable() {
        respond("/health", 503, "{\"detail\":\"GPT-2 is still loading\"}");

        Gpt2InspectorStatusDto result = service.status();

        assertThat(result.isAvailable()).isFalse();
        assertThat(result.getMessage()).contains("starting or unavailable");
    }

    @Test
    void forwardsAValidatedTraceRequest() {
        AtomicReference<String> requestBody = respond("/trace", 200, "{\"source\":\"gpt2-live\",\"layer\":3}");
        Gpt2TraceRequestDto request = Gpt2TraceRequestDto.builder()
                .prompt("The animal was too")
                .layer(3)
                .head(2)
                .selectedTokenIndex(3)
                .build();

        JsonNode result = service.trace(request);

        assertThat(result.path("source").asString()).isEqualTo("gpt2-live");
        assertThat(requestBody.get()).contains("\"prompt\":\"The animal was too\"")
                .contains("\"layer\":3")
                .contains("\"head\":2")
                .contains("\"selectedTokenIndex\":3");
    }

    @Test
    void rejectsAnEmptyEmbeddingSelectorWithoutCallingTheSidecar() {
        Gpt2EmbeddingSpaceRequestDto request = Gpt2EmbeddingSpaceRequestDto.builder().build();

        assertThatThrownBy(() -> service.embeddingSpace(request))
                .isInstanceOfSatisfying(CustomException.class, exception -> {
                    assertThat(exception.getHttpStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(exception.getMessage()).contains("query or tokenId");
                });
    }

    @Test
    void preservesControlledSidecarRejectionsAsBadRequests() {
        respond("/trace", 400, "{\"detail\":\"Prompt may contain at most 32 GPT-2 tokens\"}");
        Gpt2TraceRequestDto request = Gpt2TraceRequestDto.builder()
                .prompt("A prompt that the sidecar rejects")
                .build();

        assertThatThrownBy(() -> service.trace(request))
                .isInstanceOfSatisfying(CustomException.class, exception -> {
                    assertThat(exception.getHttpStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(exception.getMessage()).contains("rejected the trace request");
                    assertThat(exception.getMessage()).doesNotContain("32 GPT-2 tokens");
                });
    }

    @Test
    void refusesInspectionWhenThePrivateSidecarIsNotConfigured() {
        ReflectionTestUtils.setField(service, "baseUrl", " ");
        Gpt2TraceRequestDto request = Gpt2TraceRequestDto.builder().prompt("hello").build();

        assertThatThrownBy(() -> service.trace(request))
                .isInstanceOfSatisfying(CustomException.class, exception ->
                        assertThat(exception.getHttpStatus()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE));
    }

    private AtomicReference<String> respond(String path, int status, String responseBody) {
        AtomicReference<String> requestBody = new AtomicReference<>();
        server.createContext(path, exchange -> writeResponse(exchange, status, responseBody, requestBody));
        return requestBody;
    }

    private static void writeResponse(
            HttpExchange exchange,
            int status,
            String responseBody,
            AtomicReference<String> requestBody
    ) throws IOException {
        requestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
        byte[] response = responseBody.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, response.length);
        exchange.getResponseBody().write(response);
        exchange.close();
    }
}
