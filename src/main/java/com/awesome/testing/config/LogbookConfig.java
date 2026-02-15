package com.awesome.testing.config;

import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.SerializationFeature;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.zalando.logbook.HttpLogFormatter;
import tools.jackson.databind.json.JsonMapper;

@SuppressWarnings("unused")
@Configuration
public class LogbookConfig {

    public ObjectMapper logbookObjectMapper() {
        return JsonMapper.builder()
                .enable(SerializationFeature.INDENT_OUTPUT)
                .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS)
                .build();
    }

    @Bean
    public HttpLogFormatter httpLogFormatter(ObjectMapper logbookObjectMapper) {
        return new PrettyPrintingHttpLogFormatter(logbookObjectMapper);
    }
} 
