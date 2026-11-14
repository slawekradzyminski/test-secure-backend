package com.awesome.testing.service.stock;

import com.awesome.testing.dto.stock.StockLevelDto;
import com.awesome.testing.dto.stock.StockLevelsResultDto;
import com.awesome.testing.entity.ProductEntity;
import com.awesome.testing.repository.ProductRepository;
import com.awesome.testing.service.notification.StockReplenishedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Keeps {@code products.stock_quantity} as a projection of warehouse-service's stock ledger. A level
 * is applied only when its {@code ledgerVersion} is greater than the product's
 * {@code stock_ledger_version}, so replies and pushes that arrive late or twice never move the
 * projection backwards. A change from 0 to more than 0 publishes {@link StockReplenishedEvent}
 * (back-in-stock notifications), exactly as the local stock changes did before.
 */
@Component
@RequiredArgsConstructor
public class StockProjection {

    private final ProductRepository productRepository;
    private final ApplicationEventPublisher eventPublisher;

    /**
     * Applies a level to a product the current transaction has locked.
     *
     * @return true when the level was newer and changed the projection
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public boolean apply(ProductEntity product, int quantity, long ledgerVersion) {
        if (ledgerVersion <= product.getStockLedgerVersion()) {
            return false;
        }
        int previous = product.getStockQuantity();
        product.setStockQuantity(quantity);
        product.setStockLedgerVersion(ledgerVersion);
        productRepository.save(product);
        if (previous == 0 && quantity > 0) {
            eventPublisher.publishEvent(new StockReplenishedEvent(product.getId()));
        }
        return true;
    }

    /** Locks the product and applies the level; false for an unknown product or a stale level. */
    @Transactional
    public boolean apply(long productId, int quantity, long ledgerVersion) {
        Optional<ProductEntity> product = productRepository.findByIdForUpdate(productId);
        return product.isPresent() && apply(product.get(), quantity, ledgerVersion);
    }

    /**
     * {@code POST /internal/v1/inventory/levels}: applies every level in product id order (the lock
     * order used everywhere else), oldest version first. Unknown products and stale levels are
     * counted as ignored.
     */
    @Transactional
    public StockLevelsResultDto applyLevels(List<StockLevelDto> levels) {
        int applied = 0;
        List<StockLevelDto> ordered = levels.stream()
                .sorted(Comparator.comparing(StockLevelDto::productId).thenComparing(StockLevelDto::ledgerVersion))
                .toList();
        for (StockLevelDto level : ordered) {
            if (apply(level.productId(), level.quantity(), level.ledgerVersion())) {
                applied++;
            }
        }
        return new StockLevelsResultDto(applied, levels.size() - applied);
    }
}
