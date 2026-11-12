package com.awesome.testing.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.http.converter.HttpMessageConverters;
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import tools.jackson.databind.cfg.CoercionAction;
import tools.jackson.databind.cfg.CoercionInputShape;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.type.LogicalType;

/**
 * Request bodies of this API's own endpoints are bound without scalar coercion: 1.5 is not an
 * integer, "10" is not a number and true or 1234 is not a string. Jackson accepted all of them by
 * default, so a cart quantity of 1.5 was stored as 1 and a first name of true as "true". The
 * mismatch becomes a 400 field error (see RequestFormatExceptionHandler).
 *
 * <p>Only the Spring MVC server converter is replaced, so the stricter rules apply to what clients
 * send to the controllers. The shared {@link JsonMapper} bean, and with it the HTTP clients
 * (Ollama, payment, warehouse), JMS, webhooks and GraphQL, keep Jackson's defaults. Serialization is
 * unchanged because the strict mapper is rebuilt from that bean.
 */
@Configuration(proxyBeanMethods = false)
public class StrictRequestJsonConfig implements WebMvcConfigurer {

    private final JsonMapper jsonMapper;

    public StrictRequestJsonConfig(JsonMapper jsonMapper) {
        this.jsonMapper = jsonMapper;
    }

    @Override
    public void configureMessageConverters(HttpMessageConverters.ServerBuilder builder) {
        builder.withJsonConverter(new JacksonJsonHttpMessageConverter(strict(jsonMapper)));
    }

    static JsonMapper strict(JsonMapper base) {
        return base.rebuild()
                .withCoercionConfig(LogicalType.Integer, config -> config
                        .setCoercion(CoercionInputShape.Float, CoercionAction.Fail)
                        .setCoercion(CoercionInputShape.String, CoercionAction.Fail))
                .withCoercionConfig(LogicalType.Float, config -> config
                        .setCoercion(CoercionInputShape.String, CoercionAction.Fail))
                .withCoercionConfig(LogicalType.Textual, config -> config
                        .setCoercion(CoercionInputShape.Integer, CoercionAction.Fail)
                        .setCoercion(CoercionInputShape.Float, CoercionAction.Fail)
                        .setCoercion(CoercionInputShape.Boolean, CoercionAction.Fail))
                .build();
    }
}
