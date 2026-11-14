package com.awesome.testing.config;

import com.awesome.testing.correlation.CorrelationExchangeFilter;
import com.awesome.testing.correlation.CorrelationTracing;
import io.micrometer.observation.ObservationRegistry;
import io.netty.channel.ChannelOption;
import io.netty.handler.timeout.ReadTimeoutHandler;
import io.netty.handler.timeout.WriteTimeoutHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

@Configuration
@RequiredArgsConstructor
@EnableConfigurationProperties(OllamaProperties.class)
public class OllamaConfig {
    private final OllamaProperties properties;

    /**
     * With tracing on the client is instrumented (client span, W3C {@code traceparent} of that span)
     * and the correlation filter only adds {@code X-Request-Id}; with tracing off it is the plain
     * client of before, with the hand-written child {@code traceparent}.
     */
    @Bean
    public WebClient ollamaWebClient(CorrelationTracing tracing, ObservationRegistry observationRegistry) {
        HttpClient httpClient = HttpClient.create()
            .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 5000)
            .responseTimeout(Duration.ofMinutes(5))
            .doOnConnected(conn -> 
                conn.addHandlerLast(new ReadTimeoutHandler(5, TimeUnit.MINUTES))
                    .addHandlerLast(new WriteTimeoutHandler(5, TimeUnit.MINUTES))
            );

        WebClient.Builder builder = WebClient.builder()
            .clientConnector(new ReactorClientHttpConnector(httpClient))
            .baseUrl(properties.getBaseUrl())
            .filter(new CorrelationExchangeFilter(!tracing.enabled()));
        if (tracing.enabled()) {
            builder.observationRegistry(observationRegistry);
        }
        return builder.build();
    }
} 