package com.awesome.testing.fakedata;

import com.awesome.testing.repository.ProductRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Component
@Profile("!test")
@ConditionalOnProperty(name = "app.bootstrap-products.enabled", havingValue = "true")
public class BootstrapProducts implements CommandLineRunner {

    private final ProductRepository productRepository;
    private final ProductCatalogLoader productCatalogLoader;

    public BootstrapProducts(ProductRepository productRepository, ProductCatalogLoader productCatalogLoader) {
        this.productRepository = productRepository;
        this.productCatalogLoader = productCatalogLoader;
    }

    @Override
    @Transactional
    public void run(String... args) {
        if (productRepository.count() > 0) {
            log.info("Product catalog already contains data, skipping bootstrap");
            return;
        }

        List<ProductSeedDefinition> products = productCatalogLoader.loadCatalog();
        if (products.isEmpty()) {
            throw new IllegalStateException("Bootstrap product catalog is empty");
        }

        productRepository.saveAll(products.stream()
                .map(product -> com.awesome.testing.entity.ProductEntity.builder()
                        .name(product.name())
                        .description(product.description())
                        .price(product.price())
                        .stockQuantity(product.stockQuantity())
                        .category(product.category())
                        .imageUrl(product.imageUrl())
                        .build())
                .toList());

        log.info("Bootstrapped {} products into an empty catalog", products.size());
    }
}
