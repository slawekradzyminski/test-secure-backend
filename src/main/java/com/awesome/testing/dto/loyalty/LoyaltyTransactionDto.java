package com.awesome.testing.dto.loyalty;

import com.awesome.testing.entity.loyalty.LoyaltyTransactionEntity;
import com.awesome.testing.entity.loyalty.LoyaltyTransactionType;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Loyalty ledger entry")
public class LoyaltyTransactionDto {

    @Schema(description = "Entry ID", example = "7")
    private Long id;

    @Schema(description = "Kind of change", example = "EARNED")
    private LoyaltyTransactionType type;

    @Schema(description = "Signed change: positive for EARNED and RESTORED, negative for REDEEMED and REVOKED", example = "120")
    private long points;

    @Schema(description = "Balance after the change", example = "1250")
    private long balanceAfter;

    @Schema(types = {"integer", "null"}, description = "Order that caused the change", example = "42")
    private Long orderId;

    @Schema(type = "string", format = "local-date-time", description = "When the change happened")
    private LocalDateTime createdAt;

    public static LoyaltyTransactionDto from(LoyaltyTransactionEntity entity) {
        return LoyaltyTransactionDto.builder()
                .id(entity.getId())
                .type(entity.getType())
                .points(entity.getPoints())
                .balanceAfter(entity.getBalanceAfter())
                .orderId(entity.getOrderId())
                .createdAt(entity.getCreatedAt())
                .build();
    }
}
