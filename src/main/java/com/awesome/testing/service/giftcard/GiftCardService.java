package com.awesome.testing.service.giftcard;

import com.awesome.testing.controller.exception.CustomException;
import com.awesome.testing.dto.email.EmailTemplate;
import com.awesome.testing.dto.giftcard.GiftCardBalanceDto;
import com.awesome.testing.dto.giftcard.GiftCardCreateDto;
import com.awesome.testing.dto.giftcard.GiftCardDto;
import com.awesome.testing.dto.giftcard.GiftCardStatus;
import com.awesome.testing.dto.giftcard.GiftCardTransactionDto;
import com.awesome.testing.entity.OrderEntity;
import com.awesome.testing.entity.giftcard.GiftCardEntity;
import com.awesome.testing.entity.giftcard.GiftCardTransactionEntity;
import com.awesome.testing.entity.giftcard.GiftCardTransactionType;
import com.awesome.testing.repository.UserRepository;
import com.awesome.testing.repository.giftcard.GiftCardRepository;
import com.awesome.testing.repository.giftcard.GiftCardTransactionRepository;
import com.awesome.testing.service.notification.CustomerNotificationRequestedEvent;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Issues, disables and spends gift cards. Every balance change locks the card
 * row and writes one ledger row; restores are idempotent per reference.
 */
@Service
@RequiredArgsConstructor
public class GiftCardService {

    static final int CODE_ATTEMPTS = 5;
    static final String CHECKOUT_FAILURE = "Applied gift card is no longer valid: ";

    private final GiftCardRepository giftCardRepository;
    private final GiftCardTransactionRepository transactionRepository;
    private final UserRepository userRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock commerceClock;
    private final SecureRandom random = new SecureRandom();

    @Transactional
    public GiftCardDto issue(GiftCardCreateDto request, String issuedBy) {
        LocalDateTime now = LocalDateTime.now(commerceClock);
        if (request.getExpiresAt() != null && !request.getExpiresAt().isAfter(now)) {
            throw new CustomException("expiresAt must be in the future", HttpStatus.BAD_REQUEST);
        }
        String recipient = StringUtils.hasText(request.getRecipientUsername())
                ? request.getRecipientUsername().trim() : null;
        if (recipient != null && !userRepository.existsByUsername(recipient)) {
            throw new CustomException("Recipient user not found", HttpStatus.NOT_FOUND);
        }
        BigDecimal amount = request.getAmount().setScale(2, RoundingMode.UNNECESSARY);
        GiftCardEntity card = giftCardRepository.save(GiftCardEntity.builder()
                .code(uniqueCode())
                .initialAmount(amount)
                .balance(amount)
                .expiresAt(request.getExpiresAt())
                .recipientUsername(recipient)
                .note(StringUtils.hasText(request.getNote()) ? request.getNote().trim() : null)
                .createdBy(issuedBy)
                .build());
        record(card, GiftCardTransactionType.ISSUED, amount, null, null, issuedBy);
        if (recipient != null) {
            eventPublisher.publishEvent(new CustomerNotificationRequestedEvent(
                    UUID.nameUUIDFromBytes(("gift-card-issued:" + card.getCode()).getBytes(StandardCharsets.UTF_8)),
                    recipient, EmailTemplate.GIFT_CARD_ISSUED,
                    Map.of("code", card.getCode(), "amount", amount.toPlainString())));
        }
        return toDto(card, null);
    }

    @Transactional(readOnly = true)
    public Page<GiftCardDto> list(GiftCardStatus status, Pageable pageable) {
        Specification<GiftCardEntity> filter = status == null
                ? Specification.unrestricted() : withStatus(status, LocalDateTime.now(commerceClock));
        return giftCardRepository.findAll(filter, pageable).map(card -> toDto(card, null));
    }

    @Transactional(readOnly = true)
    public GiftCardDto get(String rawCode) {
        GiftCardEntity card = find(rawCode);
        return toDto(card, transactionRepository.findByGiftCardIdOrderByIdAsc(card.getId()).stream()
                .map(GiftCardTransactionDto::from).toList());
    }

    /** Customer view: balance, status and expiry only, never the recipient or note. */
    @Transactional(readOnly = true)
    public GiftCardBalanceDto lookup(String rawCode) {
        GiftCardEntity card = find(rawCode);
        return GiftCardBalanceDto.builder()
                .code(card.getCode())
                .balance(card.getBalance())
                .status(statusOf(card))
                .expiresAt(card.getExpiresAt())
                .build();
    }

