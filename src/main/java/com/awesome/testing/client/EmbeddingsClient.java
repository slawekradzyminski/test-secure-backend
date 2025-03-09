package com.awesome.testing.client;

import io.netty.channel.ChannelOption;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaderValues;
import io.netty.handler.timeout.ReadTimeoutHandler;
import io.netty.handler.timeout.WriteTimeoutHandler;
import org.springframework.beans.factory.annotation.Value;
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

@Configuration
public class EmbeddingsClient {

    @Value("${python.sidecar.url:http://localhost:5000}")
    private String pythonSidecarUrl;
    
    @Value("${python.sidecar.timeout:180000}")
    private int timeout;
    
    @Value("${python.sidecar.max-connections:500}")
    private int maxConnections;
    
    @Value("${python.sidecar.max-buffer-size:20MB}")
    private String maxBufferSize;

    @Bean
    public WebClient embeddingsWebClient(Logbook logbook) {
        // Parse buffer size
        int bufferSizeInBytes;
        if (maxBufferSize.endsWith("MB")) {
            bufferSizeInBytes = Integer.parseInt(maxBufferSize.replace("MB", "")) * 1024 * 1024;
        } else if (maxBufferSize.endsWith("KB")) {
            bufferSizeInBytes = Integer.parseInt(maxBufferSize.replace("KB", "")) * 1024;
        } else {
            bufferSizeInBytes = 20 * 1024 * 1024; // Default to 20MB
        }
        
        // Create a connection provider with increased max connections and pending acquire timeout
        ConnectionProvider provider = ConnectionProvider.builder("custom-provider")
                .maxConnections(maxConnections)
                .pendingAcquireTimeout(Duration.ofMillis(timeout))
                .maxIdleTime(Duration.ofMillis(timeout))
                .build();
        
        // Increase memory buffer size to handle larger responses
        ExchangeStrategies strategies = ExchangeStrategies.builder()
                .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(bufferSizeInBytes))
                .build();
        
        HttpClient httpClient = HttpClient.create(provider)
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, timeout)
                .responseTimeout(Duration.ofMillis(timeout))
                .keepAlive(true)
                .headers(headers -> headers.add(HttpHeaderNames.ACCEPT_ENCODING, HttpHeaderValues.GZIP))
                .doOnConnected(conn -> conn
                        .addHandlerLast(new LogbookClientHandler(logbook))
                        .addHandlerLast(new ReadTimeoutHandler(timeout, TimeUnit.MILLISECONDS))
                        .addHandlerLast(new WriteTimeoutHandler(timeout, TimeUnit.MILLISECONDS)));

        return WebClient.builder()
                .baseUrl(pythonSidecarUrl)
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .exchangeStrategies(strategies)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                .build();
    }
}
