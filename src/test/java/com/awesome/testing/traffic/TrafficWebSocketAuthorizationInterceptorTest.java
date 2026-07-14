package com.awesome.testing.traffic;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.awesome.testing.security.JwtTokenProvider;
import java.security.Principal;
import java.util.HashMap;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;

class TrafficWebSocketAuthorizationInterceptorTest {

    private JwtTokenProvider jwtTokenProvider;
    private TrafficWebSocketAuthorizationInterceptor interceptor;

    @BeforeEach
    void setUp() {
        jwtTokenProvider = mock(JwtTokenProvider.class);
        interceptor = new TrafficWebSocketAuthorizationInterceptor(jwtTokenProvider);
    }

    @Test
    void shouldAuthenticateConnectAndBindBrowserSession() {
        Authentication authentication = mock(Authentication.class);
        when(jwtTokenProvider.getAuthentication("access-token")).thenReturn(authentication);
        StompHeaderAccessor accessor = accessor(StompCommand.CONNECT);
        accessor.setNativeHeader("Authorization", "Bearer access-token");
        accessor.setNativeHeader(TrafficSession.HEADER, "browser-session-1234");

        interceptor.preSend(message(accessor), mock(MessageChannel.class));

        verify(jwtTokenProvider).validateToken("access-token");
        assertThat(accessor.getUser()).isEqualTo(authentication);
        assertThat(accessor.getSessionAttributes())
                .containsEntry(TrafficSession.STOMP_ATTRIBUTE, "browser-session-1234");
    }

    @Test
    void shouldRejectSubscriptionToAnotherBrowserSession() {
        StompHeaderAccessor accessor = accessor(StompCommand.SUBSCRIBE);
        accessor.setUser(mock(Principal.class));
        accessor.getSessionAttributes().put(TrafficSession.STOMP_ATTRIBUTE, "browser-session-1234");
        accessor.setDestination("/topic/traffic/different-session-1234");

        assertThatThrownBy(() -> interceptor.preSend(message(accessor), mock(MessageChannel.class)))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void shouldRejectConnectWithoutBearerToken() {
        StompHeaderAccessor accessor = accessor(StompCommand.CONNECT);
        accessor.setNativeHeader(TrafficSession.HEADER, "browser-session-1234");

        assertThatThrownBy(() -> interceptor.preSend(message(accessor), mock(MessageChannel.class)))
                .isInstanceOf(AccessDeniedException.class);
    }

    private static StompHeaderAccessor accessor(StompCommand command) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(command);
        accessor.setSessionAttributes(new HashMap<>());
        accessor.setLeaveMutable(true);
        return accessor;
    }

    private static Message<byte[]> message(StompHeaderAccessor accessor) {
        return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
    }
}