    @Transactional
    public GiftCardDto disable(String rawCode, String admin) {
        GiftCardEntity card = giftCardRepository.findByCodeForUpdate(wellFormed(rawCode))
                .orElseThrow(GiftCardService::notFound);
        if (card.getDisabledAt() != null) {
            throw new CustomException("Gift card is already disabled", HttpStatus.CONFLICT);
        }
        card.setDisabledAt(LocalDateTime.now(commerceClock));
        giftCardRepository.save(card);
        record(card, GiftCardTransactionType.DISABLED, BigDecimal.ZERO.setScale(2), null, null, admin);
        return toDto(card, null);
    }

    /** Finds a card by customer input: 400 when malformed, 404 when unknown. */
    @Transactional(readOnly = true)
    public GiftCardEntity find(String rawCode) {
        return giftCardRepository.findByCode(wellFormed(rawCode)).orElseThrow(GiftCardService::notFound);
    }

    @Transactional(readOnly = true)
    public Optional<GiftCardEntity> findByCode(String code) {
        return giftCardRepository.findByCode(code);
    }

    /** Why the card cannot pay right now, if it cannot. */
    public Optional<GiftCardFailure> evaluate(GiftCardEntity card) {
        return switch (statusOf(card)) {
            case DISABLED -> Optional.of(new GiftCardFailure(HttpStatus.BAD_REQUEST, "Gift card is disabled"));
            case EXPIRED -> Optional.of(new GiftCardFailure(HttpStatus.BAD_REQUEST, "Gift card has expired"));
            case REDEEMED -> Optional.of(new GiftCardFailure(HttpStatus.CONFLICT, "Gift card has no remaining balance"));
            case ACTIVE -> Optional.empty();
        };
    }

    /** The balance checkout may spend: zero for a disabled, expired or empty card. */
    public BigDecimal usableBalance(GiftCardEntity card) {
        return statusOf(card) == GiftCardStatus.ACTIVE ? card.getBalance() : BigDecimal.ZERO;
    }

    public GiftCardStatus statusOf(GiftCardEntity card) {
        if (card.getDisabledAt() != null) {
            return GiftCardStatus.DISABLED;
        }
        if (card.getExpiresAt() != null && !LocalDateTime.now(commerceClock).isBefore(card.getExpiresAt())) {
            return GiftCardStatus.EXPIRED;
        }
        if (card.getBalance().signum() <= 0) {
            return GiftCardStatus.REDEEMED;
        }
        return GiftCardStatus.ACTIVE;
    }

    /** Locks the cart's card for checkout; any reason it cannot pay is a 409. */
    @Transactional
    public GiftCardEntity lockForCheckout(String code) {
        GiftCardEntity card = giftCardRepository.findByCodeForUpdate(code)
                .orElseThrow(() -> new CustomException(CHECKOUT_FAILURE + "Gift card not found", HttpStatus.CONFLICT));
        evaluate(card).ifPresent(failure -> {
            throw new CustomException(CHECKOUT_FAILURE + failure.message(), HttpStatus.CONFLICT);
        });
        return card;
    }

    /** Takes {@code amount} from a card locked by {@link #lockForCheckout(String)}. */
    @Transactional
    public void debit(GiftCardEntity card, BigDecimal amount) {
        if (amount.signum() <= 0 || amount.compareTo(card.getBalance()) > 0) {
            throw new IllegalArgumentException("Cannot debit " + amount + " from a balance of " + card.getBalance());
        }
        card.setBalance(card.getBalance().subtract(amount));
        giftCardRepository.save(card);
    }

    /** Writes the REDEEMED ledger row of a saved order; repeating it for the same order is a no-op. */
    @Transactional
    public void recordRedemption(OrderEntity order) {
        GiftCardEntity card = giftCardRepository.findByCode(order.getGiftCardCode()).orElseThrow();
        String reference = orderReference(order.getId());
        if (transactionRepository.findByGiftCardIdAndTypeAndReference(
                card.getId(), GiftCardTransactionType.REDEEMED, reference).isEmpty()) {
            record(card, GiftCardTransactionType.REDEEMED, order.getGiftCardAmount().negate(), order.getId(),
                    reference, order.getUsername());
        }
    }

