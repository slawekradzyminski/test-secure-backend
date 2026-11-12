package com.awesome.testing.service.giftcard;

import com.awesome.testing.controller.exception.CustomException;
import com.awesome.testing.dto.email.EmailTemplate;
import com.awesome.testing.dto.giftcard.GiftCardBalanceDto;
import com.awesome.testing.dto.giftcard.GiftCardCreateDto;
import com.awesome.testing.dto.giftcard.GiftCardDto;
import com.awesome.testing.dto.giftcard.GiftCardStatus;
import com.awesome.testing.entity.OrderEntity;
import com.awesome.testing.entity.giftcard.GiftCardEntity;
import com.awesome.testing.entity.giftcard.GiftCardTransactionEntity;
import com.awesome.testing.entity.giftcard.GiftCardTransactionType;
import com.awesome.testing.repository.UserRepository;
import com.awesome.testing.repository.giftcard.GiftCardRepository;
import com.awesome.testing.repository.giftcard.GiftCardTransactionRepository;
import com.awesome.testing.service.notification.CustomerNotificationRequestedEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GiftCardServiceTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 10, 12, 0);
    private static final String CODE = "GC-ABCD-EFGH-JKMN";

    @Mock
    private GiftCardRepository giftCardRepository;
    @Mock
    private GiftCardTransactionRepository transactionRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private ApplicationEventPublisher eventPublisher;

    private GiftCardService service;

    @BeforeEach
    void setUp() {
        service = new GiftCardService(giftCardRepository, transactionRepository, userRepository, eventPublisher,
                Clock.fixed(NOW.toInstant(ZoneOffset.UTC), ZoneOffset.UTC));
    }

    @Test
    void issuesACardWithItsLedgerAndEmailsTheRecipient() {
        when(userRepository.existsByUsername("client")).thenReturn(true);
        when(giftCardRepository.existsByCode(anyString())).thenReturn(true, false);
        when(giftCardRepository.save(any(GiftCardEntity.class))).thenAnswer(invocation -> withId(invocation.getArgument(0)));

        GiftCardDto issued = service.issue(GiftCardCreateDto.builder().amount(new BigDecimal("50"))
                .expiresAt(NOW.plusSeconds(1)).recipientUsername(" client ").note("  thanks ").build(), "admin");

        assertThat(issued.getCode()).matches("^GC-[A-Z2-9]{4}-[A-Z2-9]{4}-[A-Z2-9]{4}$");
        assertThat(issued.getInitialAmount()).hasToString("50.00");
        assertThat(issued.getBalance()).hasToString("50.00");
        assertThat(issued.getRecipientUsername()).isEqualTo("client");
        assertThat(issued.getNote()).isEqualTo("thanks");
        assertThat(issued.getCreatedBy()).isEqualTo("admin");
        assertThat(issued.getStatus()).isEqualTo(GiftCardStatus.ACTIVE);
        verify(giftCardRepository, times(2)).existsByCode(anyString());
        GiftCardTransactionEntity ledger = savedTransaction();
        assertThat(ledger.getType()).isEqualTo(GiftCardTransactionType.ISSUED);
        assertThat(ledger.getAmount()).hasToString("50.00");
        assertThat(ledger.getBalanceAfter()).hasToString("50.00");
        assertThat(ledger.getActor()).isEqualTo("admin");
        assertThat(ledger.getGiftCardId()).isEqualTo(7L);
        ArgumentCaptor<CustomerNotificationRequestedEvent> email = ArgumentCaptor.forClass(CustomerNotificationRequestedEvent.class);
        verify(eventPublisher).publishEvent(email.capture());
        assertThat(email.getValue().username()).isEqualTo("client");
        assertThat(email.getValue().template()).isEqualTo(EmailTemplate.GIFT_CARD_ISSUED);
        assertThat(email.getValue().properties()).isEqualTo(Map.of("code", issued.getCode(), "amount", "50.00"));
        assertThat(email.getValue().requestId()).isNotNull();
    }

    @Test
    void issuesWithoutRecipientOrNoteSilently() {
        when(giftCardRepository.save(any(GiftCardEntity.class))).thenAnswer(invocation -> withId(invocation.getArgument(0)));

        GiftCardDto issued = service.issue(GiftCardCreateDto.builder().amount(BigDecimal.ONE)
                .recipientUsername(" ").note(" ").build(), "admin");

        assertThat(issued.getRecipientUsername()).isNull();
        assertThat(issued.getNote()).isNull();
        assertThat(issued.getExpiresAt()).isNull();
        verify(eventPublisher, never()).publishEvent(any());
        verify(userRepository, never()).existsByUsername(anyString());
    }

    @Test
    void rejectsExpiryNotInTheFutureAndUnknownRecipients() {
        assertThatThrownBy(() -> service.issue(GiftCardCreateDto.builder().amount(BigDecimal.TEN).expiresAt(NOW).build(), "admin"))
                .isInstanceOf(CustomException.class)
                .hasMessage("expiresAt must be in the future")
                .extracting("httpStatus").isEqualTo(HttpStatus.BAD_REQUEST);
        assertThatThrownBy(() -> service.issue(GiftCardCreateDto.builder().amount(BigDecimal.TEN).recipientUsername("ghost").build(), "admin"))
                .isInstanceOf(CustomException.class)
                .hasMessage("Recipient user not found")
                .extracting("httpStatus").isEqualTo(HttpStatus.NOT_FOUND);
        verify(giftCardRepository, never()).save(any());
    }

    @Test
    void givesUpWhenEveryGeneratedCodeIsTaken() {
        when(giftCardRepository.existsByCode(anyString())).thenReturn(true);

        assertThatThrownBy(() -> service.issue(GiftCardCreateDto.builder().amount(BigDecimal.TEN).build(), "admin"))
                .isInstanceOf(IllegalStateException.class);
        verify(giftCardRepository, times(GiftCardService.CODE_ATTEMPTS)).existsByCode(anyString());
    }

    @Test
    void statusFollowsDisabledThenExpiredThenBalance() {
        assertThat(service.statusOf(card("10.00"))).isEqualTo(GiftCardStatus.ACTIVE);
        assertThat(service.statusOf(card("0.00"))).isEqualTo(GiftCardStatus.REDEEMED);
        GiftCardEntity expiring = card("10.00");
        expiring.setExpiresAt(NOW.plusNanos(1000));
        assertThat(service.statusOf(expiring)).isEqualTo(GiftCardStatus.ACTIVE);
        expiring.setExpiresAt(NOW);
        assertThat(service.statusOf(expiring)).isEqualTo(GiftCardStatus.EXPIRED);
        expiring.setBalance(BigDecimal.ZERO);
        assertThat(service.statusOf(expiring)).isEqualTo(GiftCardStatus.EXPIRED);
        expiring.setDisabledAt(NOW.minusDays(1));
        assertThat(service.statusOf(expiring)).isEqualTo(GiftCardStatus.DISABLED);
    }

    @Test
    void evaluationAndUsableBalanceMatchTheStatus() {
        GiftCardEntity disabled = card("10.00");
        disabled.setDisabledAt(NOW);
        GiftCardEntity expired = card("10.00");
        expired.setExpiresAt(NOW.minusSeconds(1));

        assertThat(service.evaluate(card("10.00"))).isEmpty();
        assertThat(service.evaluate(disabled)).contains(new GiftCardFailure(HttpStatus.BAD_REQUEST, "Gift card is disabled"));
        assertThat(service.evaluate(expired)).contains(new GiftCardFailure(HttpStatus.BAD_REQUEST, "Gift card has expired"));
        assertThat(service.evaluate(card("0.00"))).contains(new GiftCardFailure(HttpStatus.CONFLICT, "Gift card has no remaining balance"));
        assertThat(service.usableBalance(card("12.50"))).isEqualByComparingTo("12.50");
        assertThat(service.usableBalance(disabled)).isEqualByComparingTo("0");
        assertThat(service.usableBalance(expired)).isEqualByComparingTo("0");
    }

    @Test
    void lookupHidesTheRecipientAndNormalizesTheCode() {
        GiftCardEntity card = card("10.00");
        card.setRecipientUsername("client");
        card.setExpiresAt(NOW.plusDays(1));
        when(giftCardRepository.findByCode(CODE)).thenReturn(Optional.of(card));

        GiftCardBalanceDto balance = service.lookup(" gc-abcd-efgh-jkmn ");

        assertThat(balance).isEqualTo(new GiftCardBalanceDto(CODE, new BigDecimal("10.00"), GiftCardStatus.ACTIVE, NOW.plusDays(1)));
    }

    @Test
    void findRejectsMalformedAndUnknownCodes() {
        when(giftCardRepository.findByCode(CODE)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.find("GC-1234"))
                .isInstanceOf(CustomException.class)
                .hasMessage("Gift card code must look like GC-XXXX-XXXX-XXXX")
                .extracting("httpStatus").isEqualTo(HttpStatus.BAD_REQUEST);
        assertThatThrownBy(() -> service.find(CODE))
                .isInstanceOf(CustomException.class)
                .hasMessage("Gift card not found")
                .extracting("httpStatus").isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void getIncludesTheLedger() {
        GiftCardEntity card = card("10.00");
        when(giftCardRepository.findByCode(CODE)).thenReturn(Optional.of(card));
        when(transactionRepository.findByGiftCardIdOrderByIdAsc(7L)).thenReturn(List.of(GiftCardTransactionEntity.builder()
                .type(GiftCardTransactionType.ISSUED).amount(BigDecimal.TEN).balanceAfter(BigDecimal.TEN).actor("admin").build()));

        GiftCardDto dto = service.get(CODE);

        assertThat(dto.getTransactions()).singleElement().satisfies(entry -> {
            assertThat(entry.getType()).isEqualTo(GiftCardTransactionType.ISSUED);
            assertThat(entry.getActor()).isEqualTo("admin");
        });
    }

    @Test
    void disablingKeepsTheBalanceAndCannotBeRepeated() {
        GiftCardEntity card = card("10.00");
        when(giftCardRepository.findByCodeForUpdate(CODE)).thenReturn(Optional.of(card));

        GiftCardDto disabled = service.disable(CODE, "admin");

        assertThat(disabled.getStatus()).isEqualTo(GiftCardStatus.DISABLED);
        assertThat(card.getDisabledAt()).isEqualTo(NOW);
        verify(giftCardRepository).saveAndFlush(card);
        GiftCardTransactionEntity ledger = savedTransaction();
        assertThat(ledger.getType()).isEqualTo(GiftCardTransactionType.DISABLED);
        assertThat(ledger.getAmount()).isEqualByComparingTo("0");
        assertThat(ledger.getBalanceAfter()).isEqualByComparingTo("10.00");
        assertThat(ledger.getActor()).isEqualTo("admin");
        assertThatThrownBy(() -> service.disable(CODE, "admin"))
                .isInstanceOf(CustomException.class)
                .hasMessage("Gift card is already disabled")
                .extracting("httpStatus").isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void disablingAnUnknownCardIsNotFound() {
        when(giftCardRepository.findByCodeForUpdate(CODE)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.disable(CODE, "admin"))
                .isInstanceOf(CustomException.class)
                .extracting("httpStatus").isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void checkoutLockReportsEveryProblemAsAConflict() {
        GiftCardEntity disabled = card("10.00");
        disabled.setDisabledAt(NOW);
        when(giftCardRepository.findByCodeForUpdate("GC-MISS-INGG-CARD")).thenReturn(Optional.empty());
        when(giftCardRepository.findByCodeForUpdate(CODE)).thenReturn(Optional.of(disabled));

        assertThatThrownBy(() -> service.lockForCheckout("GC-MISS-INGG-CARD"))
                .isInstanceOf(CustomException.class)
                .hasMessage("Applied gift card is no longer valid: Gift card not found")
                .extracting("httpStatus").isEqualTo(HttpStatus.CONFLICT);
        assertThatThrownBy(() -> service.lockForCheckout(CODE))
                .isInstanceOf(CustomException.class)
                .hasMessage("Applied gift card is no longer valid: Gift card is disabled")
                .extracting("httpStatus").isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void checkoutLockReturnsAUsableCard() {
        GiftCardEntity card = card("10.00");
        when(giftCardRepository.findByCodeForUpdate(CODE)).thenReturn(Optional.of(card));

        assertThat(service.lockForCheckout(CODE)).isSameAs(card);
    }

    @Test
    void debitTakesAtMostTheBalance() {
        GiftCardEntity card = card("10.00");

        service.debit(card, new BigDecimal("4.00"));
        service.debit(card, new BigDecimal("6.00"));

        assertThat(card.getBalance()).isEqualByComparingTo("0");
        verify(giftCardRepository, times(2)).save(card);
        assertThatThrownBy(() -> service.debit(card("5.00"), new BigDecimal("5.01"))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.debit(card("5.00"), BigDecimal.ZERO)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void recordsTheRedemptionOfAnOrder() {
        GiftCardEntity card = card("20.00");
        when(giftCardRepository.findByCode(CODE)).thenReturn(Optional.of(card));
        when(transactionRepository.findByGiftCardIdAndTypeAndReference(7L, GiftCardTransactionType.REDEEMED, "order:42"))
                .thenReturn(Optional.empty());

        service.recordRedemption(order("30.00"));

        GiftCardTransactionEntity ledger = savedTransaction();
        assertThat(ledger.getType()).isEqualTo(GiftCardTransactionType.REDEEMED);
        assertThat(ledger.getAmount()).isEqualByComparingTo("-30.00");
        assertThat(ledger.getBalanceAfter()).isEqualByComparingTo("20.00");
        assertThat(ledger.getOrderId()).isEqualTo(42L);
        assertThat(ledger.getReference()).isEqualTo("order:42");
        assertThat(ledger.getActor()).isEqualTo("buyer");
    }

    @Test
    void recordingARedemptionAgainWritesNothing() {
        when(giftCardRepository.findByCode(CODE)).thenReturn(Optional.of(card("20.00")));
        when(transactionRepository.findByGiftCardIdAndTypeAndReference(7L, GiftCardTransactionType.REDEEMED, "order:42"))
                .thenReturn(Optional.of(new GiftCardTransactionEntity()));

        service.recordRedemption(order("30.00"));

        verify(transactionRepository, never()).save(any());
    }

    @Test
    void listMapsEveryCardWithItsStatus() {
        GiftCardEntity disabled = card("10.00");
        disabled.setDisabledAt(NOW);
        PageRequest page = PageRequest.of(0, 10);
        when(giftCardRepository.findAll(org.mockito.ArgumentMatchers.<Specification<GiftCardEntity>>any(), org.mockito.ArgumentMatchers.eq(page)))
                .thenReturn(new PageImpl<>(List.of(card("10.00"), disabled), page, 2));

        List<GiftCardDto> all = service.list(null, page).getContent();
        List<GiftCardDto> filtered = service.list(GiftCardStatus.DISABLED, page).getContent();

        assertThat(all).extracting(GiftCardDto::getStatus).containsExactly(GiftCardStatus.ACTIVE, GiftCardStatus.DISABLED);
        assertThat(all).allSatisfy(dto -> assertThat(dto.getTransactions()).isNull());
        assertThat(filtered).hasSize(2);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Specification<GiftCardEntity>> specs = ArgumentCaptor.forClass(Specification.class);
        verify(giftCardRepository, times(2)).findAll(specs.capture(), org.mockito.ArgumentMatchers.eq(page));
        @SuppressWarnings("unchecked")
        jakarta.persistence.criteria.Root<GiftCardEntity> root = org.mockito.Mockito.mock(jakarta.persistence.criteria.Root.class);
        jakarta.persistence.criteria.CriteriaBuilder cb = org.mockito.Mockito.mock(jakarta.persistence.criteria.CriteriaBuilder.class);
        jakarta.persistence.criteria.Predicate disabledPredicate = org.mockito.Mockito.mock(jakarta.persistence.criteria.Predicate.class);
        when(cb.isNotNull(any())).thenReturn(disabledPredicate);
        assertThat(specs.getAllValues().getFirst().toPredicate(root, null, cb)).isNull();
        assertThat(specs.getAllValues().getLast().toPredicate(root, null, cb)).isSameAs(disabledPredicate);
    }

    @Test
    void findByCodeLooksUpTheExactCode() {
        GiftCardEntity card = card("1.00");
        when(giftCardRepository.findByCode(CODE)).thenReturn(Optional.of(card));

        assertThat(service.findByCode(CODE)).containsSame(card);
    }

    @Test
    void restoresAtMostWhatTheOrderStillHasOnTheCard() {
        GiftCardEntity card = card("5.00");
        OrderEntity order = order("30.00");
        when(giftCardRepository.findByCodeForUpdate(CODE)).thenReturn(Optional.of(card));
        when(transactionRepository.findByGiftCardIdAndTypeAndReference(7L, GiftCardTransactionType.RESTORED, "refund:r1"))
                .thenReturn(Optional.empty());
        when(transactionRepository.sumForOrder(7L, 42L, GiftCardTransactionType.RESTORED)).thenReturn(new BigDecimal("12.00"));

        BigDecimal restored = service.restore(order, new BigDecimal("25.00"), "refund:r1");

        assertThat(restored).hasToString("18.00");
        assertThat(card.getBalance()).isEqualByComparingTo("23.00");
        verify(giftCardRepository).save(card);
        GiftCardTransactionEntity ledger = savedTransaction();
        assertThat(ledger.getType()).isEqualTo(GiftCardTransactionType.RESTORED);
        assertThat(ledger.getAmount()).isEqualByComparingTo("18.00");
        assertThat(ledger.getBalanceAfter()).isEqualByComparingTo("23.00");
        assertThat(ledger.getOrderId()).isEqualTo(42L);
        assertThat(ledger.getReference()).isEqualTo("refund:r1");
    }

    @Test
    void restoreIsCappedByTheRequestedAmount() {
        GiftCardEntity card = card("0.00");
        when(giftCardRepository.findByCodeForUpdate(CODE)).thenReturn(Optional.of(card));
        when(transactionRepository.findByGiftCardIdAndTypeAndReference(7L, GiftCardTransactionType.RESTORED, "r"))
                .thenReturn(Optional.empty());
        when(transactionRepository.sumForOrder(7L, 42L, GiftCardTransactionType.RESTORED)).thenReturn(BigDecimal.ZERO);

        assertThat(service.restore(order("30.00"), new BigDecimal("7.5"), "r")).hasToString("7.50");
        assertThat(card.getBalance()).isEqualByComparingTo("7.50");
    }

    @Test
    void repeatedRestoreReturnsTheFirstResult() {
        GiftCardEntity card = card("5.00");
        when(giftCardRepository.findByCodeForUpdate(CODE)).thenReturn(Optional.of(card));
        when(transactionRepository.findByGiftCardIdAndTypeAndReference(7L, GiftCardTransactionType.RESTORED, "r"))
                .thenReturn(Optional.of(GiftCardTransactionEntity.builder().amount(new BigDecimal("3.00")).build()));

        assertThat(service.restore(order("30.00"), new BigDecimal("10.00"), "r")).isEqualByComparingTo("3.00");
        assertThat(card.getBalance()).isEqualByComparingTo("5.00");
        verify(transactionRepository, never()).save(any());
    }

    @Test
    void nothingToRestoreWritesNothing() {
        GiftCardEntity card = card("5.00");
        when(giftCardRepository.findByCodeForUpdate(CODE)).thenReturn(Optional.of(card));
        when(transactionRepository.findByGiftCardIdAndTypeAndReference(7L, GiftCardTransactionType.RESTORED, "r"))
                .thenReturn(Optional.empty());
        when(transactionRepository.sumForOrder(7L, 42L, GiftCardTransactionType.RESTORED)).thenReturn(new BigDecimal("30.00"));

        assertThat(service.restore(order("30.00"), new BigDecimal("10.00"), "r")).hasToString("0.00");
        assertThat(card.getBalance()).isEqualByComparingTo("5.00");
        verify(transactionRepository, never()).save(any());
    }

    @Test
    void ordersWithoutAGiftCardOrWithADeletedCardRestoreNothing() {
        OrderEntity plain = OrderEntity.builder().id(1L).totalAmount(BigDecimal.TEN).build();
        when(giftCardRepository.findByCodeForUpdate(CODE)).thenReturn(Optional.empty());

        assertThat(service.restore(plain, BigDecimal.TEN, "r")).hasToString("0.00");
        assertThat(service.restore(order("30.00"), BigDecimal.TEN, "r")).hasToString("0.00");
        OrderEntity amountOnly = order("30.00");
        amountOnly.setGiftCardCode(null);
        assertThat(service.restore(amountOnly, BigDecimal.TEN, "r")).hasToString("0.00");
        verify(giftCardRepository, times(1)).findByCodeForUpdate(CODE);
    }

    @Test
    void restorableIsWhatTheOrderPaidMinusEarlierRestores() {
        when(giftCardRepository.findByCode(CODE)).thenReturn(Optional.of(card("0.00")));
        when(transactionRepository.sumForOrder(7L, 42L, GiftCardTransactionType.RESTORED)).thenReturn(new BigDecimal("12.00"), new BigDecimal("40.00"));

        assertThat(service.restorable(order("30.00"))).isEqualByComparingTo("18.00");
        assertThat(service.restorable(order("30.00"))).isEqualByComparingTo("0");
        assertThat(service.restorable(OrderEntity.builder().id(1L).build())).isEqualByComparingTo("0");
        OrderEntity codeOnly = order("30.00");
        codeOnly.setGiftCardAmount(null);
        assertThat(service.restorable(codeOnly)).isEqualByComparingTo("0");
        OrderEntity deleted = order("30.00");
        deleted.setGiftCardCode("GC-GONE-GONE-GONE");
        assertThat(service.restorable(deleted)).isEqualByComparingTo("0");
    }

    @Test
    void forgettingAUserDropsThemAsRecipient() {
        service.forgetRecipient("client");

        verify(giftCardRepository).forgetRecipient("client");
    }

    private GiftCardTransactionEntity savedTransaction() {
        ArgumentCaptor<GiftCardTransactionEntity> captor = ArgumentCaptor.forClass(GiftCardTransactionEntity.class);
        verify(transactionRepository).save(captor.capture());
        return captor.getValue();
    }

    private static GiftCardEntity withId(GiftCardEntity card) {
        card.setId(7L);
        return card;
    }

    private static GiftCardEntity card(String balance) {
        return GiftCardEntity.builder().id(7L).code(CODE).initialAmount(new BigDecimal("50.00"))
                .balance(new BigDecimal(balance)).createdBy("admin").build();
    }

    private static OrderEntity order(String giftCardAmount) {
        return OrderEntity.builder().id(42L).username("buyer").totalAmount(new BigDecimal("100.00"))
                .giftCardCode(CODE).giftCardAmount(new BigDecimal(giftCardAmount)).build();
    }
}
