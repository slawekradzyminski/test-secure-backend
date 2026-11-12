package com.awesome.testing.dto.giftcard;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Gift card code to apply to the cart")
public class ApplyGiftCardDto {

    // @NotBlank alone covers empty input: a second constraint failing on "" would make the reported message random.
    @NotBlank(message = "Gift card code is required")
    @Size(max = 32, message = "Gift card code must be 1-32 characters")
    @Schema(minLength = 1, pattern = "\\S", maxLength = 32, description = "Case-insensitive code in the format GC-XXXX-XXXX-XXXX",
            example = "gc-7kq2-m9xd-4hpa")
    private String code;
}
