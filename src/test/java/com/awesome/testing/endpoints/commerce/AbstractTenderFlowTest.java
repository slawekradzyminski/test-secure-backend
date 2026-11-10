package com.awesome.testing.endpoints.commerce;

import com.awesome.testing.dto.cart.CartDto;
import com.awesome.testing.dto.giftcard.ApplyGiftCardDto;
import com.awesome.testing.dto.giftcard.GiftCardCreateDto;
import com.awesome.testing.dto.giftcard.GiftCardDto;
import com.awesome.testing.dto.loyalty.ApplyLoyaltyPointsDto;
import com.awesome.testing.dto.loyalty.LoyaltyAccountDto;
import com.awesome.testing.entity.giftcard.GiftCardEntity;
import com.awesome.testing.entity.loyalty.LoyaltyAccountEntity;
import com.awesome.testing.internal.dto.OrderRefundDto;
import com.awesome.testing.repository.giftcard.GiftCardRepository;
import com.awesome.testing.repository.loyalty.LoyaltyAccountRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/** Gift card and loyalty helpers shared by the tender endpoint tests. */
abstract class AbstractTenderFlowTest extends AbstractCommerceFlowTest {

    protected static final String ADMIN_GIFT_CARDS = "/api/v1/admin/gift-cards";
    protected static final String GIFT_CARDS = "/api/v1/gift-cards";
    protected static final String CART_GIFT_CARD = "/api/v1/cart/gift-card";
    protected static final String CART_POINTS = "/api/v1/cart/loyalty-points";
    protected static final String LOYALTY = "/api/v1/loyalty";

    @Autowired
    protected GiftCardRepository giftCardRepository;

    @Autowired
    protected LoyaltyAccountRepository loyaltyAccountRepository;

    protected GiftCardDto issueGiftCard(String amount) {
        return issueGiftCard(GiftCardCreateDto.builder().amount(new BigDecimal(amount)).build());
    }

    protected GiftCardDto issueGiftCard(GiftCardCreateDto request) {
        ResponseEntity<GiftCardDto> response = executePost(ADMIN_GIFT_CARDS, request, getHeadersWith(adminToken), GiftCardDto.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return response.getBody();
    }

    protected <T> ResponseEntity<T> applyGiftCard(String token, String code, Class<T> type) {
        return executePut(CART_GIFT_CARD, new ApplyGiftCardDto(code), getHeadersWith(token), type);
    }

    protected <T> ResponseEntity<T> applyPoints(String token, long points, Class<T> type) {
        return executePut(CART_POINTS, new ApplyLoyaltyPointsDto(points), getHeadersWith(token), type);
    }

    protected CartDto cart(String token) {
        return executeGet(CART_ENDPOINT, getHeadersWith(token), CartDto.class).getBody();
    }

    protected LoyaltyAccountDto loyalty(String token) {
        ResponseEntity<LoyaltyAccountDto> response = executeGet(LOYALTY, getHeadersWith(token), LoyaltyAccountDto.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        return response.getBody();
    }

    /** Gives the client points directly, as if earned earlier. */
    protected void grantPoints(String username, long points) {
        loyaltyAccountRepository.save(LoyaltyAccountEntity.builder()
                .username(username).balance(points).lifetimeEarned(points).build());
    }

    protected BigDecimal balanceOf(String code) {
        return giftCardRepository.findByCode(code).orElseThrow().getBalance();
    }

    protected void expire(String code) {
        GiftCardEntity card = giftCardRepository.findByCode(code).orElseThrow();
        card.setExpiresAt(LocalDateTime.now().minusDays(1));
        giftCardRepository.save(card);
    }

    protected <T> ResponseEntity<T> refund(Long orderId, OrderRefundDto request, Class<T> type) {
        HttpHeaders headers = getJsonOnlyHeaders();
        headers.add("X-Service-Token", "test-warehouse-token");
        return executePost("/internal/v1/orders/" + orderId + "/refunds", request, headers, type);
    }

    protected <T> ResponseEntity<T> cancel(Long orderId, String token, Class<T> type) {
        return executePost(ORDERS_ENDPOINT + "/" + orderId + "/cancel", null, getHeadersWith(token), type);
    }
}
