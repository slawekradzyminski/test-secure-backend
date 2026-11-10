package com.awesome.testing.service.loyalty;

import com.awesome.testing.controller.exception.CustomException;
import com.awesome.testing.dto.email.EmailTemplate;
import com.awesome.testing.dto.loyalty.LoyaltyAccountDto;
import com.awesome.testing.dto.loyalty.LoyaltyTransactionDto;
import com.awesome.testing.dto.order.OrderStatus;
import com.awesome.testing.entity.OrderEntity;
import com.awesome.testing.entity.loyalty.LoyaltyAccountEntity;
import com.awesome.testing.entity.loyalty.LoyaltyTransactionEntity;
import com.awesome.testing.entity.loyalty.LoyaltyTransactionType;
import com.awesome.testing.repository.OrderRepository;
import com.awesome.testing.repository.loyalty.LoyaltyAccountRepository;
import com.awesome.testing.repository.loyalty.LoyaltyTransactionRepository;
import com.awesome.testing.service.notification.CustomerNotificationRequestedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Loyalty points: earning on paid orders, redemption at checkout, restores on
 * cancellation and refund, and revocation of earned points. Balance changes
 * lock the order (where there is one) and then the account, and write one
 * ledger row whose reference makes the change idempotent.
 */
@Service
@RequiredArgsConstructor
public class LoyaltyService {

    static final String CHECKOUT_FAILURE = "Applied loyalty points are no longer available: ";

    private final LoyaltyAccountRepository accountRepository;
    private final LoyaltyTransactionRepository transactionRepository;
    private final OrderRepository orderRepository;
    private final LoyaltyAccountCreator accountCreator;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional(readOnly = true)
    public LoyaltyAccountDto account(String username) {
        Optional<LoyaltyAccountEntity> account = accountRepository.findById(username);
        long balance = account.map(LoyaltyAccountEntity::getBalance).orElse(0L);
        long lifetime = account.map(LoyaltyAccountEntity::getLifetimeEarned).orElse(0L);
        return LoyaltyAccountDto.builder()
                .balance(balance)
                .lifetimeEarned(lifetime)
                .tier(LoyaltyPoints.tierFor(lifetime))
                .valueOfBalance(LoyaltyPoints.valueOf(balance))
                .build();
    }

    @Transactional(readOnly = true)
    public Page<LoyaltyTransactionDto> transactions(String username, Pageable pageable) {
        return transactionRepository.findByUsername(username, pageable).map(LoyaltyTransactionDto::from);
    }

    @Transactional(readOnly = true)
    public long balanceOf(String username) {
        return accountRepository.findById(username).map(LoyaltyAccountEntity::getBalance).orElse(0L);
    }

    /**
     * Credits 1 point per full 1.00 of the order total once per order. Runs in
     * its own transaction after the payment committed; a cancelled order earns nothing.
     *
     * @return points credited by this call, zero when already credited or not earned
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public long earnForOrder(Long orderId) {
        OrderEntity order = orderRepository.findByIdForUpdate(orderId).orElse(null);
        if (order == null || order.getStatus() == OrderStatus.CANCELLED || order.getStatus() == OrderStatus.PENDING) {
            return 0;
        }
        // Points pay no points: the part of the order paid with points earns nothing.
        BigDecimal pointsAmount = order.getPointsAmount() == null ? BigDecimal.ZERO : order.getPointsAmount();
        long points = LoyaltyPoints.earnedFor(order.getTotalAmount().subtract(pointsAmount));
        if (points == 0) {
            return 0;
        }
        String username = order.getUsername();
        String reference = orderReference(orderId);
        accountCreator.ensureExists(username);
        LoyaltyAccountEntity account = accountRepository.findByUsernameForUpdate(username).orElseThrow();
        if (transactionRepository.findByUsernameAndTypeAndReference(
                username, LoyaltyTransactionType.EARNED, reference).isPresent()) {
            return 0;
        }
        account.setBalance(account.getBalance() + points);
        account.setLifetimeEarned(account.getLifetimeEarned() + points);
        accountRepository.save(account);
        record(account, LoyaltyTransactionType.EARNED, points, orderId, reference);
        eventPublisher.publishEvent(new CustomerNotificationRequestedEvent(
                UUID.nameUUIDFromBytes(("loyalty-earned:" + reference).getBytes(StandardCharsets.UTF_8)),
                username, EmailTemplate.LOYALTY_POINTS_EARNED, Map.of(
                        "points", String.valueOf(points),
                        "balance", String.valueOf(account.getBalance()),
                        "orderId", String.valueOf(orderId))));
        return points;
    }

    /** Locks the account and takes {@code points} for checkout; 409 when the balance is too small. */
    @Transactional
    public void debitForCheckout(String username, long points) {
        LoyaltyAccountEntity account = accountRepository.findByUsernameForUpdate(username)
                .filter(found -> found.getBalance() >= points)
                .orElseThrow(() -> new CustomException(
                        CHECKOUT_FAILURE + "Insufficient loyalty points", HttpStatus.CONFLICT));
        account.setBalance(account.getBalance() - points);
        accountRepository.save(account);
    }

