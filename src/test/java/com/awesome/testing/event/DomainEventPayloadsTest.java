package com.awesome.testing.event;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class DomainEventPayloadsTest {

    @Test
    void sortsKeysKeepsNullsAndWritesPlainDecimals() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("status", "PAID");
        data.put("totalAmount", new BigDecimal("1E+3").setScale(2));
        data.put("orderId", 7L);
        data.put("failureCode", null);
        data.put("paymentId", UUID.fromString("00000000-0000-0000-0000-0000000000a1"));

        assertThat(DomainEventPayloads.data(data)).isEqualTo(
                "{\"failureCode\":null,\"orderId\":7,\"paymentId\":\"00000000-0000-0000-0000-0000000000a1\","
                        + "\"status\":\"PAID\",\"totalAmount\":1000.00}");
    }

    @Test
    void theSameDataInAnotherOrderGivesTheSameBytes() {
        Map<String, Object> first = new LinkedHashMap<>();
        first.put("b", 1);
        first.put("a", "x");
        Map<String, Object> second = new LinkedHashMap<>();
        second.put("a", "x");
        second.put("b", 1);

        assertThat(DomainEventPayloads.data(first)).isEqualTo(DomainEventPayloads.data(second)).isEqualTo("{\"a\":\"x\",\"b\":1}");
    }

    @Test
    void missingDataIsAnEmptyObject() {
        assertThat(DomainEventPayloads.data(null)).isEqualTo("{}");
        assertThat(DomainEventPayloads.data(Map.of())).isEqualTo("{}");
    }
}
