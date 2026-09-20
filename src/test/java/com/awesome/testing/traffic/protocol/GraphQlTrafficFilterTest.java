package com.awesome.testing.traffic.protocol;

import com.awesome.testing.dto.traffic.TrafficEventDto;
import com.awesome.testing.traffic.TrafficLogService;
import com.awesome.testing.traffic.TrafficSession;
import jakarta.servlet.AsyncContext;
import jakarta.servlet.AsyncEvent;
import jakarta.servlet.AsyncListener;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.spy;
import org.mockito.ArgumentCaptor;

class GraphQlTrafficFilterTest {
    private final ConcurrentLinkedQueue<TrafficEventDto> events = new ConcurrentLinkedQueue<>();
    private final GraphQlTrafficFilter filter = new GraphQlTrafficFilter(
            new ProtocolTrafficRecorder(mock(TrafficLogService.class), events, JsonMapper.builder().build()));

    @Test
    void requestsWithoutSessionStillReachTheApplicationAndOtherRoutesAreExcluded() throws Exception {
        // given
        var request = new MockHttpServletRequest();
        var response = new MockHttpServletResponse();
        var chain = mock(FilterChain.class);

        // when
        filter.doFilterInternal(request, response, chain);

        // then
        verify(chain).doFilter(request, response);
        assertThat(events).isEmpty();
        assertThat(filter.shouldNotFilter(request)).isTrue();
        request.setServletPath("/api/v1/graphql");
        assertThat(filter.shouldNotFilter(request)).isFalse();
    }

    @ParameterizedTest
    @ValueSource(ints = {399, 400, 500})
    @SuppressWarnings("unchecked")
    void httpTransportFailureOverridesAnExecutionSuccess(int status) throws Exception {
        // given
        var request = request();
        var response = new MockHttpServletResponse();

        // when
        filter.doFilterInternal(request, response, (req, res) -> {
            ((AtomicReference<GraphQlTrafficFilter.Summary>) req.getAttribute(GraphQlTrafficFilter.ATTRIBUTE))
                    .set(new GraphQlTrafficFilter.Summary("query cart", "SUCCESS", List.of()));
            response.setStatus(status);
        });

        // then
        assertThat(events).singleElement().satisfies(event -> {
            assertThat(event.getStatus()).isEqualTo(status);
            assertThat(event.getProtocolDetails().outcome()).isEqualTo(status >= 400 ? "ERROR" : "SUCCESS");
            assertThat(event.getProtocolDetails().codes()).isEqualTo(status >= 400 ? List.of("TRANSPORT_ERROR") : List.of());
        });
    }

    @ParameterizedTest
    @ValueSource(strings = {"complete", "timeout", "error"})
    @SuppressWarnings("unchecked")
    void asyncCaptureWaitsForCompletionAndTracksTerminalFailures(String terminal) throws Exception {
        // given
        var request = spy(request());
        when(request.isAsyncStarted()).thenReturn(true);
        var context = mock(AsyncContext.class);
        when(request.getAsyncContext()).thenReturn(context);
        var response = new MockHttpServletResponse();

        // when
        filter.doFilterInternal(request, response, (req, res) ->
                ((AtomicReference<GraphQlTrafficFilter.Summary>) req.getAttribute(GraphQlTrafficFilter.ATTRIBUTE))
                        .set(new GraphQlTrafficFilter.Summary("query cart", "SUCCESS", List.of())));
        var listener = ArgumentCaptor.forClass(AsyncListener.class);
        verify(context).addListener(listener.capture());
        assertThat(events).isEmpty();
        var nextContext = mock(AsyncContext.class);
        listener.getValue().onStartAsync(new AsyncEvent(nextContext));
        verify(nextContext).addListener(listener.getValue());
        switch (terminal) {
            case "timeout" -> listener.getValue().onTimeout(new AsyncEvent(context));
            case "error" -> listener.getValue().onError(new AsyncEvent(context));
            default -> { }
        }
        listener.getValue().onComplete(new AsyncEvent(context));

        // then
        assertThat(events).singleElement().satisfies(event -> {
            assertThat(event.getProtocolDetails().outcome()).isEqualTo("complete".equals(terminal) ? "SUCCESS" : "ERROR");
            assertThat(event.getProtocolDetails().codes()).isEqualTo(switch (terminal) {
                case "timeout" -> List.of("TIMEOUT");
                case "error" -> List.of("TRANSPORT_ERROR");
                default -> List.of();
            });
        });
    }

    @Test
    void completionBeforeListenerRegistrationStillProducesARecord() throws Exception {
        // given
        var request = spy(request());
        when(request.isAsyncStarted()).thenReturn(true);
        var context = mock(AsyncContext.class);
        when(request.getAsyncContext()).thenReturn(context);
        doThrow(new IllegalStateException("Already completed")).when(context).addListener(any(AsyncListener.class));

        // when
        filter.doFilterInternal(request, new MockHttpServletResponse(), (req, res) -> { });

        // then
        assertThat(events).hasSize(1);
    }

    private MockHttpServletRequest request() {
        var request = new MockHttpServletRequest();
        request.addHeader(TrafficSession.HEADER, UUID.randomUUID().toString());
        return request;
    }
}
