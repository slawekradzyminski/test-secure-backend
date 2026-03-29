package com.awesome.testing.fakedata;

import java.math.BigDecimal;

public record ProductSeedDefinition(
        String name,
        String description,
        BigDecimal price,
        int stockQuantity,
        String category,
        String imageUrl) {
}