    /**
     * Returns up to {@code maxAmount} of what {@code order} paid with its gift
     * card, never more than was redeemed minus earlier restores. Repeating a
     * reference returns the amount restored the first time without changing anything.
     *
     * @return the amount put back on the card
     */
    @Transactional
    public BigDecimal restore(OrderEntity order, BigDecimal maxAmount, String reference) {
        if (order.getGiftCardCode() == null || order.getGiftCardAmount() == null) {
            return BigDecimal.ZERO.setScale(2);
        }
        Optional<GiftCardEntity> locked = giftCardRepository.findByCodeForUpdate(order.getGiftCardCode());
        if (locked.isEmpty()) {
            return BigDecimal.ZERO.setScale(2);
        }
        GiftCardEntity card = locked.get();
        Optional<GiftCardTransactionEntity> previous = transactionRepository.findByGiftCardIdAndTypeAndReference(
                card.getId(), GiftCardTransactionType.RESTORED, reference);
        if (previous.isPresent()) {
            return previous.get().getAmount();
        }
        BigDecimal restoredBefore = transactionRepository.sumForOrder(
                card.getId(), order.getId(), GiftCardTransactionType.RESTORED);
        BigDecimal amount = order.getGiftCardAmount().subtract(restoredBefore).min(maxAmount)
                .setScale(2, RoundingMode.HALF_UP);
        if (amount.signum() <= 0) {
            return BigDecimal.ZERO.setScale(2);
        }
        card.setBalance(card.getBalance().add(amount));
        giftCardRepository.save(card);
        record(card, GiftCardTransactionType.RESTORED, amount, order.getId(), reference, null);
        return amount;
    }

    /** What {@code order} paid with its gift card and has not had back yet; zero without a gift card. */
    @Transactional(readOnly = true)
    public BigDecimal restorable(OrderEntity order) {
        if (order.getGiftCardCode() == null || order.getGiftCardAmount() == null) {
            return BigDecimal.ZERO.setScale(2);
        }
        return giftCardRepository.findByCode(order.getGiftCardCode())
                .map(card -> order.getGiftCardAmount().subtract(transactionRepository.sumForOrder(
                        card.getId(), order.getId(), GiftCardTransactionType.RESTORED)).max(BigDecimal.ZERO))
                .orElse(BigDecimal.ZERO.setScale(2));
    }

    @Transactional
    public void forgetRecipient(String username) {
        giftCardRepository.forgetRecipient(username);
    }

    static String orderReference(Long orderId) {
        return "order:" + orderId;
    }

    private void record(GiftCardEntity card, GiftCardTransactionType type, BigDecimal amount, Long orderId,
                        String reference, String actor) {
        transactionRepository.save(GiftCardTransactionEntity.builder()
                .giftCardId(card.getId())
                .type(type)
                .amount(amount)
                .balanceAfter(card.getBalance())
                .orderId(orderId)
                .reference(reference)
                .actor(actor)
                .build());
    }

    private String uniqueCode() {
        for (int attempt = 0; attempt < CODE_ATTEMPTS; attempt++) {
            String code = GiftCardCodes.generate(random);
            if (!giftCardRepository.existsByCode(code)) {
                return code;
            }
        }
        throw new IllegalStateException("Could not generate a unique gift card code");
    }

    private static String wellFormed(String rawCode) {
        String code = GiftCardCodes.normalize(rawCode);
        if (!GiftCardCodes.isWellFormed(code)) {
            throw new CustomException("Gift card code must look like GC-XXXX-XXXX-XXXX", HttpStatus.BAD_REQUEST);
        }
        return code;
    }

    private static CustomException notFound() {
        return new CustomException("Gift card not found", HttpStatus.NOT_FOUND);
    }

    static Specification<GiftCardEntity> withStatus(GiftCardStatus status, LocalDateTime now) {
        return (root, query, cb) -> {
            Path<LocalDateTime> disabledAt = root.get("disabledAt");
            Path<LocalDateTime> expiresAt = root.get("expiresAt");
            Path<BigDecimal> balance = root.get("balance");
            Predicate enabled = cb.isNull(disabledAt);
            Predicate current = cb.or(cb.isNull(expiresAt), cb.greaterThan(expiresAt, now));
            return switch (status) {
                case DISABLED -> cb.isNotNull(disabledAt);
                case EXPIRED -> cb.and(enabled, cb.lessThanOrEqualTo(expiresAt, now));
                case REDEEMED -> cb.and(enabled, current, cb.lessThanOrEqualTo(balance, BigDecimal.ZERO));
                case ACTIVE -> cb.and(enabled, current, cb.greaterThan(balance, BigDecimal.ZERO));
            };
        };
    }

    private GiftCardDto toDto(GiftCardEntity card, List<GiftCardTransactionDto> transactions) {
        return GiftCardDto.builder()
                .code(card.getCode())
                .initialAmount(card.getInitialAmount())
                .balance(card.getBalance())
                .status(statusOf(card))
                .expiresAt(card.getExpiresAt())
                .recipientUsername(card.getRecipientUsername())
                .note(card.getNote())
                .createdBy(card.getCreatedBy())
                .createdAt(card.getCreatedAt())
                .updatedAt(card.getUpdatedAt())
                .transactions(transactions)
                .build();
    }
}
