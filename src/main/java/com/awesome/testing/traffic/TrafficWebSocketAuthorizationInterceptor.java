package com.awesome.testing.traffic;

import com.awesome.testing.security.JwtTokenProvider;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class TrafficWebSocketAuthorizationInterceptor implements ChannelInterceptor {

    private static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtTokenProvider jwtTokenProvider;
    private final TrafficProperties trafficProperties;

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null) {
            return message;
        }
        if (StompCommand.CONNECT.equals(accessor.getCommand())) {
            authenticate(accessor);
        } else if (StompCommand.SUBSCRIBE.equals(accessor.getCommand())) {
            authorizeSubscription(accessor);
        }
        return message;
    }

    private void authenticate(StompHeaderAccessor accessor) {
        String authorization = accessor.getFirstNativeHeader(AUTHORIZATION_HEADER);
        if (authorization == null || !authorization.startsWith(BEARER_PREFIX)) {
            if (trafficProperties.isLegacyPublicAccess()) {
                return;
            }
            throw new AccessDeniedException("A bearer token is required for traffic monitoring");
        }
        String token = authorization.substring(BEARER_PREFIX.length());
        jwtTokenProvider.validateToken(token);
        accessor.setUser(jwtTokenProvider.getAuthentication(token));

        String clientSessionId = TrafficSession.requireValid(
                accessor.getFirstNativeHeader(TrafficSession.HEADER)
        );
        Map<String, Object> sessionAttributes = accessor.getSessionAttributes();
        if (sessionAttributes == null) {
            throw new AccessDeniedException("WebSocket session attributes are unavailable");
        }
        sessionAttributes.put(TrafficSession.STOMP_ATTRIBUTE, clientSessionId);
    }

    private void authorizeSubscription(StompHeaderAccessor accessor) {
        if (trafficProperties.isLegacyPublicAccess()
                && "/topic/traffic".equals(accessor.getDestination())) {
            return;
        }
        Map<String, Object> sessionAttributes = accessor.getSessionAttributes();
        Object clientSessionId = sessionAttributes == null
                ? null
                : sessionAttributes.get(TrafficSession.STOMP_ATTRIBUTE);
        if (accessor.getUser() == null || !(clientSessionId instanceof String sessionId)) {
            throw new AccessDeniedException("Authenticated traffic session is required");
        }
        if (!TrafficSession.topic(sessionId).equals(accessor.getDestination())) {
            throw new AccessDeniedException("Traffic subscriptions are restricted to the current browser session");
        }
    }
}