    /** Writes the REDEEMED ledger row of a saved order; repeating it for the same order is a no-op. */
    @Transactional
    public void recordRedemption(OrderEntity order) {
        String reference = orderReference(order.getId());
        if (transactionRepository.findByUsernameAndTypeAndReference(
                order.getUsername(), LoyaltyTransactionType.REDEEMED, reference).isPresent()) {
            return;
        }
        LoyaltyAccountEntity account = accountRepository.findById(order.getUsername()).orElseThrow();
        record(account, LoyaltyTransactionType.REDEEMED, -order.getPointsRedeemed(), order.getId(), reference);
    }

    /**
     * Returns up to {@code maxPoints} of the points {@code order} redeemed,
     * never more than were redeemed minus earlier restores. Repeating a
     * reference returns the points restored the first time without changing anything.
     *
     * @return the points put back on the account
     */
    @Transactional
    public long restoreRedeemed(OrderEntity order, long maxPoints, String reference) {
        if (order.getPointsRedeemed() == null || maxPoints <= 0) {
            return 0;
        }
        Optional<LoyaltyAccountEntity> locked = accountRepository.findByUsernameForUpdate(order.getUsername());
        if (locked.isEmpty()) {
            return 0;
        }
        Optional<LoyaltyTransactionEntity> previous = transactionRepository.findByUsernameAndTypeAndReference(
                order.getUsername(), LoyaltyTransactionType.RESTORED, reference);
        if (previous.isPresent()) {
            return previous.get().getPoints();
        }
        long restoredBefore = transactionRepository.sumForOrder(
                order.getUsername(), order.getId(), LoyaltyTransactionType.RESTORED);
        long points = Math.min(order.getPointsRedeemed() - restoredBefore, maxPoints);
        if (points <= 0) {
            return 0;
        }
        LoyaltyAccountEntity account = locked.get();
        account.setBalance(account.getBalance() + points);
        accountRepository.save(account);
        record(account, LoyaltyTransactionType.RESTORED, points, order.getId(), reference);
        return points;
    }

    /** Points {@code order} redeemed and has not had back yet; zero without points. */
    @Transactional(readOnly = true)
    public long restorablePoints(OrderEntity order) {
        if (order.getPointsRedeemed() == null) {
            return 0;
        }
        return Math.max(0, order.getPointsRedeemed() - transactionRepository.sumForOrder(
                order.getUsername(), order.getId(), LoyaltyTransactionType.RESTORED));
    }

    /**
     * Takes back the share of the points the order earned that matches the
     * share of its total refunded so far (all of them for a cancellation). The
     * balance never drops below zero; what cannot be taken now is not owed.
     *
     * @param refundedSoFar refunds of the order including this one; the total for a cancellation
     * @return the points taken back by this call
     */
    @Transactional
    public long revokeEarned(OrderEntity order, String reference, BigDecimal refundedSoFar) {
        String username = order.getUsername();
        long earned = transactionRepository.sumForOrder(username, order.getId(), LoyaltyTransactionType.EARNED);
        if (earned <= 0) {
            return 0;
        }
        LoyaltyAccountEntity account = accountRepository.findByUsernameForUpdate(username).orElse(null);
        if (account == null) {
            return 0;
        }
        Optional<LoyaltyTransactionEntity> previous = transactionRepository.findByUsernameAndTypeAndReference(
                username, LoyaltyTransactionType.REVOKED, reference);
        if (previous.isPresent()) {
            return -previous.get().getPoints();
        }
        long revokedBefore = -transactionRepository.sumForOrder(username, order.getId(), LoyaltyTransactionType.REVOKED);
        long due = LoyaltyPoints.revocableFor(earned, order.getTotalAmount(), refundedSoFar) - revokedBefore;
        long points = Math.min(due, account.getBalance());
        if (points <= 0) {
            return 0;
        }
        account.setBalance(account.getBalance() - points);
        accountRepository.save(account);
        record(account, LoyaltyTransactionType.REVOKED, -points, order.getId(), reference);
        return points;
    }

    @Transactional
    public void forget(String username) {
        transactionRepository.deleteByUsername(username);
        accountRepository.deleteByUsername(username);
    }

    static String orderReference(Long orderId) {
        return "order:" + orderId;
    }

    private void record(LoyaltyAccountEntity account, LoyaltyTransactionType type, long points, Long orderId,
                        String reference) {
        transactionRepository.save(LoyaltyTransactionEntity.builder()
                .username(account.getUsername())
                .type(type)
                .points(points)
                .balanceAfter(account.getBalance())
                .orderId(orderId)
                .reference(reference)
                .build());
    }
}
