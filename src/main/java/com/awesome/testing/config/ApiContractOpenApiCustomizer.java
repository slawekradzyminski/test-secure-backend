package com.awesome.testing.config;

import com.awesome.testing.config.properties.PasswordResetProperties;
import com.awesome.testing.traffic.TrafficProperties;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.parameters.Parameter;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

@Component
@RequiredArgsConstructor
public class ApiContractOpenApiCustomizer implements OpenApiCustomizer {
    private static final String NON_BLANK_PATTERN = "\\S";
    private static final String SESSION_HEADER = "X-Client-Session-Id";
    private static final String OUTBOX_KEY_HEADER = "X-Local-Outbox-Key";

    private final TrafficProperties trafficProperties;
    private final PasswordResetProperties passwordResetProperties;

    @Override
    public void customise(OpenAPI openApi) {
        normalizeProperties(openApi);
        documentTrafficSecurity(openApi);
        documentOutboxSecurity(openApi);
    }

    private void normalizeProperties(OpenAPI openApi) {
        openApi.getComponents().getSchemas().values().stream()
                .flatMap(this::propertiesOf)
                .forEach(this::normalizeProperty);
    }

    private Stream<Schema<?>> propertiesOf(Schema<?> schema) {
        return Stream.ofNullable(schema.getProperties())
                .flatMap(properties -> properties.values().stream())
                .map(property -> (Schema<?>) property);
    }

    private void normalizeProperty(Schema<?> property) {
        if (Optional.ofNullable(property.getTypes()).orElseGet(Set::of).contains("null")) {
            makeReferenceNullable(property);
            if (property.getEnum() != null && !property.getEnum().contains(null)) {
                property.addEnumItemObject(null);
            }
        }
        if (NON_BLANK_PATTERN.equals(property.getPattern())) {
            property.setMinLength(Math.max(1, Optional.ofNullable(property.getMinLength()).orElse(0)));
        }
    }

    private void makeReferenceNullable(Schema<?> property) {
        Optional.ofNullable(property.get$ref()).ifPresent(reference -> {
            Schema<Object> nullAlternative = new Schema<>();
            nullAlternative.setTypes(Set.of("null"));
            property.setAnyOf(List.of(new Schema<>().$ref(reference), nullAlternative));
            property.set$ref(null);
        });
    }

    private void documentTrafficSecurity(OpenAPI openApi) {
        openApi.getPaths().entrySet().stream()
                .filter(path -> path.getKey().startsWith("/api/v1/traffic/"))
                .map(Map.Entry::getValue)
                .flatMap(path -> path.readOperations().stream())
                .forEach(this::configureTrafficOperation);
    }

    private void configureTrafficOperation(Operation operation) {
        boolean requiresAuthentication = !trafficProperties.isLegacyPublicAccess();
        operation.setSecurity(requiresAuthentication
                ? List.of(new SecurityRequirement().addList("bearerAuth"))
                : List.of());
        parametersNamed(operation, SESSION_HEADER).forEach(parameter -> {
            parameter.setRequired(requiresAuthentication);
            parameter.setDescription("Session identifier, trimmed before validation: 16–128 ASCII letters, digits, underscores or hyphens. Required unless legacy public access is enabled.");
        });
    }

    private void documentOutboxSecurity(OpenAPI openApi) {
        Optional.ofNullable(openApi.getPaths().get("/api/v1/local/email/outbox"))
                .filter(outbox -> passwordResetProperties.isRequireOutboxAccessKey())
                .ifPresent(outbox -> {
                    openApi.getComponents().addSecuritySchemes("localOutboxKey", new SecurityScheme()
                            .type(SecurityScheme.Type.APIKEY).in(SecurityScheme.In.HEADER).name(OUTBOX_KEY_HEADER));
                    outbox.readOperations().forEach(operation -> {
                        operation.setSecurity(List.of(new SecurityRequirement().addList("bearerAuth").addList("localOutboxKey")));
                        parametersNamed(operation, OUTBOX_KEY_HEADER).forEach(parameter -> parameter.setRequired(true));
                    });
                });
    }

    private Stream<Parameter> parametersNamed(Operation operation, String name) {
        return Stream.ofNullable(operation.getParameters())
                .flatMap(List::stream)
                .filter(parameter -> name.equals(parameter.getName()));
    }
}
