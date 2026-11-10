package com.awesome.testing.dto.giftcard;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Gift card as seen by administrators")
public class GiftCardDto {

    @Schema(description = "Generated code", example = "GC-7KQ2-M9XD-4HPA")
    private String code;

    @Schema(description = "Amount the card was issued with", example = "50.00")
    private BigDecimal initialAmount;

    @Schema(description = "Remaining balance", example = "25.00")
    private BigDecimal balance;

    @Schema(description = "Effective status", example = "ACTIVE")
    private GiftCardStatus status;

    @Schema(types = {"string", "null"}, format = "local-date-time", description = "Server-local end of validity")
    private LocalDateTime expiresAt;

    @Schema(types = {"string", "null"}, description = "User who was emailed the code", example = "client")
    private String recipientUsername;

    @Schema(types = {"string", "null"}, description = "Internal note", example = "Apology for a late delivery")
    private String note;

    @Schema(description = "Administrator who issued the card", example = "admin")
    private String createdBy;

    @Schema(type = "string", format = "local-date-time", description = "Creation timestamp")
    private LocalDateTime createdAt;

    @Schema(type = "string", format = "local-date-time", description = "Last update timestamp")
    private LocalDateTime updatedAt;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    @Schema(description = "Ledger, oldest first; only in single-card responses")
    private List<GiftCardTransactionDto> transactions;
}
