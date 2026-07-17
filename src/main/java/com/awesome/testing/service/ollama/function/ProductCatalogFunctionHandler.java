package com.awesome.testing.service.ollama.function;

import com.awesome.testing.dto.ollama.ChatMessageDto;
import com.awesome.testing.dto.ollama.ToolCallDto;
import com.awesome.testing.service.ProductService;
import com.awesome.testing.dto.product.ProductListDto;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Component
@RequiredArgsConstructor
public class ProductCatalogFunctionHandler implements FunctionCallHandler {

    static final String TOOL_NAME = "list_products";
    private static final Pattern LEAKED_IN_STOCK_ARGUMENT = Pattern.compile(
            "^\\s*([^<>]*?)\\s*</parameter>\\s*<parameter=inStockOnly>\\s*(true|false)\\s*(?:</parameter>)?\\s*$",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

    private final ProductService productService;
    private final ObjectMapper objectMapper;

    @Override
    public String getName() {
        return TOOL_NAME;
    }

    @Override
    public ChatMessageDto handle(ToolCallDto toolCall) {
        try {
            Map<String, Object> args = toolCall.getFunction().getArguments();
            int offset = parseInt(args != null ? args.get("offset") : null, 0);
            int limit = parseInt(args != null ? args.get("limit") : null, 25);
            ParsedCategory parsedCategory = parseCategory(args != null ? args.get("category") : null);
            Boolean explicitInStockOnly = parseBoolean(args != null ? args.get("inStockOnly") : null);
            Boolean inStockOnly = explicitInStockOnly != null
                    ? explicitInStockOnly
                    : parsedCategory.leakedInStockOnly();

            ProductListDto list = productService.listProducts(offset, limit, parsedCategory.category(), inStockOnly);
            return ChatMessageDto.builder()
                    .role("tool")
                    .toolName(TOOL_NAME)
                    .content(objectMapper.writeValueAsString(list))
                    .build();
        } catch (IllegalArgumentException ex) {
            log.warn("Invalid list_products arguments: {}", ex.getMessage());
            return errorMessage(ex.getMessage());
        } catch (Exception ex) {
            log.error("Unexpected error executing list_products", ex);
            return errorMessage("Internal error while listing products");
        }
    }

    private int parseInt(Object value, int defaultValue) {
        if (value == null) {
            return defaultValue;
        }
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value instanceof String str && !str.isBlank()) {
            try {
                return Integer.parseInt(str.trim());
            } catch (NumberFormatException ex) {
                throw new IllegalArgumentException("limit/offset must be numbers", ex);
            }
        }
        throw new IllegalArgumentException("Unsupported number format");
    }

    private ParsedCategory parseCategory(Object value) {
        if (value == null) {
            return new ParsedCategory(null, null);
        }
        String category = value.toString().trim();
        if (category.isBlank()) {
            return new ParsedCategory(null, null);
        }

        Matcher leakedArgument = LEAKED_IN_STOCK_ARGUMENT.matcher(category);
        if (leakedArgument.matches()) {
            String repairedCategory = leakedArgument.group(1).trim();
            Boolean repairedInStockOnly = Boolean.valueOf(leakedArgument.group(2));
            log.warn("Repaired leaked tool markup in list_products category; category='{}', inStockOnly={}",
                    repairedCategory, repairedInStockOnly);
            return new ParsedCategory(repairedCategory.isBlank() ? null : repairedCategory, repairedInStockOnly);
        }

        if (category.contains("<") || category.contains(">")) {
            throw new IllegalArgumentException("category must be a plain category name without tool markup");
        }
        return new ParsedCategory(category, null);
    }

    private Boolean parseBoolean(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Boolean bool) {
            return bool;
        }
        if (value instanceof String str && !str.isBlank()) {
            return Boolean.parseBoolean(str.trim());
        }
        throw new IllegalArgumentException("inStockOnly must be a boolean");
    }

    private ChatMessageDto errorMessage(String message) {
        try {
            return ChatMessageDto.builder()
                    .role("tool")
                    .toolName(TOOL_NAME)
                    .content(objectMapper.writeValueAsString(Map.of("error", message)))
                    .build();
        } catch (JacksonException e) {
            return ChatMessageDto.builder()
                    .role("tool")
                    .toolName(TOOL_NAME)
                    .content("{\"error\":\"" + message + "\"}")
                    .build();
        }
    }

    private record ParsedCategory(String category, Boolean leakedInStockOnly) {
    }
}
