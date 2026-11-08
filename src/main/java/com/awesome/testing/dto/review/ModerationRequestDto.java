package com.awesome.testing.dto.review;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Moderation decision. REJECTED requires a note.")
public class ModerationRequestDto {

    @NotNull(message = "Decision is required")
    @Schema(description = "Decision", example = "APPROVED")
    private ModerationDecision decision;

    @Size(max = 500, message = "Note cannot exceed 500 characters")
    @Schema(types = {"string", "null"}, maxLength = 500, description = "Reason shown to the author", example = "Contains a link to another shop")
    private String note;
}
