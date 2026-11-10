package com.awesome.testing.service.currency;

import com.awesome.testing.dto.currency.CurrencyDto;

/** Result of an administrator create-or-update; {@code created} selects 201 over 200. */
public record CurrencyUpsert(CurrencyDto currency, boolean created) {
}
