package com.awesome.testing.service.loyalty;

import com.awesome.testing.controller.exception.CustomException;
import com.awesome.testing.dto.email.EmailTemplate;
import com.awesome.testing.dto.loyalty.LoyaltyAccountDto;
import com.awesome.testing.dto.loyalty.LoyaltyTier;
import com.awesome.testing.dto.order.OrderStatus;
import com.awesome.testing.entity.OrderEntity;
import com.awesome.testing.entity.loyalty.LoyaltyAccountEntity;
import com.awesome.testing.entity.loyalty.LoyaltyTransactionEntity;
import com.awesome.testing.entity.loyalty.LoyaltyTransactionType;
import com.awesome.testing.repository.OrderRepository;
import com.awesome.testing.repository.loyalty.LoyaltyAccountRepository;
import com.awesome.testing.repository.loyalty.LoyaltyTransactionRepository;
import com.awesome.testing.service.notification.CustomerNotificationRequestedEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LoyaltyServiceTest {

    private static final String USER = "buyer";

    @Mock
    private LoyaltyAccountRepository accountRepository;
    @Mock
    private LoyaltyTransactionRepository transactionRepository;
    @Mock
    private OrderRepository orderRepository;
    @Mock
    private LoyaltyAccountCreator accountCreator;
    @Mock
    private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private LoyaltyService service;

    @Test
    void accountShowsZeroForUsersWithoutPoints() {
        when(accountRepository.findById(USER)).thenReturn(Optional.empty());

        assertThat(service.account(USER)).isEqualTo(new LoyaltyAccountDto(0, 0, LoyaltyTier.BRONZE, new BigDecimal("0.00")));
        assertThat(service.balanceOf(USER)).isZero();
    }

    @Test
    void accountShowsBalanceLifetimeTierAndValue() {
        when(accountRepository.findById(USER)).thenReturn(Optional.of(account(1_250, 5_000)));

        LoyaltyAccountDto dto = service.account(USER);

        assertThat(dto.getBalance()).isEqualTo(1_250);
        assertThat(dto.getLifetimeEarned()).isEqualTo(5_000);
        assertThat(dto.getTier()).isEqualTo(LoyaltyTier.GOLD);
        assertThat(dto.getValueOfBalance()).hasToString("12.50");
        assertThat(service.balanceOf(USER)).isEqualTo(1_250);
    }

    @Test
    void transactionsAreMappedFromTheLedger() {
        PageRequest page = PageRequest.of(0, 5);
        when(transactionRepository.findByUsername(USER, page)).thenReturn(new PageImpl<>(List.of(
                LoyaltyTransactionEntity.builder().id(3L).username(USER).type(LoyaltyTransactionType.EARNED)
                        .points(12L).balanceAfter(12L).orderId(9L).build()), page, 1));

        Page<?> result = service.transactions(USER, page);

        assertThat(result.getContent()).singleElement().satisfies(entry ->
                assertThat(entry).hasFieldOrPropertyWithValue("points", 12L).hasFieldOrPropertyWithValue("orderId", 9L));
    }

    @Test
    void earningCreditsFloorOfTheTotalOnceAndEmails() {
        LoyaltyAccountEntity account = account(10, 10);
        when(orderRepository.findByIdForUpdate(42L)).thenReturn(Optional.of(order(OrderStatus.PAID, "123.99")));
        when(accountRepository.findByUsernameForUpdate(USER)).thenReturn(Optional.of(account));
        when(transactionRepository.findByUsernameAndTypeAndReference(USER, LoyaltyTransactionType.EARNED, "order:42"))
                .thenReturn(Optional.empty());

        assertThat(service.earnForOrder(42L)).isEqualTo(123);

        verify(accountCreator).ensureExists(USER);
        assertThat(account.getBalance()).isEqualTo(133);
        assertThat(account.getLifetimeEarned()).isEqualTo(133);
        verify(accountRepository).save(account);
        LoyaltyTransactionEntity ledger = savedTransaction();
        assertThat(ledger.getType()).isEqualTo(LoyaltyTransactionType.EARNED);
        assertThat(ledger.getPoints()).isEqualTo(123);
        assertThat(ledger.getBalanceAfter()).isEqualTo(133);
        assertThat(ledger.getOrderId()).isEqualTo(42L);
        assertThat(ledger.getReference()).isEqualTo("order:42");
        ArgumentCaptor<CustomerNotificationRequestedEvent> email = ArgumentCaptor.forClass(CustomerNotificationRequestedEvent.class);
        verify(eventPublisher).publishEvent(email.capture());
        assertThat(email.getValue().template()).isEqualTo(EmailTemplate.LOYALTY_POINTS_EARNED);
        assertThat(email.getValue().username()).isEqualTo(USER);
        assertThat(email.getValue().properties()).isEqualTo(Map.of("points", "123", "balance", "133", "orderId", "42"));
    }

    @Test
    void earningTwiceForAnOrderCreditsNothing() {
        LoyaltyAccountEntity account = account(10, 10);
        when(orderRepository.findByIdForUpdate(42L)).thenReturn(Optional.of(order(OrderStatus.DELIVERED, "50.00")));
        when(accountRepository.findByUsernameForUpdate(USER)).thenReturn(Optional.of(account));
        when(transactionRepository.findByUsernameAndTypeAndReference(USER, LoyaltyTransactionType.EARNED, "order:42"))
                .thenReturn(Optional.of(new LoyaltyTransactionEntity()));

        assertThat(service.earnForOrder(42L)).isZero();
        assertThat(account.getBalance()).isEqualTo(10);
        verify(transactionRepository, never()).save(any());
        verifyNoInteractions(eventPublisher);
    }

    @ParameterizedTest
    @EnumSource(value = OrderStatus.class, names = {"PENDING", "CANCELLED"})
    void unpaidOrdersEarnNothing(OrderStatus status) {
        when(orderRepository.findByIdForUpdate(42L)).thenReturn(Optional.of(order(status, "50.00")));

        assertThat(service.earnForOrder(42L)).isZero();
        verifyNoInteractions(accountCreator, eventPublisher);
    }

    @Test
    void ordersBelowOneUnitOrMissingEarnNothing() {
        when(orderRepository.findByIdForUpdate(42L)).thenReturn(Optional.of(order(OrderStatus.PAID, "0.99")));
        when(orderRepository.findByIdForUpdate(43L)).thenReturn(Optional.empty());

        assertThat(service.earnForOrder(42L)).isZero();
        assertThat(service.earnForOrder(43L)).isZero();
        verifyNoInteractions(accountCreator, eventPublisher);
    }

    @Test
    void checkoutDebitNeedsEnoughPoints() {
        LoyaltyAccountEntity account = account(500, 500);
        when(accountRepository.findByUsernameForUpdate(USER)).thenReturn(Optional.of(account));

        service.debitForCheckout(USER, 500);

        assertThat(account.getBalance()).isZero();
        verify(accountRepository).save(account);
        assertThatThrownBy(() -> service.debitForCheckout(USER, 1))
                .isInstanceOf(CustomException.class)
                .hasMessage("Applied loyalty points are no longer available: Insufficient loyalty points")
                .extracting("httpStatus").isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void checkoutDebitWithoutAnAccountIsAConflict() {
        when(accountRepository.findByUsernameForUpdate(USER)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.debitForCheckout(USER, 100))
                .isInstanceOf(CustomException.class)
                .extracting("httpStatus").isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void recordsTheRedemptionOfAnOrder() {
        OrderEntity order = order(OrderStatus.PENDING, "20.00");
        order.setPointsRedeemed(700L);
        when(transactionRepository.findByUsernameAndTypeAndReference(USER, LoyaltyTransactionType.REDEEMED, "order:42"))
                .thenReturn(Optional.empty());
        when(accountRepository.findById(USER)).thenReturn(Optional.of(account(300, 1_000)));

        service.recordRedemption(order);

        LoyaltyTransactionEntity ledger = savedTransaction();
        assertThat(ledger.getType()).isEqualTo(LoyaltyTransactionType.REDEEMED);
        assertThat(ledger.getPoints()).isEqualTo(-700);
        assertThat(ledger.getBalanceAfter()).isEqualTo(300);
        assertThat(ledger.getOrderId()).isEqualTo(42L);
    }

    @Test
    void recordingARedemptionAgainWritesNothing() {
        OrderEntity order = order(OrderStatus.PENDING, "20.00");
        order.setPointsRedeemed(700L);
        when(transactionRepository.findByUsernameAndTypeAndReference(USER, LoyaltyTransactionType.REDEEMED, "order:42"))
                .thenReturn(Optional.of(new LoyaltyTransactionEntity()));

        service.recordRedemption(order);

        verify(transactionRepository, never()).save(any());
        verify(accountRepository, never()).findById(USER);
    }

    @Test
    void restoringZeroPointsLocksNothing() {
        OrderEntity order = order(OrderStatus.DELIVERED, "10.00");
        order.setPointsRedeemed(100L);

        assertThat(service.restoreRedeemed(order, 0, "r")).isZero();

        verifyNoInteractions(accountRepository, transactionRepository);
    }

    @Test
    void restoresAtMostWhatTheOrderRedeemedMinusEarlierRestores() {
        OrderEntity order = order(OrderStatus.DELIVERED, "100.00");
        order.setPointsRedeemed(2_000L);
        LoyaltyAccountEntity account = account(40, 100);
        when(accountRepository.findByUsernameForUpdate(USER)).thenReturn(Optional.of(account));
        when(transactionRepository.findByUsernameAndTypeAndReference(USER, LoyaltyTransactionType.RESTORED, "refund:2"))
                .thenReturn(Optional.empty());
        when(transactionRepository.sumForOrder(USER, 42L, LoyaltyTransactionType.RESTORED)).thenReturn(500L);

        assertThat(service.restoreRedeemed(order, 5_000, "refund:2")).isEqualTo(1_500);

        assertThat(account.getBalance()).isEqualTo(1_540);
        LoyaltyTransactionEntity ledger = savedTransaction();
        assertThat(ledger.getType()).isEqualTo(LoyaltyTransactionType.RESTORED);
        assertThat(ledger.getPoints()).isEqualTo(1_500);
        assertThat(ledger.getBalanceAfter()).isEqualTo(1_540);
        assertThat(ledger.getReference()).isEqualTo("refund:2");
    }

    @Test
    void restoreIsCappedByTheRequestedPoints() {
        OrderEntity order = order(OrderStatus.DELIVERED, "100.00");
        order.setPointsRedeemed(2_000L);
        LoyaltyAccountEntity account = account(0, 0);
        when(accountRepository.findByUsernameForUpdate(USER)).thenReturn(Optional.of(account));
        when(transactionRepository.findByUsernameAndTypeAndReference(USER, LoyaltyTransactionType.RESTORED, "r"))
                .thenReturn(Optional.empty());
        when(transactionRepository.sumForOrder(USER, 42L, LoyaltyTransactionType.RESTORED)).thenReturn(0L);

        assertThat(service.restoreRedeemed(order, 250, "r")).isEqualTo(250);
        assertThat(account.getBalance()).isEqualTo(250);
    }

    @Test
    void repeatedOrEmptyRestoresChangeNothing() {
        OrderEntity order = order(OrderStatus.DELIVERED, "100.00");
        order.setPointsRedeemed(2_000L);
        LoyaltyAccountEntity account = account(0, 0);
        when(accountRepository.findByUsernameForUpdate(USER)).thenReturn(Optional.of(account));
        when(transactionRepository.findByUsernameAndTypeAndReference(USER, LoyaltyTransactionType.RESTORED, "again"))
                .thenReturn(Optional.of(LoyaltyTransactionEntity.builder().points(300L).build()));
        when(transactionRepository.findByUsernameAndTypeAndReference(USER, LoyaltyTransactionType.RESTORED, "done"))
                .thenReturn(Optional.empty());
        when(transactionRepository.sumForOrder(USER, 42L, LoyaltyTransactionType.RESTORED)).thenReturn(2_000L);

        assertThat(service.restoreRedeemed(order, 1_000, "again")).isEqualTo(300);
        assertThat(service.restoreRedeemed(order, 1_000, "done")).isZero();
        assertThat(account.getBalance()).isZero();
        verify(transactionRepository, never()).save(any());
    }

    @Test
    void ordersWithoutPointsOrAccountRestoreNothing() {
        OrderEntity withoutPoints = order(OrderStatus.DELIVERED, "10.00");
        OrderEntity withPoints = order(OrderStatus.DELIVERED, "10.00");
        withPoints.setPointsRedeemed(100L);
        when(accountRepository.findByUsernameForUpdate(USER)).thenReturn(Optional.empty());

        assertThat(service.restoreRedeemed(withoutPoints, 100, "r")).isZero();
        assertThat(service.restoreRedeemed(withPoints, 100, "r")).isZero();
        verify(transactionRepository, never()).save(any());
    }

    @Test
    void revokesTheRefundedShareOfEarnedPoints() {
        OrderEntity order = order(OrderStatus.DELIVERED, "100.00");
        LoyaltyAccountEntity account = account(500, 500);
        when(transactionRepository.sumForOrder(USER, 42L, LoyaltyTransactionType.EARNED)).thenReturn(100L);
        when(accountRepository.findByUsernameForUpdate(USER)).thenReturn(Optional.of(account));
        when(transactionRepository.findByUsernameAndTypeAndReference(USER, LoyaltyTransactionType.REVOKED, "refund:2"))
                .thenReturn(Optional.empty());
        when(transactionRepository.sumForOrder(USER, 42L, LoyaltyTransactionType.REVOKED)).thenReturn(-60L);

        assertThat(service.revokeEarned(order, "refund:2", new BigDecimal("90.00"))).isEqualTo(30);

        assertThat(account.getBalance()).isEqualTo(470);
        assertThat(account.getLifetimeEarned()).isEqualTo(500);
        LoyaltyTransactionEntity ledger = savedTransaction();
        assertThat(ledger.getType()).isEqualTo(LoyaltyTransactionType.REVOKED);
        assertThat(ledger.getPoints()).isEqualTo(-30);
        assertThat(ledger.getBalanceAfter()).isEqualTo(470);
        assertThat(ledger.getOrderId()).isEqualTo(42L);
    }

    @Test
    void revocationNeverTakesTheBalanceBelowZero() {
        OrderEntity order = order(OrderStatus.CANCELLED, "100.00");
        LoyaltyAccountEntity account = account(25, 100);
        when(transactionRepository.sumForOrder(USER, 42L, LoyaltyTransactionType.EARNED)).thenReturn(100L);
        when(accountRepository.findByUsernameForUpdate(USER)).thenReturn(Optional.of(account));
        when(transactionRepository.findByUsernameAndTypeAndReference(USER, LoyaltyTransactionType.REVOKED, "cancel"))
                .thenReturn(Optional.empty());
        when(transactionRepository.sumForOrder(USER, 42L, LoyaltyTransactionType.REVOKED)).thenReturn(0L);

        assertThat(service.revokeEarned(order, "cancel", new BigDecimal("100.00"))).isEqualTo(25);
        assertThat(account.getBalance()).isZero();
    }

    @Test
    void repeatedOrSettledRevocationsChangeNothing() {
        OrderEntity order = order(OrderStatus.DELIVERED, "100.00");
        LoyaltyAccountEntity account = account(500, 500);
        when(transactionRepository.sumForOrder(USER, 42L, LoyaltyTransactionType.EARNED)).thenReturn(100L);
        when(accountRepository.findByUsernameForUpdate(USER)).thenReturn(Optional.of(account));
        when(transactionRepository.findByUsernameAndTypeAndReference(USER, LoyaltyTransactionType.REVOKED, "again"))
                .thenReturn(Optional.of(LoyaltyTransactionEntity.builder().points(-40L).build()));
        when(transactionRepository.findByUsernameAndTypeAndReference(USER, LoyaltyTransactionType.REVOKED, "settled"))
                .thenReturn(Optional.empty());
        when(transactionRepository.sumForOrder(USER, 42L, LoyaltyTransactionType.REVOKED)).thenReturn(-100L);

        assertThat(service.revokeEarned(order, "again", new BigDecimal("40.00"))).isEqualTo(40);
        assertThat(service.revokeEarned(order, "settled", new BigDecimal("100.00"))).isZero();
        assertThat(account.getBalance()).isEqualTo(500);
        verify(transactionRepository, never()).save(any());
    }

    @Test
    void ordersThatEarnedNothingOrLostTheAccountRevokeNothing() {
        OrderEntity order = order(OrderStatus.DELIVERED, "100.00");
        when(transactionRepository.sumForOrder(USER, 42L, LoyaltyTransactionType.EARNED)).thenReturn(0L, 50L);
        when(accountRepository.findByUsernameForUpdate(USER)).thenReturn(Optional.empty());

        assertThat(service.revokeEarned(order, "r", new BigDecimal("100.00"))).isZero();
        assertThat(service.revokeEarned(order, "r", new BigDecimal("100.00"))).isZero();
        verify(accountRepository).findByUsernameForUpdate(USER);
    }

    @Test
    void restorablePointsAreRedeemedMinusRestored() {
        OrderEntity order = order(OrderStatus.DELIVERED, "100.00");
        order.setPointsRedeemed(2_000L);
        when(transactionRepository.sumForOrder(USER, 42L, LoyaltyTransactionType.RESTORED)).thenReturn(500L, 2_500L);

        assertThat(service.restorablePoints(order)).isEqualTo(1_500);
        assertThat(service.restorablePoints(order)).isZero();
        assertThat(service.restorablePoints(order(OrderStatus.DELIVERED, "1.00"))).isZero();
    }

    @Test
    void forgettingAUserDeletesTheLedgerAndAccount() {
        service.forget(USER);

        verify(transactionRepository).deleteByUsername(USER);
        verify(accountRepository).deleteByUsername(USER);
    }

    private LoyaltyTransactionEntity savedTransaction() {
        ArgumentCaptor<LoyaltyTransactionEntity> captor = ArgumentCaptor.forClass(LoyaltyTransactionEntity.class);
        verify(transactionRepository).save(captor.capture());
        assertThat(captor.getValue().getUsername()).isEqualTo(USER);
        return captor.getValue();
    }

    private static LoyaltyAccountEntity account(long balance, long lifetime) {
        return LoyaltyAccountEntity.builder().username(USER).balance(balance).lifetimeEarned(lifetime).build();
    }

    private static OrderEntity order(OrderStatus status, String total) {
        return OrderEntity.builder().id(42L).username(USER).status(status).totalAmount(new BigDecimal(total)).build();
    }
}
