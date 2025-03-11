package com.awesome.testing.client;

import io.netty.channel.ChannelOption;
import io.netty.handler.timeout.ReadTimeoutHandler;
import io.netty.handler.timeout.WriteTimeoutHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.ExchangeStrategies;
import org.springframework.web.reactive.function.client.WebClient;
import org.zalando.logbook.Logbook;
import org.zalando.logbook.netty.LogbookClientHandler;
import reactor.netty.http.client.HttpClient;
import reactor.netty.resources.ConnectionProvider;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

@RequiredArgsConstructor
@Configuration
public class EmbeddingsClient {

    private final PythonSidecarProperties pythonSidecarProperties;

    @Bean
    public WebClient embeddingsWebClient(Logbook logbook) {
        int bufferSizeInBytes = getBufferSizeInBytes();
        ConnectionProvider provider = getProvider();
        ExchangeStrategies strategies = getStrategies(bufferSizeInBytes);
        HttpClient httpClient = getHttpClient(logbook, provider);

        return WebClient.builder()
                .baseUrl(pythonSidecarProperties.getUrl())
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .exchangeStrategies(strategies)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                .build();
    }

    private HttpClient getHttpClient(Logbook logbook, ConnectionProvider provider) {
        int timeout = pythonSidecarProperties.getTimeout();

        return HttpClient.create(provider)
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, timeout)
                .responseTimeout(Duration.ofMillis(timeout))
                .keepAlive(true)
                .doOnConnected(conn -> conn
                        .addHandlerLast(new LogbookClientHandler(logbook))
                        .addHandlerLast(new ReadTimeoutHandler(timeout, TimeUnit.MILLISECONDS))
                        .addHandlerLast(new WriteTimeoutHandler(timeout, TimeUnit.MILLISECONDS)));
    }

    private ExchangeStrategies getStrategies(int bufferSizeInBytes) {
        return ExchangeStrategies.builder()
                .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(bufferSizeInBytes))
                .build();
    }

    private ConnectionProvider getProvider() {
        int timeout = pythonSidecarProperties.getTimeout();

        return ConnectionProvider.builder("custom-provider")
                .maxConnections(pythonSidecarProperties.getMaxConnections())
                .pendingAcquireTimeout(Duration.ofMillis(timeout))
                .maxIdleTime(Duration.ofMillis(timeout))
                .build();
    }

    private int getBufferSizeInBytes() {
        int bufferSizeInBytes;
        String maxBufferSize = pythonSidecarProperties.getMaxBufferSize();
        if (maxBufferSize.endsWith("MB")) {
            bufferSizeInBytes = Integer.parseInt(maxBufferSize.replace("MB", "")) * 1024 * 1024;
        } else if (maxBufferSize.endsWith("KB")) {
            bufferSizeInBytes = Integer.parseInt(maxBufferSize.replace("KB", "")) * 1024;
        } else {
            bufferSizeInBytes = 20 * 1024 * 1024; // Default to 20MB
        }
        return bufferSizeInBytes;
    }
}
