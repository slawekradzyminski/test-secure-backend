package com.awesome.testing.dto.purchasing;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Goods receipt. Repeating requestId with the same lines replays the receipt; different lines return 409.")
public class ReceiptRequestDto {
    @NotNull(message = "requestId is required")
    @Schema(description = "Client-generated idempotency key", example = "2f1c8a8e-3b0e-4d39-9d43-0a6d6f7f0a11")
    private UUID requestId;

    @NotEmpty(message = "At least one receipt line is required")
    @Schema(description = "Lines; each product at most once")
    private List<@Valid ReceiptLineDto> lines;
}
