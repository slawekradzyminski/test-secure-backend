package com.awesome.testing.traffic.protocol;

import org.junit.jupiter.api.Test;
import org.springframework.graphql.ResponseError;
import org.springframework.graphql.server.WebGraphQlInterceptor;
import org.springframework.graphql.server.WebGraphQlRequest;
import org.springframework.graphql.server.WebGraphQlResponse;
import reactor.core.publisher.Mono;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GraphQlTrafficInterceptorTest {
    @Test
    void successfulNullableRootIsStillPartialSuccessAlongsideAFailedRoot() {
        var data = new java.util.HashMap<String, Object>();
        data.put("denied", null);
        data.put("nullable", null);
        assertThat(outcome(data, List.of("denied"))).isEqualTo("PARTIAL_ERROR");
    }

    @Test
    void nestedErrorWithRemainingRootDataIsPartialSuccess() {
        assertThat(outcome(Map.of("cart", Map.of("totalItems", 1)), List.of("cart", "items", 0)))
                .isEqualTo("PARTIAL_ERROR");
    }

    @Test
    void nestedErrorThatNullsEveryRootIsTotalFailure() {
        assertThat(outcome(Collections.singletonMap("cart", null), List.of("cart", "items", 0)))
                .isEqualTo("ERROR");
    }

    @Test
    void pathlessErrorsDoNotDiscardSuccessfulRootData() {
        var data = Map.<String, Object>of("cart", Map.of("totalItems", 1));
        assertThat(outcome(data, List.of())).isEqualTo("PARTIAL_ERROR");
        assertThat(outcome(data, (List<Object>) null)).isEqualTo("PARTIAL_ERROR");
    }

    @Test
    void noDataIsTotalFailure() {
        assertThat(outcome(null, List.of("cart"))).isEqualTo("ERROR");
        assertThat(outcome(Map.of(), List.of())).isEqualTo("ERROR");
    }

    @Test
    void noErrorsIsSuccessEvenForNullData() {
        assertThat(outcome(null)).isEqualTo("SUCCESS");
    }

    @SafeVarargs
    private String outcome(Map<String, Object> data, List<Object>... paths) {
        var request = mock(WebGraphQlRequest.class);
        var response = mock(WebGraphQlResponse.class);
        var summary = new AtomicReference<GraphQlTrafficFilter.Summary>();
        when(request.getAttributes()).thenReturn(Map.of(GraphQlTrafficFilter.ATTRIBUTE, summary));
        when(request.getDocument()).thenReturn("{cart{totalItems}}");
        when(response.getData()).thenReturn(data);
        var errors = Arrays.stream(paths).map(path -> {
            var error = mock(ResponseError.class);
            when(error.getParsedPath()).thenReturn(path);
            when(error.getExtensions()).thenReturn(Map.of("code", "FORBIDDEN"));
            return error;
        }).toList();
        when(response.getErrors()).thenReturn(errors);
        WebGraphQlInterceptor.Chain chain = ignored -> Mono.just(response);
        new GraphQlTrafficInterceptor().intercept(request, chain).block();
        return summary.get().outcome();
    }
}
