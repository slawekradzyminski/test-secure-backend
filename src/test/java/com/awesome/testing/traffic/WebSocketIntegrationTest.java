package com.awesome.testing.traffic;

import com.awesome.testing.DomainHelper;
import com.awesome.testing.config.WebSocketTestConfig;
import com.awesome.testing.dto.traffic.TrafficEventDto;
import com.awesome.testing.dto.user.Role;
import com.awesome.testing.dto.user.UserRegisterDto;
import lombok.SneakyThrows;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.messaging.converter.MappingJackson2MessageConverter;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;
import org.springframework.web.socket.sockjs.client.SockJsClient;
import org.springframework.web.socket.sockjs.client.Transport;
import org.springframework.web.socket.sockjs.client.WebSocketTransport;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static com.awesome.testing.factory.UserFactory.getRandomUserWithRoles;
import static com.awesome.testing.factory.ollama.TrafficEventFactory.trafficEvent;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;

@Import(WebSocketTestConfig.class)
@ActiveProfiles("websocket-test")
class WebSocketIntegrationTest extends DomainHelper {

    @LocalServerPort
    private int port;

    @Autowired
    private ConcurrentLinkedQueue<TrafficEventDto> trafficQueue;

    @Autowired
    private TrafficPublisher trafficPublisher;
    
    @Autowired
    private SimpMessagingTemplate messagingTemplate;

    private String authToken;
    private StompSession session;

    @SneakyThrows
    @BeforeEach
    void setup() {
        UserRegisterDto user = getRandomUserWithRoles(List.of(Role.ROLE_CLIENT));
        authToken = getToken(user);

        session = setupWebsocketSession();
    }

    @AfterEach
    public void cleanUp() {
        session.disconnect();
    }

    @Test
    void shouldReceiveTrafficEventViaWebSocket() {
        // given
        TrafficEventDto testEvent = trafficEvent();
        trafficQueue.add(testEvent);

        // when
        trafficPublisher.broadcastTraffic();

        // then
        ArgumentCaptor<TrafficEventDto> eventCaptor = ArgumentCaptor.forClass(TrafficEventDto.class);
        verify(messagingTemplate, atLeastOnce()).convertAndSend(eq("/topic/traffic"), eventCaptor.capture());
        List<TrafficEventDto> capturedEvents = eventCaptor.getAllValues();
        TrafficEventDto capturedEvent = capturedEvents.getLast();
        assertThat(capturedEvent.getMethod()).isEqualTo(testEvent.getMethod());
        assertThat(capturedEvent.getPath()).isEqualTo(testEvent.getPath());
        assertThat(capturedEvent.getStatus()).isEqualTo(testEvent.getStatus());
        assertThat(capturedEvent.getDurationMs()).isEqualTo(testEvent.getDurationMs());
    }

    private StompSession setupWebsocketSession() throws InterruptedException, ExecutionException, TimeoutException {
        List<Transport> transports = new ArrayList<>();
        transports.add(new WebSocketTransport(new StandardWebSocketClient()));
        WebSocketStompClient stompClient = new WebSocketStompClient(new SockJsClient(transports));
        stompClient.setMessageConverter(new MappingJackson2MessageConverter());

        WebSocketHttpHeaders headers = new WebSocketHttpHeaders();
        headers.add(HttpHeaders.AUTHORIZATION, "Bearer " + authToken);
        return stompClient
                .connectAsync(getWsUrl(), headers, new StompSessionHandlerAdapter() {})
                .get(5, TimeUnit.SECONDS);
    }

    private String getWsUrl() {
        return "ws://localhost:" + port + "/ws-traffic";
    }
} 