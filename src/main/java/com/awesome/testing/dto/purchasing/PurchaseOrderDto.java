package com.awesome.testing.dto.purchasing;

import com.awesome.testing.entity.purchasing.PurchaseOrderStatus;
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
@Schema(description = "Purchase order")
public class PurchaseOrderDto {
    @Schema(description = "Purchase order ID", example = "1")
    private Long id;
    @Schema(description = "Supplier ID", example = "1")
    private Long supplierId;
    @Schema(description = "Supplier name", example = "Nordic Electronics")
    private String supplierName;
    @Schema(description = "Status", example = "SUBMITTED")
    private PurchaseOrderStatus status;
    @Schema(types = {"string", "null"}, description = "Internal note")
    private String note;
    @Schema(description = "Creator", example = "admin")
    private String createdBy;
    @Schema(description = "Sum of line totals", example = "32000.00")
    private BigDecimal totalCost;
    @Schema(description = "Lines")
    private List<PurchaseOrderLineDto> lines;
    @Schema(type = "string", format = "local-date-time", description = "Creation timestamp")
    private LocalDateTime createdAt;
    @Schema(type = "string", format = "local-date-time", description = "Last update timestamp")
    private LocalDateTime updatedAt;
    @Schema(types = {"string", "null"}, format = "local-date-time", description = "Submission timestamp")
    private LocalDateTime submittedAt;
    @Schema(types = {"string", "null"}, format = "local-date-time", description = "submittedAt plus the supplier lead time")
    private LocalDateTime expectedAt;
    @Schema(types = {"string", "null"}, format = "local-date-time", description = "When the last units arrived")
    private LocalDateTime receivedAt;
    @Schema(types = {"string", "null"}, format = "local-date-time", description = "Cancellation timestamp")
    private LocalDateTime cancelledAt;
}
