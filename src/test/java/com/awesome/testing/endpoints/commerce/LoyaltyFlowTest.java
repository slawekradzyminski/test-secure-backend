package com.awesome.testing.endpoints.commerce;

import com.awesome.testing.dto.loyalty.LoyaltyAccountDto;
import com.awesome.testing.dto.loyalty.LoyaltyTier;
import com.awesome.testing.dto.loyalty.LoyaltyTransactionDto;
import com.awesome.testing.dto.order.PageDto;
import com.awesome.testing.entity.loyalty.LoyaltyTransactionType;
import org.junit.jupiter.api.Test;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

/** The loyalty account endpoints show only the caller's own points. */
class LoyaltyFlowTest extends AbstractTenderFlowTest {

    private static final ParameterizedTypeReference<PageDto<LoyaltyTransactionDto>> LEDGER = new ParameterizedTypeReference<>() {};

    @Test
    void newCustomersHaveAnEmptyBronzeAccount() {
        LoyaltyAccountDto account = loyalty(clientToken);

        assertThat(account.getBalance()).isZero();
        assertThat(account.getLifetimeEarned()).isZero();
        assertThat(account.getTier()).isEqualTo(LoyaltyTier.BRONZE);
        assertThat(account.getValueOfBalance()).isEqualByComparingTo("0.00");
    }

    @Test
    void accountShowsBalanceTierAndValue() {
        grantPoints(client.getUsername(), 1_250);

        LoyaltyAccountDto account = loyalty(clientToken);
        LoyaltyAccountDto other = loyalty(otherClientToken);

        assertThat(account.getBalance()).isEqualTo(1_250);
        assertThat(account.getTier()).isEqualTo(LoyaltyTier.SILVER);
        assertThat(account.getValueOfBalance()).isEqualByComparingTo("12.50");
        assertThat(other.getBalance()).isZero();
    }

    @Test
    void transactionsArePagedNewestFirstAndPrivate() {
        for (int i = 0; i < 3; i++) {
            markPaid(order(clientToken, product("10.00", 5), 1).getId());
        }

        ResponseEntity<PageDto<LoyaltyTransactionDto>> firstPage = executeGet(LOYALTY + "/transactions?page=0&size=2",
                getHeadersWith(clientToken), LEDGER);
        ResponseEntity<PageDto<LoyaltyTransactionDto>> clamped = executeGet(LOYALTY + "/transactions?page=-3&size=500",
                getHeadersWith(clientToken), LEDGER);
        ResponseEntity<PageDto<LoyaltyTransactionDto>> other = executeGet(LOYALTY + "/transactions",
                getHeadersWith(otherClientToken), LEDGER);

        assertThat(firstPage.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(firstPage.getBody().getTotalElements()).isEqualTo(3);
        assertThat(firstPage.getBody().getContent()).hasSize(2)
                .allSatisfy(entry -> assertThat(entry.getType()).isEqualTo(LoyaltyTransactionType.EARNED));
        assertThat(firstPage.getBody().getContent().getFirst().getId())
                .isGreaterThan(firstPage.getBody().getContent().getLast().getId());
        assertThat(firstPage.getBody().getContent().getFirst().getBalanceAfter()).isEqualTo(30);
        assertThat(clamped.getBody().getPageNumber()).isZero();
        assertThat(clamped.getBody().getPageSize()).isEqualTo(100);
        assertThat(other.getBody().getContent()).isEmpty();
    }

    @Test
    void loyaltyEndpointsNeedAToken() {
        assertThat(executeGet(LOYALTY, getJsonOnlyHeaders(), String.class).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(executeGet(LOYALTY + "/transactions", getJsonOnlyHeaders(), String.class).getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
    }
}
