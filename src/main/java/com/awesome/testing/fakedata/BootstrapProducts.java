package com.awesome.testing.fakedata;

import com.awesome.testing.repository.ProductRepository;
import com.awesome.testing.service.stock.StockCommandOutbox;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Component
@Profile("!test")
@Order(1)
public class BootstrapProducts implements CommandLineRunner {

    private final ProductRepository productRepository;
    private final ProductCatalogLoader productCatalogLoader;
    private final StockCommandOutbox stockCommands;

    private final boolean enabled;

    public BootstrapProducts(ProductRepository productRepository, ProductCatalogLoader productCatalogLoader,
                             StockCommandOutbox stockCommands,
                             @org.springframework.beans.factory.annotation.Value("${app.bootstrap-products.enabled:false}") boolean enabled) {
        this.enabled = enabled;
        this.productRepository = productRepository;
        this.productCatalogLoader = productCatalogLoader;
        this.stockCommands = stockCommands;
    }

    @Override
    @Transactional
    public void run(String... args) {
        // Checked at runtime, not with @ConditionalOnProperty: native images fix conditions at build time.
        if (!enabled) {
            return;
        }
        if (productRepository.count() > 0) {
            log.info("Product catalog already contains data, skipping bootstrap");
            return;
        }

        List<ProductSeedDefinition> products = productCatalogLoader.loadCatalog();
        if (products.isEmpty()) {
            throw new IllegalStateException("Bootstrap product catalog is empty");
        }

        // Each product's stock item is created in warehouse-service after commit (contract section 4.3).
        productRepository.saveAll(products.stream()
                .map(product -> com.awesome.testing.entity.ProductEntity.builder()
                        .name(product.name())
                        .description(product.description())
                        .price(product.price())
                        .stockQuantity(product.stockQuantity())
                        .category(product.category())
                        .imageUrl(product.imageUrl())
                        .build())
                .toList())
                .forEach(product -> stockCommands.initialStock(product, SetupProducts.SEED_ACTOR));

        log.info("Bootstrapped {} products into an empty catalog", products.size());
    }
}
