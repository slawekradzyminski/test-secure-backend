package com.awesome.testing.correlation;

import com.awesome.testing.config.OllamaConfig;
import com.awesome.testing.config.OllamaProperties;
import com.awesome.testing.dto.ollama.GenerateResponseDto;
import com.awesome.testing.service.ollama.OllamaService;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.verification.LoggedRequest;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.scheduler.Schedulers;

import java.time.Duration;
import java.util.List;

import static com.awesome.testing.factory.ollama.OllamaRequestFactory.validStreamedRequest;
import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.any;
import static com.github.tomakehurst.wiremock.client.WireMock.anyRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.anyUrl;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.assertj.core.api.Assertions.assertThat;

/** Outbound calls of the Ollama client carry the caller's request id and a child traceparent. */
class CorrelationExchangeFilterTest {

    private static final WireMockServer OLLAMA = new WireMockServer(options().dynamicPort());
    private static final String TRACE_ID = "4bf92f3577b34da6a3ce929d0e0e4736";
    private static final String SPAN_ID = "00f067aa0ba902b7";

    private WebClient webClient;

    @BeforeAll
    static void start() {
        OLLAMA.start();
    }

    @AfterAll
    static void stop() {
        OLLAMA.stop();
    }

    @BeforeEach
    void setUp() {
        OLLAMA.resetAll();
        OLLAMA.stubFor(any(anyUrl()).willReturn(aResponse().withStatus(200)
                .withHeader("Content-Type", "application/x-ndjson")
                .withBody("{\"model\":\"m\",\"response\":\"hi\",\"done\":true}\n")));
        OllamaProperties properties = new OllamaProperties();
        properties.setBaseUrl(OLLAMA.baseUrl());
        webClient = new OllamaConfig(properties).ollamaWebClient();
    }

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    @Test
    void sendsTheRequestIdAndAChildTraceparentFromTheMdc() {
        // given
        CorrelationIds ids = new CorrelationIds("smoke-123", TRACE_ID, SPAN_ID);

        // when
        try (CorrelationIds.Scope ignored = ids.open()) {
            webClient.get().uri("/api/tags").retrieve().toBodilessEntity().block(Duration.ofSeconds(5));
        }

        // then
        LoggedRequest request = onlyRequest();
        assertThat(request.getHeader("X-Request-Id")).isEqualTo("smoke-123");
        assertThat(request.getHeader("traceparent"))
                .matches("00-" + TRACE_ID + "-[0-9a-f]{16}-01")
                .isNotEqualTo(ids.traceparent());
    }

    @Test
    void sendsNoCorrelationHeadersWithoutIds() {
        // when
        webClient.get().uri("/api/tags").retrieve().toBodilessEntity().block(Duration.ofSeconds(5));

        // then
        LoggedRequest request = onlyRequest();
        assertThat(request.containsHeader("X-Request-Id")).isFalse();
        assertThat(request.containsHeader("traceparent")).isFalse();
    }

    @Test
    void ollamaStreamsKeepTheCallersIdsWhenSubscribedOnAnotherThread() {
        // given: the stream is assembled in the request thread ...
        OllamaService service = new OllamaService(webClient);
        Flux<GenerateResponseDto> stream;
        try (CorrelationIds.Scope ignored = new CorrelationIds("ollama-req", TRACE_ID, SPAN_ID).open()) {
            stream = service.generateText(validStreamedRequest());
        }

        // when: ... and subscribed later on a thread without MDC
        List<GenerateResponseDto> chunks = stream.subscribeOn(Schedulers.boundedElastic())
                .collectList().block(Duration.ofSeconds(5));

        // then
        assertThat(chunks).hasSize(1);
        LoggedRequest request = onlyRequest();
        assertThat(request.getUrl()).isEqualTo("/api/generate");
        assertThat(request.getHeader("X-Request-Id")).isEqualTo("ollama-req");
        assertThat(request.getHeader("traceparent")).startsWith("00-" + TRACE_ID + "-");
    }

    @Test
    void anOuterContextWinsOverTheInnerCapture() {
        // given
        Flux<String> inner;
        try (CorrelationIds.Scope ignored = new CorrelationIds("inner", TRACE_ID, SPAN_ID).open()) {
            inner = CorrelationExchangeFilter.withCurrentIds(
                    webClient.get().uri("/api/tags").retrieve().bodyToFlux(String.class));
        }
        CorrelationIds outer = new CorrelationIds("outer", "a".repeat(32), SPAN_ID);

        // when
        inner.contextWrite(context -> context.put(CorrelationIds.class, outer)).blockLast(Duration.ofSeconds(5));

        // then
        assertThat(onlyRequest().getHeader("X-Request-Id")).isEqualTo("outer");
    }

    @Test
    void withoutIdsTheStreamIsReturnedAsIs() {
        Flux<String> publisher = Flux.just("a");

        assertThat(CorrelationExchangeFilter.withCurrentIds(publisher)).isSameAs(publisher);
    }

    private static LoggedRequest onlyRequest() {
        List<LoggedRequest> requests = OLLAMA.findAll(anyRequestedFor(anyUrl()));
        assertThat(requests).hasSize(1);
        return requests.getFirst();
    }
}
