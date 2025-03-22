package com.awesome.testing.traffic;

import com.awesome.testing.DomainHelper;
import com.awesome.testing.config.WebSocketTestConfig;
import com.awesome.testing.dto.traffic.TrafficEventDto;
import com.awesome.testing.dto.user.Role;
import com.awesome.testing.dto.user.UserRegisterDto;
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

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeUnit;

import static com.awesome.testing.factory.UserFactory.getRandomUserWithRoles;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
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

    private WebSocketStompClient stompClient;
    private String authToken;

    @BeforeEach
    void setup() {
        // given
        List<Transport> transports = new ArrayList<>();
        transports.add(new WebSocketTransport(new StandardWebSocketClient()));
        
        stompClient = new WebSocketStompClient(new SockJsClient(transports));
        stompClient.setMessageConverter(new MappingJackson2MessageConverter());
        
        // Create a user and get auth token
        UserRegisterDto user = getRandomUserWithRoles(List.of(Role.ROLE_CLIENT));
        authToken = getToken(user);
    }

    @Test
    void shouldReceiveTrafficEventViaWebSocket() throws Exception {
        // given
        WebSocketHttpHeaders headers = new WebSocketHttpHeaders();
        headers.add(HttpHeaders.AUTHORIZATION, "Bearer " + authToken);
        
        // Connect to the WebSocket to verify connectivity
        StompSession session = stompClient
                .connect(getWsUrl(), headers, new StompSessionHandlerAdapter() {})
                .get(5, TimeUnit.SECONDS);

        // Create a test event and add it to the queue
        TrafficEventDto testEvent = TrafficEventDto.builder()
                .method("TEST")
                .path("/test-websocket")
                .status(200)
                .durationMs(123)
                .timestamp(Instant.now())
                .build();
        
        trafficQueue.add(testEvent);

        // when
        trafficPublisher.broadcastTraffic();

        // then
        // Verify that the publisher tried to send a message to the correct destination
        ArgumentCaptor<TrafficEventDto> eventCaptor = ArgumentCaptor.forClass(TrafficEventDto.class);
        verify(messagingTemplate, atLeastOnce()).convertAndSend(eq("/topic/traffic"), eventCaptor.capture());
        
        // Get the last captured event
        List<TrafficEventDto> capturedEvents = eventCaptor.getAllValues();
        TrafficEventDto capturedEvent = capturedEvents.get(capturedEvents.size() - 1);
        
        // Verify the event data
        assertNotNull(capturedEvent);
        assertEquals("TEST", capturedEvent.getMethod());
        assertEquals("/test-websocket", capturedEvent.getPath());
        assertEquals(200, capturedEvent.getStatus());
        assertEquals(123, capturedEvent.getDurationMs());
        
        // Clean up
        session.disconnect();
    }

    private String getWsUrl() {
        return "ws://localhost:" + port + "/ws-traffic";
    }
} 