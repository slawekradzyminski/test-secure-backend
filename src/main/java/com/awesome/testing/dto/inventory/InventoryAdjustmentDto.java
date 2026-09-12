package com.awesome.testing.dto.inventory;

import io.swagger.v3.oas.annotations.media.Schema;
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
    @Schema(description = "Nonzero change in stock; positive adds and negative subtracts", not = ZeroInventoryDelta.class)
    private Integer delta;

    @NotBlank
    @Size(max = 500)
    @Schema(minLength = 1, pattern = "\\S")
    private String reason;

    @NotNull
    private UUID requestId;

    @AssertTrue(message = "delta must not be zero")
    @Schema(accessMode = Schema.AccessMode.READ_ONLY, description = "Computed validation result; supply a nonzero delta")
    public boolean isDeltaNonZero() {
        return delta != null && delta != 0;
    }

    @Schema(type = "integer", allowableValues = {"0"})
    private static class ZeroInventoryDelta {}
}
