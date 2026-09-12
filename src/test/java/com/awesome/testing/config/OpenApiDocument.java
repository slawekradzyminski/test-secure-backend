package com.awesome.testing.config;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Stream;

record OpenApiDocument(JsonNode root) {
    private static final Set<String> HTTP_METHODS = Set.of(
            "get", "post", "put", "delete", "patch", "head", "options", "trace");

    JsonNode operation(Endpoint endpoint) {
        return root.path("paths").path(endpoint.path()).path(endpoint.method());
    }

    JsonNode response(Endpoint endpoint, String status) {
        return operation(endpoint).path("responses").path(status);
    }

    JsonNode property(String schema, String field) {
        return root.path("components").path("schemas").path(schema).path("properties").path(field);
    }

    Stream<DocumentedOperation> operations() {
        return root.path("paths").properties().stream()
                .flatMap(path -> path.getValue().properties().stream()
                        .filter(method -> HTTP_METHODS.contains(method.getKey()))
                        .map(method -> new DocumentedOperation(new Endpoint(path.getKey(), method.getKey()), method.getValue())));
    }

    boolean allowsNull(JsonNode schema) {
        if (schema.isMissingNode()) {
            return false;
        }
        return referenceAllowsNull(schema)
                && typeAllowsNull(schema.path("type"))
                && (!schema.has("enum") || schema.path("enum").valueStream().anyMatch(JsonNode::isNull))
                && (!schema.has("anyOf") || schema.path("anyOf").valueStream().anyMatch(this::allowsNull))
                && (!schema.has("oneOf") || schema.path("oneOf").valueStream().filter(this::allowsNull).count() == 1)
                && (!schema.has("allOf") || schema.path("allOf").valueStream().allMatch(this::allowsNull))
                && (!schema.has("not") || !allowsNull(schema.path("not")));
    }

    private boolean referenceAllowsNull(JsonNode schema) {
        return !schema.has("$ref") || allowsNull(root.at(schema.path("$ref").asText().substring(1)));
    }

    private static boolean typeAllowsNull(JsonNode type) {
        return switch (type.getNodeType()) {
            case STRING -> "null".equals(type.asText());
            case ARRAY -> type.valueStream().anyMatch(value -> "null".equals(value.asText()));
            default -> true;
        };
    }

    record Endpoint(String path, String method) {
        Endpoint {
            method = method.toLowerCase(Locale.ROOT);
        }

        @Override
        public String toString() {
            return method.toUpperCase(Locale.ROOT) + " " + path;
        }
    }

    record DocumentedOperation(Endpoint endpoint, JsonNode node) {
        Stream<DocumentedResponse> responses() {
            return node.path("responses").properties().stream()
                    .map(response -> new DocumentedResponse(endpoint, response.getKey(), response.getValue()));
        }

        List<String> responseCodes() {
            return node.path("responses").properties().stream().map(java.util.Map.Entry::getKey).toList();
        }
    }

    record DocumentedResponse(Endpoint endpoint, String status, JsonNode node) {
        JsonNode jsonSchema() {
            return node.path("content").path("application/json").path("schema");
        }

        boolean isError() {
            return status.matches("[45][0-9]{2}");
        }

        boolean isSuccess() {
            return status.startsWith("2");
        }

        @Override
        public String toString() {
            return endpoint + " -> " + status;
        }
    }
}
