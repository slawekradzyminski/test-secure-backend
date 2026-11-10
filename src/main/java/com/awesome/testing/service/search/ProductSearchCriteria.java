package com.awesome.testing.service.search;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Validated parameters of {@code GET /api/v1/products/search}.
 *
 * @param likePattern lower-case {@code %term%} pattern with {@code \}, {@code %} and {@code _} escaped by
 *     {@link #LIKE_ESCAPE}; null when no text query was given
 * @param categories lower-case category names; empty means every category
 * @param sort requested order; {@code RELEVANCE} without a text query becomes {@code NEWEST}
 */
public record ProductSearchCriteria(
        String likePattern,
        List<String> categories,
        BigDecimal minPrice,
        BigDecimal maxPrice,
        BigDecimal minRating,
        boolean inStockOnly,
        ProductSearchSort sort,
        int page,
        int size) {

    public static final char LIKE_ESCAPE = '\\';
    public static final int MAX_QUERY_LENGTH = 100;
    public static final int MAX_CATEGORIES = 20;
    public static final int MAX_SIZE = 100;
    public static final int DEFAULT_SIZE = 20;
    private static final BigDecimal MIN_RATING = BigDecimal.ONE;
    private static final BigDecimal MAX_RATING = BigDecimal.valueOf(5);
    private static final String TRUE = Boolean.TRUE.toString();
    private static final String FALSE = Boolean.FALSE.toString();

    public ProductSearchCriteria {
        categories = List.copyOf(categories);
        if (sort == ProductSearchSort.RELEVANCE && likePattern == null) {
            // Relevance has nothing to rank without a text query.
            sort = ProductSearchSort.NEWEST;
        }
    }

    public boolean hasQuery() {
        return likePattern != null;
    }

    /** Validates raw request parameters; every problem is reported at once, keyed by parameter name. */
    @SuppressWarnings("java:S107")
    public static ProductSearchCriteria parse(
            String q,
            List<String> category,
            String minPriceValue,
            String maxPriceValue,
            String minRatingValue,
            String inStockValue,
            String sort,
            String pageValue,
            String sizeValue) {
        Map<String, String> errors = new LinkedHashMap<>();
        BigDecimal minPrice = decimal("minPrice", minPriceValue, errors);
        BigDecimal maxPrice = decimal("maxPrice", maxPriceValue, errors);
        BigDecimal minRating = decimal("minRating", minRatingValue, errors);
        boolean inStockOnly = flag("inStock", inStockValue, errors);
        Integer page = integer("page", pageValue, errors);
        Integer size = integer("size", sizeValue, errors);
        String query = q == null ? "" : q.trim();
        if (query.length() > MAX_QUERY_LENGTH) {
            errors.put("q", "must be at most " + MAX_QUERY_LENGTH + " characters");
        }
        List<String> categories = categories(category);
        if (categories.size() > MAX_CATEGORIES) {
            errors.put("category", "must list at most " + MAX_CATEGORIES + " categories");
        }
        if (minPrice != null && minPrice.signum() < 0) {
            errors.put("minPrice", "must be zero or greater");
        }
        if (maxPrice != null && maxPrice.signum() < 0) {
            errors.put("maxPrice", "must be zero or greater");
        }
        if (minPrice != null && maxPrice != null && minPrice.compareTo(maxPrice) > 0) {
            errors.putIfAbsent("minPrice", "must not be greater than maxPrice");
        }
        if (minRating != null && (minRating.compareTo(MIN_RATING) < 0 || minRating.compareTo(MAX_RATING) > 0)) {
            errors.put("minRating", "must be between 1 and 5");
        }
        ProductSearchSort parsedSort = sort(sort, errors);
        int pageNumber = page == null ? 0 : page;
        if (pageNumber < 0) {
            errors.put("page", "must be zero or greater");
        }
        int pageSize = size == null ? DEFAULT_SIZE : size;
        if (pageSize < 1 || pageSize > MAX_SIZE) {
            errors.put("size", "must be between 1 and " + MAX_SIZE);
        }
        if (!errors.isEmpty()) {
            throw new ProductSearchValidationException(errors);
        }
        return new ProductSearchCriteria(
                query.isEmpty() ? null : likePattern(query),
                categories,
                minPrice,
                maxPrice,
                minRating,
                inStockOnly,
                parsedSort,
                pageNumber,
                pageSize);
    }

    static String likePattern(String query) {
        StringBuilder pattern = new StringBuilder(query.length() + 2).append('%');
        for (char character : query.toLowerCase(Locale.ROOT).toCharArray()) {
            if (character == LIKE_ESCAPE || character == '%' || character == '_') {
                pattern.append(LIKE_ESCAPE);
            }
            pattern.append(character);
        }
        return pattern.append('%').toString();
    }

    private static BigDecimal decimal(String name, String value, Map<String, String> errors) {
        if (isAbsent(value)) {
            return null;
        }
        try {
            return new BigDecimal(value.trim());
        } catch (NumberFormatException ex) {
            errors.put(name, "must be a number");
            return null;
        }
    }

    private static Integer integer(String name, String value, Map<String, String> errors) {
        if (isAbsent(value)) {
            return null;
        }
        try {
            return Integer.valueOf(value.trim());
        } catch (NumberFormatException ex) {
            errors.put(name, "must be an integer");
            return null;
        }
    }

    private static boolean flag(String name, String value, Map<String, String> errors) {
        if (isAbsent(value) || FALSE.equalsIgnoreCase(value.trim())) {
            return false;
        }
        if (TRUE.equalsIgnoreCase(value.trim())) {
            return true;
        }
        errors.put(name, "must be true or false");
        return false;
    }

    private static boolean isAbsent(String value) {
        return value == null || value.isBlank();
    }

    private static List<String> categories(List<String> values) {
        if (values == null) {
            return List.of();
        }
        Set<String> categories = new LinkedHashSet<>();
        for (String value : values) {
            if (value == null) {
                continue;
            }
            Arrays.stream(value.split(","))
                    .map(String::trim)
                    .filter(name -> !name.isEmpty())
                    .map(name -> name.toLowerCase(Locale.ROOT))
                    .forEach(categories::add);
        }
        return new ArrayList<>(categories);
    }

    /** Blank means relevance, which the constructor turns into newest without a text query. */
    private static ProductSearchSort sort(String value, Map<String, String> errors) {
        if (isAbsent(value)) {
            return ProductSearchSort.RELEVANCE;
        }
        return ProductSearchSort.parse(value).orElseGet(() -> {
            errors.put("sort", "must be one of " + ProductSearchSort.wireValues());
            return null;
        });
    }
}
