package com.awesome.testing.endpoints.commerce;

import com.awesome.testing.dto.ErrorDto;
import com.awesome.testing.dto.email.EmailTemplate;
import com.awesome.testing.dto.giftcard.GiftCardBalanceDto;
import com.awesome.testing.dto.giftcard.GiftCardCreateDto;
import com.awesome.testing.dto.giftcard.GiftCardDto;
import com.awesome.testing.dto.giftcard.GiftCardStatus;
import com.awesome.testing.dto.order.PageDto;
import com.awesome.testing.entity.EmailEventEntity;
import com.awesome.testing.entity.giftcard.GiftCardTransactionType;
import com.awesome.testing.repository.EmailEventRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/** Admin gift card management and the customer balance check. */
class GiftCardFlowTest extends AbstractTenderFlowTest {

    private static final ParameterizedTypeReference<PageDto<GiftCardDto>> PAGE = new ParameterizedTypeReference<>() {};

    @Autowired
    private EmailEventRepository emailEventRepository;

    @Test
    void adminIssuesACardWithAGeneratedCodeAndEmailsTheRecipient() {
        LocalDateTime expiresAt = LocalDateTime.now().plusDays(30).withNano(0);

        GiftCardDto card = issueGiftCard(GiftCardCreateDto.builder().amount(new BigDecimal("50"))
                .expiresAt(expiresAt).recipientUsername(client.getUsername()).note("Sorry for the delay").build());

        assertThat(card.getCode()).matches("^GC-[ABCDEFGHJKMNPQRSTUVWXYZ23456789]{4}(-[ABCDEFGHJKMNPQRSTUVWXYZ23456789]{4}){2}$");
        assertThat(card.getInitialAmount()).isEqualByComparingTo("50.00");
        assertThat(card.getBalance()).isEqualByComparingTo("50.00");
        assertThat(card.getStatus()).isEqualTo(GiftCardStatus.ACTIVE);
        assertThat(card.getExpiresAt()).isEqualTo(expiresAt);
        assertThat(card.getRecipientUsername()).isEqualTo(client.getUsername());
        assertThat(card.getNote()).isEqualTo("Sorry for the delay");
        assertThat(card.getCreatedBy()).isNotBlank();
        assertThat(card.getTransactions()).isNull();
        List<EmailEventEntity> emails = emailEventRepository.findAll().stream()
                .filter(event -> event.getType() == EmailTemplate.GIFT_CARD_ISSUED)
                .filter(event -> event.getRecipientEmail().equals(client.getEmail()))
                .toList();
        assertThat(emails).hasSize(1);
        assertThat(emails.getFirst().getPayload()).contains(card.getCode()).contains("50.00");
    }

    @Test
    void cardWithoutRecipientSendsNoEmail() {
        long before = countGiftCardEmails();

        issueGiftCard("10.00");

        assertThat(countGiftCardEmails()).isEqualTo(before);
    }

