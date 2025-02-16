package com.awesome.testing.dto.qr;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Schema(description = "QR link")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CreateQrDto {

    @NotBlank(message = "Text is required")
    @Schema(description = "Text to use in QR code", example = "https://awesome-testing.com")
    private String text;

}
