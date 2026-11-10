package com.awesome.testing.service.search;

import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;
import java.util.stream.Collectors;

/** Sort orders of {@code GET /api/v1/products/search}; the wire value is the lower-case name. */
public enum ProductSearchSort {
    RELEVANCE,
    NEWEST,
    PRICE_ASC,
    PRICE_DESC,
    NAME_ASC,
    RATING_DESC;

    public String wireValue() {
        return name().toLowerCase(Locale.ROOT);
    }

    /** Comma-separated wire values in declaration order, for error messages. */
    public static String wireValues() {
        return Arrays.stream(values()).map(ProductSearchSort::wireValue).collect(Collectors.joining(", "));
    }

    static Optional<ProductSearchSort> parse(String value) {
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        return Arrays.stream(values()).filter(sort -> sort.name().equals(normalized)).findFirst();
    }
}
