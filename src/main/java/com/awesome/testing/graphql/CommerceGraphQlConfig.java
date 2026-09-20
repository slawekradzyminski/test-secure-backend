package com.awesome.testing.graphql;

import com.awesome.testing.controller.exception.CartItemNotFoundException;
import com.awesome.testing.controller.exception.CustomException;
import com.awesome.testing.controller.exception.ProductNotFoundException;
import graphql.GraphQLError;
import graphql.analysis.MaxQueryComplexityInstrumentation;
import graphql.analysis.MaxQueryDepthInstrumentation;
import graphql.execution.instrumentation.Instrumentation;
import graphql.schema.DataFetchingEnvironment;
import jakarta.validation.ConstraintViolationException;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.graphql.execution.DataFetcherExceptionResolverAdapter;
import org.springframework.graphql.execution.ErrorType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.BindException;

import java.util.Map;
import java.util.Set;

@Configuration
@Profile("graphql")
public class CommerceGraphQlConfig {
    private static final Set<String> PAGED_FIELDS = Set.of("carts", "orders", "inventory", "inventoryMovements");

    @Bean
    Instrumentation commerceQueryDepth() {
        // Standard schema introspection traverses nested type wrappers beyond commerce query depth.
        return new MaxQueryDepthInstrumentation(20);
    }

    @Bean
    Instrumentation commerceQueryComplexity() {
        return new MaxQueryComplexityInstrumentation(1000, (environment, childComplexity) -> {
            String name = environment.getFieldDefinition().getName();
            String limitArgument = "products".equals(name) ? "limit" : "size";
            int multiplier = "products".equals(name) || PAGED_FIELDS.contains(name)
                    ? Math.max(1, Math.min(101, (int) environment.getArguments().getOrDefault(limitArgument, 20))) : 1;
            return 1 + multiplier * childComplexity;
        });
    }

    @Bean
    DataFetcherExceptionResolverAdapter commerceErrors() {
        return new DataFetcherExceptionResolverAdapter() {
            @Override
            protected GraphQLError resolveToSingleError(Throwable exception, DataFetchingEnvironment environment) {
                Failure failure = classify(exception);
                return failure == null ? null : GraphQLError.newError()
                        .errorType(failure.type()).message(failure.message())
                        .path(environment.getExecutionStepInfo().getPath())
                        .extensions(Map.of("code", failure.code())).build();
            }
        };
    }

    private static Failure classify(Throwable exception) {
        return switch (exception) {
            case ProductNotFoundException ignored -> new Failure(ErrorType.NOT_FOUND, "NOT_FOUND", "Product not found");
            case CartItemNotFoundException ignored -> new Failure(ErrorType.NOT_FOUND, "NOT_FOUND", "Cart item not found");
            case AccessDeniedException ignored -> new Failure(ErrorType.FORBIDDEN, "FORBIDDEN", "Access denied");
            case ConstraintViolationException ignored -> invalidInput();
            case BindException ignored -> invalidInput();
            case IllegalArgumentException ignored -> invalidInput();
            case DataIntegrityViolationException ignored -> new Failure(ErrorType.BAD_REQUEST, "CONFLICT", "Operation conflicts with existing data");
            case CustomException error -> switch (error.getHttpStatus().value()) {
                case 400 -> new Failure(ErrorType.BAD_REQUEST, "BAD_REQUEST", error.getMessage());
                case 403 -> new Failure(ErrorType.FORBIDDEN, "FORBIDDEN", "Access denied");
                case 404 -> new Failure(ErrorType.NOT_FOUND, "NOT_FOUND", error.getMessage());
                case 409 -> new Failure(ErrorType.BAD_REQUEST, "CONFLICT", error.getMessage());
                default -> null;
            };
            default -> null;
        };
    }

    private static Failure invalidInput() {
        return new Failure(ErrorType.BAD_REQUEST, "BAD_REQUEST", "Invalid query arguments or mutation input");
    }

    private record Failure(ErrorType type, String code, String message) { }
}
