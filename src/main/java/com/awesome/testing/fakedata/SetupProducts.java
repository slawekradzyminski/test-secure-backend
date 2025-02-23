package com.awesome.testing.fakedata;

import com.awesome.testing.entity.ProductEntity;
import com.awesome.testing.repository.ProductRepository;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

@Component
@RequiredArgsConstructor
public class SetupProducts {

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
            "https://example.com/iphone13pro.jpg"
        );

        galaxyS21 = createProduct(
            "Samsung Galaxy S21",
            "5G smartphone with 8K video, all-day battery, and powerful performance",
            new BigDecimal("799.99"),
            75,
            "Electronics",
            "https://example.com/galaxys21.jpg"
        );

        // Computers
        macBook = createProduct(
            "MacBook Pro 14\"",
            "Apple M1 Pro chip, 16GB RAM, 512GB SSD, Liquid Retina XDR display",
            new BigDecimal("1999.99"),
            25,
            "Computers",
            "https://example.com/macbookpro.jpg"
        );

        // Gaming
        playStation = createProduct(
            "PlayStation 5",
            "Next-gen gaming console with 4K graphics, ray tracing, and ultra-high speed SSD",
            new BigDecimal("499.99"),
            30,
            "Gaming",
            "https://example.com/ps5.jpg"
        );

        // Home & Kitchen
        ninjaFoodi = createProduct(
            "Ninja Foodi 9-in-1",
            "Deluxe XL pressure cooker and air fryer with multiple cooking functions",
            new BigDecimal("249.99"),
            100,
            "Home & Kitchen",
            "https://example.com/ninjafoodi.jpg"
        );

        // Books
        cleanCode = createProduct(
            "Clean Code",
            "A Handbook of Agile Software Craftsmanship by Robert C. Martin",
            new BigDecimal("44.99"),
            200,
            "Books",
            "https://example.com/cleancode.jpg"
        );

        // Wearables
        appleWatch = createProduct(
            "Apple Watch Series 7",
            "Always-On Retina display, health monitoring, and fitness tracking",
            new BigDecimal("399.99"),
            60,
            "Wearables",
            "https://example.com/applewatch.jpg"
        );

        // Audio
        sonyHeadphones = createProduct(
            "Sony WH-1000XM4",
            "Industry-leading noise canceling wireless headphones with exceptional sound",
            new BigDecimal("349.99"),
            85,
            "Audio",
            "https://example.com/sonywh1000xm4.jpg"
        );
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
} 