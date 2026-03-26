package com.awesome.testing.fakedata;

import com.awesome.testing.entity.ProductEntity;
import com.awesome.testing.repository.ProductRepository;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Component
@RequiredArgsConstructor
public class SetupProducts {

    private static final String IMAGE_PATH_PREFIX = "/images/";
    private static final List<String> LEGACY_IMAGE_URL_PREFIXES = List.of(
        "http://localhost:8082/images/",
        "http://127.0.0.1:8082/images/"
    );

    private final ProductRepository productRepository;

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
            return;
        }

        // Electronics
        iPhone = createProduct(
            "iPhone 13 Pro",
            "Latest iPhone model with A15 Bionic chip, Pro camera system, and Super Retina XDR display",
            new BigDecimal("999.99"),
            50,
            "Electronics",
            imagePath("iphone.png")
        );

        galaxyS21 = createProduct(
            "Samsung Galaxy S21",
            "5G smartphone with 8K video, all-day battery, and powerful performance",
            new BigDecimal("799.99"),
            75,
            "Electronics",
            imagePath("samsung.png")
        );

        // Computers
        macBook = createProduct(
            "MacBook Pro 14",
            "Apple M1 Pro chip, 16GB RAM, 512GB SSD, Liquid Retina XDR display",
            new BigDecimal("1999.99"),
            25,
            "Computers",
            imagePath("mac.png")
        );

        // Gaming
        playStation = createProduct(
            "PlayStation 5",
            "Next-gen gaming console with 4K graphics, ray tracing, and ultra-high speed SSD",
            new BigDecimal("499.99"),
            30,
            "Gaming",
            imagePath("ps5.png")
        );

        // Home & Kitchen
        ninjaFoodi = createProduct(
            "Ninja Foodi 9-in-1",
            "Deluxe XL pressure cooker and air fryer with multiple cooking functions",
            new BigDecimal("249.99"),
            100,
            "Home & Kitchen",
            imagePath("ninja.png")
        );

        // Books
        cleanCode = createProduct(
            "Clean Code",
            "A Handbook of Agile Software Craftsmanship by Robert C. Martin",
            new BigDecimal("44.99"),
            200,
            "Books",
            imagePath("cleancode.png")
        );

        // Wearables
        appleWatch = createProduct(
            "Apple Watch Series 7",
            "Always-On Retina display, health monitoring, and fitness tracking",
            new BigDecimal("399.99"),
            60,
            "Wearables",
            imagePath("applewatch.png")
        );

        // Audio
        sonyHeadphones = createProduct(
            "Sony WH-1000XM4",
            "Industry-leading noise canceling wireless headphones with exceptional sound",
            new BigDecimal("349.99"),
            85,
            "Audio",
            imagePath("sony.png")
        );
    }

    @Transactional
    public void normalizeProductImageUrls() {
        List<ProductEntity> products = productRepository.findAll();

        for (ProductEntity product : products) {
            String normalizedImageUrl = normalizeLegacyImageUrl(product.getImageUrl());
            if (!normalizedImageUrl.equals(product.getImageUrl())) {
                product.setImageUrl(normalizedImageUrl);
            }
        }
    }

    private ProductEntity createProduct(String name, String description, BigDecimal price, int quantity, String category, String imageUrl) {
        ProductEntity product = ProductEntity.builder()
            .name(name)
            .description(description)
            .price(price)
            .stockQuantity(quantity)
            .category(category)
            .imageUrl(imageUrl)
            .build();
        return productRepository.save(product);
    }

    private String normalizeLegacyImageUrl(String imageUrl) {
        if (imageUrl == null) {
            return null;
        }

        for (String legacyPrefix : LEGACY_IMAGE_URL_PREFIXES) {
            if (imageUrl.startsWith(legacyPrefix)) {
                return IMAGE_PATH_PREFIX + imageUrl.substring(legacyPrefix.length());
            }
        }

        return imageUrl;
    }

    private String imagePath(String filename) {
        return IMAGE_PATH_PREFIX + filename;
    }
}
