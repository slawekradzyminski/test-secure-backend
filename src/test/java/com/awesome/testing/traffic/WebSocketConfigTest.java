package com.awesome.testing.traffic;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.StompWebSocketEndpointRegistration;
import org.springframework.web.socket.config.annotation.WebSocketTransportRegistration;

import static org.mockito.Mockito.*;

class WebSocketConfigTest {

    private WebSocketConfig config;

    @BeforeEach
    void setUp() {
        config = new WebSocketConfig(mock(TrafficWebSocketAuthorizationInterceptor.class));
        org.springframework.test.util.ReflectionTestUtils.setField(
                config, "allowedOriginPatterns", java.util.List.of("http://localhost:8081"));
    }

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

        when(registry.addEndpoint("/api/v1/ws-traffic")).thenReturn(registration);

        config.registerStompEndpoints(registry);

        verify(registry).addEndpoint("/api/v1/ws-traffic");
        verify(registration).setAllowedOriginPatterns("http://localhost:8081");
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

    @Test
    void shouldConfigureManagedChannelExecutors() {
        ChannelRegistration inbound = mock(ChannelRegistration.class, RETURNS_DEEP_STUBS);
        ChannelRegistration outbound = mock(ChannelRegistration.class, RETURNS_DEEP_STUBS);

        config.configureClientInboundChannel(inbound);
        config.configureClientOutboundChannel(outbound);

        verify(inbound).taskExecutor(any(org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor.class));
        verify(inbound).interceptors(any(TrafficWebSocketAuthorizationInterceptor.class));
        verify(outbound).taskExecutor(any(org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor.class));
    }
}


