package com.awesome.testing.entity.fulfilment;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

class CarrierTest {

    @ParameterizedTest(name = "{0} accepts {1}: {2}")
    @CsvSource({
            "INPOST, 123456789012345678901234, true",
            "INPOST, 12345678901234567890123, false",
            "INPOST, 1234567890123456789012345, false",
            "DPD, 12345678901234, true",
            "DPD, 1234567890123A, false",
            "DHL, 1234567890, true",
            "DHL, 123456789, false",
            "UPS, 1Z999AA10123456784, true",
            "UPS, 1z999aa10123456784, false",
            "UPS, 2Z999AA10123456784, false",
            "UPS, 1Z999AA1012345678, false"
    })
    void trackingNumbersMustMatchTheCarrierFormat(Carrier carrier, String trackingNumber, boolean accepted) {
        assertThat(carrier.acceptsTrackingNumber(trackingNumber)).isEqualTo(accepted);
    }
}
