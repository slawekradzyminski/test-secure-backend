package com.awesome.testing.service.tax;

import com.awesome.testing.dto.tax.VatRateDto;

/** Result of an administrator create-or-update; {@code created} selects 201 over 200. */
public record VatRateUpsert(VatRateDto rate, boolean created) {
}
