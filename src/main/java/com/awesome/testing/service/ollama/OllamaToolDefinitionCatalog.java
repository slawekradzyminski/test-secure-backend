package com.awesome.testing.service.ollama;

import com.awesome.testing.dto.ollama.OllamaToolDefinitionDto;
import com.awesome.testing.dto.ollama.OllamaToolFunctionDto;
import com.awesome.testing.dto.ollama.OllamaToolParametersDto;
import com.awesome.testing.dto.ollama.OllamaToolParametersRequirementDto;
import com.awesome.testing.dto.ollama.OllamaToolSchemaPropertyDto;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Catalog of tool definitions exposed through /api/ollama/chat/tools.
 * Frontend + documentation can read from this bean to stay in sync.
 */
@Slf4j
@Component
public class OllamaToolDefinitionCatalog {

    private final List<OllamaToolDefinitionDto> definitions;

    public OllamaToolDefinitionCatalog() {
        this.definitions = List.of(
                productSnapshotTool(),
                listProductsTool()
        );
        List<String> names = definitions.stream()
                .map(def -> def.getFunction().getName())
                .collect(Collectors.toList());
        log.info("Registered {} Ollama tool definitions: {}", definitions.size(), names);
    }

    public List<OllamaToolDefinitionDto> getDefinitions() {
        return definitions;
    }

    private static OllamaToolDefinitionDto productSnapshotTool() {
        return OllamaToolDefinitionDto.builder()
                .function(OllamaToolFunctionDto.builder()
                        .name("get_product_snapshot")
                        .description("Anchor every catalog response with real price/stock/description data. qwen3:4b-instruct hallucinates if it guesses, so always lead with this snapshot and keep it paired with list_products inside the catalog lane.")
                        .parameters(OllamaToolParametersDto.builder()
                                .type("object")
                                .properties(Map.of(
                                        "productId", property("integer", "Numeric product id shown in the catalog."),
                                        "name", property("string", "Exact product name when the id is unknown.")
                                ))
                                .oneOf(List.of(
                                        requirement("productId"),
                                        requirement("name")
                                ))
                                .build())
                        .build())
                .build();
    }

    private static OllamaToolDefinitionDto listProductsTool() {
        return OllamaToolDefinitionDto.builder()
                .function(OllamaToolFunctionDto.builder()
                        .name("list_products")
                        .description("Return a lightweight catalog slice (id + name only). Use for discovery by category before calling get_product_snapshot for details.")
                        .parameters(OllamaToolParametersDto.builder()
                                .type("object")
                                .properties(Map.of(
                                        "offset", property("integer", "Zero-based offset into the catalog (default 0)."),
                                        "limit", property("integer", "Number of products to fetch (default 25, max 100)."),
                                        "category", property("string", "Case-insensitive category filter, e.g., 'electronics'."),
                                        "inStockOnly", property("boolean", "If true, only return products with stockQuantity > 0.")
                                ))
                                .build())
                        .build())
                .build();
    }

    private static OllamaToolSchemaPropertyDto property(String type, String description) {
        return OllamaToolSchemaPropertyDto.builder()
                .type(type)
                .description(description)
                .build();
    }

    private static OllamaToolParametersRequirementDto requirement(String field) {
        return OllamaToolParametersRequirementDto.builder()
                .required(List.of(field))
                .build();
    }
}
