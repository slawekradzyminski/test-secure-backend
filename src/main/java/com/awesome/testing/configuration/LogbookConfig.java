package com.awesome.testing.configuration;

import static org.zalando.logbook.HeaderFilter.none;
import static org.zalando.logbook.core.Conditions.exclude;
import static org.zalando.logbook.core.Conditions.requestTo;

import java.util.regex.Pattern;

import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.zalando.logbook.HttpLogWriter;
import org.zalando.logbook.Logbook;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.zalando.logbook.core.DefaultSink;
import org.zalando.logbook.json.JsonHttpLogFormatter;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import org.zalando.logbook.BodyFilter;
import org.zalando.logbook.HeaderFilter;
import org.zalando.logbook.HttpLogFormatter;

@Configuration
public class LogbookConfig {

    private static final Pattern NEWLINE_PATTERN = Pattern.compile("(?m)^");

    @Bean
    public ObjectMapper objectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.enable(SerializationFeature.INDENT_OUTPUT);
        mapper.registerModule(new JavaTimeModule());
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        return mapper;
    }

    @Bean
    public BodyFilter bodyFilter(ObjectMapper objectMapper) {
        return (contentType, body) -> {
            if (contentType != null && contentType.contains("json")) {
                try {
                    Object json = objectMapper.readValue(body, Object.class);
                    String prettyJson = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(json);
                    prettyJson = NEWLINE_PATTERN.matcher(prettyJson).replaceAll("    ");
                    return "\n" + prettyJson;
                } catch (Exception e) {
                    // ignore parsing errors and return the original body
                }
            }
            return body;
        };
    }

    @Bean
    public HttpLogFormatter httpLogFormatter(ObjectMapper objectMapper) {
        return new JsonHttpLogFormatter(objectMapper);
    }

    @Bean
    public Logbook logbook(BodyFilter bodyFilter, HttpLogWriter writer, HttpLogFormatter formatter) {
        return Logbook.builder()
                .sink(new DefaultSink(formatter, writer))
                .condition(exclude(
                        requestTo("/actuator/**"),
                        requestTo("/admin/**"),
                        requestTo("**/h2-console/**"),
                        requestTo("**/swagger-ui/**"),
                        requestTo("**/api-docs/**"),
                        requestTo("**/swagger/**"))
                )
                .bodyFilter(bodyFilter)
                .build();
    }

    @Bean
    public HeaderFilter headerFilter() {
        return none();
    }
}
