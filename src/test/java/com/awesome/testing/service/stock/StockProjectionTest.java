package com.awesome.testing.service.stock;

import com.awesome.testing.dto.stock.StockLevelDto;
import com.awesome.testing.dto.stock.StockLevelsResultDto;
import com.awesome.testing.entity.ProductEntity;
import com.awesome.testing.repository.ProductRepository;
import com.awesome.testing.service.notification.StockReplenishedEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** The stock projection: only newer ledger versions apply, and a refill from zero is announced. */
@ExtendWith(MockitoExtension.class)
class StockProjectionTest {

    @Mock
    private ProductRepository productRepository;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private StockProjection projection;

    private ProductEntity product;

    @BeforeEach
    void setUp() {
        product = ProductEntity.builder().id(1L).stockQuantity(5).stockLedgerVersion(10L).build();
        lenient().when(productRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(product));
        lenient().when(productRepository.advanceLedgerVersion(any(), anyLong())).thenReturn(1);
    }

    @Test
    void anEqualQuantityAdvancesOnlyTheLedgerVersionWithoutWritingTheEntity() {
        assertThat(projection.apply(product, 5, 11L)).isTrue();

        verify(productRepository).advanceLedgerVersion(1L, 11L);
        verify(productRepository, never()).save(any());
        assertThat(product.getStockLedgerVersion()).isEqualTo(11L);
        verifyNoInteractions(eventPublisher);
    }

    @Test
    void theSqlGuardRejectsAVersionAnotherTransactionAlreadyApplied() {
        when(productRepository.advanceLedgerVersion(1L, 11L)).thenReturn(0);

        assertThat(projection.apply(product, 3, 11L)).isFalse();

        assertThat(product.getStockQuantity()).isEqualTo(5);
        assertThat(product.getStockLedgerVersion()).isEqualTo(10L);
        verify(productRepository, never()).save(any());
    }

    @Test
    void aNewerVersionSetsQuantityAndVersion() {
        assertThat(projection.apply(product, 3, 11L)).isTrue();

        assertThat(product.getStockQuantity()).isEqualTo(3);
        assertThat(product.getStockLedgerVersion()).isEqualTo(11L);
        verify(productRepository).save(product);
        verifyNoInteractions(eventPublisher);
    }

    @Test
    void anEqualOrOlderVersionChangesNothing() {
        assertThat(projection.apply(product, 3, 10L)).isFalse();
        assertThat(projection.apply(product, 3, 9L)).isFalse();

        assertThat(product.getStockQuantity()).isEqualTo(5);
        assertThat(product.getStockLedgerVersion()).isEqualTo(10L);
        verify(productRepository, never()).save(any());
        verify(productRepository, never()).advanceLedgerVersion(any(), anyLong());
    }

    @Test
    void aRefillFromZeroPublishesOneReplenishmentButTopUpsAndSellOutsDoNot() {
        product.setStockQuantity(0);

        projection.apply(product, 0, 11L);
        projection.apply(product, 1, 12L);
        projection.apply(product, 4, 13L);
        projection.apply(product, 0, 14L);

        verify(eventPublisher).publishEvent(new StockReplenishedEvent(1L));
        org.mockito.Mockito.verifyNoMoreInteractions(eventPublisher);
    }

    @Test
    void applyByIdLocksTheProductAndIgnoresUnknownOnes() {
        when(productRepository.findByIdForUpdate(2L)).thenReturn(Optional.empty());

        assertThat(projection.apply(1L, 7, 11L)).isTrue();
        assertThat(projection.apply(2L, 7, 11L)).isFalse();

        assertThat(product.getStockQuantity()).isEqualTo(7);
        verify(productRepository).findByIdForUpdate(1L);
        verify(productRepository).findByIdForUpdate(2L);
    }

    @Test
    void levelsApplyInProductAndVersionOrderAndCountWhatChanged() {
        ProductEntity second = ProductEntity.builder().id(2L).stockQuantity(0).stockLedgerVersion(0L).build();
        when(productRepository.findByIdForUpdate(2L)).thenReturn(Optional.of(second));
        when(productRepository.findByIdForUpdate(3L)).thenReturn(Optional.empty());

        StockLevelsResultDto result = projection.applyLevels(List.of(
                new StockLevelDto(2L, 4, 2L),
                new StockLevelDto(3L, 1, 1L),
                new StockLevelDto(1L, 6, 12L),
                new StockLevelDto(2L, 9, 1L),
                new StockLevelDto(1L, 2, 11L)));

        assertThat(result).isEqualTo(new StockLevelsResultDto(4, 1));
        assertThat(product.getStockQuantity()).as("version 12 after 11").isEqualTo(6);
        assertThat(second.getStockQuantity()).as("version 2 after 1").isEqualTo(4);
        assertThat(second.getStockLedgerVersion()).isEqualTo(2L);
        InOrder order = inOrder(productRepository);
        order.verify(productRepository, org.mockito.Mockito.times(2)).findByIdForUpdate(1L);
        order.verify(productRepository, org.mockito.Mockito.times(2)).findByIdForUpdate(2L);
        order.verify(productRepository).findByIdForUpdate(3L);
        verify(eventPublisher).publishEvent(new StockReplenishedEvent(2L));
    }

    @Test
    void noLevelsChangeNothing() {
        assertThat(projection.applyLevels(List.of())).isEqualTo(new StockLevelsResultDto(0, 0));
        verifyNoInteractions(productRepository, eventPublisher);
    }
}
