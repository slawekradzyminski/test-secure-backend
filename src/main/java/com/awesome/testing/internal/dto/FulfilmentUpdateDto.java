package com.awesome.testing.internal.dto;

import com.awesome.testing.dto.order.FulfilmentState;
import jakarta.validation.constraints.NotNull;

public record FulfilmentUpdateDto(@NotNull(message = "state is required") FulfilmentState state) {
}
