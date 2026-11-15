package com.awesome.testing.service;

import com.awesome.testing.controller.exception.CustomException;
import com.awesome.testing.dto.order.AddressDto;
import com.awesome.testing.dto.stock.ReservationDto;
import com.awesome.testing.dto.stock.ReservedLineDto;
import com.awesome.testing.dto.stock.StockLineDto;
import com.awesome.testing.entity.AddressEntity;
import com.awesome.testing.entity.CartItemEntity;
import com.awesome.testing.entity.OrderEntity;
import com.awesome.testing.entity.OrderIdempotencyKeyEntity;
import com.awesome.testing.entity.ProductEntity;
import com.awesome.testing.dto.order.OrderStatus;
import com.awesome.testing.event.CommerceEvents;
import com.awesome.testing.repository.CartItemRepository;
import com.awesome.testing.repository.OrderIdempotencyKeyRepository;
import com.awesome.testing.repository.OrderRepository;
import com.awesome.testing.repository.ProductRepository;
import com.awesome.testing.service.checkout.CheckoutContributors;
import com.awesome.testing.service.coupon.CouponService;
import com.awesome.testing.service.reservation.OrderReservations;
import com.awesome.testing.service.stock.StockLedgerClient;
import com.awesome.testing.service.stock.StockLedgerRejection;
import com.awesome.testing.service.stock.StockProjection;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * The checkout saga (contract section 4.1): read transaction, reserve with no transaction, write
 * transaction, confirm; release when the write fails.
 */
@ExtendWith(MockitoExtension.class)
class CheckoutSagaTest {

    private static final String USER = "alice";

    @Mock private OrderRepository orderRepository;
    @Mock private CartItemRepository cartItemRepository;
    @Mock private ProductRepository productRepository;
    @Mock private InventoryService inventoryService;
    @Mock private CouponService couponService;
    @Mock private OrderIdempotencyKeyRepository idempotencyKeyRepository;
    @Mock private CommerceEvents commerceEvents;
    @Mock private StockLedgerClient stockLedger;
    @Mock private StockProjection stockProjection;
    @Mock private com.awesome.testing.service.stock.StockCommandDispatcher stockCommandDispatcher;
    @Mock private com.awesome.testing.repository.stock.StockReservationClaimRepository reservationClaims;
    @Mock private PlatformTransactionManager transactionManager;

    private OrderService service;
    private ProductEntity phone;
    private ProductEntity cable;
    private AddressDto address;

    @BeforeEach
    void setUp() {
        service = new OrderService(orderRepository, cartItemRepository, productRepository, inventoryService,
                couponService, idempotencyKeyRepository, Clock.systemUTC(), CheckoutContributors.none(),
                commerceEvents, new OrderReservations(true, Duration.ofMinutes(30)), stockLedger, stockProjection,
                stockCommandDispatcher,
                reservationClaims, transactionManager);
        phone = ProductEntity.builder().id(2L).name("Phone").price(new BigDecimal("40.00")).stockQuantity(10).build();
        cable = ProductEntity.builder().id(1L).name("Cable").price(new BigDecimal("5.00")).stockQuantity(10).build();
        address = AddressDto.builder().street("Main 1").city("Warsaw").state("MZ").zipCode("00-001").country("PL").build();
        List<CartItemEntity> cart = List.of(item(phone, 1), item(cable, 2), item(phone, 2));
        lenient().when(cartItemRepository.findByUsername(USER)).thenReturn(cart);
        lenient().when(cartItemRepository.findByUsernameForUpdate(USER)).thenReturn(cart);
        lenient().when(productRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(cable));
        lenient().when(productRepository.findByIdForUpdate(2L)).thenReturn(Optional.of(phone));
        lenient().when(couponService.quoteForCheckout(any(), any())).thenReturn(Optional.empty());
        lenient().when(orderRepository.save(any(OrderEntity.class))).thenAnswer(invocation -> {
            OrderEntity order = invocation.getArgument(0);
            order.setId(77L);
            return order;
        });
    }

    private static boolean readOnly(TransactionDefinition definition) {
        return definition != null && definition.isReadOnly();
    }

