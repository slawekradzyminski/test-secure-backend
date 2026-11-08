package com.awesome.testing.dto.returns;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Inspection result covering every returned product exactly once")
public class ReceiveReturnDto {
    @NotEmpty(message = "Item conditions are required")
    @Schema(description = "Conditions per product")
    private List<@Valid ReceiveItemDto> items;
}
