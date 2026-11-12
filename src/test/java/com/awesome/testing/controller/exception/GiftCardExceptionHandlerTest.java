package com.awesome.testing.controller.exception;

import com.awesome.testing.dto.giftcard.GiftCardStatus;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class GiftCardExceptionHandlerTest {

    @Test
    void anUnknownEnumValueListsTheAllowedValuesWithoutTheClassName() {
        assertThat(GiftCardExceptionHandler.message("status", "LOST", GiftCardStatus.class))
                .isEqualTo("Unknown status 'LOST'. Allowed values: ACTIVE, REDEEMED, EXPIRED, DISABLED");
    }

    @Test
    void anUnparseableNumberNamesTheParameterAndValue() {
        assertThat(GiftCardExceptionHandler.message("page", "abc", int.class)).isEqualTo("Invalid page 'abc'");
    }

    @Test
    void anUnknownRequiredTypeStillNamesTheParameterAndValue() {
        assertThat(GiftCardExceptionHandler.message("size", "x", null)).isEqualTo("Invalid size 'x'");
    }
}
