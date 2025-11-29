package com.awesome.testing.traffic;

import org.junit.jupiter.api.Test;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.StompWebSocketEndpointRegistration;
import org.springframework.web.socket.config.annotation.WebSocketTransportRegistration;

import static org.mockito.Mockito.*;

class WebSocketConfigTest {

    private final WebSocketConfig config = new WebSocketConfig();

    @Test
    void shouldConfigureMessageBroker() {
        MessageBrokerRegistry registry = mock(MessageBrokerRegistry.class, RETURNS_DEEP_STUBS);

        config.configureMessageBroker(registry);

        verify(registry).enableSimpleBroker("/topic");
        verify(registry).setApplicationDestinationPrefixes("/app");
    }

    @Test
    void shouldRegisterStompEndpoints() {
        StompEndpointRegistry registry = mock(StompEndpointRegistry.class);
        StompWebSocketEndpointRegistration registration = mock(StompWebSocketEndpointRegistration.class, RETURNS_SELF);

        when(registry.addEndpoint("/ws-traffic")).thenReturn(registration);

        config.registerStompEndpoints(registry);

        verify(registry).addEndpoint("/ws-traffic");
        verify(registration).setAllowedOriginPatterns("*");
        verify(registration).withSockJS();
    }

    @Test
    void shouldConfigureWebSocketTransport() {
        WebSocketTransportRegistration registration = mock(WebSocketTransportRegistration.class, RETURNS_SELF);

        config.configureWebSocketTransport(registration);

        verify(registration).setMessageSizeLimit(128 * 1024);
        verify(registration).setSendBufferSizeLimit(512 * 1024);
        verify(registration).setSendTimeLimit(20000);
    }
}



