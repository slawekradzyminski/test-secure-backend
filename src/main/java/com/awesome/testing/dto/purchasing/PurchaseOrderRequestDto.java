package com.awesome.testing.dto.purchasing;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Draft purchase order; lines may be empty until submission")
public class PurchaseOrderRequestDto {
    @NotNull(message = "Supplier ID is required")
    @Schema(description = "Supplier ID", example = "1")
    private Long supplierId;

    @Size(max = 500, message = "Note cannot exceed 500 characters")
    @Schema(types = {"string", "null"}, maxLength = 500, description = "Internal note", example = "Restock before Black Friday")
    private String note;

    @Builder.Default
    @Size(max = 200, message = "A purchase order can have at most 200 lines")
    @Schema(description = "Lines; each product at most once")
    private List<@Valid PurchaseOrderLineRequestDto> lines = new ArrayList<>();
}
