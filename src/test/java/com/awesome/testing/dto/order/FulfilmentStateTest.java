package com.awesome.testing.dto.order;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.EnumSet;

import static com.awesome.testing.dto.order.FulfilmentState.*;
import static org.assertj.core.api.Assertions.assertThat;

class FulfilmentStateTest {

    @Test
    void forwardStepsFollowTheShipmentLifecycle() {
        assertThat(NONE.nextStep()).isEqualTo(AWAITING_PICK);
        assertThat(AWAITING_PICK.nextStep()).isEqualTo(PICKED);
        assertThat(PICKED.nextStep()).isEqualTo(PACKED);
        assertThat(PACKED.nextStep()).isEqualTo(SHIPPED);
        assertThat(SHIPPED.nextStep()).isEqualTo(DELIVERED);
        assertThat(DELIVERED.nextStep()).isNull();
        assertThat(CANCELLED.nextStep()).isNull();
        assertThat(Arrays.stream(values()).map(FulfilmentState::nextStep)).doesNotContain(CANCELLED, NONE);
    }

    @Test
    void cancellationAndManualStatusRulesMatchTheContract() {
        assertThat(EnumSet.allOf(FulfilmentState.class)).filteredOn(FulfilmentState::allowsCustomerCancellation)
                .containsExactlyInAnyOrder(NONE, AWAITING_PICK);
        assertThat(EnumSet.allOf(FulfilmentState.class)).filteredOn(FulfilmentState::allowsWarehouseCancellation)
                .containsExactlyInAnyOrder(AWAITING_PICK, PICKED);
        assertThat(EnumSet.allOf(FulfilmentState.class)).filteredOn(FulfilmentState::blocksManualStatusChange)
                .containsExactlyInAnyOrder(AWAITING_PICK, PICKED, PACKED, SHIPPED);
    }

    @Test
    void onlyShippedAndDeliveredImplyAnOrderStatus() {
        assertThat(SHIPPED.impliedOrderStatus()).isEqualTo(OrderStatus.SHIPPED);
        assertThat(DELIVERED.impliedOrderStatus()).isEqualTo(OrderStatus.DELIVERED);
        assertThat(EnumSet.complementOf(EnumSet.of(SHIPPED, DELIVERED)))
                .allSatisfy(state -> assertThat(state.impliedOrderStatus()).isNull());
    }

    @Test
    void legacyNullIsTreatedAsNone() {
        assertThat(orNone(null)).isEqualTo(NONE);
        assertThat(orNone(PACKED)).isEqualTo(PACKED);
    }
}