    private static boolean writable(TransactionDefinition definition) {
        return definition != null && !definition.isReadOnly();
    }

    private static CartItemEntity item(ProductEntity product, int quantity) {
        return CartItemEntity.builder().username(USER).product(product).quantity(quantity).price(product.getPrice()).build();
    }

    private void reserveSucceeds() {
        when(stockLedger.reserve(any(), eq(USER), any())).thenAnswer(invocation -> new ReservationDto(
                invocation.getArgument(0), List.of(new ReservedLineDto(2L, 7, 12L), new ReservedLineDto(1L, 8, 5L))));
    }

    @Test
    void readThenReserveWithoutATransactionThenWriteThenConfirm() {
        TransactionStatus read = mock(TransactionStatus.class);
        TransactionStatus write = mock(TransactionStatus.class);
        when(transactionManager.getTransaction(argThat(CheckoutSagaTest::readOnly))).thenReturn(read);
        when(transactionManager.getTransaction(argThat(CheckoutSagaTest::writable))).thenReturn(write);
        reserveSucceeds();

        var created = service.createOrder(USER, address, null);

        assertThat(created.replayed()).isFalse();
        ArgumentCaptor<UUID> reservation = ArgumentCaptor.forClass(UUID.class);
        InOrder saga = inOrder(transactionManager, cartItemRepository, inventoryService, stockLedger, orderRepository,
                stockProjection);
        saga.verify(transactionManager).getTransaction(argThat(CheckoutSagaTest::readOnly));
        saga.verify(cartItemRepository).findByUsername(USER);
        saga.verify(inventoryService).checkAvailable(cable, 2);
        saga.verify(inventoryService).checkAvailable(phone, 3);
        saga.verify(transactionManager).commit(read);
        saga.verify(stockLedger).reserve(reservation.capture(), eq(USER),
                eq(List.of(new StockLineDto(1L, 2), new StockLineDto(2L, 3))));
        saga.verify(transactionManager).getTransaction(argThat(CheckoutSagaTest::writable));
        saga.verify(cartItemRepository).findByUsernameForUpdate(USER);
        saga.verify(orderRepository).save(argThat(order -> reservation.getValue().equals(order.getStockReservationId())));
        saga.verify(stockProjection).apply(cable, 8, 5L);
        saga.verify(stockProjection).apply(phone, 7, 12L);
        saga.verify(transactionManager).commit(write);
        saga.verify(stockLedger).confirm(reservation.getValue(), 77L);
        verify(stockLedger, never()).release(any());
    }

    @Test
    void theOrderTransactionClaimsTheReservationBeforeItTouchesTheCart() {
        TransactionStatus write = mock(TransactionStatus.class);
        lenient().when(transactionManager.getTransaction(argThat(CheckoutSagaTest::readOnly)))
                .thenReturn(mock(TransactionStatus.class));
        when(transactionManager.getTransaction(argThat(CheckoutSagaTest::writable))).thenReturn(write);
        reserveSucceeds();

        service.createOrder(USER, address, null);

        ArgumentCaptor<UUID> reservation = ArgumentCaptor.forClass(UUID.class);
        InOrder saga = inOrder(stockLedger, transactionManager, reservationClaims, cartItemRepository, orderRepository);
        saga.verify(stockLedger).reserve(reservation.capture(), eq(USER), any());
        saga.verify(transactionManager).getTransaction(argThat(CheckoutSagaTest::writable));
        saga.verify(reservationClaims).insert(eq(reservation.getValue()), any());
        saga.verify(reservationClaims).deleteClaim(reservation.getValue());
        saga.verify(cartItemRepository).findByUsernameForUpdate(USER);
        saga.verify(orderRepository).save(any(OrderEntity.class));
        saga.verify(transactionManager).commit(write);
    }

