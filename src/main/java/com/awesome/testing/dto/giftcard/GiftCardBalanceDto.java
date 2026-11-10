package com.awesome.testing.dto.giftcard;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Public view of a gift card: balance, status and expiry only")
public class GiftCardBalanceDto {

    @Schema(description = "Gift card code", example = "GC-7KQ2-M9XD-4HPA")
    private String code;

    @Schema(description = "Remaining balance", example = "25.00")
    private BigDecimal balance;

    @Schema(description = "Effective status", example = "ACTIVE")
    private GiftCardStatus status;

    @Schema(types = {"string", "null"}, format = "local-date-time", description = "Server-local end of validity")
    private LocalDateTime expiresAt;
}
