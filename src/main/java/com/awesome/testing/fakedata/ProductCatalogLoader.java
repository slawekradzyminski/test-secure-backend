package com.awesome.testing.fakedata;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.List;

@Component
public class ProductCatalogLoader {

    private static final TypeReference<List<ProductSeedDefinition>> PRODUCT_LIST_TYPE = new TypeReference<>() {};

    private final ObjectMapper objectMapper;
    private final Resource catalogResource;

    public ProductCatalogLoader(
            ObjectMapper objectMapper,
            @Value("${app.bootstrap-products.catalog:classpath:bootstrap/products.json}") Resource catalogResource) {
        this.objectMapper = objectMapper;
        this.catalogResource = catalogResource;
    }

    public List<ProductSeedDefinition> loadCatalog() {
        try (InputStream inputStream = catalogResource.getInputStream()) {
            return objectMapper.readValue(inputStream, PRODUCT_LIST_TYPE);
        } catch (IOException ex) {
            throw new UncheckedIOException("Failed to load bootstrap product catalog", ex);
        }
    }
}
