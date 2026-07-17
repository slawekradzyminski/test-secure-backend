package com.awesome.testing.traffic;

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketTransportRegistration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.List;
import lombok.RequiredArgsConstructor;

@Configuration
@EnableWebSocketMessageBroker
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final TrafficWebSocketAuthorizationInterceptor authorizationInterceptor;
    private final ThreadPoolTaskExecutor clientInboundExecutor = channelExecutor("clientInboundChannel-");
    private final ThreadPoolTaskExecutor clientOutboundExecutor = channelExecutor("clientOutboundChannel-");

    @Value("${app.cors.allowed-origin-patterns:http://localhost:8081,http://127.0.0.1:8081,http://host.docker.internal:8081}")
    private List<String> allowedOriginPatterns;

    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        config.enableSimpleBroker("/topic");
        config.setApplicationDestinationPrefixes("/app");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/api/v1/ws-traffic")
                .setAllowedOriginPatterns(allowedOriginPatterns.toArray(String[]::new))
                .withSockJS();
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.taskExecutor(clientInboundExecutor);
        registration.interceptors(authorizationInterceptor);
    }

    @Override
    public void configureClientOutboundChannel(ChannelRegistration registration) {
        registration.taskExecutor(clientOutboundExecutor);
    }
    
    @Override
    public void configureWebSocketTransport(WebSocketTransportRegistration registration) {
        registration.setMessageSizeLimit(128 * 1024);
        registration.setSendBufferSizeLimit(512 * 1024);
        registration.setSendTimeLimit(20_000);
    }

    private static ThreadPoolTaskExecutor channelExecutor(String threadNamePrefix) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(Runtime.getRuntime().availableProcessors() * 2);
        executor.setAllowCoreThreadTimeOut(true);
        executor.setStrictEarlyShutdown(true);
        executor.setThreadNamePrefix(threadNamePrefix);
        return executor;
    }
}
