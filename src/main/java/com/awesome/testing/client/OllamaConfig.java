package com.awesome.testing.client;

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

    @Bean
    public WebClient ollamaWebClient() {
        HttpClient httpClient = HttpClient.create()
            .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 5000)
            .responseTimeout(Duration.ofMinutes(5))
            .doOnConnected(conn -> 
                conn.addHandlerLast(new ReadTimeoutHandler(5, TimeUnit.MINUTES))
                    .addHandlerLast(new WriteTimeoutHandler(5, TimeUnit.MINUTES))
            );

        return WebClient.builder()
            .clientConnector(new ReactorClientHttpConnector(httpClient))
            .baseUrl(properties.getBaseUrl())
            .build();
    }
} 