    @Test
    void aReservationTheReaperAlreadyGaveUpOnStoresNoOrderAndAnswersConflict() {
        reserveSucceeds();
        doThrow(new org.springframework.dao.DataIntegrityViolationException("duplicate key"))
                .when(reservationClaims).insert(any(), any());

        assertThatThrownBy(() -> service.createOrder(USER, address, null))
                .isInstanceOfSatisfying(CustomException.class, error -> {
                    assertThat(error.getHttpStatus()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(error.getMessage()).isEqualTo("Stock reservation expired during checkout; please try again");
                });
        verify(cartItemRepository, never()).findByUsernameForUpdate(any());
        verify(orderRepository, never()).save(any());
        verify(reservationClaims, never()).deleteClaim(any());
        verify(stockLedger).release(any());
        verify(stockLedger, never()).confirm(any(), anyLong());
    }

    @Test
    void reservedLinesWithoutALevelLeaveTheProjectionAlone() {
        when(stockLedger.reserve(any(), eq(USER), any())).thenAnswer(invocation -> new ReservationDto(
                invocation.getArgument(0), List.of(new ReservedLineDto(2L, null, 3L), new ReservedLineDto(1L, 1, null),
                        new ReservedLineDto(null, 1, 1L), new ReservedLineDto(9L, 1, 1L))));

        service.createOrder(USER, address, null);

        verifyNoInteractions(stockProjection);
        verify(productRepository).findByIdForUpdate(9L);
    }

    @Test
    void aReservationWithoutLinesStillPlacesTheOrder() {
        when(stockLedger.reserve(any(), eq(USER), any()))
                .thenAnswer(invocation -> new ReservationDto(invocation.getArgument(0), null));

        assertThat(service.createOrder(USER, address, null).order().getId()).isEqualTo(77L);
        verifyNoInteractions(stockProjection);
    }

    @Test
    void aShortProductAnswersTodaysConflictWithoutWritingAnything() {
        when(stockLedger.reserve(any(), any(), any())).thenThrow(new StockLedgerRejection(409, "whatever", 2L));

        assertThatThrownBy(() -> service.createOrder(USER, address, null))
                .isInstanceOfSatisfying(CustomException.class, error -> {
                    assertThat(error.getHttpStatus()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(error.getMessage()).isEqualTo("Insufficient stock for product 2");
                });
        verify(cartItemRepository, never()).findByUsernameForUpdate(any());
        verify(orderRepository, never()).save(any());
        verify(stockLedger, never()).release(any());
    }

    @Test
    void aConflictWithoutAProductNamesTheFirstLine() {
        when(stockLedger.reserve(any(), any(), any())).thenThrow(new StockLedgerRejection(409, null, null));

        assertThatThrownBy(() -> service.createOrder(USER, address, null))
                .hasMessage("Insufficient stock for product 1");
    }

    @Test
    void anUnknownProductIsNotFound() {
        when(stockLedger.reserve(any(), any(), any())).thenThrow(new StockLedgerRejection(404, "Product 2 not found", 2L));

        assertThatThrownBy(() -> service.createOrder(USER, address, null))
                .isInstanceOfSatisfying(CustomException.class, error -> {
                    assertThat(error.getHttpStatus()).isEqualTo(HttpStatus.NOT_FOUND);
                    assertThat(error.getMessage()).isEqualTo("Product not found");
                });
    }

    @Test
    void anUnavailableWarehouseAnswers503WithoutWritingAnything() {
        when(stockLedger.reserve(any(), any(), any()))
                .thenThrow(new CustomException(StockLedgerClient.UNAVAILABLE, HttpStatus.SERVICE_UNAVAILABLE));

        assertThatThrownBy(() -> service.createOrder(USER, address, null))
                .isInstanceOfSatisfying(CustomException.class,
                        error -> assertThat(error.getHttpStatus()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE));
        verify(orderRepository, never()).save(any());
        verify(cartItemRepository, never()).deleteByUsername(any());
    }

    @Test
    void aFailedWriteReleasesTheReservationAndRethrowsTheOriginalErrorEvenWhenTheReleaseFails() {
        reserveSucceeds();
        RuntimeException original = new IllegalStateException("database gone");
        when(orderRepository.save(any(OrderEntity.class))).thenThrow(original);
        doThrow(new CustomException(StockLedgerClient.UNAVAILABLE, HttpStatus.SERVICE_UNAVAILABLE))
                .when(stockLedger).release(any());

        assertThatThrownBy(() -> service.createOrder(USER, address, null)).isSameAs(original);

        ArgumentCaptor<UUID> reserved = ArgumentCaptor.forClass(UUID.class);
        verify(stockLedger).reserve(reserved.capture(), any(), any());
        verify(stockLedger).release(reserved.getValue());
        verify(transactionManager).rollback(any());
        verify(stockLedger, never()).confirm(any(), anyLong());
    }

    @Test
    void aFailedConfirmIsOnlyLoggedBecauseTheOrderExists() {
        reserveSucceeds();
        when(stockLedger.confirm(any(), anyLong()))
                .thenThrow(new CustomException(StockLedgerClient.UNAVAILABLE, HttpStatus.SERVICE_UNAVAILABLE));

        var created = service.createOrder(USER, address, null);

        assertThat(created.order().getId()).isEqualTo(77L);
        verify(stockLedger, never()).release(any());
    }

    @Test
    void aCartThatChangedBeforeTheWriteReleasesAndStartsAgain() {
        reserveSucceeds();
        List<CartItemEntity> before = List.of(item(phone, 1));
        List<CartItemEntity> after = List.of(item(phone, 2));
        when(cartItemRepository.findByUsername(USER)).thenReturn(before, after);
        when(cartItemRepository.findByUsernameForUpdate(USER)).thenReturn(after);

        var created = service.createOrder(USER, address, null);

        assertThat(created.order().getId()).isEqualTo(77L);
        ArgumentCaptor<UUID> reservations = ArgumentCaptor.forClass(UUID.class);
        verify(stockLedger, times(2)).reserve(reservations.capture(), eq(USER), any());
        verify(stockLedger).release(reservations.getAllValues().getFirst());
        verify(stockLedger).confirm(reservations.getAllValues().get(1), 77L);
        assertThat(reservations.getAllValues().getFirst()).isNotEqualTo(reservations.getAllValues().get(1));
    }

    @Test
    void aCartThatKeepsChangingGivesUpAfterThreeAttempts() {
        reserveSucceeds();
        when(cartItemRepository.findByUsername(USER)).thenReturn(List.of(item(phone, 1)));
        when(cartItemRepository.findByUsernameForUpdate(USER)).thenReturn(List.of(item(phone, 2)));

        assertThatThrownBy(() -> service.createOrder(USER, address, null))
                .isInstanceOfSatisfying(CustomException.class, error -> {
                    assertThat(error.getHttpStatus()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(error.getMessage()).isEqualTo("Cart changed during checkout; please try again");
                });
        verify(stockLedger, times(OrderService.MAX_CHECKOUT_ATTEMPTS)).reserve(any(), any(), any());
        verify(stockLedger, times(OrderService.MAX_CHECKOUT_ATTEMPTS)).release(any());
        verify(orderRepository, never()).save(any());
    }

    @Test
    void aCartEmptiedByAConcurrentCheckoutReleasesAndAnswersCartIsEmpty() {
        reserveSucceeds();
        when(cartItemRepository.findByUsernameForUpdate(USER)).thenReturn(List.of());

        assertThatThrownBy(() -> service.createOrder(USER, address, null)).hasMessage("Cart is empty");
        verify(stockLedger).release(any());
    }

    @Test
    void aConcurrentDuplicateWithTheSameKeyReplaysTheFirstOrderAndReleasesItsOwnReservation() {
        reserveSucceeds();
        OrderEntity first = OrderEntity.builder().id(5L).username(USER).status(OrderStatus.PENDING)
                .shippingAddress(AddressEntity.from(address)).totalAmount(BigDecimal.TEN).build();
        when(idempotencyKeyRepository.findByUsernameAndKey(USER, "checkout-key-1")).thenReturn(Optional.empty(),
                Optional.of(OrderIdempotencyKeyEntity.builder().order(first)
                        .requestHash(OrderService.requestHash(address)).build()));

        var replay = service.createOrder(USER, address, "checkout-key-1");

        assertThat(replay.replayed()).isTrue();
        assertThat(replay.order().getId()).isEqualTo(5L);
        verify(stockLedger).release(any());
        verify(stockLedger, never()).confirm(any(), anyLong());
        verify(orderRepository, never()).save(any());
    }
}
