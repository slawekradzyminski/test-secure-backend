package com.awesome.testing.dto.inventory;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InventoryAdjustmentDto {

    @NotNull
    private Integer delta;

    @NotBlank
    @Size(max = 500)
    private String reason;

    @NotNull
    private UUID requestId;

    @AssertTrue(message = "delta must not be zero")
    public boolean isDeltaNonZero() {
        return delta != null && delta != 0;
    }
}