    @Test
    void issuingRejectsInvalidRequestsAndNonAdmins() {
        ResponseEntity<String> tooSmall = issue(GiftCardCreateDto.builder().amount(new BigDecimal("0.99")).build(), adminToken);
        ResponseEntity<String> tooLarge = issue(GiftCardCreateDto.builder().amount(new BigDecimal("1000.01")).build(), adminToken);
        ResponseEntity<String> fractions = issue(GiftCardCreateDto.builder().amount(new BigDecimal("10.001")).build(), adminToken);
        ResponseEntity<String> missing = issue(GiftCardCreateDto.builder().build(), adminToken);
        ResponseEntity<String> past = issue(GiftCardCreateDto.builder().amount(BigDecimal.TEN)
                .expiresAt(LocalDateTime.now().minusDays(1)).build(), adminToken);
        ResponseEntity<String> unknownRecipient = issue(GiftCardCreateDto.builder().amount(BigDecimal.TEN)
                .recipientUsername("nobody-" + System.nanoTime()).build(), adminToken);
        ResponseEntity<String> client = issue(GiftCardCreateDto.builder().amount(BigDecimal.TEN).build(), clientToken);
        ResponseEntity<String> anonymous = executePost(ADMIN_GIFT_CARDS,
                GiftCardCreateDto.builder().amount(BigDecimal.TEN).build(), getJsonOnlyHeaders(), String.class);
        ResponseEntity<GiftCardDto> boundary = executePost(ADMIN_GIFT_CARDS,
                GiftCardCreateDto.builder().amount(new BigDecimal("1000.00")).build(), getHeadersWith(adminToken), GiftCardDto.class);

        assertThat(tooSmall.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(tooSmall.getBody()).contains("Gift card amount must be between 1.00 and 1000.00");
        assertThat(tooLarge.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(fractions.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(missing.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(past.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(past.getBody()).contains("expiresAt must be in the future");
        assertThat(unknownRecipient.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(unknownRecipient.getBody()).contains("Recipient user not found");
        assertThat(client.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(anonymous.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(boundary.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    }

    @Test
    void adminGetsACardWithItsLedgerCaseInsensitively() {
        GiftCardDto issued = issueGiftCard("25.00");

        ResponseEntity<GiftCardDto> found = executeGet(ADMIN_GIFT_CARDS + "/" + issued.getCode().toLowerCase(),
                getHeadersWith(adminToken), GiftCardDto.class);
        ResponseEntity<ErrorDto> unknown = executeGet(ADMIN_GIFT_CARDS + "/GC-AAAA-AAAA-AAAA",
                getHeadersWith(adminToken), ErrorDto.class);
        ResponseEntity<ErrorDto> malformed = executeGet(ADMIN_GIFT_CARDS + "/GC-0000-AAAA-AAAA",
                getHeadersWith(adminToken), ErrorDto.class);
        ResponseEntity<String> client = executeGet(ADMIN_GIFT_CARDS + "/" + issued.getCode(),
                getHeadersWith(clientToken), String.class);

        assertThat(found.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(found.getBody().getTransactions()).singleElement().satisfies(entry -> {
            assertThat(entry.getType()).isEqualTo(GiftCardTransactionType.ISSUED);
            assertThat(entry.getAmount()).isEqualByComparingTo("25.00");
            assertThat(entry.getBalanceAfter()).isEqualByComparingTo("25.00");
            assertThat(entry.getActor()).isEqualTo(issued.getCreatedBy());
        });
        assertThat(unknown.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(unknown.getBody().getMessage()).isEqualTo("Gift card not found");
        assertThat(malformed.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(malformed.getBody().getMessage()).isEqualTo("Gift card code must look like GC-XXXX-XXXX-XXXX");
        assertThat(client.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void disablingIsRecordedOnceAndKeepsTheBalance() {
        GiftCardDto issued = issueGiftCard("40.00");
        String path = ADMIN_GIFT_CARDS + "/" + issued.getCode() + "/disable";

        ResponseEntity<GiftCardDto> disabled = executePost(path, null, getHeadersWith(adminToken), GiftCardDto.class);
        ResponseEntity<ErrorDto> again = executePost(path, null, getHeadersWith(adminToken), ErrorDto.class);
        ResponseEntity<ErrorDto> unknown = executePost(ADMIN_GIFT_CARDS + "/GC-AAAA-AAAA-AAAA/disable", null,
                getHeadersWith(adminToken), ErrorDto.class);
        ResponseEntity<ErrorDto> malformed = executePost(ADMIN_GIFT_CARDS + "/nope/disable", null,
                getHeadersWith(adminToken), ErrorDto.class);
        ResponseEntity<String> client = executePost(path, null, getHeadersWith(clientToken), String.class);
        GiftCardDto reloaded = executeGet(ADMIN_GIFT_CARDS + "/" + issued.getCode(), getHeadersWith(adminToken),
                GiftCardDto.class).getBody();

        assertThat(disabled.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(disabled.getBody().getStatus()).isEqualTo(GiftCardStatus.DISABLED);
        assertThat(disabled.getBody().getBalance()).isEqualByComparingTo("40.00");
        assertThat(again.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(again.getBody().getMessage()).isEqualTo("Gift card is already disabled");
        assertThat(unknown.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(malformed.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(client.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(reloaded.getTransactions()).extracting(entry -> entry.getType())
                .containsExactly(GiftCardTransactionType.ISSUED, GiftCardTransactionType.DISABLED);
        assertThat(reloaded.getTransactions().getLast().getAmount()).isEqualByComparingTo("0");
    }

    @Test
    void listFiltersByEffectiveStatusNewestFirst() {
        GiftCardDto active = issueGiftCard("10.00");
        GiftCardDto disabled = issueGiftCard("11.00");
        GiftCardDto expired = issueGiftCard("12.00");
        GiftCardDto redeemed = issueGiftCard("13.00");
        executePost(ADMIN_GIFT_CARDS + "/" + disabled.getCode() + "/disable", null, getHeadersWith(adminToken), GiftCardDto.class);
        expire(expired.getCode());
        var empty = giftCardRepository.findByCode(redeemed.getCode()).orElseThrow();
        empty.setBalance(BigDecimal.ZERO);
        giftCardRepository.save(empty);

        Map<GiftCardStatus, List<String>> byStatus = new java.util.EnumMap<>(GiftCardStatus.class);
        for (GiftCardStatus status : GiftCardStatus.values()) {
            byStatus.put(status, codes(executeGet(ADMIN_GIFT_CARDS + "?status=" + status + "&size=100",
                    getHeadersWith(adminToken), PAGE)));
        }
        List<String> all = codes(executeGet(ADMIN_GIFT_CARDS + "?size=100", getHeadersWith(adminToken), PAGE));
        PageDto<GiftCardDto> clamped = executeGet(ADMIN_GIFT_CARDS + "?page=-1&size=0", getHeadersWith(adminToken), PAGE).getBody();
        ResponseEntity<String> badStatus = executeGet(ADMIN_GIFT_CARDS + "?status=LOST", getHeadersWith(adminToken), String.class);
        ResponseEntity<String> client = executeGet(ADMIN_GIFT_CARDS, getHeadersWith(clientToken), String.class);

        assertThat(byStatus.get(GiftCardStatus.ACTIVE)).contains(active.getCode())
                .doesNotContain(disabled.getCode(), expired.getCode(), redeemed.getCode());
        assertThat(byStatus.get(GiftCardStatus.DISABLED)).contains(disabled.getCode())
                .doesNotContain(active.getCode(), expired.getCode(), redeemed.getCode());
        assertThat(byStatus.get(GiftCardStatus.EXPIRED)).contains(expired.getCode())
                .doesNotContain(active.getCode(), disabled.getCode(), redeemed.getCode());
        assertThat(byStatus.get(GiftCardStatus.REDEEMED)).contains(redeemed.getCode())
                .doesNotContain(active.getCode(), disabled.getCode(), expired.getCode());
        assertThat(all.indexOf(redeemed.getCode())).isLessThan(all.indexOf(active.getCode()));
        assertThat(clamped.getPageNumber()).isZero();
        assertThat(clamped.getPageSize()).isEqualTo(1);
        assertThat(badStatus.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(client.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void customersSeeBalanceStatusAndExpiryButNotTheRecipientOrNote() {
        GiftCardDto issued = issueGiftCard(GiftCardCreateDto.builder().amount(new BigDecimal("30"))
                .recipientUsername(client.getUsername()).note("secret note").build());

        ResponseEntity<String> raw = executeGet(GIFT_CARDS + "/" + issued.getCode().toLowerCase(),
                getHeadersWith(otherClientToken), String.class);
        ResponseEntity<GiftCardBalanceDto> balance = executeGet(GIFT_CARDS + "/" + issued.getCode(),
                getHeadersWith(otherClientToken), GiftCardBalanceDto.class);
        ResponseEntity<ErrorDto> unknown = executeGet(GIFT_CARDS + "/GC-AAAA-AAAA-AAAA", getHeadersWith(clientToken), ErrorDto.class);
        ResponseEntity<ErrorDto> malformed = executeGet(GIFT_CARDS + "/GIFT", getHeadersWith(clientToken), ErrorDto.class);
        ResponseEntity<String> anonymous = executeGet(GIFT_CARDS + "/" + issued.getCode(), getJsonOnlyHeaders(), String.class);

        assertThat(raw.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(fields(raw.getBody())).isEqualTo(Set.of("code", "balance", "status", "expiresAt"));
        assertThat(raw.getBody()).doesNotContain(client.getUsername()).doesNotContain("secret note");
        assertThat(balance.getBody().getBalance()).isEqualByComparingTo("30.00");
        assertThat(balance.getBody().getStatus()).isEqualTo(GiftCardStatus.ACTIVE);
        assertThat(unknown.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(malformed.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(anonymous.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void lookupShowsExpiredAndDisabledCards() {
        GiftCardDto expired = issueGiftCard("10.00");
        GiftCardDto disabled = issueGiftCard("10.00");
        expire(expired.getCode());
        executePost(ADMIN_GIFT_CARDS + "/" + disabled.getCode() + "/disable", null, getHeadersWith(adminToken), GiftCardDto.class);

        GiftCardBalanceDto expiredView = executeGet(GIFT_CARDS + "/" + expired.getCode(), getHeadersWith(clientToken),
                GiftCardBalanceDto.class).getBody();
        GiftCardBalanceDto disabledView = executeGet(GIFT_CARDS + "/" + disabled.getCode(), getHeadersWith(clientToken),
                GiftCardBalanceDto.class).getBody();

        assertThat(expiredView.getStatus()).isEqualTo(GiftCardStatus.EXPIRED);
        assertThat(expiredView.getExpiresAt()).isNotNull();
        assertThat(disabledView.getStatus()).isEqualTo(GiftCardStatus.DISABLED);
    }

    private ResponseEntity<String> issue(GiftCardCreateDto request, String token) {
        return executePost(ADMIN_GIFT_CARDS, request, getHeadersWith(token), String.class);
    }

    private long countGiftCardEmails() {
        return emailEventRepository.findAll().stream().filter(event -> event.getType() == EmailTemplate.GIFT_CARD_ISSUED).count();
    }

    private static List<String> codes(ResponseEntity<PageDto<GiftCardDto>> response) {
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        return response.getBody().getContent().stream().map(GiftCardDto::getCode).toList();
    }

    private static Set<String> fields(String json) {
        JsonNode node = JsonMapper.builder().build().readTree(json);
        Set<String> names = new HashSet<>();
        node.propertyNames().forEach(names::add);
        return names;
    }
}
