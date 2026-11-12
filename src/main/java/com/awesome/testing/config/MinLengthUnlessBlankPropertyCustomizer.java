package com.awesome.testing.config;

import com.awesome.testing.dto.validation.MinLengthUnlessBlank;
import io.swagger.v3.core.converter.AnnotatedType;
import io.swagger.v3.oas.models.media.Schema;
import org.springdoc.core.customizers.PropertyCustomizer;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Optional;

/**
 * Documents {@link MinLengthUnlessBlank} as {@code minLength}. swagger-core only knows {@code @Size}, and it
 * applies {@code @Size} after property customizers, overwriting {@code minLength} with {@code @Size(max = ...)}'s
 * default {@code min = 0}. So the minimum is carried in a temporary extension and
 * {@link ApiContractOpenApiCustomizer} turns it into {@code minLength} once every schema is resolved.
 */
@Component
public class MinLengthUnlessBlankPropertyCustomizer implements PropertyCustomizer {

    static final String MIN_LENGTH_EXTENSION = "x-min-length-unless-blank";

    @Override
    public Schema customize(Schema property, AnnotatedType type) {
        Optional.ofNullable(type.getCtxAnnotations()).stream()
                .flatMap(Arrays::stream)
                .filter(MinLengthUnlessBlank.class::isInstance)
                .map(MinLengthUnlessBlank.class::cast)
                .findFirst()
                .ifPresent(constraint -> property.addExtension(MIN_LENGTH_EXTENSION, constraint.value()));
        return property;
    }
}
