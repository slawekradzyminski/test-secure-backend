package com.awesome.testing.dto.returns;

import com.awesome.testing.entity.returns.ItemCondition;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Condition of one returned product")
public class ReceiveItemDto {
    @NotNull(message = "Product ID is required")
    @Schema(description = "Product ID", example = "3")
    private Long productId;

    @NotNull(message = "Condition is required")
    @Schema(description = "RESELLABLE items return to stock", example = "RESELLABLE")
    private ItemCondition condition;
}
