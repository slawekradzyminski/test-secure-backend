package com.awesome.testing.dto.returns;

import com.awesome.testing.entity.returns.ReturnReason;
import com.awesome.testing.entity.returns.ReturnStatus;
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
@Schema(description = "Return request (RMA)")
public class ReturnDto {
    @Schema(description = "Return ID", example = "1")
    private Long id;
    @Schema(description = "Order ID", example = "42")
    private Long orderId;
    @Schema(description = "Customer", example = "client")
    private String username;
    @Schema(description = "Status", example = "REQUESTED")
    private ReturnStatus status;
    @Schema(description = "Reason", example = "DAMAGED")
    private ReturnReason reason;
    @Schema(types = {"string", "null"}, description = "Customer comment")
    private String comment;
    @Schema(types = {"string", "null"}, description = "Administrator note")
    private String resolutionNote;
    @Schema(description = "Refund the customer receives, proportional to any order discount", example = "899.99")
    private BigDecimal estimatedRefund;
    @Schema(types = {"number", "null"}, description = "Refund paid; set when REFUNDED", example = "899.99")
    private BigDecimal refundAmount;
    @Schema(description = "Returned items")
    private List<ReturnItemDto> items;
    @Schema(type = "string", format = "local-date-time", description = "Creation timestamp")
    private LocalDateTime createdAt;
    @Schema(type = "string", format = "local-date-time", description = "Last update timestamp")
    private LocalDateTime updatedAt;
    @Schema(types = {"string", "null"}, format = "local-date-time", description = "Approval timestamp")
    private LocalDateTime approvedAt;
    @Schema(types = {"string", "null"}, format = "local-date-time", description = "Rejection timestamp")
    private LocalDateTime rejectedAt;
    @Schema(types = {"string", "null"}, format = "local-date-time", description = "Receipt timestamp")
    private LocalDateTime receivedAt;
    @Schema(types = {"string", "null"}, format = "local-date-time", description = "Refund timestamp")
    private LocalDateTime refundedAt;
}
