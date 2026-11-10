package com.awesome.testing.dto.giftcard;

import com.awesome.testing.entity.giftcard.GiftCardTransactionEntity;
import com.awesome.testing.entity.giftcard.GiftCardTransactionType;
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
@Schema(description = "Gift card ledger entry")
public class GiftCardTransactionDto {

    @Schema(description = "Kind of change", example = "REDEEMED")
    private GiftCardTransactionType type;

    @Schema(description = "Signed balance change: positive for ISSUED and RESTORED, negative for REDEEMED, zero for DISABLED",
            example = "-25.00")
    private BigDecimal amount;

    @Schema(description = "Balance after the change", example = "25.00")
    private BigDecimal balanceAfter;

    @Schema(types = {"integer", "null"}, description = "Order that redeemed or got money back", example = "42")
    private Long orderId;

    @Schema(types = {"string", "null"}, description = "Administrator or customer who caused the change", example = "admin")
    private String actor;

    @Schema(type = "string", format = "local-date-time", description = "When the change happened")
    private LocalDateTime createdAt;

    public static GiftCardTransactionDto from(GiftCardTransactionEntity entity) {
        return GiftCardTransactionDto.builder()
                .type(entity.getType())
                .amount(entity.getAmount())
                .balanceAfter(entity.getBalanceAfter())
                .orderId(entity.getOrderId())
                .actor(entity.getActor())
                .createdAt(entity.getCreatedAt())
                .build();
    }
}
