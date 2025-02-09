package com.awesome.testing.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.zalando.logbook.HttpLogFormatter;

@SuppressWarnings("unused")
@Configuration
public class LogbookConfig {

    public ObjectMapper logbookObjectMapper() {
        return new ObjectMapper()
                .enable(SerializationFeature.INDENT_OUTPUT)
                .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS);
    }

    @Bean
    public HttpLogFormatter httpLogFormatter(ObjectMapper logbookObjectMapper) {
        return new PrettyPrintingHttpLogFormatter(logbookObjectMapper);
    }
} 