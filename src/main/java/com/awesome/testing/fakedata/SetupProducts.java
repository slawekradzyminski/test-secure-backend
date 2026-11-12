package com.awesome.testing.fakedata;

import com.awesome.testing.entity.ProductEntity;
import com.awesome.testing.repository.ProductRepository;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Component
@RequiredArgsConstructor
public class SetupProducts {

    private static final String IMAGE_PATH_PREFIX = "/images/";
    private static final List<String> LEGACY_IMAGE_URL_PREFIXES = List.of(
        "http://localhost:8082/images/",
        "http://127.0.0.1:8082/images/"
    );

    private final ProductRepository productRepository;
    private final ProductCatalogLoader productCatalogLoader;

    @Getter private ProductEntity iPhone;
    @Getter private ProductEntity galaxyS21;
    @Getter private ProductEntity macBook;
    @Getter private ProductEntity playStation;
    @Getter private ProductEntity ninjaFoodi;
    @Getter private ProductEntity cleanCode;
    @Getter private ProductEntity appleWatch;
    @Getter private ProductEntity sonyHeadphones;

    @Transactional
    public void createProducts() {
        if (productRepository.count() > 0) {
            assignDemoProductHandles(productRepository.findAll());
            return;
        }

        List<ProductEntity> createdProducts = productCatalogLoader.loadCatalog().stream()
                .map(this::createProduct)
                .toList();
        assignDemoProductHandles(createdProducts);
    }

    /**
     * Rewrites legacy absolute image URLs to relative paths and turns a missing image URL into an empty
     * string. Runs on every start of a seeding profile, so it must accept any row the API, GraphQL or the
     * CSV import can store (catalog X-01: a null image URL crash-looped the backend).
     */
    @Transactional
    public void normalizeProductImageUrls() {
        List<ProductEntity> products = productRepository.findAll();

        for (ProductEntity product : products) {
            String normalizedImageUrl = normalizeLegacyImageUrl(product.getImageUrl());
            if (!Objects.equals(normalizedImageUrl, product.getImageUrl())) {
                product.setImageUrl(normalizedImageUrl);
            }
        }
    }

    private ProductEntity createProduct(ProductSeedDefinition definition) {
        ProductEntity product = ProductEntity.builder()
            .name(definition.name())
            .description(definition.description())
            .price(definition.price())
            .stockQuantity(definition.stockQuantity())
            .category(definition.category())
            .imageUrl(imagePath(definition.imageUrl()))
            .build();
        return productRepository.save(product);
    }

    private void assignDemoProductHandles(List<ProductEntity> products) {
        Map<String, ProductEntity> productsByName = new HashMap<>();
        for (ProductEntity product : products) {
            productsByName.put(product.getName(), product);
        }
        iPhone = productsByName.get("iPhone 13 Pro");
        galaxyS21 = productsByName.get("Samsung Galaxy S21");
        macBook = productsByName.get("MacBook Pro 14");
        playStation = productsByName.get("PlayStation 5");
        ninjaFoodi = productsByName.get("Ninja Foodi 9-in-1");
        cleanCode = productsByName.get("Clean Code");
        appleWatch = productsByName.get("Apple Watch Series 7");
        sonyHeadphones = productsByName.get("Sony WH-1000XM4");
    }

    private String normalizeLegacyImageUrl(String imageUrl) {
        if (imageUrl == null) {
            return ProductEntity.NO_IMAGE;
        }

        for (String legacyPrefix : LEGACY_IMAGE_URL_PREFIXES) {
            if (imageUrl.startsWith(legacyPrefix)) {
                return IMAGE_PATH_PREFIX + imageUrl.substring(legacyPrefix.length());
            }
        }

        return imageUrl;
    }

    private String imagePath(String filename) {
        if (filename == null || filename.startsWith(IMAGE_PATH_PREFIX)) {
            return filename;
        }
        return IMAGE_PATH_PREFIX + filename;
    }
}
