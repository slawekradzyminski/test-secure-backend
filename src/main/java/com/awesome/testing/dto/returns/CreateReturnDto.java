package com.awesome.testing.dto.returns;

import com.awesome.testing.entity.returns.ReturnReason;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Return request for a delivered order")
public class CreateReturnDto {
    @NotNull(message = "Reason is required")
    @Schema(description = "Return reason", example = "DAMAGED")
    private ReturnReason reason;

    @Size(max = 1000, message = "Comment cannot exceed 1000 characters")
    @Schema(types = {"string", "null"}, maxLength = 1000, description = "Customer comment", example = "Screen arrived cracked")
    private String comment;

    @NotEmpty(message = "At least one item is required")
    @Schema(description = "Items to return; each product at most once")
    private List<@Valid ReturnItemRequestDto> items;
}
